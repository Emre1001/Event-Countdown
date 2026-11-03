#!/usr/bin/env python3
"""Erzeugt die TextClock-Layouts für Widget-Sekunden und die Kotlin-Zuordnung FontRes.kt."""
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main")
FAMS = [("sans", "SANS"), ("rounded", "ROUNDED"), ("serif", "SERIF"), ("mono", "MONO")]
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
