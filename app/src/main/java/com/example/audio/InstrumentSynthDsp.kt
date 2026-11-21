package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PianoVoice(
  val id: String,
  val label: String,
  val subtitle: String,
  val brightness: Float,
  val decayRate: Float,
  val fmModIndex: Float,
  val tremoloDepth: Float,
) {
  CONCERT_GRAND(
    id = "concert_grand",
    label = "Concert Grand",
    subtitle = "Rich harmonic hammer & warm sustain",
    brightness = 0.72f,
    decayRate = 2.1f,
    fmModIndex = 0.08f,
    tremoloDepth = 0.0f,
  ),
  BRIGHT_UPRIGHT(
    id = "bright_upright",
    label = "Bright Upright",
    subtitle = "Crisp upper harmonics & tight room snap",
    brightness = 0.92f,
    decayRate = 2.8f,
    fmModIndex = 0.14f,
    tremoloDepth = 0.0f,
  ),
  RHODES_EP(
    id = "rhodes_ep",
    label = "Rhodes EP",
    subtitle = "Bell tine attack + warm electric tremolo",
    brightness = 0.56f,
    decayRate = 1.9f,
    fmModIndex = 0.85f,
    tremoloDepth = 0.18f,
  ),
  VINTAGE_WURLI(
    id = "vintage_wurli",
    label = "Vintage Wurli",
    subtitle = "Reed bark midrange warmth",
    brightness = 0.68f,
    decayRate = 2.4f,
    fmModIndex = 0.55f,
    tremoloDepth = 0.10f,
  ),
  ANALOG_KEYS(
    id = "analog_keys",
    label = "Analog Keys",
    subtitle = "Detuned poly-synth pluck",
    brightness = 0.82f,
    decayRate = 3.0f,
    fmModIndex = 0.30f,
    tremoloDepth = 0.05f,
  ),
}

enum class PianoProgressionPreset(
  val id: String,
  val label: String,
  val keyLabel: String,
  val defaultBpm: Int,
  val chordsMidi: List<IntArray>,
) {
  POP_CLASSIC(
    id = "pop_classic",
    label = "C · G · Am · F",
    keyLabel = "C Maj",
    defaultBpm = 96,
    chordsMidi = listOf(
      intArrayOf(48, 60, 64, 67),
      intArrayOf(43, 59, 62, 67),
      intArrayOf(45, 60, 64, 69),
      intArrayOf(41, 60, 65, 69),
    ),
  ),
  LOFI_SEVENTHS(
    id = "lofi_sevenths",
    label = "Dm9 · G13 · Cmaj7 · A7",
    keyLabel = "C Maj",
    defaultBpm = 84,
    chordsMidi = listOf(
      intArrayOf(50, 60, 64, 65, 69),
      intArrayOf(43, 59, 62, 64, 65),
      intArrayOf(48, 59, 64, 67, 71),
      intArrayOf(45, 55, 61, 64, 67),
    ),
  ),
  CINEMATIC_MINOR(
    id = "cinematic_minor",
    label = "Am · F · C · Em",
    keyLabel = "A Min",
    defaultBpm = 88,
    chordsMidi = listOf(
      intArrayOf(45, 57, 60, 64),
      intArrayOf(41, 57, 60, 65),
      intArrayOf(48, 60, 64, 67),
      intArrayOf(40, 59, 64, 67),
    ),
  ),
  NEO_SOUL_GROOVE(
    id = "neo_soul",
    label = "Em7 · A9 · Dmaj7 · Bm7",
    keyLabel = "D Maj",
    defaultBpm = 92,
    chordsMidi = listOf(
      intArrayOf(52, 62, 67, 71),
      intArrayOf(45, 59, 61, 67),
      intArrayOf(50, 61, 66, 69),
      intArrayOf(47, 57, 62, 66),
    ),
  ),
}

enum class GuitarVoice(
  val id: String,
  val label: String,
  val subtitle: String,
  val sustainRho: Double,
  val dampingAlpha: Double,
  val driveAmount: Double,
  val bodyWarmth: Double,
) {
  ACOUSTIC_STEEL(
    id = "acoustic_steel",
    label = "Acoustic Steel",
    subtitle = "Bright dreadnought string resonance",
    sustainRho = 0.9962,
    dampingAlpha = 0.34,
    driveAmount = 0.0,
    bodyWarmth = 0.42,
  ),
  CLASSICAL_NYLON(
    id = "classical_nylon",
    label = "Nylon Classical",
    subtitle = "Warm fingerstyle gut-string decay",
    sustainRho = 0.9945,
    dampingAlpha = 0.52,
    driveAmount = 0.0,
    bodyWarmth = 0.65,
  ),
  CLEAN_ELECTRIC(
    id = "clean_electric",
    label = "Clean Electric",
    subtitle = "Glass-clear single-coil chime",
    sustainRho = 0.9974,
    dampingAlpha = 0.24,
    driveAmount = 0.15,
    bodyWarmth = 0.18,
  ),
  OVERDRIVE_ROCK(
    id = "overdrive_rock",
    label = "Overdrive Rock",
    subtitle = "Saturated harmonic amp sustain",
    sustainRho = 0.9980,
    dampingAlpha = 0.28,
    driveAmount = 2.65,
    bodyWarmth = 0.25,
  ),
  JAZZ_HOLLOWBODY(
    id = "jazz_hollow",
    label = "Jazz Hollowbody",
    subtitle = "Mellow rounded neck-pickup tone",
    sustainRho = 0.9955,
    dampingAlpha = 0.60,
    driveAmount = 0.22,
    bodyWarmth = 0.55,
  ),
}

/**
 * Standard 6-string guitar tuning E2 A2 D3 G3 B3 E4 with real fret shapes (-1 = muted string).
 */
