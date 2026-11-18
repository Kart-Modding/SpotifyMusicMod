package dev.karton.spotifywidget.media;

import com.google.gson.JsonObject;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import dev.karton.spotifywidget.SpotifyWidgetClient;

import java.util.Locale;

/**
 * Talks to the Windows media session (SMTC) straight from Java through WinRT, using the JNA copy
 * Minecraft already ships. This is the fallback for when PowerShell cannot be started, typically
 * because an antivirus blocks it: no extra process is launched, so there is nothing to block.
 *
 * <p>WinRT objects are COM interfaces, so every call below goes through the object's vtable. The
 * slot numbers follow the interface order in windows.media.control.idl; slots 0-5 are IUnknown and
 * IInspectable on every interface.
 */
final class WindowsNativeMedia {
    private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final String IID_MANAGER_STATICS = "2050C4EE-11A0-57DE-AED7-C97C70338245";
    private static final String IID_ASYNC_INFO = "00000036-0000-0000-C000-000000000046";

    private static final int RO_INIT_MULTITHREADED = 1;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    // IUnknown
    private static final int QUERY_INTERFACE = 0;
    private static final int RELEASE = 2;
    // IGlobalSystemMediaTransportControlsSessionManagerStatics
    private static final int STATICS_REQUEST_ASYNC = 6;
    // IGlobalSystemMediaTransportControlsSessionManager
    private static final int MANAGER_GET_CURRENT_SESSION = 6;
    private static final int MANAGER_GET_SESSIONS = 7;
    // IVectorView<T>
    private static final int VECTOR_GET_AT = 6;
    private static final int VECTOR_GET_SIZE = 7;
    // IGlobalSystemMediaTransportControlsSession
    private static final int SESSION_APP_ID = 6;
    private static final int SESSION_MEDIA_PROPERTIES_ASYNC = 7;
    private static final int SESSION_TIMELINE = 8;
    private static final int SESSION_PLAYBACK_INFO = 9;
    private static final int SESSION_PLAY = 10;
    private static final int SESSION_PAUSE = 11;
    private static final int SESSION_SKIP_NEXT = 16;
    private static final int SESSION_SKIP_PREVIOUS = 17;
    private static final int SESSION_TOGGLE_PLAY_PAUSE = 20;
    // IGlobalSystemMediaTransportControlsSessionMediaProperties
    private static final int PROPS_TITLE = 6;
    private static final int PROPS_ARTIST = 9;
    private static final int PROPS_ALBUM = 10;
    // IGlobalSystemMediaTransportControlsSessionTimelineProperties
    private static final int TIMELINE_END_TIME = 7;
    private static final int TIMELINE_POSITION = 10;
    // IGlobalSystemMediaTransportControlsSessionPlaybackInfo
    private static final int PLAYBACK_STATUS = 7;
    // IAsyncOperation<T>
    private static final int ASYNC_OP_GET_RESULTS = 8;
    // IAsyncInfo
    private static final int ASYNC_INFO_STATUS = 7;
    private static final int ASYNC_INFO_CANCEL = 9;

    private static final int ASYNC_STARTED = 0;
    private static final int ASYNC_COMPLETED = 1;

    private static final byte VK_VOLUME_DOWN = (byte) 0xAE;
    private static final byte VK_VOLUME_UP = (byte) 0xAF;
    private static final int KEYEVENTF_KEYUP = 2;

    /** Set once PowerShell failed to start, so the source and the controls both stop trying it. */
    private static volatile boolean powershellBlocked;

    private static final Object LOCK = new Object();
    private static final ThreadLocal<Boolean> THREAD_READY = new ThreadLocal<>();
    private static Boolean loaded;
    private static NativeLibrary combase;
    private static Pointer manager;

    private WindowsNativeMedia() {
    }

    static boolean powershellBlocked() {
        return powershellBlocked;
    }

    static void markPowershellBlocked(String reason) {
        if (powershellBlocked) return;
        powershellBlocked = true;
        SpotifyWidgetClient.LOGGER.warn("PowerShell is not available ({}), reading the Windows media session"
                + " directly instead.", reason);
    }

