package tv.jellyvault.app.ui.playback.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.HardwareAccelerationType
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.TranscodeReason
import org.jellyfin.sdk.model.api.TranscodingInfo
import timber.log.Timber
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.preferences.PlayerBackend
import tv.jellyvault.app.ui.PreviewTvSpec
import tv.jellyvault.app.ui.formatBitrate
import tv.jellyvault.app.ui.formatBytes
import tv.jellyvault.app.ui.letNotEmpty
import tv.jellyvault.app.ui.playback.AnalyticsState
import tv.jellyvault.app.ui.playback.CurrentPlayback
import tv.jellyvault.app.ui.theme.JellyVaultTheme
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.TrackSupport
import tv.jellyvault.app.util.TrackSupportReason
import tv.jellyvault.app.util.TrackType
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlaybackDebugOverlay(
    analyticsState: AnalyticsState,
    currentPlayback: CurrentPlayback?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val display =
        remember(context) {
            try {
                val displayManager =
                    context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            } catch (ex: Exception) {
                Timber.e(ex)
                null
            }
        }
    val displayMode by produceState<String?>(null) {
        while (isActive) {
            value =
                display?.mode?.let {
                    val rate = String.format(Locale.getDefault(), "%.3f", it.refreshRate)
                    "${it.physicalWidth}x${it.physicalHeight}@${rate}fps, id=${it.modeId}"
                }
            delay(10.seconds)
        }
    }
    val textStyle =
        MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )

    val memoryUsed by produceState("") {
        withContext(JvDispatchers.Default) {
            while (isActive) {
                val runtime = Runtime.getRuntime()
                val total = runtime.totalMemory()
                val free = runtime.freeMemory()
                val used = total - free
                val totalMemory = formatBytes(total)
                val usedMemory = formatBytes(used)
                value = "$usedMemory / $totalMemory"
                delay(2.seconds)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        ) {
            ProvideTextStyle(textStyle) {
                SimpleTable(
                    remember(currentPlayback, displayMode) {
                        buildList {
                            add("Backend:" to currentPlayback?.backend?.toString())
                            add("Play method:" to currentPlayback?.playMethod?.serialName)
                            if (currentPlayback?.backend == PlayerBackend.EXO_PLAYER) {
                                add("Video Decoder:" to currentPlayback.videoDecoder)
                                add("Audio Decoder:" to (currentPlayback.audioDecoder ?: "Non-ExoPlayer"))
                            }
                            add("Display Mode: " to displayMode)
                        }
                    },
                    modifier = Modifier.weight(1f, fill = false),
                    keyWidth = 80.dp,
                )
                SimpleTable(
                    rows =
                        listOf(
                            "Bitrate: " to analyticsState.bitrate,
                            "Bitrate (est): " to analyticsState.bitrateEstimate,
                            "Dropped frames: " to analyticsState.droppedFrames,
                            "Memory" to memoryUsed,
                        ),
                    keyWidth = 80.dp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                currentPlayback?.transcodeInfo?.let { info ->
                    SimpleTable(
                        listOf(
                            "Reason:" to info.transcodeReasons.joinToString(", "),
                            "HW Accel:" to info.hardwareAccelerationType?.toString(),
                            "Container:" to info.container,
                            "Bitrate:" to info.bitrate?.let { formatBitrate(it) },
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        keyWidth = 64.dp,
                    )
                    SimpleTable(
                        listOf(
                            "Video:" to "${info.videoCodec}, ${info.width}x${info.height}",
                            "Video Direct:" to info.isVideoDirect.toString(),
                            "Audio:" to "${info.audioCodec}, ch=${info.audioChannels}",
                            "Audio Direct:" to info.isAudioDirect.toString(),
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        keyWidth = 64.dp,
                    )
                }
            }
        }
        currentPlayback?.tracks?.letNotEmpty {
            PlaybackTrackInfo(
                trackSupport = it,
                textStyle = textStyle,
            )
        }
    }
}

@Composable
fun SimpleTable(
    rows: List<Pair<String, Any?>>,
    modifier: Modifier = Modifier,
    keyWidth: Dp = 100.dp,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        rows.forEach {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = it.first,
                    modifier = Modifier.width(keyWidth),
                )
                Text(
                    text = it.second.toString(),
                    modifier = Modifier,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@PreviewTvSpec
@Composable
fun PlaybackDebugOverlayPreview() {
    val currentPlayback =
        CurrentPlayback(
            item =
                BaseItem(
                    data =
                        BaseItemDto(
                            id = UUID.randomUUID(),
                            type = BaseItemKind.EPISODE,
                        ),
                ),
            backend = PlayerBackend.EXO_PLAYER,
            playMethod = PlayMethod.TRANSCODE,
            playSessionId = "123",
            liveStreamId = "123",
            videoDecoder = "video.decoder.name",
            audioDecoder = null, // "audio.decoder.name",
            mediaSourceInfo =
                MediaSourceInfo(
                    protocol = MediaProtocol.HTTP,
                    type = MediaSourceType.DEFAULT,
                    isRemote = false,
                    readAtNativeFramerate = true,
                    ignoreDts = true,
                    ignoreIndex = true,
                    genPtsInput = false,
                    supportsTranscoding = true,
                    supportsDirectStream = true,
                    supportsDirectPlay = true,
                    isInfiniteStream = false,
                    requiresOpening = false,
                    requiresClosing = false,
                    requiresLooping = false,
                    supportsProbing = true,
                    transcodingSubProtocol = MediaStreamProtocol.HTTP,
                    hasSegments = false,
                ),
            transcodeInfo =
                TranscodingInfo(
                    videoCodec = "h264",
                    audioCodec = "aac",
                    container = "HLS",
                    width = 1080,
                    height = 1920,
                    isVideoDirect = false,
                    isAudioDirect = false,
                    transcodeReasons =
                        listOf(
                            TranscodeReason.VIDEO_PROFILE_NOT_SUPPORTED,
                            TranscodeReason.AUDIO_CHANNELS_NOT_SUPPORTED,
                        ),
                    hardwareAccelerationType = HardwareAccelerationType.NONE,
                ),
            tracks =
                listOf(
                    TrackSupport(
                        id = "0",
                        type = TrackType.VIDEO,
                        supported = TrackSupportReason.EXCEEDS_CAPABILITIES,
                        selected = true,
                        labels = listOf(),
                        codecs = "avc1",
                        format = Format.Builder().build(),
                    ),
                    TrackSupport(
                        id = "0",
                        type = TrackType.AUDIO,
                        supported = TrackSupportReason.HANDLED,
                        selected = true,
                        labels = listOf(),
                        codecs = "ac3",
                        format = Format.Builder().build(),
                    ),
                ) +
                    List(10) {
                        TrackSupport(
                            id = "0",
                            type = TrackType.TEXT,
                            supported = TrackSupportReason.HANDLED,
                            selected = false,
                            labels = listOf(),
                            codecs = "srt",
                            format = Format.Builder().build(),
                        )
                    },
        )
    JellyVaultTheme {
        PlaybackDebugOverlay(
            analyticsState = AnalyticsState(),
            currentPlayback = currentPlayback,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
