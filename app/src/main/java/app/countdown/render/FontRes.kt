// Generiert von tools/gen_widget_res.py – nicht von Hand bearbeiten.
package app.countdown.render

import app.countdown.R
import app.countdown.model.FontChoice
import app.countdown.model.WeightChoice

internal object FontRes {
    fun full(font: FontChoice, weight: WeightChoice): Int = when (font) {
        FontChoice.SANS -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_sans_300
            WeightChoice.REGULAR -> R.font.f_sans_400
            WeightChoice.SEMIBOLD -> R.font.f_sans_600
            WeightChoice.BOLD -> R.font.f_sans_800
        }
        FontChoice.ROUNDED -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_rounded_300
            WeightChoice.REGULAR -> R.font.f_rounded_400
            WeightChoice.SEMIBOLD -> R.font.f_rounded_600
            WeightChoice.BOLD -> R.font.f_rounded_800
        }
        FontChoice.SERIF -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_serif_300
            WeightChoice.REGULAR -> R.font.f_serif_400
            WeightChoice.SEMIBOLD -> R.font.f_serif_600
            WeightChoice.BOLD -> R.font.f_serif_800
        }
        FontChoice.MONO -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_mono_300
            WeightChoice.REGULAR -> R.font.f_mono_400
            WeightChoice.SEMIBOLD -> R.font.f_mono_600
            WeightChoice.BOLD -> R.font.f_mono_800
        }
        FontChoice.GEO -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_geo_300
            WeightChoice.REGULAR -> R.font.f_geo_400
            WeightChoice.SEMIBOLD -> R.font.f_geo_600
            WeightChoice.BOLD -> R.font.f_geo_800
        }
        FontChoice.GROTESK -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_grotesk_300
            WeightChoice.REGULAR -> R.font.f_grotesk_400
            WeightChoice.SEMIBOLD -> R.font.f_grotesk_600
            WeightChoice.BOLD -> R.font.f_grotesk_800
        }
        FontChoice.DISPLAY -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_display_300
            WeightChoice.REGULAR -> R.font.f_display_400
            WeightChoice.SEMIBOLD -> R.font.f_display_600
            WeightChoice.BOLD -> R.font.f_display_800
        }
        FontChoice.HAND -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_hand_300
            WeightChoice.REGULAR -> R.font.f_hand_400
            WeightChoice.SEMIBOLD -> R.font.f_hand_600
            WeightChoice.BOLD -> R.font.f_hand_800
        }
        FontChoice.TECH -> when (weight) {
            WeightChoice.LIGHT -> R.font.f_tech_300
            WeightChoice.REGULAR -> R.font.f_tech_400
            WeightChoice.SEMIBOLD -> R.font.f_tech_600
            WeightChoice.BOLD -> R.font.f_tech_800
        }
    }

    fun seconds(font: FontChoice, weight: WeightChoice, countdown: Boolean): Int = when (font) {
        FontChoice.SANS -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_sans_300_down else R.font.s_sans_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_sans_400_down else R.font.s_sans_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_sans_600_down else R.font.s_sans_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_sans_800_down else R.font.s_sans_800_up
        }
        FontChoice.ROUNDED -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_rounded_300_down else R.font.s_rounded_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_rounded_400_down else R.font.s_rounded_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_rounded_600_down else R.font.s_rounded_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_rounded_800_down else R.font.s_rounded_800_up
        }
        FontChoice.SERIF -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_serif_300_down else R.font.s_serif_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_serif_400_down else R.font.s_serif_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_serif_600_down else R.font.s_serif_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_serif_800_down else R.font.s_serif_800_up
        }
        FontChoice.MONO -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_mono_300_down else R.font.s_mono_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_mono_400_down else R.font.s_mono_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_mono_600_down else R.font.s_mono_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_mono_800_down else R.font.s_mono_800_up
        }
        FontChoice.GEO -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_geo_300_down else R.font.s_geo_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_geo_400_down else R.font.s_geo_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_geo_600_down else R.font.s_geo_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_geo_800_down else R.font.s_geo_800_up
        }
        FontChoice.GROTESK -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_grotesk_300_down else R.font.s_grotesk_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_grotesk_400_down else R.font.s_grotesk_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_grotesk_600_down else R.font.s_grotesk_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_grotesk_800_down else R.font.s_grotesk_800_up
        }
        FontChoice.DISPLAY -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_display_300_down else R.font.s_display_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_display_400_down else R.font.s_display_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_display_600_down else R.font.s_display_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_display_800_down else R.font.s_display_800_up
        }
        FontChoice.HAND -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_hand_300_down else R.font.s_hand_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_hand_400_down else R.font.s_hand_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_hand_600_down else R.font.s_hand_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_hand_800_down else R.font.s_hand_800_up
        }
        FontChoice.TECH -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.font.s_tech_300_down else R.font.s_tech_300_up
            WeightChoice.REGULAR -> if (countdown) R.font.s_tech_400_down else R.font.s_tech_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.font.s_tech_600_down else R.font.s_tech_600_up
            WeightChoice.BOLD -> if (countdown) R.font.s_tech_800_down else R.font.s_tech_800_up
        }
    }

    fun secondsLayout(font: FontChoice, weight: WeightChoice, countdown: Boolean): Int = when (font) {
        FontChoice.SANS -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_sans_300_down else R.layout.w_s_sans_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_sans_400_down else R.layout.w_s_sans_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_sans_600_down else R.layout.w_s_sans_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_sans_800_down else R.layout.w_s_sans_800_up
        }
        FontChoice.ROUNDED -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_rounded_300_down else R.layout.w_s_rounded_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_rounded_400_down else R.layout.w_s_rounded_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_rounded_600_down else R.layout.w_s_rounded_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_rounded_800_down else R.layout.w_s_rounded_800_up
        }
        FontChoice.SERIF -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_serif_300_down else R.layout.w_s_serif_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_serif_400_down else R.layout.w_s_serif_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_serif_600_down else R.layout.w_s_serif_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_serif_800_down else R.layout.w_s_serif_800_up
        }
        FontChoice.MONO -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_mono_300_down else R.layout.w_s_mono_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_mono_400_down else R.layout.w_s_mono_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_mono_600_down else R.layout.w_s_mono_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_mono_800_down else R.layout.w_s_mono_800_up
        }
        FontChoice.GEO -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_geo_300_down else R.layout.w_s_geo_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_geo_400_down else R.layout.w_s_geo_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_geo_600_down else R.layout.w_s_geo_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_geo_800_down else R.layout.w_s_geo_800_up
        }
        FontChoice.GROTESK -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_grotesk_300_down else R.layout.w_s_grotesk_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_grotesk_400_down else R.layout.w_s_grotesk_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_grotesk_600_down else R.layout.w_s_grotesk_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_grotesk_800_down else R.layout.w_s_grotesk_800_up
        }
        FontChoice.DISPLAY -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_display_300_down else R.layout.w_s_display_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_display_400_down else R.layout.w_s_display_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_display_600_down else R.layout.w_s_display_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_display_800_down else R.layout.w_s_display_800_up
        }
        FontChoice.HAND -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_hand_300_down else R.layout.w_s_hand_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_hand_400_down else R.layout.w_s_hand_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_hand_600_down else R.layout.w_s_hand_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_hand_800_down else R.layout.w_s_hand_800_up
        }
        FontChoice.TECH -> when (weight) {
            WeightChoice.LIGHT -> if (countdown) R.layout.w_s_tech_300_down else R.layout.w_s_tech_300_up
            WeightChoice.REGULAR -> if (countdown) R.layout.w_s_tech_400_down else R.layout.w_s_tech_400_up
            WeightChoice.SEMIBOLD -> if (countdown) R.layout.w_s_tech_600_down else R.layout.w_s_tech_600_up
            WeightChoice.BOLD -> if (countdown) R.layout.w_s_tech_800_down else R.layout.w_s_tech_800_up
        }
    }
}
