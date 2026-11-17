package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioPlaybackState
import com.example.audio.ConvolutionReverbDsp
import com.example.audio.CrossfadeDsp
import com.example.audio.EqPreset
import com.example.audio.GraphicEqualizerDsp
import com.example.audio.LoudnessNormalizerDsp
import com.example.audio.LoudnessTarget
import com.example.audio.PitchShiftDsp
import com.example.audio.RawBufferDiagnostics
import com.example.audio.ReverbDampingOption
import com.example.audio.ReverbEngineMode
import com.example.audio.ReverbPreset
import com.example.audio.ReverbRoomSizeOption
import com.example.audio.RoomReverbDsp
import com.example.audio.TrackAudioEnvelope
import com.example.audio.WaveformInteractionMode
import com.example.model.GeneratedTrack
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCyan
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Custom Audio Player UI component featuring:
 * - Play / Pause transport with animated studio disc & live waveform indicator
 * - Interactive PCM waveform visualizer + precision Seek Slider with live scrubbing
 * - Skip Previous / Next, -5s / +5s seek jumps, and Loop toggle
 * - Dedicated Volume Control Slider + Mute/Unmute toggle and percentage badge
 */
@Composable
fun CustomAudioPlayer(
  track: GeneratedTrack?,
  playbackState: AudioPlaybackState,
  canSkipPrevious: Boolean,
  canSkipNext: Boolean,
  onTogglePlayPause: () -> Unit,
  onBeginSeek: () -> Unit,
  onPreviewSeek: (Long) -> Unit,
  onCommitSeek: (Long) -> Unit,
  onSeekBy: (Long) -> Unit,
  onSkipPrevious: () -> Unit,
  onSkipNext: () -> Unit,
  onToggleLoop: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onToggleMute: () -> Unit,
  onPitchChange: (Float) -> Unit = {},
  onStepPitch: (Int) -> Unit = {},
  onResetPitch: () -> Unit = {},
  onSelectReverbPreset: (ReverbPreset) -> Unit = {},
  onReverbWetMixChange: (Float) -> Unit = {},
  onReverbRoomSizeChange: (Float) -> Unit = {},
  onReverbDampingChange: (Float) -> Unit = {},
  onReverbEngineModeChange: (ReverbEngineMode) -> Unit = {},
  onResetReverb: () -> Unit = {},
  onSaveReverbCopy: () -> Unit = {},
  isApplyingReverb: Boolean = false,
  onEqualizerEnabledChange: (Boolean) -> Unit = {},
  onSelectEqualizerPreset: (EqPreset) -> Unit = {},
  onEqualizerBandGainChange: (bandIndex: Int, gainDb: Float) -> Unit = { _, _ -> },
  onResetEqualizer: () -> Unit = {},
  loudnessTarget: LoudnessTarget = LoudnessTarget.STREAMING_14,
  autoPostGainEnabled: Boolean = true,
  isNormalizing: Boolean = false,
  onAutoPostGainEnabledChange: (Boolean) -> Unit = {},
  onSelectLoudnessTarget: (LoudnessTarget) -> Unit = {},
  onNormalizeActiveTrack: () -> Unit = {},
  onNormalizeLibrary: () -> Unit = {},
  onCrossfadeEnabledChange: (Boolean) -> Unit = {},
  onCrossfadeDurationChange: (Float) -> Unit = {},
  onSelectCrossfadePreset: (Float) -> Unit = {},
  interactionMode: WaveformInteractionMode = WaveformInteractionMode.SCRUB,
  onSelectInteractionMode: (WaveformInteractionMode) -> Unit = {},
  onPaintEnvelopePoint: (fraction: Float, gain: Float) -> Unit = { _, _ -> },
  onMuteSegment: (startFraction: Float, endFraction: Float) -> Unit = { _, _ -> },
  onToggleMuteTap: (fraction: Float) -> Unit = {},
  onResetAudioEnvelope: () -> Unit = {},
  modifier: Modifier = Modifier,
  bufferDiagnostics: RawBufferDiagnostics? = null,
) {
  val colorScheme = MaterialTheme.colorScheme
  val hasTrack = track != null
  val durationMs = playbackState.durationMs.coerceAtLeast(track?.durationMs ?: 0L).coerceAtLeast(1L)
  val currentPositionMs = playbackState.positionMs.coerceIn(0L, durationMs)
  val progressFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

  val playButtonScale by animateFloatAsState(
    targetValue = if (playbackState.isPlaying) 1.04f else 1f,
    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
    label = "playButtonScale",
  )

  val glowAlpha by animateFloatAsState(
    targetValue = if (playbackState.isPlaying) 0.22f else 0.08f,
    animationSpec = tween(durationMillis = 350),
    label = "ambientGlow",
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("custom_audio_player"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    tonalElevation = 6.dp,
    border = BorderStroke(
      width = 1.dp,
      color = if (playbackState.isPlaying) {
        colorScheme.primary.copy(alpha = 0.45f)
      } else {
        colorScheme.outline
      },
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(
                colorScheme.primary.copy(alpha = glowAlpha),
                colorScheme.secondary.copy(alpha = glowAlpha * 0.45f),
                Color.Transparent,
              ),
              center = Offset(size.width * 0.25f, size.height * 0.15f),
              radius = size.maxDimension * 0.85f,
            )
          )
        }
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Track Info & Animated Disc Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        StudioDiscIndicator(
          isPlaying = playbackState.isPlaying,
          isPreparing = playbackState.isPreparing,
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = track?.title ?: stringResource(R.string.player_no_track_selected),
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("player_track_title"),
          )

          if (track != null) {
            Text(
              text = "\"${track.prompt}\"",
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val hasLiveBpm = playbackState.liveBpm in 55..180
              val displayBpm = when {
                hasLiveBpm -> playbackState.liveBpm
                bufferDiagnostics?.detectedBpm in 55..180 -> bufferDiagnostics!!.detectedBpm
                else -> track.bpm
              }
              val bpmLabel = when {
                hasLiveBpm && playbackState.isPlaying ->
                  stringResource(R.string.bpm_live_badge, displayBpm)
                playbackState.isBpmAnalyzing && playbackState.isPlaying ->
                  stringResource(R.string.bpm_live_analyzing)
                else -> stringResource(R.string.bpm_live_badge_plain, displayBpm)
              }
              val displayKey = PitchShiftDsp.transposeKeyLabel(track.musicalKey, playbackState.pitchSemitones)
              MetadataPill(text = track.genre, accentColor = colorScheme.primary)
              LiveBpmPill(
                text = bpmLabel,
                beatPulse = if (playbackState.isPlaying) playbackState.liveBeatPulse else 0f,
                isLiveActive = playbackState.isPlaying && (hasLiveBpm || playbackState.isBpmAnalyzing),
                accentColor = colorScheme.secondary,
              )
              MetadataPill(
                text = displayKey,
                accentColor = colorScheme.tertiary,
                modifier = Modifier.testTag("player_key_pill"),
              )
            }
          } else {
            Text(
              text = stringResource(R.string.empty_tracks_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Error message banner if playback fails
      playbackState.errorMessage?.let { error ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = colorScheme.tertiaryContainer,
          border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.5f)),
        ) {
          Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onTertiaryContainer,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }

      // 2. Canvas Waveform Visualizer + Visual Editing Modes + Precision Seek Slider
      val activeEnvelope = when {
        track != null && playbackState.currentTrackId == track.id -> playbackState.activeEnvelope
        track != null -> track.audioEnvelope
        else -> TrackAudioEnvelope()
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val modeItems = listOf(
            Triple(WaveformInteractionMode.SCRUB, stringResource(R.string.waveform_mode_scrub), Icons.Rounded.GraphicEq),
            Triple(WaveformInteractionMode.ENVELOPE, stringResource(R.string.waveform_mode_envelope), Icons.Rounded.ShowChart),
            Triple(WaveformInteractionMode.MUTE, stringResource(R.string.waveform_mode_mute), Icons.AutoMirrored.Rounded.VolumeOff),
          )
          modeItems.forEach { (mode, label, icon) ->
            val selected = interactionMode == mode
            val tag = when (mode) {
              WaveformInteractionMode.SCRUB -> "waveform_mode_scrub"
              WaveformInteractionMode.ENVELOPE -> "waveform_mode_envelope"
              WaveformInteractionMode.MUTE -> "waveform_mode_mute"
            }
            FilterChip(
              selected = selected,
              enabled = hasTrack,
              onClick = { onSelectInteractionMode(mode) },
              leadingIcon = {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = when (mode) {
                  WaveformInteractionMode.SCRUB -> colorScheme.primaryContainer
                  WaveformInteractionMode.ENVELOPE -> colorScheme.tertiaryContainer
                  WaveformInteractionMode.MUTE -> colorScheme.errorContainer
                },
                selectedLabelColor = when (mode) {
                  WaveformInteractionMode.SCRUB -> colorScheme.onPrimaryContainer
                  WaveformInteractionMode.ENVELOPE -> colorScheme.onTertiaryContainer
                  WaveformInteractionMode.MUTE -> colorScheme.onErrorContainer
                },
              ),
              modifier = Modifier
                .height(30.dp)
                .testTag(tag),
            )
          }

          if (hasTrack && activeEnvelope.isEdited) {
            TextButton(
              onClick = onResetAudioEnvelope,
              modifier = Modifier.testTag("waveform_reset_envelope_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = stringResource(R.string.waveform_reset_envelope),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }

        if (hasTrack && interactionMode != WaveformInteractionMode.SCRUB) {
          Text(
            text = when (interactionMode) {
              WaveformInteractionMode.ENVELOPE -> stringResource(R.string.waveform_hint_envelope)
              WaveformInteractionMode.MUTE -> stringResource(R.string.waveform_hint_mute)
              WaveformInteractionMode.SCRUB -> stringResource(R.string.waveform_hint_scrub)
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (interactionMode) {
              WaveformInteractionMode.ENVELOPE -> colorScheme.tertiary
              WaveformInteractionMode.MUTE -> colorScheme.error
              WaveformInteractionMode.SCRUB -> colorScheme.onSurfaceVariant
            },
            modifier = Modifier.testTag("waveform_mode_hint"),
          )
        }

        WaveformVisualizer(
          peaks = track?.waveformPeaks.orEmpty(),
          positionMs = currentPositionMs,
          durationMs = durationMs,
          isPlaying = playbackState.isPlaying,
          enabled = hasTrack && playbackState.isPrepared,
          contentDescriptionText = stringResource(R.string.cd_waveform_visualizer),
          onScrubStart = onBeginSeek,
          onScrubToPosition = onPreviewSeek,
          onScrubEnd = onCommitSeek,
          envelope = activeEnvelope,
          interactionMode = interactionMode,
          onPaintEnvelopePoint = onPaintEnvelopePoint,
          onMuteSegment = onMuteSegment,
          onToggleMuteTap = onToggleMuteTap,
          modifier = Modifier
            .fillMaxWidth()
            .height(if (interactionMode == WaveformInteractionMode.SCRUB) 52.dp else 76.dp)
            .testTag("player_waveform"),
        )

        Slider(
          value = if (hasTrack) currentPositionMs.toFloat() else 0f,
          onValueChange = { newValue ->
            if (hasTrack) {
              if (!playbackState.isScrubbing) {
                onBeginSeek()
              }
              onPreviewSeek(newValue.toLong())
            }
          },
          onValueChangeFinished = {
            if (hasTrack) {
              onCommitSeek(playbackState.positionMs)
            }
          },
          valueRange = 0f..durationMs.toFloat(),
          enabled = hasTrack && playbackState.isPrepared,
          colors = SliderDefaults.colors(
            thumbColor = colorScheme.primary,
            activeTrackColor = colorScheme.primary,
            inactiveTrackColor = colorScheme.surfaceContainerHigh,
            disabledThumbColor = colorScheme.outline,
            disabledActiveTrackColor = colorScheme.outline,
            disabledInactiveTrackColor = colorScheme.surfaceContainerHigh,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("player_seek_slider"),
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (hasTrack) formatDurationMs(currentPositionMs) else "00:00",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
            modifier = Modifier.testTag("player_elapsed_time"),
          )
          if (track != null) {
            Text(
              text = "WAV · ${formatFileSize(track.fileSizeBytes)}",
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = if (hasTrack) formatDurationMs(durationMs) else "00:00",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("player_total_time"),
          )
        }

        if (track != null && bufferDiagnostics != null && bufferDiagnostics.sampleCount > 0) {
          FourierSpectrumStrip(
            diagnostics = bufferDiagnostics,
            isPlaying = playbackState.isPlaying,
            liveBpm = playbackState.liveBpm,
            liveBpmConfidence = playbackState.liveBpmConfidence,
            liveBeatPulse = playbackState.liveBeatPulse,
            isBpmAnalyzing = playbackState.isBpmAnalyzing,
            liveSpectrum = playbackState.liveSpectrum,
          )
        }
      }

      // 3. Transport Controls Row (Loop, Prev, -5s, Play/Pause, +5s, Next)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val loopTint by animateColorAsState(
          targetValue = if (playbackState.isLooping) colorScheme.secondary else colorScheme.onSurfaceVariant,
          label = "loopTint",
        )
        IconButton(
          onClick = onToggleLoop,
          enabled = hasTrack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("player_loop_button"),
        ) {
          Icon(
            imageVector = if (playbackState.isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = stringResource(R.string.cd_toggle_loop),
            tint = if (hasTrack) loopTint else colorScheme.outline,
          )
        }

        IconButton(
          onClick = onSkipPrevious,
          enabled = hasTrack && canSkipPrevious,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("player_prev_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.cd_previous_track),
            tint = if (hasTrack && canSkipPrevious) colorScheme.onSurface else colorScheme.outline,
          )
        }

        IconButton(
          onClick = { onSeekBy(-5_000L) },
          enabled = hasTrack && playbackState.isPrepared,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("player_rewind_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Replay5,
            contentDescription = stringResource(R.string.cd_rewind_5s),
            tint = if (hasTrack && playbackState.isPrepared) colorScheme.onSurface else colorScheme.outline,
          )
        }

        FilledIconButton(
          onClick = onTogglePlayPause,
          enabled = hasTrack && !playbackState.isPreparing,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            disabledContainerColor = colorScheme.surfaceContainerHigh,
            disabledContentColor = colorScheme.onSurfaceVariant,
          ),
          modifier = Modifier
            .size(54.dp)
            .scale(playButtonScale)
            .testTag("player_play_pause_button"),
        ) {
          if (playbackState.isPreparing) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.5.dp,
              color = colorScheme.onPrimary,
            )
          } else {
            Icon(
              imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = if (playbackState.isPlaying) {
                stringResource(R.string.cd_pause_track)
              } else {
                stringResource(R.string.cd_play_track)
              },
              modifier = Modifier.size(32.dp),
            )
          }
        }

        IconButton(
          onClick = { onSeekBy(5_000L) },
          enabled = hasTrack && playbackState.isPrepared,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("player_forward_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Forward5,
            contentDescription = stringResource(R.string.cd_forward_5s),
            tint = if (hasTrack && playbackState.isPrepared) colorScheme.onSurface else colorScheme.outline,
          )
        }

        IconButton(
          onClick = onSkipNext,
          enabled = hasTrack && canSkipNext,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("player_next_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next_track),
            tint = if (hasTrack && canSkipNext) colorScheme.onSurface else colorScheme.outline,
          )
        }
      }

      // 4. Dedicated Volume Control Slider Section
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val volumeIcon = when {
            playbackState.isMuted || playbackState.effectiveVolume <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
            playbackState.effectiveVolume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
            else -> Icons.AutoMirrored.Rounded.VolumeUp
          }

          IconButton(
            onClick = onToggleMute,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("player_mute_button"),
          ) {
            Icon(
              imageVector = volumeIcon,
              contentDescription = stringResource(R.string.cd_mute_volume),
              tint = if (playbackState.isMuted) colorScheme.tertiary else colorScheme.secondary,
            )
          }

          Slider(
            value = playbackState.effectiveVolume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
              thumbColor = colorScheme.secondary,
              activeTrackColor = colorScheme.secondary,
              inactiveTrackColor = colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("player_volume_slider"),
          )

          val volumePercent = (playbackState.effectiveVolume * 100f).roundToInt()
          Text(
            text = "$volumePercent%",
            style = MaterialTheme.typography.labelMedium,
            color = if (playbackState.isMuted) colorScheme.tertiary else colorScheme.onSurface,
            modifier = Modifier
              .width(42.dp)
              .testTag("player_volume_label"),
          )
        }
      }

      // 4b. Real-Time 20-Band Frequency Spectrum Analyzer (40 Hz – 16 kHz)
      FrequencySpectrumAnalyzerCard(
        liveSpectrum = playbackState.liveSpectrum,
        bufferDiagnostics = bufferDiagnostics,
        isPlaying = playbackState.isPlaying,
        hasTrack = hasTrack,
      )

      // 5. Real-Time Pitch Shifting & Musical Key Transpose DSP Section
      val isPitchActive = abs(playbackState.pitchSemitones) >= 0.05f
      val semitoneBadge = PitchShiftDsp.formatSemitoneBadge(playbackState.pitchSemitones)
      val transposedKeyLabel = track?.let {
        PitchShiftDsp.transposeKeyLabel(it.musicalKey, playbackState.pitchSemitones)
      }
      val pitchSummary = if (transposedKeyLabel != null) {
        "$semitoneBadge · $transposedKeyLabel"
      } else {
        semitoneBadge
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isPitchActive) {
          colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
          colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (isPitchActive) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant,
        ),
        modifier = Modifier.testTag("player_pitch_dsp_card"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = if (isPitchActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = stringResource(R.string.pitch_dsp_title),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurface,
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = pitchSummary,
                style = MaterialTheme.typography.labelMedium,
                color = if (isPitchActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .padding(end = 4.dp)
                  .testTag("player_pitch_label"),
              )
              if (isPitchActive) {
                TextButton(
                  onClick = onResetPitch,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("player_pitch_reset_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.cd_pitch_reset),
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = stringResource(R.string.pitch_dsp_reset),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            IconButton(
              onClick = { onStepPitch(-1) },
              enabled = playbackState.pitchSemitones > PitchShiftDsp.MIN_SEMITONES + 0.05f,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("player_pitch_down_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.cd_pitch_down),
                tint = colorScheme.onSurfaceVariant,
              )
            }

            Slider(
              value = playbackState.pitchSemitones.coerceIn(
                PitchShiftDsp.MIN_SEMITONES,
                PitchShiftDsp.MAX_SEMITONES,
              ),
              onValueChange = { raw ->
                val snapped = raw.roundToInt().toFloat()
                onPitchChange(snapped)
              },
              valueRange = PitchShiftDsp.MIN_SEMITONES..PitchShiftDsp.MAX_SEMITONES,
              steps = 23,
              colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = colorScheme.surfaceContainerHigh,
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("player_pitch_slider"),
            )

            IconButton(
              onClick = { onStepPitch(1) },
              enabled = playbackState.pitchSemitones < PitchShiftDsp.MAX_SEMITONES - 0.05f,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("player_pitch_up_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.cd_pitch_up),
                tint = colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // 6. Real-Time Configurable Convolution & Spatial Reverb DSP Section
      val reverb = playbackState.reverb
      val isReverbActive = reverb.isActive
      val rt60Label = ConvolutionReverbDsp.formatRt60(reverb.roomSize)
      val reverbSummary = if (isReverbActive) {
        "${reverb.preset.label} · ${RoomReverbDsp.formatWetPercent(reverb.wetMix)}"
      } else {
        ReverbPreset.OFF.label
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isReverbActive) {
          colorScheme.secondaryContainer.copy(alpha = 0.42f)
        } else {
          colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (isReverbActive) colorScheme.secondary.copy(alpha = 0.5f) else colorScheme.outlineVariant,
        ),
        modifier = Modifier.testTag("player_reverb_dsp_card"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Rounded.SurroundSound,
                contentDescription = null,
                tint = if (isReverbActive) colorScheme.secondary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Column {
                Text(
                  text = stringResource(R.string.reverb_dsp_title),
                  style = MaterialTheme.typography.labelMedium,
                  color = colorScheme.onSurface,
                )
                Text(
                  text = stringResource(
                    R.string.reverb_convolution_subtitle,
                    rt60Label,
                    RoomReverbDsp.formatWetPercent(reverb.damping),
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.testTag("player_reverb_ir_badge"),
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = reverbSummary,
                style = MaterialTheme.typography.labelMedium,
                color = if (isReverbActive) colorScheme.secondary else colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .padding(end = 4.dp)
                  .testTag("player_reverb_label"),
              )
              if (isReverbActive) {
                TextButton(
                  onClick = onResetReverb,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("player_reverb_reset_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.cd_reverb_reset),
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = stringResource(R.string.pitch_dsp_reset),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }

          // Engine mode selector + Space presets row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilterChip(
              selected = reverb.engineMode == ReverbEngineMode.CONVOLUTION,
              onClick = {
                val nextMode = if (reverb.engineMode == ReverbEngineMode.CONVOLUTION) {
                  ReverbEngineMode.SCHROEDER
                } else {
                  ReverbEngineMode.CONVOLUTION
                }
                onReverbEngineModeChange(nextMode)
              },
              label = {
                Text(
                  text = reverb.engineMode.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.tertiaryContainer,
                selectedLabelColor = colorScheme.onTertiaryContainer,
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("reverb_engine_mode_chip"),
            )

            ReverbPreset.entries.forEach { preset ->
              val selected = reverb.preset == preset && (preset != ReverbPreset.OFF || !isReverbActive)
              FilterChip(
                selected = selected,
                onClick = { onSelectReverbPreset(preset) },
                label = {
                  Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.secondaryContainer,
                  selectedLabelColor = colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("reverb_preset_${preset.name.lowercase()}"),
              )
            }
          }

          // Convolution Impulse Response Decay Curve Preview
          val irEnvelope = ConvolutionReverbDsp.sampleImpulseEnvelopePreview(
            roomSize = reverb.roomSize,
            damping = reverb.damping,
            points = 24,
          )
          val irCanvasDesc = stringResource(R.string.cd_reverb_impulse_preview)
          val irFillColor = if (isReverbActive) colorScheme.secondary else colorScheme.outline
          val irDampTint = colorScheme.tertiary
          val irBg = colorScheme.surfaceContainerHighest
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
          ) {
            Canvas(
              modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = irCanvasDesc }
                .testTag("reverb_impulse_canvas"),
            ) {
              val w = size.width
              val h = size.height
              if (w <= 0f || h <= 0f || irEnvelope.isEmpty()) return@Canvas
              val slotW = w / irEnvelope.size.toFloat()
              val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())
              irEnvelope.forEachIndexed { idx, amp ->
                val x = idx * slotW + (slotW - barW) / 2f
                val barH = (h * amp).coerceIn(2.dp.toPx(), h)
                val barColor = if (idx < 3) irDampTint else irFillColor.copy(
                  alpha = (0.90f - reverb.damping * 0.45f * (idx / irEnvelope.size.toFloat())).coerceIn(0.28f, 0.92f)
                )
                drawRect(
                  color = irBg.copy(alpha = 0.35f),
                  topLeft = Offset(x, 0f),
                  size = androidx.compose.ui.geometry.Size(barW, h),
                )
                drawRect(
                  color = barColor,
                  topLeft = Offset(x, h - barH),
                  size = androidx.compose.ui.geometry.Size(barW, barH),
                )
              }
            }
          }

          // Selectable Room Size Presets
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.reverb_room_sizes_label),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ReverbRoomSizeOption.entries.forEach { sizeOption ->
                val isSelected = abs(reverb.roomSize - sizeOption.roomSize) < 0.08f
                FilterChip(
                  selected = isSelected,
                  onClick = { onReverbRoomSizeChange(sizeOption.roomSize) },
                  label = {
                    Text(
                      text = sizeOption.label,
                      style = MaterialTheme.typography.labelSmall,
                    )
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primaryContainer,
                    selectedLabelColor = colorScheme.onPrimaryContainer,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reverb_size_chip_${sizeOption.name.lowercase()}"),
                )
              }
            }
          }

          // Selectable Surface Dampening Level Presets
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.reverb_damping_levels_label),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ReverbDampingOption.entries.forEach { dampOption ->
                val isSelected = abs(reverb.damping - dampOption.damping) < 0.09f
                FilterChip(
                  selected = isSelected,
                  onClick = { onReverbDampingChange(dampOption.damping) },
                  label = {
                    Text(
                      text = dampOption.label,
                      style = MaterialTheme.typography.labelSmall,
                    )
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.tertiaryContainer,
                    selectedLabelColor = colorScheme.onTertiaryContainer,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reverb_damp_chip_${dampOption.name.lowercase()}"),
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.reverb_mix_label),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              modifier = Modifier.width(68.dp),
            )
            Slider(
              value = reverb.wetMix.coerceIn(0f, 1f),
              onValueChange = onReverbWetMixChange,
              valueRange = 0f..1f,
              colors = SliderDefaults.colors(
                thumbColor = colorScheme.secondary,
                activeTrackColor = colorScheme.secondary,
                inactiveTrackColor = colorScheme.surfaceContainerHigh,
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("player_reverb_mix_slider"),
            )
            Text(
              text = RoomReverbDsp.formatWetPercent(reverb.wetMix),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurface,
              modifier = Modifier.width(36.dp),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.reverb_size_label),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              modifier = Modifier.width(68.dp),
            )
            Slider(
              value = reverb.roomSize.coerceIn(0.10f, 0.98f),
              onValueChange = onReverbRoomSizeChange,
              valueRange = 0.10f..0.98f,
              colors = SliderDefaults.colors(
                thumbColor = colorScheme.secondary,
                activeTrackColor = colorScheme.secondary,
                inactiveTrackColor = colorScheme.surfaceContainerHigh,
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("player_reverb_size_slider"),
            )
            Text(
              text = RoomReverbDsp.formatWetPercent(reverb.roomSize),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurface,
              modifier = Modifier.width(36.dp),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.reverb_damping_label),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              modifier = Modifier.width(68.dp),
            )
            Slider(
              value = reverb.damping.coerceIn(0.05f, 0.95f),
              onValueChange = onReverbDampingChange,
              valueRange = 0.05f..0.95f,
              colors = SliderDefaults.colors(
                thumbColor = colorScheme.tertiary,
                activeTrackColor = colorScheme.tertiary,
                inactiveTrackColor = colorScheme.surfaceContainerHigh,
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("player_reverb_damping_slider"),
            )
            Text(
              text = RoomReverbDsp.formatWetPercent(reverb.damping),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurface,
              modifier = Modifier.width(36.dp),
            )
          }

          if (hasTrack && isReverbActive) {
            OutlinedButton(
              onClick = onSaveReverbCopy,
              enabled = !isApplyingReverb,
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("reverb_save_copy_button"),
            ) {
              if (isApplyingReverb) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reverb_saving_button))
              } else {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reverb_save_copy_button))
              }
            }
          }
        }
      }

      // 7. 8-Band Graphic Equalizer DSP Section
      val eq = playbackState.equalizer
      val isEqActive = eq.isActive
      val eqSummary = GraphicEqualizerDsp.formatSummaryBadge(eq)
      val eqToggleCd = stringResource(R.string.cd_eq_toggle)
      val eqBands = eq.normalizedBands
      val hasNonZeroEqBands = eqBands.any { abs(it) >= 0.1f }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isEqActive) {
          colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
          colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (isEqActive) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant,
        ),
        modifier = Modifier.testTag("player_eq_dsp_card"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = null,
                tint = if (isEqActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                  text = stringResource(R.string.eq_dsp_title),
                  style = MaterialTheme.typography.labelMedium,
                  color = colorScheme.onSurface,
                )
                Text(
                  text = eqSummary,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isEqActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                  modifier = Modifier.testTag("player_eq_label"),
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              if (hasNonZeroEqBands || eq.isEnabled) {
                TextButton(
                  onClick = onResetEqualizer,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("player_eq_reset_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(2.dp))
                  Text(
                    text = stringResource(R.string.pitch_dsp_reset),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }

              Switch(
                checked = eq.isEnabled,
                onCheckedChange = onEqualizerEnabledChange,
                modifier = Modifier
                  .semantics { contentDescription = eqToggleCd }
                  .testTag("player_eq_toggle"),
              )
            }
          }

          Text(
            text = stringResource(R.string.eq_dsp_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            EqPreset.selectablePresets.forEach { preset ->
              val selected = if (preset == EqPreset.FLAT) {
                !eq.isEnabled || (eq.preset == EqPreset.FLAT && !hasNonZeroEqBands)
              } else {
                eq.isEnabled && eq.preset == preset
              }
              FilterChip(
                selected = selected,
                onClick = { onSelectEqualizerPreset(preset) },
                label = {
                  Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.primaryContainer,
                  selectedLabelColor = colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("eq_preset_${preset.name.lowercase()}"),
              )
            }
          }

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            for (bandIdx in 0 until GraphicEqualizerDsp.BAND_COUNT) {
              val gainDb = eqBands[bandIdx]
              val isBoosted = gainDb > 0.05f
              val isCut = gainDb < -0.05f
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = GraphicEqualizerDsp.BAND_LABELS[bandIdx],
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onSurfaceVariant,
                  modifier = Modifier.width(30.dp),
                )
                Slider(
                  value = gainDb.coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB),
                  onValueChange = { raw ->
                    onEqualizerBandGainChange(bandIdx, raw)
                  },
                  valueRange = GraphicEqualizerDsp.MIN_GAIN_DB..GraphicEqualizerDsp.MAX_GAIN_DB,
                  colors = SliderDefaults.colors(
                    thumbColor = when {
                      isBoosted -> colorScheme.primary
                      isCut -> colorScheme.tertiary
                      else -> colorScheme.outline
                    },
                    activeTrackColor = when {
                      isBoosted -> colorScheme.primary
                      isCut -> colorScheme.tertiary
                      else -> colorScheme.outline
                    },
                    inactiveTrackColor = colorScheme.surfaceContainerHigh,
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .testTag("eq_band_slider_$bandIdx"),
                )
                Text(
                  text = "${GraphicEqualizerDsp.formatGainDb(gainDb)} dB",
                  style = MaterialTheme.typography.labelSmall,
                  color = when {
                    isBoosted -> colorScheme.primary
                    isCut -> colorScheme.tertiary
                    else -> colorScheme.onSurfaceVariant
                  },
                  modifier = Modifier
                    .width(56.dp)
                    .testTag("eq_band_value_$bandIdx"),
                )
              }
            }
          }
        }
      }

      // 7. ITU-R BS.1770-4 LUFS Post-Processing Gain Normalization DSP Section
      val measuredLufsText = bufferDiagnostics?.let {
        "%.1f LUFS · %.1f dBTP".format(it.integratedLufs, it.truePeakDbfs)
      } ?: loudnessTarget.label
      val autoLevelToggleCd = stringResource(R.string.loudness_auto_level_cd)
      val trimBadge = LoudnessNormalizerDsp.formatGainTrimBadge(playbackState.autoGainTrimDb)
      val autoStatusText = if (autoPostGainEnabled) {
        val baseStatus = stringResource(R.string.loudness_auto_active_status, loudnessTarget.label)
        if (hasTrack && kotlin.math.abs(playbackState.autoGainTrimDb) >= 0.05f) {
          "$baseStatus · Live Trim $trimBadge"
        } else {
          baseStatus
        }
      } else {
        stringResource(R.string.loudness_auto_paused_status)
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (autoPostGainEnabled) {
          colorScheme.tertiaryContainer.copy(alpha = 0.28f)
        } else {
          colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (autoPostGainEnabled) {
            colorScheme.tertiary.copy(alpha = 0.45f)
          } else {
            colorScheme.outlineVariant
          },
        ),
        modifier = Modifier.testTag("player_loudness_dsp_card"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = null,
                tint = colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = stringResource(R.string.loudness_dsp_title),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.35f)),
              ) {
                Text(
                  text = measuredLufsText,
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onTertiaryContainer,
                  modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("player_lufs_badge"),
                )
              }

              Switch(
                checked = autoPostGainEnabled,
                onCheckedChange = onAutoPostGainEnabledChange,
                enabled = !isNormalizing,
                modifier = Modifier
                  .scale(0.8f)
                  .minimumInteractiveComponentSize()
                  .semantics { contentDescription = autoLevelToggleCd }
                  .testTag("player_auto_post_gain_toggle"),
              )
            }
          }

          Text(
            text = stringResource(R.string.loudness_dsp_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )

          Text(
            text = autoStatusText,
            style = MaterialTheme.typography.labelSmall,
            color = if (autoPostGainEnabled) colorScheme.tertiary else colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("player_auto_post_gain_status"),
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            LoudnessTarget.entries.forEach { target ->
              val selected = loudnessTarget == target
              FilterChip(
                selected = selected,
                enabled = !isNormalizing,
                onClick = { onSelectLoudnessTarget(target) },
                label = {
                  Text(
                    text = "${target.label} · ${target.subtitle}",
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.tertiaryContainer,
                  selectedLabelColor = colorScheme.onTertiaryContainer,
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("lufs_target_${target.name.lowercase()}"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onNormalizeActiveTrack,
              enabled = hasTrack && !isNormalizing,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("normalize_track_button"),
            ) {
              if (isNormalizing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.normalizing_label),
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.Equalizer,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.normalize_track_button),
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            OutlinedButton(
              onClick = onNormalizeLibrary,
              enabled = !isNormalizing,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("normalize_library_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.normalize_library_button),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      // 8. Playlist Crossfade Dual-Deck Transition Section
      val crossfade = playbackState.crossfade
      val isCrossfadeActive = crossfade.isActive
      val crossfadeBadgeText = CrossfadeDsp.formatDurationBadge(crossfade)
      val crossfadeToggleCd = stringResource(R.string.cd_toggle_crossfade)

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isCrossfadeActive) {
          colorScheme.secondaryContainer.copy(alpha = 0.35f)
        } else {
          colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (isCrossfadeActive) colorScheme.secondary.copy(alpha = 0.45f) else colorScheme.outlineVariant,
        ),
        modifier = Modifier.testTag("player_crossfade_card"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = if (isCrossfadeActive) colorScheme.secondary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                  text = stringResource(R.string.crossfade_title),
                  style = MaterialTheme.typography.labelMedium,
                  color = colorScheme.onSurface,
                )
                Text(
                  text = crossfadeBadgeText,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isCrossfadeActive) colorScheme.secondary else colorScheme.onSurfaceVariant,
                  modifier = Modifier.testTag("player_crossfade_badge"),
                )
              }
            }

            Switch(
              checked = isCrossfadeActive,
              onCheckedChange = onCrossfadeEnabledChange,
              modifier = Modifier
                .semantics { contentDescription = crossfadeToggleCd }
                .testTag("player_crossfade_toggle"),
            )
          }

          Text(
            text = stringResource(R.string.crossfade_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )

          if (playbackState.isLooping && isCrossfadeActive) {
            Text(
              text = stringResource(R.string.crossfade_loop_note),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.tertiary,
            )
          }

          if (playbackState.isCrossfading && !playbackState.crossfadeNextTrackTitle.isNullOrBlank()) {
            val pct = (playbackState.crossfadeProgress * 100f).roundToInt().coerceIn(0, 100)
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = colorScheme.primaryContainer.copy(alpha = 0.7f),
              modifier = Modifier.testTag("player_crossfade_active_banner"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = stringResource(
                    R.string.crossfade_active_transition,
                    playbackState.crossfadeNextTrackTitle,
                    pct,
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onPrimaryContainer,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                  progress = { playbackState.crossfadeProgress.coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            CrossfadeDsp.PRESET_SECONDS.forEach { sec ->
              val isOffPreset = sec == 0
              val selected = if (isOffPreset) {
                !isCrossfadeActive
              } else {
                isCrossfadeActive && abs(crossfade.durationSec - sec.toFloat()) < 0.25f
              }
              FilterChip(
                selected = selected,
                onClick = { onSelectCrossfadePreset(sec.toFloat()) },
                label = {
                  Text(
                    text = if (isOffPreset) stringResource(R.string.crossfade_off_label) else "${sec}s",
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.secondaryContainer,
                  selectedLabelColor = colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("crossfade_preset_$sec"),
              )
            }
          }

          Slider(
            value = if (isCrossfadeActive) {
              crossfade.durationSec.coerceIn(CrossfadeDsp.MIN_DURATION_SEC, CrossfadeDsp.MAX_DURATION_SEC)
            } else {
              0f
            },
            onValueChange = { raw ->
              val snappedHalf = (raw * 2f).roundToInt() / 2f
              onCrossfadeDurationChange(snappedHalf)
            },
            valueRange = CrossfadeDsp.MIN_DURATION_SEC..CrossfadeDsp.MAX_DURATION_SEC,
            steps = 23,
            colors = SliderDefaults.colors(
              thumbColor = colorScheme.secondary,
              activeTrackColor = colorScheme.secondary,
              inactiveTrackColor = colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("player_crossfade_slider"),
          )
        }
      }
    }
  }
}

@Composable
private fun StudioDiscIndicator(
  isPlaying: Boolean,
  isPreparing: Boolean,
) {
  val colorScheme = MaterialTheme.colorScheme
  val infiniteTransition = rememberInfiniteTransition(label = "discRotation")
  val rotationAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4_800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "discAngle",
  )

  val activeAngle = if (isPlaying) rotationAngle else 0f

  Box(
    modifier = Modifier
      .size(48.dp)
      .clip(CircleShape)
      .background(colorScheme.surfaceContainerHighest)
      .border(
        width = 1.5.dp,
        color = if (isPlaying) colorScheme.primary else colorScheme.outline,
        shape = CircleShape,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      rotate(degrees = activeAngle) {
        drawCircle(
          color = colorScheme.primary.copy(alpha = if (isPlaying) 0.25f else 0.10f),
          radius = size.minDimension * 0.36f,
        )
        drawLine(
          color = colorScheme.secondary.copy(alpha = if (isPlaying) 0.7f else 0.25f),
          start = Offset(center.x, center.y - size.minDimension * 0.42f),
          end = Offset(center.x, center.y - size.minDimension * 0.24f),
          strokeWidth = 3f,
        )
      }
    }
    if (isPreparing) {
      CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        strokeWidth = 2.dp,
        color = colorScheme.primary,
      )
    } else {
      Icon(
        imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = if (isPlaying) colorScheme.primary else colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun LiveBpmPill(
  text: String,
  beatPulse: Float,
  isLiveActive: Boolean,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val clampedPulse = beatPulse.coerceIn(0f, 1f)
  val pulseCd = stringResource(R.string.cd_beat_pulse_indicator)
  Surface(
    modifier = modifier.testTag("player_bpm_pill"),
    shape = RoundedCornerShape(6.dp),
    color = accentColor.copy(alpha = if (isLiveActive) 0.18f + clampedPulse * 0.14f else 0.14f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = if (isLiveActive) 0.45f else 0.3f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (isLiveActive) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .scale(0.85f + clampedPulse * 0.55f)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.45f + clampedPulse * 0.55f))
            .semantics { contentDescription = pulseCd }
            .testTag("player_beat_pulse_dot"),
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = accentColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun MetadataPill(
  text: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(6.dp),
    color = accentColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

private fun formatFileSize(bytes: Long): String {
  val kb = bytes / 1024.0
  return if (kb >= 1024.0) {
    "%.1f MB".format(kb / 1024.0)
  } else {
    "${kb.roundToInt()} KB"
  }
}

fun formatDurationMs(durationMs: Long): String {
  val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return "%02d:%02d".format(minutes, seconds)
}
