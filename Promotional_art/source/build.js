// Builds the promotional infographics: one PNG and one layered PSD per page in pages/.
//
//   cd Promotional_art/source
//   npm install
//   node build.js            # every page
//   node build.js banner     # just pages/banner.html
//
// Each page is plain HTML. Elements marked up with data-* attributes become PSD layers:
//   data-group="Name"   a layer group (folder) holding the layers inside it
//   data-layer="Name"   a pixel layer: the element painted on its own (nested layers are left out)
//   data-text           on a data-layer: also written as live, editable Photoshop text
// Layers stack in document order, the first one at the bottom. Anything that paints must sit inside a
// data-layer, otherwise it is only in the PNG; the build warns when the layers don't add up to the PNG.
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require('playwright');
const { writePsdBuffer } = require('ag-psd');
const { PNG } = require('pngjs');

const SRC = __dirname;
const OUT = path.join(SRC, '..', 'infographics');
const OUT_PSD = path.join(OUT, 'psd');
const COMPRESSED = path.join(SRC, '..', 'compressed');
const ORIGINAL = path.join(SRC, '..', 'original');

// CSS font family + weight -> PostScript name Photoshop looks the font up by
const PS_FONTS = {
    'Inter': { 400: 'Inter-Regular', 500: 'Inter-Medium', 600: 'Inter-SemiBold', 700: 'Inter-Bold', 800: 'Inter-ExtraBold', 900: 'Inter-Black' },
    'Inter Display': { 400: 'InterDisplay-Regular', 500: 'InterDisplay-Medium', 600: 'InterDisplay-SemiBold', 700: 'InterDisplay-Bold', 800: 'InterDisplay-ExtraBold', 900: 'InterDisplay-Black' },
    'DejaVu Sans Mono': { 400: 'DejaVuSansMono', 700: 'DejaVuSansMono-Bold' },
};

