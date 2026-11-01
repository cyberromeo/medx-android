package quest.srihari.medxelite.ui.components

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import quest.srihari.medxelite.auth.AuthSession
import quest.srihari.medxelite.ui.theme.Mx

// ---------------------------------------------------------------- Logo mark (MedxLogoMark.swift)

@Composable
fun MedxLogoMark(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val points = listOf(6f to 62f, 20f to 62f, 33f to 20f, 50f to 78f, 67f to 20f, 80f to 62f, 94f to 62f)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2237f))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3D8FFF), Color(0xFF294DEB), Color(0xFF4A2ED1)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Specular sheen.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(this.size.width * 0.22f, this.size.height * 0.16f),
                    radius = this.size.width * 0.55f,
                ),
            )
            val inset = this.size.width * 0.16f
            val w = this.size.width - inset * 2
            val path = Path()
            points.forEachIndexed { i, (x, y) ->
                val px = inset + x / 100f * w
                val py = inset + y / 100f * w
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path, Color.White,
                style = Stroke(width = this.size.width * 0.105f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ---------------------------------------------------------------- Grouped lists (iOS insetGrouped)

fun LazyListScope.groupedSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item {
        GroupedSection(header, footer, content = content)
    }
}

@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Mx.colors.secondaryText,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Mx.colors.card),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = Mx.colors.secondaryText,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
fun MedxRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Mx.colors.accent,
    trailing: String? = null,
    showChevron: Boolean = true,
    divider: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            } else if (icon != null) {
                IconTile(icon, iconTint)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText, modifier = Modifier.padding(start = 8.dp))
            }
            trailingContent?.invoke()
            if (showChevron && onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Mx.colors.tertiaryText)
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (icon != null || leading != null) 60.dp else 16.dp), color = Mx.colors.separator, thickness = 0.5.dp)
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun Avatar(session: AuthSession?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val accent = Mx.colors.accent
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), accent))),
        contentAlignment = Alignment.Center,
    ) {
        Text(session?.initials ?: "?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
        if (!session?.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = session?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

// ---------------------------------------------------------------- States

@Composable
fun LoadingState(title: String = "Loading", subtitle: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, color = Mx.colors.secondaryText, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Filled.WarningAmber,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Mx.colors.secondaryText, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(message, color = Mx.colors.secondaryText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        }
        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionTitle) }
        }
    }
}

// ---------------------------------------------------------------- Top bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedxTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Mx.colors.groupedBackground,
        scrolledContainerColor = Mx.colors.groupedBackground,
    )
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
    }
    if (large) {
        LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = nav, actions = actions, colors = colors, scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = nav, actions = actions, colors = colors, scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun Pill(text: String, color: Color = Mx.colors.accent) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Mx.colors.card).padding(padding),
        content = content,
    )
}

fun openCustomTab(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
