#!/usr/bin/env python3
"""Erzeugt die TextClock-Layouts für Widget-Sekunden und die Kotlin-Zuordnung FontRes.kt."""
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main")
FAMS = [("sans", "SANS"), ("rounded", "ROUNDED"), ("serif", "SERIF"), ("mono", "MONO"), ("geo", "GEO"),
        ("grotesk", "GROTESK"), ("display", "DISPLAY"), ("hand", "HAND"), ("tech", "TECH")]
WEIGHTS = [(300, "LIGHT"), (400, "REGULAR"), (600, "SEMIBOLD"), (800, "BOLD")]

LAYOUT = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generiert von tools/gen_widget_res.py. Tickt im Launcher; die Schrift wandelt "ss" per Ligatur um. -->
<TextClock xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/sec_clock"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|start"
    android:fontFamily="@font/{font}"
    android:format12Hour="ss"
    android:format24Hour="ss"
    android:importantForAccessibility="no"
    android:includeFontPadding="false"
    android:singleLine="true"
    android:textColor="#FFFFFFFF"
    android:textSize="20sp" />
"""

os.makedirs(os.path.join(ROOT, "res", "layout"), exist_ok=True)
for fam, _ in FAMS:
    for w, _ in WEIGHTS:
        for mode in ("down", "up"):
            name = "s_%s_%d_%s" % (fam, w, mode)
            with open(os.path.join(ROOT, "res", "layout", "w_%s.xml" % name), "w") as f:
                f.write(LAYOUT.format(font=name))


def when_block(fn):
    out = []
    for fam, F in FAMS:
        inner = "\n".join("            WeightChoice.%s -> %s" % (W, fn(fam, w)) for w, W in WEIGHTS)
        out.append("        FontChoice.%s -> when (weight) {\n%s\n        }" % (F, inner))
    return "\n".join(out)


kt = """// Generiert von tools/gen_widget_res.py – nicht von Hand bearbeiten.
package app.countdown.render

import app.countdown.R
import app.countdown.model.FontChoice
import app.countdown.model.WeightChoice

internal object FontRes {
    fun full(font: FontChoice, weight: WeightChoice): Int = when (font) {
%s
    }

    fun seconds(font: FontChoice, weight: WeightChoice, countdown: Boolean): Int = when (font) {
%s
    }

    fun secondsLayout(font: FontChoice, weight: WeightChoice, countdown: Boolean): Int = when (font) {
%s
    }
}
""" % (
    when_block(lambda fam, w: "R.font.f_%s_%d" % (fam, w)),
    when_block(lambda fam, w: "if (countdown) R.font.s_%s_%d_down else R.font.s_%s_%d_up" % (fam, w, fam, w)),
    when_block(lambda fam, w: "if (countdown) R.layout.w_s_%s_%d_down else R.layout.w_s_%s_%d_up" % (fam, w, fam, w)),
)
os.makedirs(os.path.join(ROOT, "java", "app", "countdown", "render"), exist_ok=True)
with open(os.path.join(ROOT, "java", "app", "countdown", "render", "FontRes.kt"), "w") as f:
    f.write(kt)
print("ok")


# --- Widget-Grundlayouts je Animation: Hintergrund-Bild + 6 Zahlen-Ebenen (je ViewFlipper) + Sekunden ---
ANIMS = {
    # name: (in, out) – Dateien unter res/anim/
    "roll_up": ("w_in_from_bottom", "w_out_to_top"),
    "roll_down": ("w_in_from_top", "w_out_to_bottom"),
    "fade": ("w_fade_in", "w_fade_out"),
    "zoom": ("w_zoom_in", "w_zoom_out"),
    "flip": ("w_flip_in", "w_flip_out"),
    "melt": ("w_melt_in", "w_melt_out"),
    "none": (None, None),
}
SLOTS = 6


def root_layout(anim_in, anim_out):
    attrs = ""
    if anim_in:
        attrs = '\n                android:inAnimation="@anim/%s"\n                android:outAnimation="@anim/%s"' % (anim_in, anim_out)
    slots = ""
    for i in range(SLOTS):
        slots += """
    <FrameLayout
        android:id="@+id/u{i}"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ViewFlipper
            android:id="@+id/u{i}_flip"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:measureAllChildren="false"{attrs}>

            <ImageView
                android:id="@+id/u{i}_a"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:contentDescription="@null" />

            <ImageView
                android:id="@+id/u{i}_b"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:contentDescription="@null" />
        </ViewFlipper>
    </FrameLayout>
