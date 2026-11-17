package com.example.ai

import com.example.audio.RawAudioBuffer
import com.example.audio.RawBufferDiagnostics
import com.example.model.GeneratedTrack
import java.util.Locale

data class TrackAutoTagInput(
  val title: String,
  val prompt: String,
  val genre: String,
  val bpm: Int,
  val musicalKey: String,
  val durationMs: Long,
  val spectralCentroidHz: Int,
  val rmsDb: Float,
  val integratedLufs: Float,
  val fourierSpectrumBins: List<Float>,
)

data class TrackAutoTagResult(
  val suggestedCategory: String,
  val suggestedTags: List<String>,
  val moodLabel: String,
  val confidenceLabel: String,
  val isPlaceholder: Boolean,
  val noticeMessage: String?,
)

/**
 * Abstraction for AI-based audio + metadata auto-tagging.
 * Backed by [PlaceholderTrackAutoTagger] when Firebase AI Logic is not enabled.
 */
interface TrackAutoTagger {
  suspend fun analyzeTrack(input: TrackAutoTagInput): TrackAutoTagResult
}

/**
 * Deterministic acoustic & metadata auto-tagger returning clearly labeled sample AI
 * suggestions while Firebase AI Logic (Gemini) is disabled.
 */
class PlaceholderTrackAutoTagger : TrackAutoTagger {

  override suspend fun analyzeTrack(input: TrackAutoTagInput): TrackAutoTagResult {
    val promptLower = "${input.title} ${input.prompt} ${input.genre}".lowercase(Locale.US)
    val lowEndEnergy = input.fourierSpectrumBins.take(3).averageOrDefault(0.4)
    val highEndEnergy = input.fourierSpectrumBins.takeLast(4).averageOrDefault(0.3)

    val suggestedCategory = when {
      promptLower.contains("ambient") ||
        promptLower.contains("meditation") ||
        promptLower.contains("sleep") ||
        promptLower.contains("pad") ||
        (input.bpm in 1..78 && input.spectralCentroidHz < 1_050) -> "Ambient"

      promptLower.contains("lo-fi") ||
        promptLower.contains("lofi") ||
        promptLower.contains("rhodes") ||
        promptLower.contains("study") ||
        (input.bpm in 70..94 && lowEndEnergy >= highEndEnergy) -> "Lo-Fi"

      promptLower.contains("synthwave") ||
        promptLower.contains("retro") ||
        promptLower.contains("neon") -> "Synthwave"

      promptLower.contains("cinematic") ||
        promptLower.contains("piano") ||
        promptLower.contains("orchestral") -> "Cinematic"

      input.bpm >= 114 ||
        promptLower.contains("upbeat") ||
        promptLower.contains("dance") ||
        promptLower.contains("energetic") ||
        promptLower.contains("techno") -> "Upbeat"

      else -> "Electronic"
    }

    val tags = linkedSetOf<String>()
    when (suggestedCategory) {
      "Ambient" -> {
        tags.add("ambient")
        tags.add("atmospheric")
      }
      "Lo-Fi" -> {
        tags.add("lo-fi")
        tags.add("chill")
      }
      "Upbeat" -> {
        tags.add("upbeat")
        tags.add("energetic")
      }
      "Synthwave" -> {
        tags.add("synthwave")
        tags.add("retro")
      }
      "Cinematic" -> {
        tags.add("cinematic")
        tags.add("emotive")
      }
      else -> {
        tags.add("electronic")
        tags.add("groove")
      }
    }

    if (input.bpm >= 116) {
      tags.add("upbeat")
    } else if (input.bpm in 1..85) {
      tags.add("relaxed")
    }

    if (input.spectralCentroidHz >= 1_500 || highEndEnergy > 0.55) {
      tags.add("bright")
    } else if (lowEndEnergy > 0.55) {
      tags.add("warm-bass")
    }

    if (input.integratedLufs >= -15.0f && input.rmsDb >= -18.0f) {
      tags.add("mastered")
    }

    val moodLabel = when {
      suggestedCategory == "Ambient" -> "Calm & Spacious"
      suggestedCategory == "Lo-Fi" -> "Warm & Focused"
      input.bpm >= 116 -> "Upbeat & Driving"
      else -> "Balanced & Melodic"
    }

    return TrackAutoTagResult(
      suggestedCategory = suggestedCategory,
      suggestedTags = tags.take(4),
      moodLabel = moodLabel,
      confidenceLabel = "Sample heuristic (${input.bpm} BPM · ${input.spectralCentroidHz} Hz)",
      isPlaceholder = true,
      noticeMessage = FIREBASE_REQUIRED_NOTICE,
    )
  }

  private fun List<Float>.averageOrDefault(defaultValue: Double): Double {
    if (isEmpty()) return defaultValue
    return map { it.toDouble() }.average()
  }

  companion object {
    const val FIREBASE_REQUIRED_NOTICE =
      "Sample auto-tags shown — Gemini AI analysis requires Firebase setup."

    fun buildInput(
      track: GeneratedTrack,
      rawBuffer: RawAudioBuffer? = null,
      diagnostics: RawBufferDiagnostics? = null,
    ): TrackAutoTagInput {
      val bpm = diagnostics?.detectedBpm?.takeIf { it in 55..180 }
        ?: rawBuffer?.detectedBpm?.takeIf { it in 55..180 }
        ?: track.bpm
      return TrackAutoTagInput(
        title = track.title,
        prompt = track.prompt,
        genre = track.genre,
        bpm = bpm,
        musicalKey = track.musicalKey,
        durationMs = track.durationMs,
        spectralCentroidHz = diagnostics?.spectralCentroidHz
          ?: rawBuffer?.spectralCentroidHz
          ?: 1_000,
        rmsDb = diagnostics?.rmsDb ?: rawBuffer?.rmsDb ?: -16.0f,
        integratedLufs = diagnostics?.integratedLufs ?: rawBuffer?.integratedLufs ?: -14.0f,
        fourierSpectrumBins = diagnostics?.fourierSpectrumBins
          ?: rawBuffer?.fourierSpectrumBins
          ?: emptyList(),
      )
    }
  }
}