    /** True when the WinRT entry points could be loaded. Never throws. */
    static boolean available() {
        synchronized (LOCK) {
            if (loaded == null) {
                try {
                    combase = NativeLibrary.getInstance("combase");
                    combase.getFunction("RoGetActivationFactory");
                    loaded = true;
                } catch (Throwable t) {
                    SpotifyWidgetClient.LOGGER.warn("Native Windows media session is not available: {}", t.toString());
                    loaded = false;
                }
            }
            return loaded;
        }
    }

    /**
     * Reads the current session in the same JSON shape the PowerShell helper prints, so both paths
     * share one parser.
     */
    static JsonObject poll(boolean spotifyOnly) {
        JsonObject json = new JsonObject();
        synchronized (LOCK) {
            Pointer session = null;
            Pointer props = null;
            Pointer timeline = null;
            Pointer playback = null;
            try {
                session = findSession(spotifyOnly);
                if (session == null) {
                    json.addProperty("state", "none");
                    return json;
                }
                props = awaitObject(callForObject(session, SESSION_MEDIA_PROPERTIES_ASYNC), 5000);
                if (props == null) {
                    json.addProperty("state", "none");
                    return json;
                }
                timeline = callForObject(session, SESSION_TIMELINE);
                playback = callForObject(session, SESSION_PLAYBACK_INFO);

                json.addProperty("state", "ok");
                json.addProperty("app", callForString(session, SESSION_APP_ID));
                json.addProperty("title", callForString(props, PROPS_TITLE));
                json.addProperty("artist", callForString(props, PROPS_ARTIST));
                json.addProperty("album", callForString(props, PROPS_ALBUM));
                json.addProperty("status", playbackStatus(callForInt(playback, PLAYBACK_STATUS)));
                json.addProperty("position", callForTimeSpanMillis(timeline, TIMELINE_POSITION));
                json.addProperty("duration", callForTimeSpanMillis(timeline, TIMELINE_END_TIME));
                return json;
            } catch (Exception e) {
                // The session manager may have gone stale; ask for a fresh one next time
                dropManager();
                json = new JsonObject();
                json.addProperty("state", "error");
                json.addProperty("message", e.getMessage());
                return json;
            } finally {
                release(playback);
                release(timeline);
                release(props);
                release(session);
            }
        }
    }

    /** Runs one of the control commands (next, prev, playpause, play, pause, volup, voldown). */
    static boolean command(String command, boolean spotifyOnly) throws Exception {
        switch (command) {
            case "volup" -> {
                tapKey(VK_VOLUME_UP);
                return true;
            }
            case "voldown" -> {
                tapKey(VK_VOLUME_DOWN);
                return true;
            }
            default -> {
                // handled below
            }
        }
        int slot = switch (command) {
            case "next" -> SESSION_SKIP_NEXT;
            case "prev" -> SESSION_SKIP_PREVIOUS;
            case "playpause" -> SESSION_TOGGLE_PLAY_PAUSE;
            case "play" -> SESSION_PLAY;
            case "pause" -> SESSION_PAUSE;
            default -> -1;
        };
        if (slot < 0) return false;
        synchronized (LOCK) {
            Pointer session = null;
            try {
                session = findSession(spotifyOnly);
                if (session == null) return false;
                return awaitBoolean(callForObject(session, slot), 4000);
            } catch (Exception e) {
                dropManager();
                throw e;
            } finally {
                release(session);
            }
        }
    }

    // --- Sessions --------------------------------------------------------------

    /** Spotify's session if there is one, otherwise the current session unless spotifyOnly. */
    private static Pointer findSession(boolean spotifyOnly) throws Exception {
        Pointer mgr = manager();
        Pointer sessions = callForObject(mgr, MANAGER_GET_SESSIONS);
        try {
            int size = callForInt(sessions, VECTOR_GET_SIZE);
            for (int i = 0; i < size; i++) {
                Memory out = new Memory(Native.POINTER_SIZE);
                out.setPointer(0, null);
                check(vtable(sessions, VECTOR_GET_AT).invokeInt(new Object[]{sessions, i, out}), "GetAt");
                Pointer candidate = out.getPointer(0);
                if (candidate == null) continue;
                String app = callForString(candidate, SESSION_APP_ID);
                if (app.toLowerCase(Locale.ROOT).contains("spotify")) return candidate;
                release(candidate);
            }
        } finally {
            release(sessions);
        }
        if (spotifyOnly) return null;
        return callForObject(mgr, MANAGER_GET_CURRENT_SESSION);
    }