""".format(i=i, attrs=attrs)
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- Generiert von tools/gen_widget_res.py. Design als Bild, darüber je Einheit eine animierte Zahl. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageView
        android:id="@+id/bg"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:contentDescription="@null"
        android:scaleType="fitXY" />
%s
    <FrameLayout
        android:id="@+id/sec_slot"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <ImageView
        android:id="@+id/top"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:contentDescription="@null"
        android:scaleType="fitXY" />
</FrameLayout>
""" % slots


for name, (a_in, a_out) in ANIMS.items():
    with open(os.path.join(ROOT, "res", "layout", "widget_root_%s.xml" % name), "w") as f:
        f.write(root_layout(a_in, a_out))

ANIM_XML = {
    "w_in_from_bottom": '<translate android:duration="460" android:fromYDelta="100%" android:toYDelta="0%" />\n    <alpha android:duration="300" android:fromAlpha="0" android:toAlpha="1" />',
    "w_out_to_top": '<translate android:duration="420" android:fromYDelta="0%" android:toYDelta="-100%" />\n    <alpha android:duration="260" android:fromAlpha="1" android:toAlpha="0" />',
    "w_in_from_top": '<translate android:duration="460" android:fromYDelta="-100%" android:toYDelta="0%" />\n    <alpha android:duration="300" android:fromAlpha="0" android:toAlpha="1" />',
    "w_out_to_bottom": '<translate android:duration="420" android:fromYDelta="0%" android:toYDelta="100%" />\n    <alpha android:duration="260" android:fromAlpha="1" android:toAlpha="0" />',
    "w_fade_in": '<alpha android:duration="420" android:fromAlpha="0" android:toAlpha="1" />',
    "w_fade_out": '<alpha android:duration="420" android:fromAlpha="1" android:toAlpha="0" />',
    "w_zoom_in": '<scale android:duration="420" android:fromXScale="0.6" android:fromYScale="0.6" android:pivotX="50%" android:pivotY="50%" android:toXScale="1" android:toYScale="1" />\n    <alpha android:duration="320" android:fromAlpha="0" android:toAlpha="1" />',
    "w_zoom_out": '<scale android:duration="360" android:fromXScale="1" android:fromYScale="1" android:pivotX="50%" android:pivotY="50%" android:toXScale="1.4" android:toYScale="1.4" />\n    <alpha android:duration="300" android:fromAlpha="1" android:toAlpha="0" />',
    # Klappanzeige: alte Zahl klappt weg, danach klappt die neue auf.
    "w_flip_out": '<scale android:duration="220" android:fromXScale="1" android:fromYScale="1" android:pivotX="50%" android:pivotY="50%" android:toXScale="1" android:toYScale="0" />',
    "w_flip_in": '<scale android:duration="240" android:fromXScale="1" android:fromYScale="0" android:pivotX="50%" android:pivotY="50%" android:startOffset="220" android:toXScale="1" android:toYScale="1" />',
    # Schmelzen: alte Zahl zieht sich nach unten und zerfließt, die neue wächst weich nach.
    "w_melt_out": '<scale android:duration="520" android:fromXScale="1" android:fromYScale="1" android:pivotX="50%" android:pivotY="100%" android:toXScale="1.25" android:toYScale="0.15" />\n    <alpha android:duration="480" android:fromAlpha="1" android:toAlpha="0" />',
    "w_melt_in": '<scale android:duration="560" android:fromXScale="0.7" android:fromYScale="1.35" android:pivotX="50%" android:pivotY="0%" android:startOffset="120" android:toXScale="1" android:toYScale="1" />\n    <alpha android:duration="420" android:fromAlpha="0" android:startOffset="120" android:toAlpha="1" />',
}
for name, body in ANIM_XML.items():
    with open(os.path.join(ROOT, "res", "anim", name + ".xml"), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<set xmlns:android="http://schemas.android.com/apk/res/android"\n    android:interpolator="@android:interpolator/fast_out_slow_in">\n    %s\n</set>\n' % body)
print("roots ok")
