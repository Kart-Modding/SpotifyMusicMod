# Promotional art

| Folder | What's in it |
|---|---|
| `original/` | The in-game screenshots, untouched (1920x1200) |
| `compressed/` | The same screenshots as JPEGs, small enough for the mod pages |
| `infographics/` | The finished images used on Modrinth and CurseForge |
| `infographics/psd/` | Layered Photoshop files for each infographic: open them to edit by hand |
| `source/` | What the infographics are built from: one HTML page each, plus `build.js` |

## Editing in Photoshop (or Photopea, GIMP, Affinity)

Every PSD has named layers and groups. Headings and captions are live **text layers** set in Inter and DejaVu
Sans Mono, so you can retype them; install [Inter](https://rsms.me/inter/) first, or Photoshop swaps in another
font. Screenshots, cards and icons are plain pixel layers. After editing, export the PNG over the matching file
in `infographics/`.

## Rebuilding from the HTML

Changing a page in `source/pages/` and rebuilding regenerates both the PNG and the PSD (this overwrites hand
edits made to the PSD):

```bash
cd Promotional_art/source
npm install
node build.js              # every page plus compressed/, or e.g. `node build.js banner`
```

It renders with Microsoft Edge. Fonts come from `node_modules`.

## Icons

The icons in `features` are from [Lucide](https://lucide.dev), under the ISC license. The license allows use
and changes, as long as its notice is kept: see `LUCIDE-LICENSE.txt`.
