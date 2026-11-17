package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.RawBufferDiagnostics
import com.example.audio.RealTimeSpectrumAnalyzer
import com.example.audio.RealTimeSpectrumSnapshot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Displays live/decoded Fourier FFT spectral magnitude bins alongside raw PCM audio buffer
 * diagnostics (sample rate, bit depth, sample count, RMS dB, and spectral centroid Hz).
 */
@Composable
fun FourierSpectrumStrip(
  diagnostics: RawBufferDiagnostics,
  isPlaying: Boolean,
  liveBpm: Int = 0,
  liveBpmConfidence: Float = 0f,
  liveBeatPulse: Float = 0f,
  isBpmAnalyzing: Boolean = false,
  liveSpectrum: RealTimeSpectrumSnapshot = RealTimeSpectrumSnapshot(),
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val infiniteTransition = rememberInfiniteTransition(label = "fftSpectrumMotion")
  val phase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 6.2831855f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1_600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "fftPhase",
  )

  val primaryColor = colorScheme.primary
  val secondaryColor = colorScheme.secondary
  val trackBg = colorScheme.surfaceContainerHigh
  val beatLift = if (isPlaying) 1f + liveBeatPulse.coerceIn(0f, 1f) * 0.22f else 1f
  val useLiveFft = isPlaying && liveSpectrum.isActive && liveSpectrum.bandLevels.isNotEmpty()

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
    modifier = modifier
      .fillMaxWidth()
      .testTag("fourier_buffer_diagnostics"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Canvas(
        modifier = Modifier
          .width(88.dp)
          .height(34.dp)
          .testTag("fourier_fft_canvas"),
      ) {
        val bins = if (useLiveFft) {
          RealTimeSpectrumAnalyzer.downsampleToBins(liveSpectrum.bandLevels, 12)
        } else {
          diagnostics.fourierSpectrumBins.ifEmpty { List(12) { 0.35f } }
        }
        val count = bins.size
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.62f).coerceAtLeast(2.dp.toPx())
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        bins.forEachIndexed { idx, amp ->
          val levelFactor = if (useLiveFft) {
            amp.coerceIn(0.06f, 1f)
          } else {
            val shimmer = if (isPlaying) {
              (0.84f + 0.16f * sin(phase + idx * 0.65f)) * beatLift
            } else {
              1f
            }
            amp.coerceIn(0.08f, 1f) * shimmer
          }
          val barHeight = (size.height * levelFactor)
            .coerceIn(3.dp.toPx(), size.height)
          val x = idx * slotWidth + (slotWidth - barWidth) / 2f
          val y = size.height - barHeight

          drawRoundRect(
            color = trackBg,
            topLeft = Offset(x, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = radius,
          )
          val barColor = if (idx < count / 2) secondaryColor else primaryColor
          drawRoundRect(
            color = barColor,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = radius,
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        val sampleRateKhz = "%.1f kHz".format(diagnostics.sampleRateHz / 1000.0)
        val channelLabel = if (diagnostics.channelCount >= 2) "Stereo" else "Mono"
        val bpmText = when {
          liveBpm > 0 -> {
            val confPct = (liveBpmConfidence * 100f).roundToInt()
            " · Live $liveBpm BPM ($confPct%)"
          }
          isBpmAnalyzing && isPlaying -> " · Analyzing BPM…"
          diagnostics.detectedBpm > 0 -> {
            val confPct = (diagnostics.bpmConfidence * 100f).roundToInt()
            " · Beat ${diagnostics.detectedBpm} BPM ($confPct%)"
          }
          else -> ""
        }
        Text(
          text = "Raw PCM · $sampleRateKhz · 16-bit $channelLabel$bpmText",
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.secondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("fourier_bpm_readout"),
        )
        val sampleCountLabel = if (diagnostics.sampleCount >= 1_000_000) {
          "%.2fM samples".format(diagnostics.sampleCount / 1_000_000.0)
        } else if (diagnostics.sampleCount >= 1_000) {
          "${(diagnostics.sampleCount / 1_000.0).roundToInt()}k samples"
        } else {
          "${diagnostics.sampleCount} samples"
        }
        val rmsLabel = "%.1f dB".format(diagnostics.rmsDb)
        val lufsLabel = "%.1f LUFS".format(diagnostics.integratedLufs)
        Text(
          text = "$lufsLabel · RMS $rmsLabel · Centroid ${diagnostics.spectralCentroidHz} Hz",
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("fourier_lufs_readout"),
        )
      }
    }
  }
}