    private static Pointer manager() throws Exception {
        if (manager != null) return manager;
        initThread();
        long classId = createHString(MANAGER_CLASS);
        Pointer statics;
        try {
            Memory out = new Memory(Native.POINTER_SIZE);
            out.setPointer(0, null);
            check(combase.getFunction("RoGetActivationFactory", Function.ALT_CONVENTION)
                    .invokeInt(new Object[]{new Pointer(classId), guid(IID_MANAGER_STATICS), out}), "RoGetActivationFactory");
            statics = out.getPointer(0);
        } finally {
            deleteHString(classId);
        }
        try {
            Pointer result = awaitObject(callForObject(statics, STATICS_REQUEST_ASYNC), 5000);
            if (result == null) throw new IllegalStateException("no media session manager");
            manager = result;
            return manager;
        } finally {
            release(statics);
        }
    }

    private static void dropManager() {
        release(manager);
        manager = null;
    }

    /** Every thread that touches WinRT has to join an apartment first. */
    private static void initThread() throws Exception {
        if (THREAD_READY.get() != null) return;
        int hr = combase.getFunction("RoInitialize", Function.ALT_CONVENTION)
                .invokeInt(new Object[]{RO_INIT_MULTITHREADED});
        // S_FALSE (already initialised) and RPC_E_CHANGED_MODE (already in another apartment) are fine
        if (hr < 0 && hr != RPC_E_CHANGED_MODE) check(hr, "RoInitialize");
        THREAD_READY.set(Boolean.TRUE);
    }

    // --- Async operations ------------------------------------------------------

    /** Waits for an IAsyncOperation that yields an object, releases the operation, returns the object. */
    private static Pointer awaitObject(Pointer operation, long timeoutMillis) throws Exception {
        if (operation == null) return null;
        try {
            if (!waitForCompletion(operation, timeoutMillis)) return null;
            Memory out = new Memory(Native.POINTER_SIZE);
            out.setPointer(0, null);
            check(vtable(operation, ASYNC_OP_GET_RESULTS).invokeInt(new Object[]{operation, out}), "GetResults");
            return out.getPointer(0);
        } finally {
            release(operation);
        }
    }

    private static boolean awaitBoolean(Pointer operation, long timeoutMillis) throws Exception {
        if (operation == null) return false;
        try {
            if (!waitForCompletion(operation, timeoutMillis)) return false;
            Memory out = new Memory(4);
            out.setInt(0, 0);
            check(vtable(operation, ASYNC_OP_GET_RESULTS).invokeInt(new Object[]{operation, out}), "GetResults");
            return out.getByte(0) != 0;
        } finally {
            release(operation);
        }
    }

    /** Polls IAsyncInfo.Status; true when the operation completed successfully in time. */
    private static boolean waitForCompletion(Pointer operation, long timeoutMillis) throws Exception {
        Memory out = new Memory(Native.POINTER_SIZE);
        out.setPointer(0, null);
        check(vtable(operation, QUERY_INTERFACE).invokeInt(new Object[]{operation, guid(IID_ASYNC_INFO), out}),
                "QueryInterface(IAsyncInfo)");
        Pointer info = out.getPointer(0);
        try {
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
            while (true) {
                int status = callForInt(info, ASYNC_INFO_STATUS);
                if (status == ASYNC_COMPLETED) return true;
                if (status != ASYNC_STARTED) return false; // canceled or error
                if (System.nanoTime() > deadline) {
                    vtable(info, ASYNC_INFO_CANCEL).invokeInt(new Object[]{info});
                    return false;
                }
                Thread.sleep(5);
            }
        } finally {
            release(info);
        }
    }

    // --- Vtable calls ----------------------------------------------------------

    private static Function vtable(Pointer object, int slot) {
        Pointer table = object.getPointer(0);
        return Function.getFunction(table.getPointer((long) slot * Native.POINTER_SIZE), Function.ALT_CONVENTION);
    }

    private static Pointer callForObject(Pointer object, int slot) throws Exception {
        Memory out = new Memory(Native.POINTER_SIZE);
        out.setPointer(0, null);
        check(vtable(object, slot).invokeInt(new Object[]{object, out}), "slot " + slot);
        return out.getPointer(0);
    }

