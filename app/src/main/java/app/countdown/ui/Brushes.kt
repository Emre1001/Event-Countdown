package app.countdown.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import app.countdown.model.Fill
import app.countdown.render.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verlauf, der sich an die tatsächliche Zeichenfläche anpasst (gleiche Geometrie wie im Widget). */
private class FillBrush(val fill: Fill) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val p = fill.points(size.width, size.height)
        return LinearGradientShader(Offset(p[0], p[1]), Offset(p[2], p[3]), fill.colors.map { Color(it) })
    }

    override fun equals(other: Any?) = other is FillBrush && other.fill == fill
    override fun hashCode() = fill.hashCode()
}

fun Fill.brush(): Brush = if (isGradient) FillBrush(this) else SolidColor(Color(first))

/** Färbt den Inhalt mit einem Verlauf ein (Inhalt muss deckend gezeichnet sein). */
fun Modifier.fillOverlay(fill: Fill): Modifier =
    if (!fill.isGradient) this
    else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(fill.brush(), blendMode = BlendMode.SrcIn)
        }

/** Lädt ein gespeichertes Bild im Hintergrund. */
@Composable
fun rememberStoredImage(name: String?): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, name) {
        value = if (name == null) null else withContext(Dispatchers.IO) { ImageStore.load(context, name)?.asImageBitmap() }
    }
}