enum class GuitarChordShape(
  val id: String,
  val label: String,
  val frets: IntArray,
) {
  C_MAJ("c_maj", "C", intArrayOf(-1, 3, 2, 0, 1, 0)),
  G_MAJ("g_maj", "G", intArrayOf(3, 2, 0, 0, 0, 3)),
  A_MIN("a_min", "Am", intArrayOf(-1, 0, 2, 2, 1, 0)),
  F_MAJ("f_maj", "F", intArrayOf(1, 3, 3, 2, 1, 1)),
  E_MIN("e_min", "Em", intArrayOf(0, 2, 2, 0, 0, 0)),
  D_MIN("d_min", "Dm", intArrayOf(-1, -1, 0, 2, 3, 1)),
  C_ADD9("c_add9", "Cadd9", intArrayOf(-1, 3, 2, 0, 3, 3)),
  E_POWER("e5", "E5 Power", intArrayOf(0, 2, 2, -1, -1, -1)),
  ;

  fun activeMidiNotes(): List<Int> {
    return buildList {
      frets.forEachIndexed { stringIdx, fret ->
        if (fret >= 0) {
          add(InstrumentSynthDsp.GUITAR_OPEN_MIDI[stringIdx] + fret)
        }
      }
    }
  }
}

enum class GuitarStrumStyle(
  val id: String,
  val label: String,
) {
  ACOUSTIC_STRUM("acoustic_strum", "Down-Up Strum"),
  FINGERSTYLE_ARP("fingerstyle_arp", "Fingerstyle Arp"),
  FUNK_CHOP("funk_chop", "Funk Rhythm"),
  ROCK_RIFF("rock_riff", "Driving Riff"),
}

enum class DrumKitPreset(
  val id: String,
  val label: String,
  val subtitle: String,
  val kickSubHz: Double,
  val kickDecay: Double,
  val snareToneHz: Double,
  val snareCrispness: Float,
  val hatBrightness: Float,
  val drive: Float,
) {
  STUDIO_ACOUSTIC(
    id = "studio_acoustic",
    label = "Studio Acoustic",
    subtitle = "Punchy live maple kit",
    kickSubHz = 52.0,
    kickDecay = 12.5,
    snareToneHz = 195.0,
    snareCrispness = 0.85f,
    hatBrightness = 0.80f,
    drive = 1.05f,
  ),
  TR_808(
    id = "tr_808",
    label = "TR-808 Analog",
    subtitle = "Deep sub boom & crisp analog hats",
    kickSubHz = 41.0,
    kickDecay = 7.2,
    snareToneHz = 172.0,
    snareCrispness = 0.95f,
    hatBrightness = 0.95f,
    drive = 1.25f,
  ),
  LOFI_VINYL(
    id = "lofi_vinyl",
    label = "Lo-Fi Vinyl",
    subtitle = "Dusty saturated boom-bap thump",
    kickSubHz = 48.0,
    kickDecay = 14.0,
    snareToneHz = 162.0,
    snareCrispness = 0.60f,
    hatBrightness = 0.52f,
    drive = 1.45f,
  ),
  CYBER_ELECTRO(
    id = "cyber_electro",
    label = "Cyber Electro",
    subtitle = "Tight club kick & metallic percussion",
    kickSubHz = 46.0,
    kickDecay = 10.2,
    snareToneHz = 215.0,
    snareCrispness = 1.10f,
    hatBrightness = 1.05f,
    drive = 1.32f,
  ),
}

enum class DrumPadVoice(
  val id: String,
  val label: String,
  val shortTag: String,
) {
  KICK("kick", "Kick", "KCK"),
  SNARE("snare", "Snare", "SNR"),
  CLOSED_HAT("closed_hat", "Closed Hat", "CHH"),
  OPEN_HAT("open_hat", "Open Hat", "OHH"),
  CLAP("clap", "Hand Clap", "CLP"),
  LOW_TOM("low_tom", "Low Tom", "TOM"),
  RIMSHOT("rimshot", "Rimshot", "RIM"),
  CRASH("crash", "Crash Cymbal", "CRS"),
}

enum class StudioSoundResource(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: String,
  val bpm: Int,
  val promptRecipe: String,
  val badge: String,
) {
  RHODES_LOFI_CHORDS(
    id = "res_rhodes_lofi",
    title = "Rhodes Lo-Fi Chords",
    subtitle = "Warm electric keys + dusty pocket",
    category = "Lo-Fi Chill",
    bpm = 84,
    promptRecipe = "Rainy lo-fi rhodes study beat 84 bpm",
    badge = "Keys · 84 BPM",
  ),
  WARM_ACOUSTIC_FOLK(
    id = "res_acoustic_folk",
    title = "Warm Acoustic Groove",
    subtitle = "Guitar-ready chord bed + soft pulse",
    category = "Acoustic",
    bpm = 96,
    promptRecipe = "Warm acoustic guitar chill groove 96 bpm",
    badge = "Guitar · 96 BPM",
  ),
  NEON_SYNTHWAVE_ARP(
    id = "res_synthwave_arp",
    title = "Neon Synthwave Arp",
    subtitle = "Analog 16th arpeggio + sub bass",
    category = "Synthwave",
    bpm = 112,
    promptRecipe = "Neon midnight synthwave drive 112 bpm",
    badge = "Synth · 112 BPM",
  ),
  TRAP_808_GROOVE(
    id = "res_808_trap",
    title = "808 Sub & Crisp Hats",
    subtitle = "Deep sub foundation + rhythmic hats",
    category = "Electronic",
    bpm = 132,
    promptRecipe = "Energetic cyber techno bassline 132 bpm",
    badge = "808 · 132 BPM",
  ),
  AMBIENT_CATHEDRAL_PAD(
    id = "res_ambient_pad",
    title = "Ambient Cathedral Pad",
    subtitle = "Sustained harmonic wash for reverb",
    category = "Ambient",
    bpm = 72,
    promptRecipe = "Deep space ambient meditation pad 72 bpm",
    badge = "Pad · 72 BPM",
  ),
  CINEMATIC_PIANO_THEME(
    id = "res_cinema_piano",
    title = "Cinematic Grand Theme",
    subtitle = "Expressive minor piano + warm strings",
    category = "Cinematic",
    bpm = 94,
    promptRecipe = "Emotional cinematic piano theme 94 bpm",
    badge = "Piano · 94 BPM",
  ),
}

data class RecordedNoteEvent(
  val midiNote: Int,
  val offsetMs: Long,
  val durationMs: Long = 550L,
  val velocity: Float = 0.85f,
)

data class RecordedDrumHit(
  val pad: DrumPadVoice,
  val offsetMs: Long,
  val velocity: Float = 0.92f,
)

