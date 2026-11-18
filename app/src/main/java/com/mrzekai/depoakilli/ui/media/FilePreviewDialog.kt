package com.mrzekai.depoakilli.ui.media

import android.view.LayoutInflater
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.theme.OnBlack

/** What a preview needs to know about a file, whatever screen it comes from. */
internal data class MediaPreviewFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val voiceNote: Boolean = false,
)

private val DefaultAccent = com.mrzekai.depoakilli.ui.theme.Lime

/** Picks image, video, audio or a generic card for [file]. Every screen uses this one body. */
@Composable
internal fun MediaPreviewContent(
    file: MediaPreviewFile,
    accent: Color = DefaultAccent,
    allowAudio: Boolean = true,
    muted: Boolean = false,
) {
    val mime = file.mimeType.lowercase()
    when {
        mime.startsWith("image/") -> MediaImagePreview(file, accent)
        mime.startsWith("video/") -> MediaVideoPreview(file, accent, muted)
        allowAudio && (mime.startsWith("audio/") || file.voiceNote) -> MediaAudioPreview(file, accent)
        else -> MediaGenericPreview(file, accent)
    }
}

@Composable
internal fun MediaImagePreview(file: MediaPreviewFile, accent: Color = DefaultAccent) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file.uri) {
        value = MediaThumbnails.display(context, file.uri)
    }
    val loaded = bitmap
    if (loaded == null) {
        CircularProgressIndicator(color = accent)
    } else {
        Image(
            bitmap = loaded,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Video with a single play/pause control; nothing plays until it is tapped. */
@Composable
internal fun MediaVideoPreview(file: MediaPreviewFile, accent: Color = DefaultAccent, muted: Boolean = false) {
    val context = LocalContext.current
    val player = remember(file.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            // The swipe review plays videos silently (Faz 5 T5.5).
            if (muted) volume = 0f
            prepare()
        }
    }
    var prepared by remember(file.uri) { mutableStateOf(false) }
    var playing by remember(file.uri) { mutableStateOf(false) }
    var failed by remember(file.uri) { mutableStateOf(false) }
    var noVideoTrack by remember(file.uri) { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    prepared = true
                    failed = false
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (tracks.groups.isEmpty()) return
                noVideoTrack = tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                if (noVideoTrack) {
                    playing = false
                    player.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                prepared = false
                playing = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.smart_video_player, null, false) as PlayerView).apply {
                    this.player = player
                    useController = false
                }
            },
            update = { view -> if (view.player !== player) view.player = player },
        )
        if (!prepared && !failed) CircularProgressIndicator(color = accent)
        if (failed || noVideoTrack) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.VideoFile, contentDescription = null, tint = com.mrzekai.depoakilli.ui.theme.AdLabel, modifier = Modifier.size(56.dp))
                Text(
                    stringResource(if (noVideoTrack) R.string.smart_video_no_picture else R.string.smart_video_preview_error),
                    color = OnBlack,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                color = com.mrzekai.depoakilli.ui.theme.Black.copy(alpha = .9f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, OnBlack.copy(alpha = .24f)),
            ) {
                Row(
                    modifier = Modifier
                        .clickable(enabled = prepared && !noVideoTrack) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = OnBlack,
                    )
                    Text(
                        stringResource(if (playing) R.string.smart_video_pause else R.string.smart_video_play),
                        color = OnBlack,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

/** Audio and voice notes through the same ExoPlayer path as video. */
@Composable
internal fun MediaAudioPreview(file: MediaPreviewFile, accent: Color = DefaultAccent) {
    val context = LocalContext.current
    val player = remember(file.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            prepare()
        }
    }
    var playing by remember(file.uri) { mutableStateOf(false) }
    var failed by remember(file.uri) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = accent.copy(alpha = .15f), shape = CircleShape) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.padding(24.dp).size(56.dp))
        }
        Text(file.name, color = OnBlack, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (failed) {
            Text(stringResource(R.string.whatsapp_audio_preview_error), color = com.mrzekai.depoakilli.ui.theme.AdLabel)
        } else {
            Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(if (playing) R.string.smart_audio_pause else R.string.smart_audio_play))
            }
        }
    }
}

@Composable
internal fun MediaGenericPreview(file: MediaPreviewFile, accent: Color = DefaultAccent) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = accent.copy(alpha = .15f), shape = RoundedCornerShape(26.dp)) {
            Icon(
                FileVisuals.icon(file.mimeType, file.name, file.voiceNote),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(26.dp).size(64.dp),
            )
        }
        Text(stringResource(R.string.smart_generic_preview_title), color = OnBlack, fontWeight = FontWeight.Black)
        Text(file.name, color = OnBlack.copy(alpha = .82f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(ByteFormatter.format(file.sizeBytes), color = accent, fontWeight = FontWeight.Black)
    }
}
