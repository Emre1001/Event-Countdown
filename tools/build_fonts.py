#!/usr/bin/env python3
"""
Erzeugt die gebündelten Schriften der App (reproduzierbar).

  pip install fonttools uharfbuzz
  python3 tools/build_fonts.py <ordner-mit-quell-ttfs>

Quellen (Google Fonts, SIL OFL 1.1): Inter, Nunito, Fraunces, JetBrains Mono (variable TTFs).

Ausgabe:
  res/font/f_<familie>_<gewicht>.ttf       Text + Zahlen (Ziffern fest tabellarisch)
  res/font/s_<familie>_<gewicht>_down.ttf  Nur Ziffern; Ligaturen "ss" -> (60 - ss) % 60
  res/font/s_<familie>_<gewicht>_up.ttf    Nur Ziffern, unverändert

Die *_down-Schrift ist der Trick für Sekunden im Widget: Ein TextClock mit Format "ss" tickt im
Launcher jede Sekunde; die Ligatur zeigt statt der Uhr-Sekunde die verbleibenden Sekunden bis zur
nächsten vollen Minute (Events liegen immer auf einer vollen Minute).
"""
import os
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

FAMILIES = {
    # id: (datei, feste Achsen)
    "sans": ("inter.ttf", {"opsz": 28}),
    "rounded": ("nunito.ttf", {}),
    "serif": ("fraunces.ttf", {"opsz": 72, "SOFT": 0, "WONK": 0}),
    "mono": ("mono.ttf", {}),
}
WEIGHTS = [300, 400, 600, 800]

UNICODES = (
    list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) +
    [0x2013, 0x2014, 0x2018, 0x2019, 0x201A, 0x201C, 0x201D, 0x201E, 0x2022, 0x2026,
     0x2030, 0x2039, 0x203A, 0x20AC, 0x2190, 0x2192, 0x2212]
)
DIGITS = [0x30 + i for i in range(10)]


def freeze_tnum(font):
    """Ziffern im cmap fest auf die tabellarischen Varianten legen (gleiche Breite)."""
    if "GSUB" not in font:
        return
    gsub = font["GSUB"].table
    if not gsub.FeatureList:
        return
    mapping = {}
    for rec in gsub.FeatureList.FeatureRecord:
        if rec.FeatureTag != "tnum":
            continue
        for li in rec.Feature.LookupListIndex:
            lookup = gsub.LookupList.Lookup[li]
            for st in lookup.SubTable:
                if lookup.LookupType == 7:
                    st = st.ExtSubTable
                if hasattr(st, "mapping"):
                    mapping.update(st.mapping)
    if not mapping:
        return
    for table in font["cmap"].tables:
        if not table.isUnicode():
            continue
        for cp in DIGITS:
            g = table.cmap.get(cp)
            if g in mapping:
                table.cmap[cp] = mapping[g]


def force_tabular(font):
    """Für Schriften ohne 'tnum': alle Ziffern auf die breiteste Ziffer setzen und mittig ausrichten."""
    cmap = font.getBestCmap()
    names = [cmap[c] for c in DIGITS]
    hmtx = font["hmtx"]
    widest = max(hmtx[n][0] for n in names)
    glyf = font["glyf"]
    for n in names:
        adv, lsb = hmtx[n]
        delta = (widest - adv) // 2
        if delta == 0 and adv == widest:
            continue
        g = glyf[n]
        if g.isComposite():
            for comp in g.components:
                comp.x += delta
        elif g.numberOfContours > 0:
            g.coordinates.translate((delta, 0))
        g.recalcBounds(glyf)
        hmtx[n] = (widest, lsb + delta)


def static_instance(src, axes, weight):
    font = TTFont(src)
    loc = {"wght": weight}
    for tag, val in axes.items():
        loc[tag] = val
    present = {a.axisTag: a for a in font["fvar"].axes}
    for tag in list(loc):
        a = present.get(tag)
        if a is None:
            del loc[tag]
        else:
            loc[tag] = max(a.minValue, min(a.maxValue, loc[tag]))
    for tag, a in present.items():
        loc.setdefault(tag, a.defaultValue)
    font = instantiateVariableFont(font, loc, updateFontNames=False)
    freeze_tnum(font)
    force_tabular(font)
    return font