enum class GuitarProgressionPreset(
  val id: String,
  val label: String,
  val chords: List<GuitarChordShape>,
) {
  POP_ANTHEM(
    id = "pop",
    label = "Pop (C·G·Am·F)",
    chords = listOf(GuitarChordShape.C_MAJ, GuitarChordShape.G_MAJ, GuitarChordShape.A_MIN, GuitarChordShape.F_MAJ),
  ),
  ACOUSTIC_FOLK(
    id = "folk",
    label = "Folk (G·Cadd9·Em·D)",
    chords = listOf(GuitarChordShape.G_MAJ, GuitarChordShape.C_ADD9, GuitarChordShape.E_MIN, GuitarChordShape.D_MIN),
  ),
  INDIE_MINOR(
    id = "minor",
    label = "Minor (Am·F·C·Em)",
    chords = listOf(GuitarChordShape.A_MIN, GuitarChordShape.F_MAJ, GuitarChordShape.C_MAJ, GuitarChordShape.E_MIN),
  ),
  ROCK_DRIVE(
    id = "rock",
    label = "Rock (E5·G·C·Em)",
    chords = listOf(GuitarChordShape.E_POWER, GuitarChordShape.G_MAJ, GuitarChordShape.C_MAJ, GuitarChordShape.E_MIN),
  ),
}

object InstrumentSynthDsp {

  const val SAMPLE_RATE = 44_100
  val GUITAR_OPEN_MIDI = intArrayOf(40, 45, 50, 55, 59, 64) // E2, A2, D3, G3, B3, E4
  val GUITAR_STRING_NAMES = listOf("E2", "A2", "D3", "G3", "B3", "E4")

  fun midiToFreqHz(midiNote: Int): Double =
    440.0 * 2.0.pow((midiNote - 69) / 12.0)

  fun midiToNoteName(midiNote: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val pitchClass = ((midiNote % 12) + 12) % 12
    val octave = (midiNote / 12) - 1
    return "${names[pitchClass]}$octave"
  }

