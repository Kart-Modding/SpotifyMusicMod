![Spoty Widget](https://raw.githubusercontent.com/Kart-Modding/SpotifyMusicMod/main/docs/images/banner.png)

Shows the song you're playing in a corner of the screen: album art, title, artist, time and a little equaliser. No more alt-tabbing to check what's on.

**No Spotify account. No API key. No login.** The mod reads what the Spotify desktop app already reports to your system's media controls.

![Widget in game](https://cdn.modrinth.com/data/pjhN6TWN/images/bffd731843d1b21b0347f1ebd6a5b42a2ffa25eb.png)

---

## Features

- **5 designs:** Classic, Progress bar, Compact, Above hotbar and Vanilla tooltip. Switch with one button.
- **Your layout:** any corner, scale from 0.5x to 2x, adjustable offset and background opacity. Album art, timestamps and equaliser can each be turned off.
- **Show it when you want:** always, or for 5 or 15 seconds when a song starts and before it ends. It can also hide while paused or while a menu is open.
- **Media keys in game:** next and previous track, volume up and down, on the arrow keys by default. Change them in Options → Controls → Spoty Widget.
- **Jukebox aware:** when a record plays nearby, the widget shows the disc and Spotify pauses. Spotify resumes when the record ends.
- **Other players too:** set Player to "Any player" to also show browsers, VLC and more.

![The five designs](https://raw.githubusercontent.com/Kart-Modding/SpotifyMusicMod/main/docs/images/designs.png)

---

## How it works

![How it works](https://raw.githubusercontent.com/Kart-Modding/SpotifyMusicMod/main/docs/images/how-it-works.png)

- **Windows:** reads the media session behind the volume flyout.
- **Linux:** reads the Spotify MPRIS player through `gdbus` or `playerctl`.
- **Album art:** looked up by artist and title on Deezer or iTunes. On Linux, Spotify provides the cover directly.
- **Client side only:** the mod never talks to the server you play on, so it works on any server.

---

## Install

1. Download the jar for your loader and Minecraft version.
2. Put it in your `mods` folder.
3. Start Spotify, play something and launch the game.

| Loader | Needs |
|---|---|
| Fabric | Fabric API. Mod Menu is optional (settings screen) |
| Quilt | Use the Fabric jar, with Fabric API or QFAPI |
| NeoForge | Nothing extra. Settings: Mods → Spoty Widget → Config |
| Forge | Nothing extra. Settings: Mods → Spoty Widget → Config |

**Minecraft 1.21 to 26.3.** The jar suffix tells you which versions it covers:

`+1.21.1` `+1.21.3` `+1.21.4` `+1.21.5` `+1.21.8` `+1.21.10` `+1.21.11` `+26.1.2` `+26.2` `+26.3`

**Windows and Linux only.** macOS is not supported.

---

Also available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/spoty-widget). Source code on [GitHub](https://github.com/Kart-Modding/SpotifyMusicMod).

Suggestions or problems? Open an [issue](https://github.com/Kart-Modding/SpotifyMusicMod/issues) or visit [my links](https://link.karton.dev/).
