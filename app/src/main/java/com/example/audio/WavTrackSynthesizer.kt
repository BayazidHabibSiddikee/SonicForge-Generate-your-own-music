package com.example.audio

import com.example.model.GeneratedTrack
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

data class SynthesizedNoteEvent(
  val midiNote: Int,
  val startStep: Int,
  val durationSteps: Int,
  val velocity: Int,
)

data class SynthesizedSequencePattern(
  val profile: WavTrackSynthesizer.StyleProfile,
  val bpm: Int,
  val rootMidi: Int,
  val musicalKey: String,
  val barCount: Int,
  val beatsPerBar: Int,
  val stepsPerBar: Int,
  val motif: IntArray,
  val melodyNotes: List<SynthesizedNoteEvent>,
  val chordNotes: List<SynthesizedNoteEvent>,
  val bassNotes: List<SynthesizedNoteEvent>,
  val drumHits: List<SynthesizedNoteEvent>,
) {
  val totalSteps: Int
    get() = barCount * stepsPerBar
}

/**
 * Procedural DSP music synthesizer that converts a natural-language text prompt
 * into a playable 16-bit PCM WAV audio file and extracts real waveform peak amplitudes.
 */
object WavTrackSynthesizer {

  private const val SAMPLE_RATE = 44_100
  private const val NUM_CHANNELS = 1
  private const val BITS_PER_SAMPLE = 16
  private const val WAVEFORM_BAR_COUNT = 56

  enum class StyleProfile(
    val displayName: String,
    val defaultBpm: Int,
    val scaleIntervals: IntArray,
    val chordProgression: List<IntArray>,
    val hasDrums: Boolean,
    val bassOctaveShift: Int,
    val leadBrightness: Double,
    val padWarmth: Double,
  ) {
    SYNTHWAVE(
      displayName = "Synthwave",
      defaultBpm = 112,
      scaleIntervals = intArrayOf(0, 2, 3, 5, 7, 8, 10),
      chordProgression = listOf(
        intArrayOf(0, 3, 7),
        intArrayOf(8, 12, 15),
        intArrayOf(5, 8, 12),
        intArrayOf(7, 10, 14),
      ),
      hasDrums = true,
      bassOctaveShift = -24,
      leadBrightness = 0.72,
      padWarmth = 0.65,
    ),
    LOFI_CHILL(
      displayName = "Lo-Fi Chill",
      defaultBpm = 84,
      scaleIntervals = intArrayOf(0, 2, 4, 7, 9, 11),
      chordProgression = listOf(
        intArrayOf(2, 5, 9, 12),
        intArrayOf(7, 11, 14, 17),
        intArrayOf(0, 4, 7, 11),
        intArrayOf(9, 12, 16, 19),
      ),
      hasDrums = true,
      bassOctaveShift = -24,
      leadBrightness = 0.38,
      padWarmth = 0.85,
    ),
    AMBIENT_DRIFT(
      displayName = "Ambient",
      defaultBpm = 72,
      scaleIntervals = intArrayOf(0, 2, 4, 7, 9),
      chordProgression = listOf(
        intArrayOf(0, 7, 12, 16),
        intArrayOf(5, 12, 17, 21),
        intArrayOf(9, 16, 21, 24),
        intArrayOf(7, 14, 19, 23),
      ),
      hasDrums = false,
      bassOctaveShift = -24,
      leadBrightness = 0.45,
      padWarmth = 0.92,
    ),
    TECHNO_PULSE(
      displayName = "Electronic",
      defaultBpm = 126,
      scaleIntervals = intArrayOf(0, 1, 3, 5, 7, 8, 10),
      chordProgression = listOf(
        intArrayOf(0, 7, 12),
        intArrayOf(0, 7, 10),
        intArrayOf(3, 10, 15),
        intArrayOf(1, 8, 13),
      ),
      hasDrums = true,
      bassOctaveShift = -24,
      leadBrightness = 0.85,
      padWarmth = 0.50,
    ),
    PIANO_CINEMA(
      displayName = "Cinematic",
      defaultBpm = 94,
      scaleIntervals = intArrayOf(0, 2, 3, 5, 7, 9, 10),
      chordProgression = listOf(
        intArrayOf(0, 3, 7, 12),
        intArrayOf(5, 8, 12, 17),
        intArrayOf(10, 14, 17, 22),
        intArrayOf(7, 10, 14, 19),
      ),
      hasDrums = true,
      bassOctaveShift = -24,
      leadBrightness = 0.55,
      padWarmth = 0.78,
    ),
  }