  /**
   * Synthesizes a single polyphonic piano key sample array in [-1f, 1f].
   */
  fun synthesizePianoNoteSamples(
    midiNote: Int,
    durationSec: Double = 1.25,
    velocity: Float = 0.85f,
    voice: PianoVoice = PianoVoice.CONCERT_GRAND,
    sampleRate: Int = SAMPLE_RATE,
  ): FloatArray {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val totalSamples = (durationSec.coerceIn(0.15, 4.5) * safeRate).roundToInt()
    val freq = midiToFreqHz(midiNote)
    val vel = velocity.coerceIn(0.15f, 1.0f).toDouble()
    val out = FloatArray(totalSamples)

    // Higher piano strings decay faster naturally
    val pitchDecayScale = (freq / 261.63).pow(0.28).coerceIn(0.65, 2.2)
    val decay = voice.decayRate * pitchDecayScale
    val harmonicWeights = when (voice) {
      PianoVoice.CONCERT_GRAND -> doubleArrayOf(1.0, 0.44, 0.24, 0.13, 0.06, 0.03)
      PianoVoice.BRIGHT_UPRIGHT -> doubleArrayOf(1.0, 0.62, 0.38, 0.22, 0.12, 0.06)
      PianoVoice.RHODES_EP -> doubleArrayOf(1.0, 0.20, 0.08, 0.04, 0.02, 0.01)
      PianoVoice.VINTAGE_WURLI -> doubleArrayOf(1.0, 0.28, 0.34, 0.14, 0.16, 0.05)
      PianoVoice.ANALOG_KEYS -> doubleArrayOf(1.0, 0.55, 0.33, 0.24, 0.15, 0.09)
    }

    val fadeOutStart = (totalSamples * 0.86).toInt()
    val fadeOutLen = (totalSamples - fadeOutStart).coerceAtLeast(1)
    val rng = Random(midiNote * 31 + voice.ordinal)

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / safeRate
      val attackEnv = 1.0 - exp(-t * 360.0)
      val releaseEnv = if (i >= fadeOutStart) {
        ((totalSamples - i).toDouble() / fadeOutLen).coerceIn(0.0, 1.0)
      } else {
        1.0
      }

      var sample = 0.0
      // Subtle hammer felt transient for acoustic pianos
      if (t < 0.014 && (voice == PianoVoice.CONCERT_GRAND || voice == PianoVoice.BRIGHT_UPRIGHT)) {
        val hammerEnv = exp(-t * 280.0)
        sample += (rng.nextDouble() * 2.0 - 1.0) * hammerEnv * 0.12 * voice.brightness
      }

      // Additive harmonics with slight acoustic stiffness stretch & brightness damping
      for (hIdx in harmonicWeights.indices) {
        val n = hIdx + 1
        val stretch = if (voice == PianoVoice.CONCERT_GRAND || voice == PianoVoice.BRIGHT_UPRIGHT) {
          kotlin.math.sqrt(1.0 + 0.00018 * n * n)
        } else {
          1.0
        }
        val partialFreq = freq * n * stretch
        if (partialFreq >= safeRate * 0.44) break
        val partialDecay = exp(-t * decay * (1.0 + 0.38 * hIdx))
        val weight = harmonicWeights[hIdx] * (0.55 + 0.45 * voice.brightness)
        sample += weight * partialDecay * sin(2.0 * PI * partialFreq * t)
      }

      // FM tine bell ping (Rhodes / Wurli / Analog)
      if (voice.fmModIndex > 0.05f) {
        val tineEnv = exp(-t * 14.0)
        val mod = voice.fmModIndex * tineEnv * sin(2.0 * PI * freq * 7.0 * t)
        sample += 0.32 * exp(-t * decay * 0.9) * sin(2.0 * PI * freq * t + mod)
      }

      // Tremolo modulation
      val tremolo = if (voice.tremoloDepth > 0f) {
        1.0 - voice.tremoloDepth * (0.5 + 0.5 * sin(2.0 * PI * 4.6 * t))
      } else {
        1.0
      }

      out[i] = (tanh(sample * 0.58 * vel) * attackEnv * releaseEnv * tremolo).toFloat()
    }
    return out
  }

  /**
   * Karplus-Strong physical modeling plucked-string synthesizer for authentic Guitar voices.
   */
  fun synthesizeGuitarStringSamples(
    midiNote: Int,
    durationSec: Double = 1.55,
    velocity: Float = 0.88f,
    voice: GuitarVoice = GuitarVoice.ACOUSTIC_STEEL,
    sampleRate: Int = SAMPLE_RATE,
  ): FloatArray {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val totalSamples = (durationSec.coerceIn(0.20, 4.5) * safeRate).roundToInt()
    val freq = midiToFreqHz(midiNote)
    val vel = velocity.coerceIn(0.15f, 1.0f).toDouble()
    // Exact fractional delay length accounts for 0.5-sample group delay of the 2-point averaging filter
    val exactDelay = (safeRate / freq - 0.5).coerceIn(8.0, (safeRate / 40).toDouble())
    val intDelay = floor(exactDelay).toInt().coerceAtLeast(8)
    val frac = (exactDelay - intDelay).coerceIn(0.0, 0.9999)
    val bufferSize = intDelay + 2

    val delayLine = DoubleArray(bufferSize)
    val rng = Random(midiNote * 131 + voice.ordinal * 17)
    // Pluck excitation: shaped noise burst with DC removal representing pick position
    var prevExc = 0.0
    val excSmooth = when (voice) {
      GuitarVoice.CLASSICAL_NYLON -> 0.58
      GuitarVoice.JAZZ_HOLLOWBODY -> 0.64
      GuitarVoice.ACOUSTIC_STEEL -> 0.30
      GuitarVoice.CLEAN_ELECTRIC -> 0.22
      GuitarVoice.OVERDRIVE_ROCK -> 0.16
    }
    var dcSum = 0.0
    for (i in 0 until intDelay) {
      val white = (rng.nextDouble() * 2.0 - 1.0) * vel
      prevExc = excSmooth * prevExc + (1.0 - excSmooth) * white
      delayLine[i] = prevExc
      dcSum += prevExc
    }
    val dcMean = dcSum / intDelay.coerceAtLeast(1)
    for (i in 0 until intDelay) {
      delayLine[i] -= dcMean
    }

    val out = FloatArray(totalSamples)
    // Scale sustain slightly by pitch so high strings stay musical and low strings don't ring forever
    val pitchRhoAdjust = ((freq - 110.0) / 1000.0).coerceIn(-0.0004, 0.0014)
    val rho = (voice.sustainRho - pitchRhoAdjust).coerceIn(0.990, 0.9992)
    val alpha = voice.dampingAlpha
    var writeIdx = 0
    var prevFilterIn = 0.0
    var bodyLp1 = 0.0
    var bodyLp2 = 0.0
    val fadeOutStart = (totalSamples * 0.88).toInt()
    val fadeOutLen = (totalSamples - fadeOutStart).coerceAtLeast(1)

    for (i in 0 until totalSamples) {
      // Read fractional delay sample: linear interpolation between (writeIdx - intDelay) and (writeIdx - intDelay - 1)
      val idxA = (writeIdx - intDelay + bufferSize) % bufferSize
      val idxB = (idxA - 1 + bufferSize) % bufferSize
      val delayed = delayLine[idxA] * (1.0 - frac) + delayLine[idxB] * frac

      val filtered = rho * ((1.0 - alpha) * delayed + alpha * prevFilterIn)
      prevFilterIn = delayed
      delayLine[writeIdx] = filtered
      writeIdx = (writeIdx + 1) % bufferSize

      // Acoustic wooden body resonance approximation (warm low-mid bloom)
      bodyLp1 += 0.055 * (filtered - bodyLp1)
      bodyLp2 += 0.018 * (bodyLp1 - bodyLp2)
      var shaped = filtered + voice.bodyWarmth * (bodyLp1 - 0.55 * bodyLp2)

      // Subtle pick click on the initial 4ms transient
      val t = i.toDouble() / safeRate
      if (t < 0.004) {
        shaped += (rng.nextDouble() * 2.0 - 1.0) * exp(-t * 900.0) * 0.14 * vel
      }

      if (voice.driveAmount > 0.05) {
        shaped = tanh(shaped * (1.0 + voice.driveAmount)) / tanh(1.0 + voice.driveAmount * 0.5)
      }

      val attack = 1.0 - exp(-t * 480.0)
      val release = if (i >= fadeOutStart) {
        ((totalSamples - i).toDouble() / fadeOutLen).coerceIn(0.0, 1.0)
      } else {
        1.0
      }
      out[i] = (shaped * attack * release * 0.88).toFloat().coerceIn(-1f, 1f)
    }
    return out
  }

  /**
   * Strums a multi-string guitar chord shape with realistic downward pick stagger (18 ms/string).
   */
  fun synthesizeGuitarChordStrumSamples(
    frets: IntArray,
    voice: GuitarVoice = GuitarVoice.ACOUSTIC_STEEL,
    durationSec: Double = 1.65,
    sampleRate: Int = SAMPLE_RATE,
  ): FloatArray {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val totalSamples = (durationSec * safeRate).roundToInt()
    val mix = DoubleArray(totalSamples)
    val staggerSamples = (0.018 * safeRate).roundToInt()
    var activeCount = 0

    for (stringIdx in 0 until min(6, frets.size)) {
      val fret = frets[stringIdx]
      if (fret < 0) continue
      val midi = GUITAR_OPEN_MIDI[stringIdx] + fret
      val startOffset = activeCount * staggerSamples
      activeCount++
      if (startOffset >= totalSamples) break
      val remainingSec = (totalSamples - startOffset).toDouble() / safeRate
      val plucked = synthesizeGuitarStringSamples(
        midiNote = midi,
        durationSec = remainingSec,
        velocity = 0.82f,
        voice = voice,
        sampleRate = safeRate,
      )
      val limit = min(plucked.size, totalSamples - startOffset)
      for (k in 0 until limit) {
        mix[startOffset + k] += plucked[k] * 0.42
      }
    }

    return FloatArray(totalSamples) { idx ->
      tanh(mix[idx]).toFloat()
    }
  }

  /**
   * Synthesizes one drum pad hit tuned by the selected [DrumKitPreset].
   */
  fun synthesizeDrumPadHitSamples(
    pad: DrumPadVoice,
    kit: DrumKitPreset = DrumKitPreset.STUDIO_ACOUSTIC,
    velocity: Float = 0.92f,
    sampleRate: Int = SAMPLE_RATE,
  ): FloatArray {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val vel = velocity.coerceIn(0.2f, 1.0f).toDouble()
    val rng = Random(pad.ordinal * 97 + kit.ordinal * 19)

    return when (pad) {
      DrumPadVoice.KICK -> {
        val len = (0.34 * safeRate).toInt()
        FloatArray(len).also { out ->
          var phase = 0.0
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val freq = kit.kickSubHz + 125.0 * exp(-t * 36.0)
            phase += 2.0 * PI * freq / safeRate
            val env = exp(-t * kit.kickDecay)
            val click = if (t < 0.008) exp(-t * 420.0) * 0.35 else 0.0
            out[i] = (tanh((sin(phase) * env + click) * kit.drive * vel) * 0.88).toFloat()
          }
        }
      }
      DrumPadVoice.SNARE -> {
        val len = (0.25 * safeRate).toInt()
        var lpNoise = 0.0
        var bodyPhase = 0.0
        FloatArray(len).also { out ->
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val shellFreq = kit.snareToneHz + 95.0 * exp(-t * 48.0)
            bodyPhase += 2.0 * PI * shellFreq / safeRate
            val body = sin(bodyPhase) * exp(-t * 20.0) * 0.62
            val rawNoise = rng.nextDouble() * 2.0 - 1.0
            lpNoise = 0.52 * lpNoise + 0.48 * rawNoise
            val hpNoise = rawNoise - 0.42 * lpNoise
            val snap = if (t < 0.012) exp(-t * 320.0) * 0.25 else 0.0
            val noiseEnv = exp(-t * 14.5) * kit.snareCrispness * 0.72
            out[i] = (tanh((body + hpNoise * noiseEnv + snap) * kit.drive * vel) * 0.84).toFloat()
          }
        }
      }
      DrumPadVoice.CLOSED_HAT -> {
        val len = (0.08 * safeRate).toInt()
        var prev = 0.0
        FloatArray(len).also { out ->
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val noise = rng.nextDouble() * 2.0 - 1.0
            val hp = noise - prev * 0.78
            prev = noise
            val metallic = 0.25 * sin(2.0 * PI * 3_420.0 * t) + 0.20 * sin(2.0 * PI * 5_680.0 * t)
            val env = exp(-t * 54.0) * kit.hatBrightness
            out[i] = (tanh((hp * 0.68 + metallic) * env * vel) * 0.66).toFloat()
          }
        }
      }
      DrumPadVoice.OPEN_HAT -> {
        val len = (0.38 * safeRate).toInt()
        var prev = 0.0
        FloatArray(len).also { out ->
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val noise = rng.nextDouble() * 2.0 - 1.0
            val hp = noise - prev * 0.76
            prev = noise
            val metallic = 0.28 * sin(2.0 * PI * 3_250.0 * t) + 0.22 * sin(2.0 * PI * 6_120.0 * t)
            val env = exp(-t * 9.0) * kit.hatBrightness
            out[i] = (tanh((hp * 0.68 + metallic) * env * vel) * 0.66).toFloat()
          }
        }
      }
      DrumPadVoice.CLAP -> {
        val len = (0.24 * safeRate).toInt()
        var bpLow = 0.0
        var bpHigh = 0.0
        FloatArray(len).also { out ->
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val burst1 = exp(-abs(t - 0.000) * 210.0)
            val burst2 = exp(-abs(t - 0.011) * 210.0)
            val burst3 = exp(-abs(t - 0.022) * 190.0)
            val burst4 = exp(-abs(t - 0.032) * 150.0) * 0.78
            val tail = if (t >= 0.022) exp(-(t - 0.022) * 14.5) * 0.52 else 0.0
            val env = max(max(burst1, burst2), max(max(burst3, burst4), tail))
            val raw = rng.nextDouble() * 2.0 - 1.0
            // 1.1 kHz bandpass character for realistic hand-clap snap
            bpLow += 0.28 * (raw - bpLow)
            bpHigh += 0.08 * (bpLow - bpHigh)
            val bandpassed = bpLow - bpHigh
            out[i] = (tanh(bandpassed * 2.6 * env * kit.drive * vel) * 0.84).toFloat()
          }
        }
      }
      DrumPadVoice.LOW_TOM -> {
        val len = (0.32 * safeRate).toInt()
        FloatArray(len).also { out ->
          var phase = 0.0
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val freq = 92.0 + 95.0 * exp(-t * 22.0)
            phase += 2.0 * PI * freq / safeRate
            val env = exp(-t * 9.8) * (1.0 - exp(-t * 360.0))
            val tone = sin(phase) * 0.82 + sin(phase * 1.5) * 0.20
            out[i] = (tanh(tone * env * 1.15 * vel) * 0.88).toFloat()
          }
        }
      }
      DrumPadVoice.RIMSHOT -> {
        val len = (0.09 * safeRate).toInt()
        FloatArray(len).also { out ->
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val woody = sin(2.0 * PI * 860.0 * t) * 0.6 + sin(2.0 * PI * 1_620.0 * t) * 0.4
            val env = exp(-t * 48.0)
            out[i] = (tanh(woody * env * 0.82 * vel)).toFloat()
          }
        }
      }
      DrumPadVoice.CRASH -> {
        val len = (0.58 * safeRate).toInt()
        var prev = 0.0
        FloatArray(len).also { out ->
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val n = rng.nextDouble() * 2.0 - 1.0
            val hp = n - prev * 0.74
            prev = n
            val shimmer = 0.26 * sin(2.0 * PI * 3_110.0 * t) +
              0.22 * sin(2.0 * PI * 4_680.0 * t) +
              0.16 * sin(2.0 * PI * 6_910.0 * t)
            val env = exp(-t * 5.2) * kit.hatBrightness
            out[i] = (tanh((hp * 0.60 + shimmer) * env * 0.68 * vel)).toFloat()
          }
        }
      }
    }
  }

  /**
   * Renders a full multi-bar Piano track (either from recorded notes or the chosen chord progression).
   */
  fun synthesizePianoTrackToBuffer(
    voice: PianoVoice,
    progression: PianoProgressionPreset,
    recordedNotes: List<RecordedNoteEvent>,
    bpm: Int,
    bars: Int = 4,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): RawAudioBuffer {
    val safeRate = SAMPLE_RATE
    val safeBpm = bpm.coerceIn(55, 180)
    val safeBars = bars.coerceIn(2, 8)
    val barDurationSec = (60.0 / safeBpm) * 4.0
    val totalDurationSec = if (recordedNotes.isNotEmpty()) {
      val maxEndMs = recordedNotes.maxOf { it.offsetMs + it.durationMs }
      max(barDurationSec * 2.0, (maxEndMs + 400L) / 1000.0)
    } else {
      barDurationSec * safeBars
    }
    val totalSamples = (totalDurationSec * safeRate).roundToInt().coerceAtLeast(safeRate)
    val mix = DoubleArray(totalSamples)

    if (recordedNotes.isNotEmpty()) {
      val baseOffset = recordedNotes.minOf { it.offsetMs }
      recordedNotes.forEach { ev ->
        val startSample = (((ev.offsetMs - baseOffset).coerceAtLeast(0L) / 1000.0) * safeRate).roundToInt()
        if (startSample < totalSamples) {
          val noteDur = (ev.durationMs / 1000.0).coerceIn(0.35, 2.2)
          val noteBuf = synthesizePianoNoteSamples(
            midiNote = ev.midiNote,
            durationSec = noteDur,
            velocity = ev.velocity,
            voice = voice,
            sampleRate = safeRate,
          )
          val limit = min(noteBuf.size, totalSamples - startSample)
          for (k in 0 until limit) {
            mix[startSample + k] += noteBuf[k] * 0.45
          }
        }
      }
    } else {
      val beatSec = 60.0 / safeBpm
      for (bar in 0 until safeBars) {
        val chord = progression.chordsMidi[bar % progression.chordsMidi.size]
        val barStartSec = bar * barDurationSec
        // Root + warm block chord on beat 1, gentle arpeggio on beats 2, 3, 4
        chord.forEachIndexed { idx, midi ->
          val startSample = ((barStartSec + idx * 0.025) * safeRate).roundToInt()
          if (startSample < totalSamples) {
            val chordNote = synthesizePianoNoteSamples(
              midiNote = midi,
              durationSec = (barDurationSec * 0.82).coerceIn(0.6, 2.8),
              velocity = if (idx == 0) 0.88f else 0.72f,
              voice = voice,
              sampleRate = safeRate,
            )
            val limit = min(chordNote.size, totalSamples - startSample)
            for (k in 0 until limit) {
              mix[startSample + k] += chordNote[k] * 0.34
            }
          }
        }
        // Expressive melody/arp hits on beats 2, 2.5, 3, 4
        val arpOffsets = listOf(1.0, 1.5, 2.0, 3.0, 3.5)
        arpOffsets.forEachIndexed { hitIdx, beatOffset ->
          val noteMidi = chord[(hitIdx + 1) % chord.size] + 12
          val startSample = ((barStartSec + beatOffset * beatSec) * safeRate).roundToInt()
          if (startSample < totalSamples) {
            val hit = synthesizePianoNoteSamples(
              midiNote = noteMidi.coerceIn(36, 96),
              durationSec = (beatSec * 1.15).coerceIn(0.35, 1.4),
              velocity = 0.78f,
              voice = voice,
              sampleRate = safeRate,
            )
            val limit = min(hit.size, totalSamples - startSample)
            for (k in 0 until limit) {
              mix[startSample + k] += hit[k] * 0.36
            }
          }
        }
      }
    }

    return finalizeMixToRawBuffer(
      mix = mix,
      sampleRate = safeRate,
      bridgeSource = "Piano · ${voice.label}",
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
  }

  /**
   * Renders a full multi-bar Guitar track using Karplus-Strong physical modeling.
   */
  fun synthesizeGuitarTrackToBuffer(
    voice: GuitarVoice,
    chordSequence: List<GuitarChordShape>,
    strumStyle: GuitarStrumStyle,
    recordedPlucks: List<RecordedNoteEvent>,
    bpm: Int,
    bars: Int = 4,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): RawAudioBuffer {
    val safeRate = SAMPLE_RATE
    val safeBpm = bpm.coerceIn(55, 180)
    val safeBars = bars.coerceIn(2, 8)
    val safeChords = chordSequence.ifEmpty {
      listOf(GuitarChordShape.C_MAJ, GuitarChordShape.G_MAJ, GuitarChordShape.A_MIN, GuitarChordShape.F_MAJ)
    }
    val beatSec = 60.0 / safeBpm
    val barDurationSec = beatSec * 4.0
    val totalDurationSec = if (recordedPlucks.isNotEmpty()) {
      val maxEndMs = recordedPlucks.maxOf { it.offsetMs + it.durationMs }
      max(barDurationSec * 2.0, (maxEndMs + 500L) / 1000.0)
    } else {
      barDurationSec * safeBars
    }
    val totalSamples = (totalDurationSec * safeRate).roundToInt().coerceAtLeast(safeRate)
    val mix = DoubleArray(totalSamples)

    if (recordedPlucks.isNotEmpty()) {
      val baseOffset = recordedPlucks.minOf { it.offsetMs }
      recordedPlucks.forEach { ev ->
        val startSample = (((ev.offsetMs - baseOffset).coerceAtLeast(0L) / 1000.0) * safeRate).roundToInt()
        if (startSample < totalSamples) {
          val plucked = synthesizeGuitarStringSamples(
            midiNote = ev.midiNote,
            durationSec = (ev.durationMs / 1000.0).coerceIn(0.4, 2.4),
            velocity = ev.velocity,
            voice = voice,
            sampleRate = safeRate,
          )
          val limit = min(plucked.size, totalSamples - startSample)
          for (k in 0 until limit) {
            mix[startSample + k] += plucked[k] * 0.50
          }
        }
      }
    } else {
      for (bar in 0 until safeBars) {
        val shape = safeChords[bar % safeChords.size]
        val barStartSec = bar * barDurationSec
        when (strumStyle) {
          GuitarStrumStyle.ACOUSTIC_STRUM -> {
            val strumBeats = listOf(0.0, 1.0, 1.5, 2.5, 3.0)
            strumBeats.forEachIndexed { idx, beatOff ->
              val startSample = ((barStartSec + beatOff * beatSec) * safeRate).roundToInt()
              if (startSample < totalSamples) {
                val strum = synthesizeGuitarChordStrumSamples(
                  frets = shape.frets,
                  voice = voice,
                  durationSec = if (idx == 0) beatSec * 1.4 else beatSec * 0.9,
                  sampleRate = safeRate,
                )
                val gain = if (idx == 0) 0.72 else 0.54
                val limit = min(strum.size, totalSamples - startSample)
                for (k in 0 until limit) {
                  mix[startSample + k] += strum[k] * gain
                }
              }
            }
          }
          GuitarStrumStyle.FINGERSTYLE_ARP -> {
            val notes = shape.activeMidiNotes()
            if (notes.isNotEmpty()) {
              for (step in 0 until 8) {
                val beatOff = step * 0.5
                val midi = notes[step % notes.size]
                val startSample = ((barStartSec + beatOff * beatSec) * safeRate).roundToInt()
                if (startSample < totalSamples) {
                  val plucked = synthesizeGuitarStringSamples(
                    midiNote = midi,
                    durationSec = beatSec * 1.25,
                    velocity = if (step == 0) 0.92f else 0.78f,
                    voice = voice,
                    sampleRate = safeRate,
                  )
                  val limit = min(plucked.size, totalSamples - startSample)
                  for (k in 0 until limit) {
                    mix[startSample + k] += plucked[k] * 0.48
                  }
                }
              }
            }
          }
          GuitarStrumStyle.FUNK_CHOP -> {
            val syncBeats = listOf(0.0, 0.75, 1.5, 2.0, 2.75, 3.5)
            syncBeats.forEach { beatOff ->
              val startSample = ((barStartSec + beatOff * beatSec) * safeRate).roundToInt()
              if (startSample < totalSamples) {
                val strum = synthesizeGuitarChordStrumSamples(
                  frets = shape.frets,
                  voice = voice,
                  durationSec = beatSec * 0.45,
                  sampleRate = safeRate,
                )
                val limit = min(strum.size, totalSamples - startSample)
                for (k in 0 until limit) {
                  mix[startSample + k] += strum[k] * 0.60
                }
              }
            }
          }
          GuitarStrumStyle.ROCK_RIFF -> {
            val notes = shape.activeMidiNotes().take(3)
            if (notes.isNotEmpty()) {
              for (eighth in 0 until 8) {
                val beatOff = eighth * 0.5
                val startSample = ((barStartSec + beatOff * beatSec) * safeRate).roundToInt()
                if (startSample < totalSamples) {
                  val palmMuteDur = if (eighth % 2 == 0) beatSec * 0.75 else beatSec * 0.42
                  notes.forEach { midi ->
                    val plucked = synthesizeGuitarStringSamples(
                      midiNote = midi,
                      durationSec = palmMuteDur,
                      velocity = if (eighth % 2 == 0) 0.94f else 0.76f,
                      voice = voice,
                      sampleRate = safeRate,
                    )
                    val limit = min(plucked.size, totalSamples - startSample)
                    for (k in 0 until limit) {
                      mix[startSample + k] += plucked[k] * 0.34
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return finalizeMixToRawBuffer(
      mix = mix,
      sampleRate = safeRate,
      bridgeSource = "Guitar · ${voice.label}",
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
  }

  /**
   * Renders a recorded live Drum Pad performance into a LUFS-normalized [RawAudioBuffer].
   */
  fun synthesizeDrumPadTakeToBuffer(
    kit: DrumKitPreset,
    recordedHits: List<RecordedDrumHit>,
    bpm: Int = 112,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): RawAudioBuffer {
    val safeRate = SAMPLE_RATE
    val safeBpm = bpm.coerceIn(55, 180)
    val barDurationSec = (60.0 / safeBpm) * 4.0
    val baseOffset = recordedHits.minOfOrNull { it.offsetMs } ?: 0L
    val maxEndMs = recordedHits.maxOfOrNull { (it.offsetMs - baseOffset).coerceAtLeast(0L) + 650L } ?: 2_000L
    val totalDurationSec = max(barDurationSec, (maxEndMs + 350L) / 1000.0)
    val totalSamples = (totalDurationSec * safeRate).roundToInt().coerceAtLeast(safeRate)
    val mix = DoubleArray(totalSamples)

    val hitCache = HashMap<DrumPadVoice, FloatArray>()
    recordedHits.forEach { hit ->
      val startSample = (((hit.offsetMs - baseOffset).coerceAtLeast(0L) / 1000.0) * safeRate).roundToInt()
      if (startSample < totalSamples) {
        val sampleBuf = hitCache.getOrPut(hit.pad) {
          synthesizeDrumPadHitSamples(
            pad = hit.pad,
            kit = kit,
            velocity = hit.velocity,
            sampleRate = safeRate,
          )
        }
        val limit = min(sampleBuf.size, totalSamples - startSample)
        for (k in 0 until limit) {
          mix[startSample + k] += sampleBuf[k] * 0.78
        }
      }
    }

    return finalizeMixToRawBuffer(
      mix = mix,
      sampleRate = safeRate,
      bridgeSource = "Drums · ${kit.label} (Live Pads)",
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
  }

  private fun finalizeMixToRawBuffer(
    mix: DoubleArray,
    sampleRate: Int,
    bridgeSource: String,
    targetLufs: Float,
    applyPostGain: Boolean,
  ): RawAudioBuffer {
    val totalSamples = mix.size
    val fadeInSamples = min(totalSamples / 20, (0.012 * sampleRate).toInt())
    val fadeOutSamples = min(totalSamples / 10, (0.045 * sampleRate).toInt())
    val pcm = ShortArray(totalSamples)
    for (i in 0 until totalSamples) {
      var v = tanh(mix[i])
      if (i < fadeInSamples) {
        v *= i.toDouble() / fadeInSamples.coerceAtLeast(1)
      }
      val distEnd = totalSamples - 1 - i
      if (distEnd < fadeOutSamples) {
        v *= distEnd.toDouble() / fadeOutSamples.coerceAtLeast(1)
      }
      pcm[i] = (v * Short.MAX_VALUE * 0.90).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }

    val finalPcm = if (applyPostGain) {
      LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = pcm,
        sampleRate = sampleRate,
        channelCount = 1,
        targetLufs = targetLufs,
      ).normalizedSamples
    } else {
      pcm
    }
    return FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = finalPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = bridgeSource,
    )
  }
}

/**
 * Real-time POLYPHONIC low-latency preview & one-shot audio mixer for interactive Piano keys,
 * Guitar strings/chords, and Drum pads.
 *
 * Uses a persistent ring-buffer [AudioTrack] mixer so simultaneous chords, fast arpeggios, and
 * rapid finger-drumming layer naturally with zero note-cutoff clicks and zero [AudioTrack]
 * re-initialization lag. Also delegates full pattern previews to [AudioEngine].
 */
class LiveInstrumentAudioEngine(
  private val audioEngine: AudioEngine = AudioEngine(),
) {

  val engineState = audioEngine.state

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val ringLock = Any()
  private val sampleRate = InstrumentSynthDsp.SAMPLE_RATE
  private val ringSize = sampleRate * 3 // 3-second polyphonic overlap tail
  private val mixRing = FloatArray(ringSize)
  private var readCursor = 0
  private var activeFramesRemaining = 0
  private val mixerRunning = AtomicBoolean(false)
  private val lastTriggerTimeMs = AtomicLong(0L)
  private var mixerJob: Job? = null

  // Cached drum hits per (kit, pad) for sub-millisecond pad response
  private val drumPadCache = ConcurrentHashMap<Pair<DrumKitPreset, DrumPadVoice>, FloatArray>()

  fun getOrSynthesizeDrumPad(
    pad: DrumPadVoice,
    kit: DrumKitPreset,
    velocity: Float = 0.92f,
  ): FloatArray {
    return drumPadCache.getOrPut(kit to pad) {
      InstrumentSynthDsp.synthesizeDrumPadHitSamples(
        pad = pad,
        kit = kit,
        velocity = velocity,
        sampleRate = sampleRate,
      )
    }
  }

  /**
   * Mixes [samples] polyphonically into the live ring buffer without stopping previously ringing notes.
   */
  fun triggerSamples(
    samples: FloatArray,
    sampleRate: Int = InstrumentSynthDsp.SAMPLE_RATE,
  ) {
    if (samples.isEmpty()) return
    lastTriggerTimeMs.set(System.currentTimeMillis())
    val writeLen = min(samples.size, ringSize - 1)
    synchronized(ringLock) {
      val base = readCursor
      for (i in 0 until writeLen) {
        val pos = (base + i) % ringSize
        mixRing[pos] += samples[i]
      }
      activeFramesRemaining = max(activeFramesRemaining, writeLen)
    }
    ensurePolyphonicMixerRunning()
  }

  fun playPatternPreview(
    buffer: RawAudioBuffer,
    onCompleted: () -> Unit = {},
  ) {
    clearPolyphonicVoices()
    if (buffer.pcmSamples.isEmpty()) {
      onCompleted()
      return
    }
    audioEngine.playBuffer(
      buffer = buffer,
      title = buffer.bridgeSource,
      onCompleted = onCompleted,
    )
  }

  fun stopPreview() {
    clearPolyphonicVoices()
    audioEngine.stop()
  }

  fun release() {
    clearPolyphonicVoices()
    mixerJob?.cancel()
    mixerJob = null
    mixerRunning.set(false)
    scope.cancel()
    audioEngine.release()
  }

  private fun clearPolyphonicVoices() {
    synchronized(ringLock) {
      mixRing.fill(0f)
      activeFramesRemaining = 0
    }
  }

  private fun ensurePolyphonicMixerRunning() {
    if (!mixerRunning.compareAndSet(false, true)) return
    mixerJob = scope.launch {
      val chunkFrames = 512
      val outShorts = ShortArray(chunkFrames)
      val audioTrack = createLowLatencyTrack()
      if (audioTrack == null) {
        // Headless/unit-test fallback: drain state cleanly without hardware
        clearPolyphonicVoices()
        mixerRunning.set(false)
        return@launch
      }

      try {
        while (isActive) {
          var hasAudio = false
          synchronized(ringLock) {
            if (activeFramesRemaining > 0) {
              hasAudio = true
              for (i in 0 until chunkFrames) {
                val mixed = mixRing[readCursor].toDouble()
                mixRing[readCursor] = 0f
                val limited = tanh(mixed * 0.85)
                outShorts[i] = (limited * Short.MAX_VALUE * 0.88).toInt()
                  .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                  .toShort()
                readCursor = (readCursor + 1) % ringSize
              }
              activeFramesRemaining = (activeFramesRemaining - chunkFrames).coerceAtLeast(0)
            }
          }

          if (hasAudio) {
            val startNs = System.nanoTime()
            val written = audioTrack.write(outShorts, 0, chunkFrames, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            if (elapsedMs < 2L) {
              delay(10L)
            }
          } else {
            // Keep track warm for 1.5s so rapid note sequences have zero cold-start lag
            if (System.currentTimeMillis() - lastTriggerTimeMs.get() > 1_500L) {
              break
            }
            outShorts.fill(0)
            val written = audioTrack.write(outShorts, 0, chunkFrames, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
            delay(10L)
          }
        }
      } catch (_: CancellationException) {
      } finally {
        try {
          if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
          }
          audioTrack.flush()
          audioTrack.release()
        } catch (_: Exception) {
        }
        mixerRunning.set(false)
        // If a note arrived right as we exited, spin right back up
        val stillHasPending = synchronized(ringLock) { activeFramesRemaining > 0 }
        if (stillHasPending) {
          ensurePolyphonicMixerRunning()
        }
      }
    }
  }

  private fun createLowLatencyTrack(): AudioTrack? {
    return try {
      val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(2048)
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build(),
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(minBuf * 2)
        .build()
        .apply { play() }
    } catch (e: Exception) {
      Log.w("LiveInstrumentAudio", "Polyphonic AudioTrack unavailable: ${e.message}")
      null
    }
  }
}
