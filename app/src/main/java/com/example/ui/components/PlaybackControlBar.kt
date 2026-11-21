package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Reusable studio playback control component featuring dedicated Play, Pause, and Stop
 * transport buttons with live status indicator and accessible 48dp+ touch targets.
 */
@Composable
fun PlaybackControlBar(
  isPlaying: Boolean,
  isPreparing: Boolean,
  hasTrack: Boolean,
  positionMs: Long,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val isPaused = hasTrack && !isPlaying && !isPreparing && positionMs > 0L
  val canPlay = hasTrack && !isPreparing && !isPlaying
  val canPause = hasTrack && !isPreparing && isPlaying
  val canStop = hasTrack && !isPreparing && (isPlaying || positionMs > 0L)

  val statusLabel = when {
    isPreparing -> stringResource(R.string.playback_status_loading)
    isPlaying -> stringResource(R.string.playback_status_playing)
    isPaused -> stringResource(R.string.playback_status_paused)
    else -> stringResource(R.string.playback_status_stopped)
  }

  val statusColor by animateColorAsState(
    targetValue = when {
      isPlaying -> colorScheme.primary
      isPaused -> colorScheme.secondary
      isPreparing -> colorScheme.tertiary
      else -> colorScheme.onSurfaceVariant
    },
    label = "playbackStatusColor",
  )

  val playScale by animateFloatAsState(
    targetValue = if (isPlaying) 1.03f else 1f,
    animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
    label = "playbackPlayScale",
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("playback_control_bar"),
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(
      width = 1.dp,
      color = if (isPlaying) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Live Transport Status Indicator
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(statusColor),
          )
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = statusColor,
            modifier = Modifier.testTag("playback_status_label"),
          )
        }
        Text(
          text = if (hasTrack) formatDurationMs(positionMs) else "00:00",
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
        )
      }

      // Dedicated Play / Pause / Stop Transport Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // PLAY Button
        Button(
          onClick = onPlay,
          enabled = canPlay,
          colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            disabledContainerColor = if (isPlaying) {
              colorScheme.primaryContainer
            } else {
              colorScheme.surfaceContainerHigh
            },
            disabledContentColor = if (isPlaying) {
              colorScheme.onPrimaryContainer
            } else {
              colorScheme.onSurfaceVariant
            },
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .scale(playScale)
            .minimumInteractiveComponentSize()
            .testTag("playback_play_button"),
        ) {
          if (isPreparing) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = colorScheme.onSurfaceVariant,
            )
          } else {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = stringResource(R.string.cd_play_track),
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.playback_control_play),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }

        // PAUSE Button
        FilledTonalButton(
          onClick = onPause,
          enabled = canPause,
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            disabledContainerColor = if (isPaused) {
              colorScheme.secondaryContainer.copy(alpha = 0.6f)
            } else {
              colorScheme.surfaceContainerHigh
            },
            disabledContentColor = if (isPaused) {
              colorScheme.onSecondaryContainer
            } else {
              colorScheme.onSurfaceVariant
            },
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("playback_pause_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Pause,
            contentDescription = stringResource(R.string.cd_pause_track),
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.playback_control_pause),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }

        // STOP Button
        OutlinedButton(
          onClick = onStop,
          enabled = canStop,
          border = BorderStroke(
            width = 1.dp,
            color = if (canStop) colorScheme.error.copy(alpha = 0.65f) else colorScheme.outlineVariant,
          ),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colorScheme.error,
            disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("playback_stop_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = stringResource(R.string.cd_stop_track),
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.playback_control_stop),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }
      }
    }
  }
}