    private static int callForInt(Pointer object, int slot) throws Exception {
        Memory out = new Memory(4);
        out.setInt(0, 0);
        check(vtable(object, slot).invokeInt(new Object[]{object, out}), "slot " + slot);
        return out.getInt(0);
    }

    /** TimeSpan is a struct holding one int64 of 100ns ticks. */
    private static long callForTimeSpanMillis(Pointer object, int slot) throws Exception {
        Memory out = new Memory(8);
        out.setLong(0, 0);
        check(vtable(object, slot).invokeInt(new Object[]{object, out}), "slot " + slot);
        return out.getLong(0) / 10_000L;
    }

    private static String callForString(Pointer object, int slot) throws Exception {
        Memory out = new Memory(Native.POINTER_SIZE);
        out.setPointer(0, null);
        check(vtable(object, slot).invokeInt(new Object[]{object, out}), "slot " + slot);
        long hstring = Pointer.nativeValue(out.getPointer(0));
        try {
            return readHString(hstring);
        } finally {
            deleteHString(hstring);
        }
    }

    private static void release(Pointer object) {
        if (object == null) return;
        try {
            vtable(object, RELEASE).invokeInt(new Object[]{object});
        } catch (Throwable ignored) {
            // nothing sensible to do
        }
    }

    // --- HSTRING / GUID helpers ------------------------------------------------

    private static long createHString(String value) throws Exception {
        Memory out = new Memory(Native.POINTER_SIZE);
        out.setPointer(0, null);
        check(combase.getFunction("WindowsCreateString", Function.ALT_CONVENTION)
                .invokeInt(new Object[]{new WString(value), value.length(), out}), "WindowsCreateString");
        return Pointer.nativeValue(out.getPointer(0));
    }

    private static String readHString(long hstring) {
        if (hstring == 0) return ""; // a null HSTRING is the empty string
        Memory length = new Memory(4);
        length.setInt(0, 0);
        Pointer buffer = combase.getFunction("WindowsGetStringRawBuffer", Function.ALT_CONVENTION)
                .invokePointer(new Object[]{new Pointer(hstring), length});
        int chars = length.getInt(0);
        if (buffer == null || chars <= 0) return "";
        return new String(buffer.getCharArray(0, chars));
    }

    private static void deleteHString(long hstring) {
        if (hstring == 0) return;
        combase.getFunction("WindowsDeleteString", Function.ALT_CONVENTION)
                .invokeInt(new Object[]{new Pointer(hstring)});
    }

    private static Memory guid(String text) {
        String hex = text.replace("-", "");
        Memory guid = new Memory(16);
        guid.setInt(0, (int) Long.parseLong(hex.substring(0, 8), 16));
        guid.setShort(4, (short) Integer.parseInt(hex.substring(8, 12), 16));
        guid.setShort(6, (short) Integer.parseInt(hex.substring(12, 16), 16));
        for (int i = 0; i < 8; i++) {
            guid.setByte(8 + i, (byte) Integer.parseInt(hex.substring(16 + i * 2, 18 + i * 2), 16));
        }
        return guid;
    }

    private static void check(int hresult, String what) throws Exception {
        if (hresult < 0) {
            throw new IllegalStateException(what + " failed: 0x" + Integer.toHexString(hresult).toUpperCase(Locale.ROOT));
        }
    }

    private static String playbackStatus(int status) {
        return switch (status) {
            case 0 -> "Closed";
            case 1 -> "Opened";
            case 2 -> "Changing";
            case 3 -> "Stopped";
            case 4 -> "Playing";
            case 5 -> "Paused";
            default -> "Unknown";
        };
    }

    // --- Volume keys -----------------------------------------------------------

    private static void tapKey(byte key) {
        Function keybdEvent = NativeLibrary.getInstance("user32").getFunction("keybd_event", Function.ALT_CONVENTION);
        keybdEvent.invokeVoid(new Object[]{key, (byte) 0, 0, Pointer.NULL});
        keybdEvent.invokeVoid(new Object[]{key, (byte) 0, KEYEVENTF_KEYUP, Pointer.NULL});
    }
}