  private val rootNotes = listOf(
    "C" to 60,
    "D" to 62,
    "Eb" to 63,
    "E" to 64,
    "F" to 65,
    "G" to 67,
    "A" to 69,
    "Bb" to 70,
  )

  fun synthesizeToFile(
    prompt: String,
    outputDir: File,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): GeneratedTrack {
    return synthesizeWithRawBuffer(prompt, outputDir, targetLufs, applyPostGain).first
  }

  fun extractSequencePattern(
    prompt: String,
    overrideBpm: Int? = null,
  ): SynthesizedSequencePattern {
    val cleanPrompt = prompt.trim().ifEmpty { "Warm rainy lo-fi chill beat" }
    val normalized = cleanPrompt.lowercase()
    val seed = cleanPrompt.hashCode()
    val rng = Random(seed)

    val profile = detectProfile(normalized)
    val generatedBpm = extractBpm(normalized) ?: (profile.defaultBpm + rng.nextInt(-6, 7)).coerceIn(66, 142)
    val bpm = overrideBpm?.takeIf { it in 55..180 } ?: generatedBpm
    val rootPair = rootNotes[abs(seed) % rootNotes.size]
    val rootMidi = rootPair.second
    val musicalKey = if (profile == StyleProfile.LOFI_CHILL || profile == StyleProfile.AMBIENT_DRIFT) {
      "${rootPair.first} Maj"
    } else {
      "${rootPair.first} Min"
    }

    val barCount = 8
    val beatsPerBar = 4
    val stepsPerBar = 16
    val totalSteps = barCount * stepsPerBar

    // Deterministic 16-step melody motif matching audio synthesis
    val motif = IntArray(stepsPerBar) { step ->
      when {
        step % 2 == 0 -> profile.scaleIntervals[rng.nextInt(profile.scaleIntervals.size)]
        rng.nextFloat() < 0.45f -> profile.scaleIntervals[rng.nextInt(profile.scaleIntervals.size)]
        else -> -1
      }
    }

    val chordNotes = ArrayList<SynthesizedNoteEvent>(barCount * 4)
    for (bar in 0 until barCount) {
      val chord = profile.chordProgression[bar % profile.chordProgression.size]
      val startStep = bar * stepsPerBar
      for (noteOffset in chord) {
        chordNotes.add(
          SynthesizedNoteEvent(
            midiNote = (rootMidi - 12 + noteOffset).coerceIn(24, 108),
            startStep = startStep,
            durationSteps = stepsPerBar,
            velocity = 72,
          )
        )
      }
    }

    val melodyNotes = ArrayList<SynthesizedNoteEvent>(totalSteps)
    val bassNotes = ArrayList<SynthesizedNoteEvent>(barCount * 4)
    val drumHits = ArrayList<SynthesizedNoteEvent>(totalSteps * 2)

    for (step in 0 until totalSteps) {
      val barIndex = step / stepsPerBar
      val stepInBar = step % stepsPerBar
      val chord = profile.chordProgression[barIndex % profile.chordProgression.size]

      val playBass = stepInBar == 0 || stepInBar == 6 || stepInBar == 10 ||
        (profile == StyleProfile.TECHNO_PULSE && stepInBar % 4 == 2)
      if (playBass) {
        bassNotes.add(
          SynthesizedNoteEvent(
            midiNote = (rootMidi + profile.bassOctaveShift + chord[0]).coerceIn(24, 84),
            startStep = step,
            durationSteps = 2,
            velocity = if (stepInBar == 0) 100 else 86,
          )
        )
      }

      val motifNote = motif[stepInBar]
      if (motifNote >= 0) {
        val octaveLift = if (barIndex % 4 == 3 && stepInBar >= 8) 12 else 0
        melodyNotes.add(
          SynthesizedNoteEvent(
            midiNote = (rootMidi + motifNote + octaveLift).coerceIn(36, 108),
            startStep = step,
            durationSteps = 2,
            velocity = if (stepInBar % 4 == 0) 104 else 88,
          )
        )
      }

      if (profile.hasDrums) {
        val isKick = stepInBar == 0 || stepInBar == 8 ||
          ((profile == StyleProfile.TECHNO_PULSE || profile == StyleProfile.SYNTHWAVE) && (stepInBar == 4 || stepInBar == 12))
        if (isKick) {
          drumHits.add(SynthesizedNoteEvent(midiNote = 36, startStep = step, durationSteps = 1, velocity = 110))
        }
        if (stepInBar == 4 || stepInBar == 12) {
          drumHits.add(SynthesizedNoteEvent(midiNote = 38, startStep = step, durationSteps = 1, velocity = 96))
        }
        if (stepInBar % 2 == 0) {
          drumHits.add(SynthesizedNoteEvent(midiNote = 42, startStep = step, durationSteps = 1, velocity = 74))
        }
      }
    }

    return SynthesizedSequencePattern(
      profile = profile,
      bpm = bpm,
      rootMidi = rootMidi,
      musicalKey = musicalKey,
      barCount = barCount,
      beatsPerBar = beatsPerBar,
      stepsPerBar = stepsPerBar,
      motif = motif,
      melodyNotes = melodyNotes,
      chordNotes = chordNotes,
      bassNotes = bassNotes,
      drumHits = drumHits,
    )
  }

