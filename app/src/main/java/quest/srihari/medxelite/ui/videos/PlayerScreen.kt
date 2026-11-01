package quest.srihari.medxelite.ui.videos

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.RecordedVideo
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import java.util.UUID

/**
 * The header set the class CDNs expect, per host — the same table as iOS HLSProxyServer.spoofHeaders.
 * ExoPlayer attaches them to every playlist/segment/key request directly, so no local proxy is needed.
 */
object StreamHeaders {
    private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
    private val playback = mapOf(
        "Origin" to "capacitor://localhost",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-Mode" to "no-cors",
        "Sec-Fetch-Dest" to "video",
        "Accept" to "*/*",
        "Accept-Language" to "en-IN,en;q=0.9",
    )
    private val api = mapOf(
        "Origin" to "capacitor://localhost",
        "appId" to "mobile",
        "deviceType" to "ios",
        "appVersion" to "1.5.6",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Dest" to "empty",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-IN,en;q=0.9",
    )
    private val session = UUID.randomUUID().toString().uppercase()

    fun forUri(uri: Uri): Map<String, String> {
        val host = uri.host ?: ""
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
        val isAsset = ext in setOf("m3u8", "ts", "m4s", "mp4", "m4v", "m4a", "aac", "key")
        return when {
            host.contains("liveplayback") || isAsset -> playback + ("X-Playback-Session-Id" to session)
            host.contains("arisemedicalacademy") -> api
            host.contains("cloudfront") -> mapOf("Sec-Fetch-Site" to "cross-site", "Sec-Fetch-Mode" to "no-cors")
            else -> playback + ("X-Playback-Session-Id" to session)
        }
    }

    const val USER_AGENT = UA
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(nav: Navigator, video: RecordedVideo?) {
    if (video == null || video.streamUrl.isBlank()) {
        EmptyState("Can't play this class", "It has no stream address.", actionTitle = "Back") { nav.back() }
        return
    }
    val context = LocalContext.current
    val activity = context as? Activity
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember(video.id) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(StreamHeaders.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        val resolving = ResolvingDataSource.Factory(http) { spec: DataSpec ->
            spec.withAdditionalHeaders(StreamHeaders.forUri(spec.uri))
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .build().apply {
                val isHls = video.kind == "hls" || video.streamUrl.contains(".m3u8", true) || !video.streamUrl.contains(".mp4", true)
                val item = MediaItem.Builder().setUri(video.streamUrl)
                    .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .build()
                setMediaItem(item)
                val resume = Medx.activity.historyEntry(video.id)?.resumePosition ?: 0.0
                if (resume > 0) seekTo((resume * 1000).toLong())
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Playback failed (${e.errorCodeName}). Check your connection, or the class may have moved."
            }
        }
        player.addListener(listener)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            val pos = player.currentPosition / 1000.0
            val dur = player.duration.takeIf { it > 0 }?.div(1000.0) ?: (video.durationSeconds ?: 0).toDouble()
            Medx.activity.recordProgress(video, pos, dur)
            player.removeListener(listener)
            player.release()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.isPlaying) {
                val dur = player.duration.takeIf { it > 0 }?.div(1000.0) ?: (video.durationSeconds ?: 0).toDouble()
                Medx.activity.recordProgress(video, player.currentPosition / 1000.0, dur)
            }
        }
    }

    BackHandler { nav.back() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.statusBarsPadding().padding(4.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Text(video.displayTitle, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    activity?.requestedOrientation =
                        if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }) { Icon(Icons.Rounded.ScreenRotation, "Rotate", tint = Color.White) }
            }
        }
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }
    }
}
