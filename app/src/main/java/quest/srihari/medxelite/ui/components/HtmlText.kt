package quest.srihari.medxelite.ui.components

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import quest.srihari.medxelite.data.resolveCdn

/**
 * Question / explanation HTML (HTMLRichTextView.swift). Text runs through Android's HTML parser
 * (bold, italics, sub/sup, lists, links); figures are lifted out and drawn below as zoomable images,
 * resolved against the image CDN exactly like iOS.
 */
object RichHtml {
    private val imgTag = Regex("<img[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)

    fun images(html: String): List<String> = imgTag.findAll(html).mapNotNull { resolveCdn(it.groupValues[1]) }.toList()

    fun textOnly(html: String): String = html
        .replace(imgTag, "")
        .replace(Regex("</t[dh]>\\s*<t[dh][^>]*>", RegexOption.IGNORE_CASE), " | ")
        .replace(Regex("</tr>", RegexOption.IGNORE_CASE), "<br>")
        .replace(Regex("</?(table|tbody|thead|tr|td|th)[^>]*>", RegexOption.IGNORE_CASE), "")

    fun plain(html: String): String =
        HtmlCompat.fromHtml(textOnly(html), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    sizeSp: Float = 16f,
    bold: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    extraImages: List<String> = emptyList(),
) {
    val images = remember(html, extraImages) { (RichHtml.images(html) + extraImages.mapNotNull { resolveCdn(it) }).distinct() }
    val spanned = remember(html) {
        HtmlCompat.fromHtml(RichHtml.textOnly(html), HtmlCompat.FROM_HTML_MODE_COMPACT).let { s ->
            // Trim trailing newlines the parser leaves after block elements.
            var end = s.length
            while (end > 0 && s[end - 1].isWhitespace()) end--
            s.subSequence(0, end)
        }
    }
    val argb = color.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    Column(modifier) {
        if (spanned.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        movementMethod = LinkMovementMethod.getInstance()
                        setLineSpacing(0f, 1.15f)
                    }
                },
                update = { tv ->
                    tv.text = spanned
                    tv.setTextColor(argb)
                    tv.setLinkTextColor(linkColor)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                    tv.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        images.forEach { url -> Figure(url) }
    }
}

@Composable
fun Figure(url: String) {
    var zoom by remember { mutableStateOf(false) }
    AsyncImage(
        model = url,
        contentDescription = "Figure",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { zoom = true },
    )
    if (zoom) {
        Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { zoom = false }) {
                ZoomableImage(url)
            }
        }
    }
}

@Composable
fun ZoomableImage(url: String, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .transformable(state),
    )
}