  fun synthesizeWithRawBuffer(
    prompt: String,
    outputDir: File,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): Pair<GeneratedTrack, RawAudioBuffer> {
    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    val cleanPrompt = prompt.trim().ifEmpty { "Warm rainy lo-fi chill beat" }
    val normalized = cleanPrompt.lowercase()
    val seed = cleanPrompt.hashCode()
    val rng = Random(seed)

    val pattern = extractSequencePattern(cleanPrompt)
    val profile = pattern.profile
    val bpm = pattern.bpm
    val rootMidi = pattern.rootMidi
    val musicalKey = pattern.musicalKey

    val barCount = pattern.barCount
    val beatsPerBar = pattern.beatsPerBar
    val secondsPerBeat = 60.0 / bpm
    val totalSeconds = (barCount * beatsPerBar * secondsPerBeat).coerceIn(14.0, 24.0)
    val totalSamples = (totalSeconds * SAMPLE_RATE).toInt()
    val buffer = DoubleArray(totalSamples)

    val stepsPerBar = pattern.stepsPerBar
    val totalSteps = pattern.totalSteps
    val stepDurationSec = totalSeconds / totalSteps
    val motif = pattern.motif

    val stepSamples = (stepDurationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
    val chordDurationSamples = ((beatsPerBar * secondsPerBeat) * SAMPLE_RATE).toInt().coerceAtLeast(1)

    // 1. Render chord pads across each bar
    for (bar in 0 until barCount) {
      val chord = profile.chordProgression[bar % profile.chordProgression.size]
      val barStartSample = (bar * beatsPerBar * secondsPerBeat * SAMPLE_RATE).toInt()
      val barEndSample = min(totalSamples, barStartSample + chordDurationSamples)

      for (noteOffset in chord) {
        val freq = midiToFreq(rootMidi - 12 + noteOffset)
        var phase1 = rng.nextDouble() * 2.0 * PI
        var phase2 = rng.nextDouble() * 2.0 * PI
        val inc1 = 2.0 * PI * freq / SAMPLE_RATE
        val inc2 = 2.0 * PI * (freq * 1.004) / SAMPLE_RATE

        for (i in barStartSample until barEndSample) {
          val localT = (i - barStartSample).toDouble() / (barEndSample - barStartSample).coerceAtLeast(1)
          val env = smoothPadEnvelope(localT)
          val osc = FourierAudioBufferProcessor.fourierSeriesHarmonicSample(
            phase = phase1,
            harmonicCount = 6,
            rolloff = 1.35 - profile.padWarmth * 0.45,
          ) * 0.65 +
            FourierAudioBufferProcessor.fourierSeriesHarmonicSample(
              phase = phase2,
              harmonicCount = 4,
              rolloff = 1.4,
            ) * 0.35
          buffer[i] += osc * env * 0.14
          phase1 += inc1
          phase2 += inc2
        }
      }
    }

    // 2. Render bassline, lead melody, and percussion step-by-step
    for (step in 0 until totalSteps) {
      val stepStartSample = (step * stepDurationSec * SAMPLE_RATE).toInt()
      if (stepStartSample >= totalSamples) break
      val barIndex = step / stepsPerBar
      val stepInBar = step % stepsPerBar
      val chord = profile.chordProgression[barIndex % profile.chordProgression.size]
      val bassRootOffset = chord[0]

      // Bass on downbeats and syncopated eighth notes
      val playBass = stepInBar == 0 || stepInBar == 6 || stepInBar == 10 ||
        (profile == StyleProfile.TECHNO_PULSE && stepInBar % 4 == 2)
      if (playBass) {
        val bassFreq = midiToFreq(rootMidi + profile.bassOctaveShift + bassRootOffset)
        val bassLen = min(totalSamples - stepStartSample, (stepSamples * 1.8).toInt())
        var phase = 0.0
        val inc = 2.0 * PI * bassFreq / SAMPLE_RATE
        for (k in 0 until bassLen) {
          val t = k.toDouble() / SAMPLE_RATE
          val env = exp(-t * 5.2) * (1.0 - exp(-t * 140.0))
          val sub = FourierAudioBufferProcessor.fourierSeriesHarmonicSample(phase, harmonicCount = 4, rolloff = 1.65)
          buffer[stepStartSample + k] += sub * env * 0.34
          phase += inc
        }
      }

      // Lead melody note via Fourier harmonic synthesis
      val motifNote = motif[stepInBar]
      if (motifNote >= 0) {
        val octaveLift = if (barIndex % 4 == 3 && stepInBar >= 8) 12 else 0
        val leadFreq = midiToFreq(rootMidi + motifNote + octaveLift)
        val noteLen = min(totalSamples - stepStartSample, (stepSamples * 2.2).toInt())
        var phase = 0.0
        val inc = 2.0 * PI * leadFreq / SAMPLE_RATE
        for (k in 0 until noteLen) {
          val t = k.toDouble() / SAMPLE_RATE
          val env = exp(-t * 4.6) * (1.0 - exp(-t * 180.0))
          val rolloff = 1.6 - profile.leadBrightness * 0.65
          val harmonic = FourierAudioBufferProcessor.fourierSeriesHarmonicSample(
            phase = phase,
            harmonicCount = 8,
            rolloff = rolloff,
          )
          buffer[stepStartSample + k] += harmonic * env * 0.24
          phase += inc
        }
      }

      // Drums
      if (profile.hasDrums) {
        // Kick on 0, 8 (and 4, 12 for techno/synthwave)
        val isKick = stepInBar == 0 || stepInBar == 8 ||
          ((profile == StyleProfile.TECHNO_PULSE || profile == StyleProfile.SYNTHWAVE) && (stepInBar == 4 || stepInBar == 12))
        if (isKick) {
          val kickLen = min(totalSamples - stepStartSample, (0.22 * SAMPLE_RATE).toInt())
          var phase = 0.0
          for (k in 0 until kickLen) {
            val t = k.toDouble() / SAMPLE_RATE
            val freq = 48.0 + 115.0 * exp(-t * 28.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = exp(-t * 13.0) * (1.0 - exp(-t * 320.0))
            buffer[stepStartSample + k] += sin(phase) * env * 0.55
          }
        }

        // Snare on 4 and 12 (for non-four-on-the-floor or layered clap)
        if (stepInBar == 4 || stepInBar == 12) {
          val snareLen = min(totalSamples - stepStartSample, (0.16 * SAMPLE_RATE).toInt())
          val snareRng = Random(seed + step)
          var bodyPhase = 0.0
          for (k in 0 until snareLen) {
            val t = k.toDouble() / SAMPLE_RATE
            bodyPhase += 2.0 * PI * 185.0 / SAMPLE_RATE
            val noise = (snareRng.nextDouble() * 2.0 - 1.0) * exp(-t * 20.0)
            val body = sin(bodyPhase) * exp(-t * 24.0)
            val env = 1.0 - exp(-t * 400.0)
            buffer[stepStartSample + k] += (noise * 0.22 + body * 0.14) * env
          }
        }

        // Crisp closed hi-hat on every 2nd step
        if (stepInBar % 2 == 0) {
          val hatLen = min(totalSamples - stepStartSample, (0.045 * SAMPLE_RATE).toInt())
          val hatRng = Random(seed * 31 + step)
          var prev = 0.0
          for (k in 0 until hatLen) {
            val t = k.toDouble() / SAMPLE_RATE
            val white = hatRng.nextDouble() * 2.0 - 1.0
            val hp = white - prev
            prev = white
            val env = exp(-t * 75.0)
            buffer[stepStartSample + k] += hp * env * 0.085
          }
        }
      }
    }

    // 3. Subtle delay echo for spaciousness
    val delaySamples = (0.375 * secondsPerBeat * SAMPLE_RATE).toInt().coerceIn(2205, SAMPLE_RATE)
    for (i in delaySamples until totalSamples) {
      buffer[i] += buffer[i - delaySamples] * 0.24
    }

    // 4. Master fade-in / fade-out + soft limiter
    val fadeInSamples = (0.04 * SAMPLE_RATE).toInt()
    val fadeOutSamples = (0.45 * SAMPLE_RATE).toInt()
    val rawShorts = ShortArray(totalSamples)

    for (i in 0 until totalSamples) {
      var sample = buffer[i]
      if (i < fadeInSamples) {
        sample *= i.toDouble() / fadeInSamples.coerceAtLeast(1)
      }
      val distFromEnd = totalSamples - 1 - i
      if (distFromEnd < fadeOutSamples) {
        sample *= distFromEnd.toDouble() / fadeOutSamples.coerceAtLeast(1)
      }
      val limited = tanh(sample * 1.15)
      rawShorts[i] = (limited * Short.MAX_VALUE * 0.92).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }

    // 5. Post-processing ITU-R BS.1770 LUFS loudness normalization for consistent library volume
    val pcmShorts = if (applyPostGain) {
      LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = rawShorts,
        sampleRate = SAMPLE_RATE,
        channelCount = NUM_CHANNELS,
        targetLufs = targetLufs,
      ).normalizedSamples
    } else {
      rawShorts
    }

    val rawBuffer = FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = pcmShorts,
      sampleRate = SAMPLE_RATE,
      channelCount = NUM_CHANNELS,
      bridgeSource = "Fourier Synth",
    )
    val waveformPeaks = FourierAudioBufferProcessor.extractWaveformPeaks(pcmShorts, WAVEFORM_BAR_COUNT)

    val id = UUID.randomUUID().toString()
    val wavFile = File(outputDir, "track_$id.wav")
    FourierAudioBufferProcessor.writeWavFile(wavFile, rawBuffer)

    val durationMs = rawBuffer.durationMs
    val title = buildTrackTitle(cleanPrompt, profile)
    val autoTags = extractAutoTags(normalized, profile)

    val track = GeneratedTrack(
      id = id,
      title = title,
      prompt = cleanPrompt,
      genre = profile.displayName,
      bpm = bpm,
      musicalKey = musicalKey,
      durationMs = durationMs,
      filePath = wavFile.absolutePath,
      fileSizeBytes = wavFile.length(),
      waveformPeaks = waveformPeaks,
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Fourier Synth",
      category = profile.displayName,
      tags = autoTags,
    )
    return track to rawBuffer
  }