def subset(font, unicodes):
    opts = Options()
    opts.layout_features = ["kern", "liga", "calt", "ccmp", "locl", "mark", "mkmk"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.hinting = False
    s = Subsetter(opts)
    s.populate(unicodes=unicodes)
    s.subset(font)
    return font


def digit_font(base, family_name, countdown):
    """Neue, minimale Schrift: Ziffern + (optional) 60 Ligaturen mit gleichen Metriken wie `base`."""
    cmap = base.getBestCmap()
    gs = base.getGlyphSet()
    upm = base["head"].unitsPerEm
    digit_glyphs = [cmap[c] for c in DIGITS]
    adv = base["hmtx"][digit_glyphs[0]][0]

    def draw(names_with_offsets):
        pen = TTGlyphPen(None)
        for name, dx in names_with_offsets:
            rec = DecomposingRecordingPen(gs)
            gs[name].draw(rec)
            rec.replay(TransformPen(pen, (1, 0, 0, 1, dx, 0)))
        return pen.glyph()

    order = [".notdef"] + ["d%d" % i for i in range(10)]
    glyphs = {".notdef": TTGlyphPen(None).glyph()}
    advances = {".notdef": adv}
    for i in range(10):
        glyphs["d%d" % i] = draw([(digit_glyphs[i], 0)])
        advances["d%d" % i] = adv
    fea = ""
    if countdown:
        rules = []
        for v in range(60):
            shown = (60 - v) % 60
            name = "s%02d" % v
            order.append(name)
            glyphs[name] = draw([(digit_glyphs[shown // 10], 0), (digit_glyphs[shown % 10], adv)])
            advances[name] = adv * 2
            rules.append("    sub d%d d%d by %s;" % (v // 10, v % 10, name))
        body = "\n".join(rules)
        fea = ("languagesystem DFLT dflt;\nlanguagesystem latn dflt;\n"
               "lookup secs {\n%s\n} secs;\n"
               "feature rlig { lookup secs; } rlig;\nfeature liga { lookup secs; } liga;\n" % body)

    def lsb(g):
        if getattr(g, "numberOfContours", 0) and g.numberOfContours > 0:
            return min(x for x, _ in g.coordinates)
        return 0

    fb = FontBuilder(upm, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({c: "d%d" % i for i, c in enumerate(DIGITS)})
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics({n: (advances[n], lsb(glyphs[n])) for n in order})
    hhea = base["hhea"]
    fb.setupHorizontalHeader(ascent=hhea.ascent, descent=hhea.descent, lineGap=hhea.lineGap)
    os2 = base["OS/2"]
    fb.setupOS2(
        sTypoAscender=os2.sTypoAscender, sTypoDescender=os2.sTypoDescender, sTypoLineGap=os2.sTypoLineGap,
        usWinAscent=os2.usWinAscent, usWinDescent=os2.usWinDescent, usWeightClass=os2.usWeightClass,
    )
    fb.setupNameTable({"familyName": family_name, "styleName": "Regular"})
    fb.setupPost()
    if fea:
        fb.addOpenTypeFeatures(fea)
    return fb.font


def main():
    src_dir = sys.argv[1]
    out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "font")
    os.makedirs(out, exist_ok=True)
    for fam, (file, axes) in FAMILIES.items():
        for w in WEIGHTS:
            inst = static_instance(os.path.join(src_dir, file), axes, w)
            down = digit_font(inst, "CountdownSeconds%s%d" % (fam, w), countdown=True)
            up = digit_font(inst, "CountupSeconds%s%d" % (fam, w), countdown=False)
            down.save(os.path.join(out, "s_%s_%d_down.ttf" % (fam, w)))
            up.save(os.path.join(out, "s_%s_%d_up.ttf" % (fam, w)))
            subset(inst, UNICODES).save(os.path.join(out, "f_%s_%d.ttf" % (fam, w)))
            print("ok", fam, w)


if __name__ == "__main__":
    main()