function psFont(family, weight) {
    const fam = family.split(',')[0].trim().replace(/["']/g, '');
    const table = PS_FONTS[fam];
    if (!table) throw new Error(`No PostScript name for font "${fam}" - add it to PS_FONTS`);
    const w = Object.keys(table).map(Number).reduce((a, b) => Math.abs(b - weight) < Math.abs(a - weight) ? b : a);
    return table[w];
}

function rgb(css) {
    const m = css.match(/rgba?\(([\d.]+),\s*([\d.]+),\s*([\d.]+)/);
    return { r: +m[1], g: +m[2], b: +m[3] };
}

// Runs in the page: the layer tree, with each text layer's runs and metrics
function collectTree() {
    let next = 0;
    function textInfo(el) {
        const runs = [];
        const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
        for (let n = walker.nextNode(); n; n = walker.nextNode()) {
            if (n.nodeType === 1) {
                if (n.tagName === 'BR') runs.push({ text: '\r', style: getComputedStyle(n.parentElement) });
                continue;
            }
            const t = n.textContent.replace(/\s+/g, ' ');
            if (t) runs.push({ text: t, style: getComputedStyle(n.parentElement) });
        }
        // trim the ends the way the browser renders them
        if (runs.length) {
            runs[0].text = runs[0].text.replace(/^ /, '');
            runs[runs.length - 1].text = runs[runs.length - 1].text.replace(/ $/, '');
        }
        const probe = document.createElement('span');
        probe.style.cssText = 'display:inline-block;width:0;height:0;vertical-align:baseline';
        el.prepend(probe);
        const baseline = probe.getBoundingClientRect().bottom;
        probe.remove();
        const range = document.createRange();
        range.selectNodeContents(el);
        const lines = new Set([...range.getClientRects()].map(r => Math.round(r.top))).size;
        const cs = getComputedStyle(el);
        const r = el.getBoundingClientRect();
        const tr = range.getBoundingClientRect();
        return {
            baseline, lines, align: cs.textAlign,
            box: { x: r.left + parseFloat(cs.paddingLeft), y: r.top + parseFloat(cs.paddingTop), w: el.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight), h: el.clientHeight },
            ink: { x: tr.left, w: tr.width },
            runs: runs.map(({ text, style }) => ({
                text, family: style.fontFamily, weight: +style.fontWeight, size: parseFloat(style.fontSize),
                color: style.color, letterSpacing: parseFloat(style.letterSpacing) || 0,
                lineHeight: style.lineHeight === 'normal' ? null : parseFloat(style.lineHeight),
            })),
        };
    }
    function walk(el) {
        const out = [];
        for (const c of el.children) {
            if (c.dataset.group !== undefined) {
                out.push({ kind: 'group', name: c.dataset.group, children: walk(c) });
            } else if (c.dataset.layer !== undefined) {
                const id = String(next++);
                c.dataset.lid = id;
                out.push({ kind: 'layer', id, name: c.dataset.layer, text: c.dataset.text !== undefined ? textInfo(c) : null });
                out.push(...walk(c));
            } else {
                out.push(...walk(c));
            }
        }
        return out;
    }
    return walk(document.body);
}

function trim(png) {
    const { width: w, height: h, data } = png;
    let x0 = w, y0 = h, x1 = -1, y1 = -1;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        if (data[(y * w + x) * 4 + 3]) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    }
    if (x1 < 0) return null;
    const cw = x1 - x0 + 1, ch = y1 - y0 + 1, out = new Uint8ClampedArray(cw * ch * 4);
    for (let y = 0; y < ch; y++) out.set(data.subarray(((y + y0) * w + x0) * 4, ((y + y0) * w + x1 + 1) * 4), y * cw * 4);
    return { x0, y0, data: { width: cw, height: ch, data: out } };
}

function textData(t) {
    const style = run => ({
        font: { name: psFont(run.family, run.weight) },
        fontSize: run.size,
        fillColor: rgb(run.color),
        tracking: Math.round(run.letterSpacing / run.size * 1000),
        ...(run.lineHeight && t.lines > 1 ? { autoLeading: false, leading: run.lineHeight } : { autoLeading: true }),
    });
    const text = t.runs.map(r => r.text).join('');
    const justification = { center: 'center', right: 'right', end: 'right' }[t.align] || 'left';
    const data = {
        text,
        antiAlias: 'smooth',
        style: style(t.runs[0]),
        styleRuns: t.runs.map(r => ({ length: r.text.length, style: style(r) })),
        paragraphStyle: { justification },
    };
    if (t.lines > 1 && !t.runs.some(r => r.text === '\r')) {
        // Wrapped paragraph: box text that keeps the element's width, so it still wraps when edited
        data.shapeType = 'box';
        data.transform = [1, 0, 0, 1, t.box.x, t.box.y];
        data.boxBounds = [0, 0, t.box.w + 2, t.box.h + 8];
    } else {
        const x = justification === 'center' ? t.ink.x + t.ink.w / 2 : justification === 'right' ? t.ink.x + t.ink.w : t.ink.x;
        data.transform = [1, 0, 0, 1, x, t.baseline];
    }
    return data;
}

async function build(browser, name) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 1 });
    await page.goto(pathToFileURL(path.join(SRC, 'pages', name + '.html')).href);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForLoadState('networkidle');
    const size = await page.evaluate(() => ({ width: document.body.scrollWidth, height: document.body.scrollHeight }));
    await page.setViewportSize(size);

    fs.writeFileSync(path.join(OUT, name + '.png'), await page.screenshot({ fullPage: false }));
    const flat = PNG.sync.read(fs.readFileSync(path.join(OUT, name + '.png')));

    const tree = await page.evaluate(collectTree);
    await page.addStyleTag({ content: `
        html, body { background: transparent !important; }
        [data-layer] { visibility: hidden !important; }
        [data-layer].solo, [data-layer].solo * { visibility: visible !important; }
        [data-layer].solo [data-layer], [data-layer].solo [data-layer] * { visibility: hidden !important; }` });

    const composite = new Uint8ClampedArray(size.width * size.height * 4);
    async function toPsd(nodes) {
        const out = [];
        for (const n of nodes) {
            if (n.kind === 'group') {
                out.push({ name: n.name, opened: true, children: await toPsd(n.children) });
                continue;
            }
            await page.evaluate(id => {
                document.querySelectorAll('.solo').forEach(e => e.classList.remove('solo'));
                document.querySelector(`[data-lid="${id}"]`).classList.add('solo');
            }, n.id);
            const shot = PNG.sync.read(await page.screenshot({ omitBackground: true }));
            const t = trim(shot);
            if (!t) { console.warn(`  ${name}: layer "${n.name}" is empty, skipped`); continue; }
            // over-composite for the sanity check below
            for (let y = 0; y < t.data.height; y++) for (let x = 0; x < t.data.width; x++) {
                const s = (y * t.data.width + x) * 4, d = ((y + t.y0) * size.width + x + t.x0) * 4, a = t.data.data[s + 3] / 255;
                for (let c = 0; c < 3; c++) composite[d + c] = t.data.data[s + c] * a + composite[d + c] * (1 - a);
                composite[d + 3] = 255 * (a + composite[d + 3] / 255 * (1 - a));
            }
            const layer = { name: n.name, left: t.x0, top: t.y0, imageData: t.data };
            if (n.text) layer.text = textData(n.text);
            out.push(layer);
        }
        return out;
    }
    const children = await toPsd(tree);
    await page.close();

    let worst = 0;
    for (let i = 0; i < composite.length; i += 4) for (let c = 0; c < 3; c++) worst = Math.max(worst, Math.abs(composite[i + c] - flat.data[i + c]));
    if (worst > 24) console.warn(`  ${name}: layers differ from the PNG by up to ${worst}/255 - something paints outside a data-layer`);

    const psd = {
        width: size.width, height: size.height, children,
        imageData: { width: size.width, height: size.height, data: new Uint8ClampedArray(flat.data) },
    };
    fs.writeFileSync(path.join(OUT_PSD, name + '.psd'), writePsdBuffer(psd, { generateThumbnail: false, noBackground: true }));
    console.log(`${name}: ${size.width}x${size.height}, ${count(children)} layers`);
}