  private fun extractAutoTags(normalizedPrompt: String, profile: StyleProfile): List<String> {
    val candidates = listOf(
      "chill", "rain", "study", "midnight", "neon", "dream", "ambient",
      "sleep", "focus", "energetic", "bass", "piano", "cinematic", "drive",
    )
    val matched = candidates.filter { it in normalizedPrompt }
      .map { token -> token.replaceFirstChar { it.uppercase() } }
    return (listOf(profile.displayName) + matched).distinct().take(4)
  }

  private fun detectProfile(prompt: String): StyleProfile {
    return when {
      listOf("lofi", "lo-fi", "chill", "rain", "study", "rhodes", "jazzy", "calm").any { it in prompt } ->
        StyleProfile.LOFI_CHILL
      listOf("ambient", "space", "dream", "meditation", "drone", "float", "ethereal").any { it in prompt } ->
        StyleProfile.AMBIENT_DRIFT
      listOf("techno", "edm", "house", "club", "energetic", "cyber", "rave", "fast", "bass").any { it in prompt } ->
        StyleProfile.TECHNO_PULSE
      listOf("piano", "cinema", "orchestral", "sad", "epic", "movie", "acoustic").any { it in prompt } ->
        StyleProfile.PIANO_CINEMA
      else -> StyleProfile.SYNTHWAVE
    }
  }

  private fun extractBpm(prompt: String): Int? {
    val match = Regex("""(\d{2,3})\s*bpm""").find(prompt)
    val value = match?.groupValues?.getOrNull(1)?.toIntOrNull()
    return value?.takeIf { it in 55..180 }
  }

  private fun buildTrackTitle(prompt: String, profile: StyleProfile): String {
    val words = prompt
      .replace(Regex("""[^A-Za-z0-9\s-]"""), " ")
      .trim()
      .split(Regex("""\s+"""))
      .filter { it.isNotBlank() }
      .take(4)
      .joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
      }
    return if (words.isNotBlank()) words else "${profile.displayName} Session"
  }

  private fun midiToFreq(midi: Int): Double {
    return 440.0 * 2.0.pow((midi - 69) / 12.0)
  }

  private fun smoothPadEnvelope(normalizedTime: Double): Double {
    val attack = min(1.0, normalizedTime / 0.14)
    val release = min(1.0, (1.0 - normalizedTime) / 0.18)
    return max(0.0, min(attack, release))
  }
}