// The screenshots as JPEGs, small enough for the mod pages
async function compress(browser) {
    fs.mkdirSync(COMPRESSED, { recursive: true });
    const page = await browser.newPage({ viewport: { width: 1920, height: 1200 }, deviceScaleFactor: 1 });
    for (const file of fs.readdirSync(ORIGINAL).filter(f => f.endsWith('.png'))) {
        await page.setContent('<!doctype html><style>html,body{margin:0}img{display:block;width:1920px;height:1200px}</style><img>');
        const data = fs.readFileSync(path.join(ORIGINAL, file)).toString('base64');
        await page.evaluate(src => new Promise(done => {
            const img = document.querySelector('img');
            img.onload = done;
            img.src = src;
        }), 'data:image/png;base64,' + data);
        await page.screenshot({ path: path.join(COMPRESSED, file.replace(/\.png$/, '.jpg')), type: 'jpeg', quality: 85 });
    }
    await page.close();
}

const count = ls => ls.reduce((n, l) => n + (l.children ? count(l.children) : 1), 0);

(async () => {
    fs.mkdirSync(OUT_PSD, { recursive: true });
    const only = process.argv.slice(2);
    const pages = fs.readdirSync(path.join(SRC, 'pages')).filter(f => f.endsWith('.html')).map(f => f.slice(0, -5))
        .filter(p => !only.length || only.includes(p));
    // Uses the Edge that ships with Windows, so no browser download is needed
    const browser = await chromium.launch({ channel: 'msedge' });
    try {
        for (const p of pages) await build(browser, p);
        if (!only.length) await compress(browser);
    } finally {
        await browser.close();
    }
})();
