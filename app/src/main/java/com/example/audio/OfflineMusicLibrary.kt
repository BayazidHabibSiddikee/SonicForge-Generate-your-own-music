package com.example.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OfflineSampleCategory(val id: String, val label: String) {
  ALL("all", "All"),
  FULL_MUSIC("full_music", "Full Music"),
  TEXTURES("textures", "Textures"),
  STEMS("stems", "Stems"),
}

enum class OfflineSynthKind {
  LOFI_MIDNIGHT_RAIN,
  ACOUSTIC_GOLDEN_HOUR,
  NEON_SYNTHWAVE,
  VELVET_NEOSOUL,
  EMERALD_AMBIENT,
  SUNSET_BOSSA,
  DEEP_FOCUS_PIANO,
  TOKYO_CITY_FUNK,
  TEX_VINYL_CRACKLE,
  TEX_MIDNIGHT_RAIN,
  TEX_SHIMMER_PAD,
  TEX_CHOIR_SWELL,
  STEM_RHODES_CHORDS,
  STEM_DEEP_808_SUB,
  STEM_SHAKER_TAMBOURINE,
  STEM_HARP_ARP,
}

data class OfflineMusicAsset(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: OfflineSampleCategory,
  val genre: String,
  val bpm: Int,
  val musicalKey: String,
  val durationSec: Double,
  val tags: List<String>,
  val synthKind: OfflineSynthKind,
)

/**
 * Curated on-device music loops, ambient textures, and instrument stems that work 100% offline
 * with zero API keys or network calls.
 */
object OfflineMusicLibrary {

  val allAssets: List<OfflineMusicAsset> = listOf(
    // --- Full Multi-Instrument Offline Music Loops (8) ---
    OfflineMusicAsset(
      id = "lofi_midnight_rain",
      title = "Midnight Rain Lo-Fi",
      subtitle = "Warm Rhodes 7ths, dusty boom-bap drums & soft vinyl rain",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Lo-Fi Chill",
      bpm = 84,
      musicalKey = "F Maj",
      durationSec = 11.4,
      tags = listOf("lofi", "chill", "rain", "rhodes", "vinyl", "study", "mellow", "drums"),
      synthKind = OfflineSynthKind.LOFI_MIDNIGHT_RAIN,
    ),
    OfflineMusicAsset(
      id = "acoustic_golden_hour",
      title = "Golden Hour Acoustic",
      subtitle = "Fingerpicked steel guitar, warm upright bass & soft shaker",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Indie Folk",
      bpm = 102,
      musicalKey = "G Maj",
      durationSec = 9.4,
      tags = listOf("acoustic", "folk", "guitar", "warm", "uplifting", "indie", "organic"),
      synthKind = OfflineSynthKind.ACOUSTIC_GOLDEN_HOUR,
    ),
    OfflineMusicAsset(
      id = "neon_horizon_synthwave",
      title = "Neon Horizon Synthwave",
      subtitle = "Detuned analog pads, driving 80s octave bass & shimmer arp",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Synthwave",
      bpm = 114,
      musicalKey = "A Min",
      durationSec = 8.4,
      tags = listOf("synthwave", "80s", "retro", "neon", "synth", "electronic", "driving", "arp"),
      synthKind = OfflineSynthKind.NEON_SYNTHWAVE,
    ),
    OfflineMusicAsset(
      id = "velvet_neosoul_pocket",
      title = "Velvet Neo-Soul Pocket",
      subtitle = "Lush electric piano 9th chords, deep sub & swung groove",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Neo-Soul",
      bpm = 92,
      musicalKey = "D Min",
      durationSec = 10.4,
      tags = listOf("neosoul", "soul", "rnb", "rhodes", "groove", "smooth", "chill", "bass"),
      synthKind = OfflineSynthKind.VELVET_NEOSOUL,
    ),
    OfflineMusicAsset(
      id = "emerald_ambient_drift",
      title = "Emerald Ambient Drift",
      subtitle = "Evolving shimmer pads, crystal bell droplets & hall bloom",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Ambient",
      bpm = 76,
      musicalKey = "C Maj",
      durationSec = 12.6,
      tags = listOf("ambient", "dreamy", "pad", "cinematic", "calm", "meditation", "space"),
      synthKind = OfflineSynthKind.EMERALD_AMBIENT,
    ),
    OfflineMusicAsset(
      id = "sunset_bossa_groove",
      title = "Sunset Bossa Nova",
      subtitle = "Syncopated nylon guitar chords, walking bass & brushed percussion",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Jazz / Bossa",
      bpm = 108,
      musicalKey = "D Min",
      durationSec = 8.9,
      tags = listOf("bossa", "jazz", "latin", "nylon", "guitar", "cafe", "warm", "smooth"),
      synthKind = OfflineSynthKind.SUNSET_BOSSA,
    ),
    OfflineMusicAsset(
      id = "deep_focus_piano",
      title = "Deep Focus Felt Piano",
      subtitle = "Expressive felt grand melody over flowing left-hand arpeggios",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Cinematic Piano",
      bpm = 88,
      musicalKey = "A Min",
      durationSec = 10.9,
      tags = listOf("piano", "cinematic", "emotional", "focus", "classical", "melody", "soft"),
      synthKind = OfflineSynthKind.DEEP_FOCUS_PIANO,
    ),
    OfflineMusicAsset(
      id = "tokyo_city_funk",
      title = "Tokyo City Funk",
      subtitle = "Syncopated slap bassline, bright key stabs & crisp disco drums",
      category = OfflineSampleCategory.FULL_MUSIC,
      genre = "Funk / Pop",
      bpm = 116,
      musicalKey = "E Min",
      durationSec = 8.3,
      tags = listOf("funk", "pop", "disco", "bass", "groovy", "upbeat", "dance", "citypop"),
      synthKind = OfflineSynthKind.TOKYO_CITY_FUNK,
    ),

    // --- Studio Textures & Ambience Layers (4) ---
    OfflineMusicAsset(
      id = "tex_vinyl_crackle",
      title = "Warm Vinyl Crackle & Dust",
      subtitle = "Authentic turntable pops, analog warmth & subtle room noise",
      category = OfflineSampleCategory.TEXTURES,
      genre = "Texture",
      bpm = 85,
      musicalKey = "Any",
      durationSec = 8.0,
      tags = listOf("vinyl", "crackle", "dust", "lofi", "tape", "analog", "texture", "foley"),
      synthKind = OfflineSynthKind.TEX_VINYL_CRACKLE,
    ),
    OfflineMusicAsset(
      id = "tex_midnight_rain",
      title = "Soft Midnight Rain",
      subtitle = "Filtered window rain patter with gentle atmospheric wash",
      category = OfflineSampleCategory.TEXTURES,
      genre = "Ambience",
      bpm = 80,
      musicalKey = "Any",
      durationSec = 8.0,
      tags = listOf("rain", "ambience", "nature", "sleep", "chill", "lofi", "texture", "weather"),
      synthKind = OfflineSynthKind.TEX_MIDNIGHT_RAIN,
    ),
    OfflineMusicAsset(
      id = "tex_analog_shimmer_pad",
      title = "Analog Shimmer Pad",
      subtitle = "Warm detuned C/Am fifth drone with slow resonant filter sweep",
      category = OfflineSampleCategory.TEXTURES,
      genre = "Synth Texture",
      bpm = 90,
      musicalKey = "C Maj",
      durationSec = 8.0,
      tags = listOf("pad", "shimmer", "analog", "synth", "ambient", "warm", "texture", "dreamy"),
      synthKind = OfflineSynthKind.TEX_SHIMMER_PAD,
    ),
    OfflineMusicAsset(
      id = "tex_cinematic_choir_swell",
      title = "Cinematic Choir & String Swell",
      subtitle = "Formant-filtered orchestral chord swell with lush hall decay",
      category = OfflineSampleCategory.TEXTURES,
      genre = "Orchestral",
      bpm = 84,
      musicalKey = "A Min",
      durationSec = 8.0,
      tags = listOf("choir", "strings", "cinematic", "swell", "orchestral", "epic", "texture"),
      synthKind = OfflineSynthKind.TEX_CHOIR_SWELL,
    ),

    // --- Instrument & Percussion Stems (4) ---
    OfflineMusicAsset(
      id = "stem_rhodes_chords",
      title = "Warm Rhodes Jazz Chords",
      subtitle = "Mellow electric piano Maj9/Min9 voicings with silky tremolo",
      category = OfflineSampleCategory.STEMS,
      genre = "Keys Stem",
      bpm = 88,
      musicalKey = "C Maj",
      durationSec = 10.9,
      tags = listOf("rhodes", "chords", "piano", "jazz", "lofi", "neosoul", "keys", "loop"),
      synthKind = OfflineSynthKind.STEM_RHODES_CHORDS,
    ),
    OfflineMusicAsset(
      id = "stem_deep_808_sub",
      title = "Deep 808 Sub-Bass Groove",
      subtitle = "Punchy pitched 808 glide bassline locked to a 4-bar minor pocket",
      category = OfflineSampleCategory.STEMS,
      genre = "Bass Stem",
      bpm = 96,
      musicalKey = "A Min",
      durationSec = 10.0,
      tags = listOf("808", "sub", "bass", "trap", "hiphop", "lowend", "groove", "punchy"),
      synthKind = OfflineSynthKind.STEM_DEEP_808_SUB,
    ),
    OfflineMusicAsset(
      id = "stem_shaker_tambourine",
      title = "Acoustic Shaker & Tambourine",
      subtitle = "Humanized 16th-note acoustic percussion for instant live groove",
      category = OfflineSampleCategory.STEMS,
      genre = "Percussion Stem",
      bpm = 100,
      musicalKey = "Any",
      durationSec = 9.6,
      tags = listOf("shaker", "tambourine", "percussion", "acoustic", "groove", "drums", "folk", "pop"),
      synthKind = OfflineSynthKind.STEM_SHAKER_TAMBOURINE,
    ),
    OfflineMusicAsset(
      id = "stem_sparkle_harp_arp",
      title = "Crystal Harp & Bell Arp",
      subtitle = "Plucked pentatonic harp & celesta arpeggio with shimmer reverb",
      category = OfflineSampleCategory.STEMS,
      genre = "Pluck Stem",
      bpm = 94,
      musicalKey = "C Maj",
      durationSec = 10.2,
      tags = listOf("harp", "bell", "arp", "sparkle", "ambient", "pop", "melody", "dreamy"),
      synthKind = OfflineSynthKind.STEM_HARP_ARP,
    ),
  )

  fun findById(id: String): OfflineMusicAsset? = allAssets.firstOrNull { it.id == id }

  /**
   * Filters and ranks built-in offline tracks/samples by category and keyword relevance.
   */
  fun search(
    query: String,
    category: OfflineSampleCategory = OfflineSampleCategory.ALL,
  ): List<OfflineMusicAsset> {
    val pool = if (category == OfflineSampleCategory.ALL) {
      allAssets
    } else {
      allAssets.filter { it.category == category }
    }
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isEmpty()) return pool

    val tokens = cleanQuery
      .split(Regex("""[\s,;/\-_]+"""))
      .map { it.trim() }
      .filter { it.length >= 2 }
    if (tokens.isEmpty()) return pool

    val scored = pool.mapNotNull { asset ->
      val titleLower = asset.title.lowercase()
      val subLower = asset.subtitle.lowercase()
      val genreLower = asset.genre.lowercase()
      val keyLower = asset.musicalKey.lowercase()
      val tagsLower = asset.tags.map { it.lowercase() }

      var score = 0
      if (titleLower.contains(cleanQuery)) score += 12
      if (genreLower.contains(cleanQuery)) score += 8
      for (token in tokens) {
        if (titleLower.contains(token)) score += 6
        if (genreLower.contains(token)) score += 5
        if (tagsLower.any { it == token }) score += 7
        else if (tagsLower.any { it.contains(token) || token.contains(it) }) score += 4
        if (subLower.contains(token)) score += 3
        if (keyLower.contains(token)) score += 2
      }
      if (score > 0) asset to score else null
    }
    return scored.sortedByDescending { it.second }.map { it.first }
  }

  /**
   * Recommends the top 3-4 concise search keywords that match real assets in this offline library.
   */
  fun recommendQueriesForPrompt(prompt: String, genre: String): List<String> {
    val matches = search("$prompt $genre", OfflineSampleCategory.ALL)
    val preferredTexturesAndStems = matches
      .filter { it.category != OfflineSampleCategory.FULL_MUSIC }
      .take(2)
      .map { it.tags.take(2).joinToString(" ") }
    val preferredLoops = matches
      .filter { it.category == OfflineSampleCategory.FULL_MUSIC }
      .take(2)
      .map { it.genre }
    return (preferredTexturesAndStems + preferredLoops + listOf("vinyl crackle", "rhodes chords", "shaker tambourine"))
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .distinct()
      .take(4)
  }
}

/**
 * Pure on-device studio synthesizer that renders rich 44.1 kHz 16-bit PCM WAV audio for every
 * [OfflineMusicAsset] in [OfflineMusicLibrary], with local disk caching for instant replay.
 */
object OfflineMusicSynthesizerDsp {

  private const val SAMPLE_RATE = 44100

  suspend fun ensureRenderedWav(
    asset: OfflineMusicAsset,
    cacheDir: File,
  ): File = withContext(Dispatchers.IO) {
    if (!cacheDir.exists()) {
      cacheDir.mkdirs()
    }
    val outFile = File(cacheDir, "offline_${asset.id}_v1.wav")
    if (outFile.exists() && outFile.length() > 1024L) {
      return@withContext outFile
    }
    val pcm = renderAssetPcm(asset)
    val analyzed = FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = pcm,
      sampleRate = SAMPLE_RATE,
      channelCount = 1,
      bridgeSource = "Offline Studio · ${asset.title}",
    )
    FourierAudioBufferProcessor.writeWavFile(outFile, analyzed)
    outFile
  }

  fun renderAssetPcm(asset: OfflineMusicAsset): ShortArray {
    val floatBuf = when (asset.synthKind) {
      OfflineSynthKind.LOFI_MIDNIGHT_RAIN -> renderLofiMidnightRain(asset.bpm)
      OfflineSynthKind.ACOUSTIC_GOLDEN_HOUR -> renderAcousticGoldenHour(asset.bpm)
      OfflineSynthKind.NEON_SYNTHWAVE -> renderNeonSynthwave(asset.bpm)
      OfflineSynthKind.VELVET_NEOSOUL -> renderVelvetNeoSoul(asset.bpm)
      OfflineSynthKind.EMERALD_AMBIENT -> renderEmeraldAmbient(asset.bpm)
      OfflineSynthKind.SUNSET_BOSSA -> renderSunsetBossa(asset.bpm)
      OfflineSynthKind.DEEP_FOCUS_PIANO -> renderDeepFocusPiano(asset.bpm)
      OfflineSynthKind.TOKYO_CITY_FUNK -> renderTokyoCityFunk(asset.bpm)
      OfflineSynthKind.TEX_VINYL_CRACKLE -> renderVinylCrackle(8.0)
      OfflineSynthKind.TEX_MIDNIGHT_RAIN -> renderMidnightRain(8.0)
      OfflineSynthKind.TEX_SHIMMER_PAD -> renderShimmerPad(8.0)
      OfflineSynthKind.TEX_CHOIR_SWELL -> renderChoirSwell(8.0)
      OfflineSynthKind.STEM_RHODES_CHORDS -> renderRhodesChordStem(asset.bpm)
      OfflineSynthKind.STEM_DEEP_808_SUB -> renderDeep808SubStem(asset.bpm)
      OfflineSynthKind.STEM_SHAKER_TAMBOURINE -> renderShakerTambourineStem(asset.bpm)
      OfflineSynthKind.STEM_HARP_ARP -> renderHarpArpStem(asset.bpm)
    }

    val rawShorts = floatToPcm16(floatBuf)
    val reverbPreset = when (asset.synthKind) {
      OfflineSynthKind.EMERALD_AMBIENT,
      OfflineSynthKind.TEX_CHOIR_SWELL,
      OfflineSynthKind.STEM_HARP_ARP -> ReverbPreset.HALL
      OfflineSynthKind.DEEP_FOCUS_PIANO,
      OfflineSynthKind.LOFI_MIDNIGHT_RAIN,
      OfflineSynthKind.NEON_SYNTHWAVE,
      OfflineSynthKind.TEX_SHIMMER_PAD -> ReverbPreset.STUDIO
      OfflineSynthKind.TEX_VINYL_CRACKLE,
      OfflineSynthKind.TEX_MIDNIGHT_RAIN,
      OfflineSynthKind.STEM_DEEP_808_SUB -> ReverbPreset.OFF
      else -> ReverbPreset.CHAMBER
    }
    val reverbed = if (reverbPreset != ReverbPreset.OFF) {
      RoomReverbDsp.processPcmOffline(
        pcmSamples = rawShorts,
        sampleRate = SAMPLE_RATE,
        channelCount = 1,
        settings = ReverbSettings().withPreset(reverbPreset).copy(
          wetMix = reverbPreset.wetMix * 0.65f,
        ),
        tailDurationMs = 0L,
      )
    } else {
      rawShorts
    }

    val targetLufs = if (asset.category == OfflineSampleCategory.TEXTURES) -20f else -14f
    return LoudnessNormalizerDsp.normalizePcm(
      pcmSamples = reverbed,
      sampleRate = SAMPLE_RATE,
      channelCount = 1,
      targetLufs = targetLufs,
    ).normalizedSamples
  }

  private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

  private fun barSamples(bpm: Int): Int = ((60.0 / bpm) * 4.0 * SAMPLE_RATE).roundToInt()

  private fun renderLofiMidnightRain(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(53, 57, 60, 64, 67), // Fmaj9
      intArrayOf(50, 57, 60, 64, 65), // Dm9
      intArrayOf(46, 53, 57, 60, 62), // Bbmaj9
      intArrayOf(48, 55, 58, 62, 64), // C9
    )
    val bass = intArrayOf(41, 38, 34, 36)
    val beat = bar / 4
    val step = bar / 16
    val rng = Random(101)

    for (b in 0 until 4) {
      val barStart = b * bar
      addRhodesChord(out, barStart, bar, chords[b], 0.34f, tremoloHz = 3.8)
      addRhodesChord(out, barStart + beat * 2 + step / 2, beat * 2, chords[b], 0.18f, tremoloHz = 3.8)
      addWarmBass(out, barStart, beat * 3, bass[b], 0.38f)
      addWarmBass(out, barStart + beat * 3 + step, beat - step, bass[b] + 2, 0.28f)

      // Dusty boom-bap groove with gentle swing
      val kickSteps = intArrayOf(0, 7, 10)
      val snareSteps = intArrayOf(4, 12)
      for (s in 0 until 16) {
        val swingOffset = if (s % 2 == 1) (step * 0.14).toInt() else 0
        val pos = barStart + s * step + swingOffset
        if (s in kickSteps) addPunchyKick(out, pos, baseHz = 48.0, gain = 0.56f)
        if (s in snareSteps) addWarmSnare(out, pos, gain = 0.34f, rng = rng, crispness = 0.45f)
        if (s % 2 == 0) addHiHat(out, pos, gain = if (s % 4 == 0) 0.14f else 0.09f, decay = 48.0, rng = rng)
      }
    }
    // Layer subtle vinyl warmth & rain
    val vinyl = renderVinylCrackle(total.toDouble() / SAMPLE_RATE)
    for (i in out.indices) {
      out[i] += vinyl[i % vinyl.size] * 0.16f
    }
    return applyLowPassWarmth(out, alpha = 0.52f)
  }

  private fun renderAcousticGoldenHour(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(43, 47, 50, 55, 59, 62), // G
      intArrayOf(50, 54, 57, 62, 66),     // D
      intArrayOf(40, 47, 52, 55, 59, 64), // Em
      intArrayOf(48, 52, 55, 60, 64),     // C
    )
    val bass = intArrayOf(43, 38, 40, 36)
    val eighth = bar / 8
    val rng = Random(202)

    for (b in 0 until 4) {
      val barStart = b * bar
      val chord = chords[b]
      addWarmBass(out, barStart, bar / 2, bass[b], 0.34f)
      addWarmBass(out, barStart + bar / 2, bar / 2, bass[b] + 7, 0.24f)

      // Strummed acoustic down-up pattern + fingerpicked arpeggio shimmer
      val strumIndices = intArrayOf(0, 2, 3, 5, 6)
      for (e in 0 until 8) {
        val pos = barStart + e * eighth
        if (e in strumIndices) {
          val up = e == 3 || e == 6
          val ordered = if (up) chord.reversedArray() else chord
          ordered.forEachIndexed { idx, midi ->
            val offset = idx * (SAMPLE_RATE / 90)
            addPluckedString(out, pos + offset, (eighth * 2.4).toInt(), midi, gain = if (up) 0.13f else 0.17f, damping = 0.35, rng = rng)
          }
        }
        val arpMidi = chord[e % chord.size] + 12
        addPluckedString(out, pos, (eighth * 1.8).toInt(), arpMidi, gain = 0.10f, damping = 0.42, rng = rng)
        addShakerHit(out, pos, gain = if (e % 2 == 0) 0.14f else 0.08f, rng = rng)
      }
      addPunchyKick(out, barStart, baseHz = 56.0, gain = 0.32f)
      addPunchyKick(out, barStart + bar / 2, baseHz = 56.0, gain = 0.26f)
    }
    return out
  }

  private fun renderNeonSynthwave(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(57, 60, 64, 69), // Am
      intArrayOf(53, 57, 60, 65), // F
      intArrayOf(48, 55, 60, 64), // C
      intArrayOf(55, 59, 62, 67), // G
    )
    val bass = intArrayOf(33, 29, 36, 31)
    val step = bar / 16
    val rng = Random(303)

    for (b in 0 until 4) {
      val barStart = b * bar
      addSupersawPad(out, barStart, bar, chords[b], 0.24f)
      for (s in 0 until 16) {
        val pos = barStart + s * step
        val octaveShift = if (s % 2 == 1) 12 else 0
        addAnalogSawBass(out, pos, (step * 0.85).toInt(), bass[b] + octaveShift, 0.28f)
        val arpNote = chords[b][s % chords[b].size] + if (s >= 8) 12 else 0
        addSynthPluck(out, pos, (step * 1.4).toInt(), arpNote, 0.14f)
        if (s % 4 == 0) addPunchyKick(out, pos, baseHz = 46.0, gain = 0.52f)
        if (s == 4 || s == 12) addWarmSnare(out, pos, gain = 0.42f, rng = rng, crispness = 0.95f)
        if (s % 2 == 1) addHiHat(out, pos, gain = 0.14f, decay = 36.0, rng = rng)
      }
    }
    return out
  }

  private fun renderVelvetNeoSoul(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(50, 57, 60, 64, 65), // Dm9
      intArrayOf(55, 59, 62, 65, 69), // G9
      intArrayOf(48, 55, 59, 62, 64), // Cmaj9
      intArrayOf(57, 60, 64, 67, 71), // Am9
    )
    val bass = intArrayOf(38, 43, 36, 45)
    val step = bar / 16
    val rng = Random(404)

    for (b in 0 until 4) {
      val barStart = b * bar
      addRhodesChord(out, barStart, step * 7, chords[b], 0.36f, tremoloHz = 4.6)
      addRhodesChord(out, barStart + step * 7, step * 5, chords[b], 0.24f, tremoloHz = 4.6)
      addRhodesChord(out, barStart + step * 13, step * 3, chords[b], 0.20f, tremoloHz = 4.6)
      addWarmBass(out, barStart, step * 6, bass[b], 0.42f)
      addWarmBass(out, barStart + step * 7, step * 5, bass[b] + 7, 0.30f)
      addWarmBass(out, barStart + step * 14, step * 2, bass[b] + 5, 0.28f)

      val kickSteps = intArrayOf(0, 6, 10)
      for (s in 0 until 16) {
        val swing = if (s % 2 == 1) (step * 0.16).toInt() else 0
        val pos = barStart + s * step + swing
        if (s in kickSteps) addPunchyKick(out, pos, baseHz = 44.0, gain = 0.50f)
        if (s == 4 || s == 12) addWarmSnare(out, pos, gain = 0.30f, rng = rng, crispness = 0.72f)
        if (s % 2 == 0 || s == 15) addHiHat(out, pos, gain = if (s % 4 == 0) 0.13f else 0.08f, decay = 55.0, rng = rng)
      }
    }
    return out
  }

  private fun renderEmeraldAmbient(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(48, 55, 60, 62, 64, 67), // Cmaj9
      intArrayOf(45, 52, 57, 60, 64, 67), // Am7
      intArrayOf(41, 48, 53, 57, 60, 64), // Fmaj7
      intArrayOf(43, 50, 55, 59, 62, 67), // G
    )
    val bellScale = intArrayOf(72, 74, 76, 79, 81, 84, 79, 76)
    val quarter = bar / 4

    for (b in 0 until 4) {
      val barStart = b * bar
      addSupersawPad(out, barStart, bar, chords[b], 0.26f)
      // Gentle crystal bell droplets
      addBellNote(out, barStart + quarter / 2, (quarter * 2.5).toInt(), bellScale[(b * 2) % bellScale.size], 0.16f)
      addBellNote(out, barStart + quarter * 2 + quarter / 2, (quarter * 2.0).toInt(), bellScale[(b * 2 + 1) % bellScale.size], 0.13f)
    }
    return applyLowPassWarmth(out, alpha = 0.40f)
  }

  private fun renderSunsetBossa(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(50, 57, 60, 65), // Dm7
      intArrayOf(43, 53, 59, 64), // G13
      intArrayOf(48, 55, 59, 64), // Cmaj7
      intArrayOf(45, 55, 61, 64), // A7
    )
    val bass = intArrayOf(38, 43, 36, 45)
    val step = bar / 16
    val rng = Random(505)
    val claveSteps = intArrayOf(0, 3, 6, 10, 12)

    for (b in 0 until 4) {
      val barStart = b * bar
      addWarmBass(out, barStart, step * 6, bass[b], 0.36f)
      addWarmBass(out, barStart + step * 6, step * 2, bass[b] + 7, 0.28f)
      addWarmBass(out, barStart + step * 8, step * 6, bass[b] + 7, 0.32f)
      addWarmBass(out, barStart + step * 14, step * 2, bass[b], 0.26f)

      for (s in claveSteps) {
        val pos = barStart + s * step
        chords[b].forEachIndexed { idx, midi ->
          addPluckedString(out, pos + idx * 180, step * 4, midi, gain = 0.17f, damping = 0.56, rng = rng)
        }
      }
      for (s in 0 until 16) {
        val pos = barStart + s * step
        addShakerHit(out, pos, gain = if (s % 4 == 0) 0.14f else 0.08f, rng = rng)
        if (s == 0 || s == 6 || s == 8 || s == 14) addPunchyKick(out, pos, baseHz = 54.0, gain = 0.26f)
        if (s == 3 || s == 6 || s == 10 || s == 13) addRimshot(out, pos, 0.24f)
      }
    }
    return out
  }

  private fun renderDeepFocusPiano(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val leftHand = listOf(
      intArrayOf(45, 52, 57, 60, 57, 52, 60, 57), // Am
      intArrayOf(41, 48, 53, 57, 53, 48, 57, 53), // F
      intArrayOf(48, 55, 60, 64, 60, 55, 64, 60), // C
      intArrayOf(43, 50, 55, 59, 55, 50, 59, 55), // G
    )
    val melody = listOf(
      listOf(0 to 72, 3 to 71, 4 to 69, 6 to 67),
      listOf(0 to 69, 2 to 72, 4 to 74, 6 to 72),
      listOf(0 to 76, 3 to 74, 4 to 72, 6 to 71),
      listOf(0 to 74, 2 to 71, 4 to 67, 6 to 69),
    )
    val eighth = bar / 8

    for (b in 0 until 4) {
      val barStart = b * bar
      leftHand[b].forEachIndexed { idx, midi ->
        addPianoTone(out, barStart + idx * eighth, (eighth * 2.6).toInt(), midi, 0.20f)
      }
      melody[b].forEach { (e, midi) ->
        addPianoTone(out, barStart + e * eighth, (eighth * 3.2).toInt(), midi, 0.30f)
      }
    }
    return applyLowPassWarmth(out, alpha = 0.65f)
  }

  private fun renderTokyoCityFunk(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val total = bar * 4
    val out = FloatArray(total)
    val chords = listOf(
      intArrayOf(52, 59, 62, 67), // Em7
      intArrayOf(57, 61, 64, 67), // A7
      intArrayOf(50, 57, 60, 65), // Dm7
      intArrayOf(55, 59, 62, 65), // G7
    )
    val bass = intArrayOf(40, 33, 38, 31)
    val step = bar / 16
    val rng = Random(606)

    for (b in 0 until 4) {
      val barStart = b * bar
      val funkBassSteps = listOf(0 to 0, 2 to 12, 3 to 0, 6 to 7, 8 to 0, 10 to 10, 11 to 12, 14 to 7)
      for ((s, semi) in funkBassSteps) {
        addAnalogSawBass(out, barStart + s * step, (step * 1.1).toInt(), bass[b] + semi, 0.34f)
      }
      val stabSteps = intArrayOf(2, 6, 10, 13)
      for (s in stabSteps) {
        addRhodesChord(out, barStart + s * step, step * 2, chords[b], 0.26f, tremoloHz = 0.0)
      }
      for (s in 0 until 16) {
        val pos = barStart + s * step
        if (s % 4 == 0) addPunchyKick(out, pos, baseHz = 50.0, gain = 0.50f)
        if (s == 4 || s == 12) addWarmSnare(out, pos, gain = 0.40f, rng = rng, crispness = 0.90f)
        addHiHat(out, pos, gain = if (s % 2 == 0) 0.14f else 0.08f, decay = if (s == 14) 16.0 else 52.0, rng = rng)
      }
    }
    return out
  }

  private fun renderVinylCrackle(durationSec: Double): FloatArray {
    val total = (durationSec * SAMPLE_RATE).roundToInt()
    val out = FloatArray(total)
    val rng = Random(707)
    var pink0 = 0.0
    var pink1 = 0.0
    var pink2 = 0.0
    for (i in 0 until total) {
      val white = rng.nextDouble(-1.0, 1.0)
      pink0 = 0.997 * pink0 + white * 0.029
      pink1 = 0.985 * pink1 + white * 0.032
      pink2 = 0.950 * pink2 + white * 0.048
      var sample = (pink0 + pink1 + pink2) * 0.16
      // Subtle 50 Hz warm turntable hum
      val t = i.toDouble() / SAMPLE_RATE
      sample += sin(2.0 * PI * 55.0 * t) * 0.015
      // Randomized micro vinyl dust pops
      val r = rng.nextDouble()
      if (r < 0.00055) {
        val popAmp = rng.nextDouble(0.18, 0.52) * if (rng.nextBoolean()) 1.0 else -1.0
        sample += popAmp
        if (i + 1 < total) out[i + 1] += (popAmp * 0.45).toFloat()
      } else if (r < 0.0022) {
        sample += rng.nextDouble(-0.08, 0.08)
      }
      out[i] += sample.toFloat()
    }
    return applyCrossfadeEdges(out, fadeSamples = 2000)
  }

  private fun renderMidnightRain(durationSec: Double): FloatArray {
    val total = (durationSec * SAMPLE_RATE).roundToInt()
    val out = FloatArray(total)
    val rng = Random(808)
    var lp1 = 0.0
    var lp2 = 0.0
    var hpPrev = 0.0
    for (i in 0 until total) {
      val t = i.toDouble() / SAMPLE_RATE
      val white = rng.nextDouble(-1.0, 1.0)
      // Gentle rain intensity swell
      val swell = 0.78 + 0.22 * sin(2.0 * PI * 0.18 * t)
      lp1 = 0.88 * lp1 + 0.12 * white
      lp2 = 0.96 * lp2 + 0.04 * white
      val rainWash = (lp1 - lp2 * 0.55) * 0.42 * swell
      val highDrop = (white - hpPrev) * 0.05
      hpPrev = white
      var sample = rainWash + highDrop
      // Occasional soft raindrop tap on glass
      if (rng.nextDouble() < 0.00035) {
        val dropLen = 280
        val dropFreq = rng.nextDouble(900.0, 2400.0)
        val dropGain = rng.nextDouble(0.06, 0.16)
        for (d in 0 until dropLen) {
          if (i + d >= total) break
          val dt = d.toDouble() / SAMPLE_RATE
          val env = exp(-dt * 42.0)
          out[i + d] += (sin(2.0 * PI * dropFreq * dt) * env * dropGain).toFloat()
        }
      }
      out[i] += sample.toFloat()
    }
    return applyCrossfadeEdges(out, fadeSamples = 4000)
  }

  private fun renderShimmerPad(durationSec: Double): FloatArray {
    val total = (durationSec * SAMPLE_RATE).roundToInt()
    val out = FloatArray(total)
    val chord = intArrayOf(48, 55, 60, 64, 67, 74) // Cmaj9/5 shimmer
    addSupersawPad(out, 0, total, chord, 0.30f)
    return applyCrossfadeEdges(applyLowPassWarmth(out, alpha = 0.34f), fadeSamples = 6000)
  }

  private fun renderChoirSwell(durationSec: Double): FloatArray {
    val total = (durationSec * SAMPLE_RATE).roundToInt()
    val out = FloatArray(total)
    val notes = intArrayOf(45, 52, 57, 60, 64, 69) // Am lush choir voicing
    for (midi in notes) {
      val f0 = midiToHz(midi)
      for (i in 0 until total) {
        val progress = i.toDouble() / total
        val swell = sin(PI * progress).pow(1.25)
        val t = i.toDouble() / SAMPLE_RATE
        val vib = 1.0 + 0.0035 * sin(2.0 * PI * 4.8 * t)
        val phase = 2.0 * PI * f0 * vib * t
        val voice = sin(phase) * 0.55 +
          sin(phase * 2.0) * 0.28 +
          sin(phase * 3.0) * 0.14 +
          sin(phase * 4.0) * 0.06
        out[i] += (voice * swell * 0.12).toFloat()
      }
    }
    return applyLowPassWarmth(out, alpha = 0.42f)
  }

  private fun renderRhodesChordStem(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val out = FloatArray(bar * 4)
    val chords = listOf(
      intArrayOf(48, 55, 59, 62, 64), // Cmaj9
      intArrayOf(45, 52, 55, 59, 60), // Am9
      intArrayOf(50, 57, 60, 64, 65), // Dm9
      intArrayOf(43, 53, 57, 59, 62), // G13
    )
    for (b in 0 until 4) {
      addRhodesChord(out, b * bar, bar, chords[b], 0.42f, tremoloHz = 4.2)
    }
    return out
  }

  private fun renderDeep808SubStem(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val out = FloatArray(bar * 4)
    val roots = intArrayOf(33, 33, 29, 31) // A1, A1, F1, G1
    val step = bar / 16
    val pattern = intArrayOf(0, 6, 10, 14)
    for (b in 0 until 4) {
      val barStart = b * bar
      for (s in pattern) {
        val pitch = if (s == 14) roots[b] + 2 else roots[b]
        add808SubHit(out, barStart + s * step, step * 5, pitch, 0.62f)
      }
    }
    return out
  }

  private fun renderShakerTambourineStem(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val out = FloatArray(bar * 4)
    val step = bar / 16
    val rng = Random(909)
    for (b in 0 until 4) {
      val barStart = b * bar
      for (s in 0 until 16) {
        val accent = when {
          s % 4 == 0 -> 0.34f
          s % 4 == 2 -> 0.24f
          else -> 0.14f
        }
        addShakerHit(out, barStart + s * step, gain = accent, rng = rng)
        if (s == 4 || s == 12) {
          addHiHat(out, barStart + s * step, gain = 0.22f, decay = 22.0, rng = rng)
        }
      }
    }
    return out
  }

  private fun renderHarpArpStem(bpm: Int): FloatArray {
    val bar = barSamples(bpm)
    val out = FloatArray(bar * 4)
    val step = bar / 16
    val rng = Random(1010)
    val arp = intArrayOf(60, 64, 67, 71, 72, 76, 79, 76, 72, 71, 67, 64, 62, 67, 71, 74)
    for (b in 0 until 4) {
      val barStart = b * bar
      val shift = when (b) {
        1 -> -3
        2 -> -5
        3 -> -2
        else -> 0
      }
      for (s in 0 until 16) {
        addPluckedString(out, barStart + s * step, step * 6, arp[s] + shift, gain = 0.22f, damping = 0.28, rng = rng)
        if (s % 4 == 0) {
          addBellNote(out, barStart + s * step, step * 8, arp[s] + shift + 12, 0.08f)
        }
      }
    }
    return out
  }

  // ---- Reusable Studio DSP Primitives ----

  private fun addRhodesChord(
    out: FloatArray,
    start: Int,
    length: Int,
    midis: IntArray,
    gain: Float,
    tremoloHz: Double,
  ) {
    val perNote = gain / midis.size.coerceAtLeast(1)
    for (midi in midis) {
      val f0 = midiToHz(midi)
      for (i in 0 until length) {
        val idx = start + i
        if (idx !in out.indices) break
        val t = i.toDouble() / SAMPLE_RATE
        val attack = (t / 0.012).coerceAtMost(1.0)
        val decay = exp(-t * 1.45)
        val release = if (i > length - 800) ((length - i).toDouble() / 800.0).coerceIn(0.0, 1.0) else 1.0
        val tremolo = if (tremoloHz > 0.0) 1.0 - 0.16 * (0.5 + 0.5 * sin(2.0 * PI * tremoloHz * t)) else 1.0
        val bell = exp(-t * 16.0) * 0.45 * sin(2.0 * PI * f0 * 4.0 * t)
        val body = sin(2.0 * PI * f0 * t + bell) + 0.18 * sin(2.0 * PI * f0 * 2.0 * t)
        out[idx] += (body * attack * decay * release * tremolo * perNote).toFloat()
      }
    }
  }

  private fun addPianoTone(out: FloatArray, start: Int, length: Int, midi: Int, gain: Float) {
    val f0 = midiToHz(midi)
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val attack = (t / 0.008).coerceAtMost(1.0)
      val env = attack * exp(-t * 2.0) * if (i > length - 600) ((length - i) / 600.0).coerceIn(0.0, 1.0) else 1.0
      val s = sin(2.0 * PI * f0 * t) * 0.62 +
        sin(2.0 * PI * f0 * 2.0 * t) * 0.24 * exp(-t * 1.5) +
        sin(2.0 * PI * f0 * 3.0 * t) * 0.10 * exp(-t * 3.0) +
        sin(2.0 * PI * f0 * 4.0 * t) * 0.04 * exp(-t * 5.0)
      out[idx] += (s * env * gain).toFloat()
    }
  }

  private fun addPluckedString(
    out: FloatArray,
    start: Int,
    length: Int,
    midi: Int,
    gain: Float,
    damping: Double,
    rng: Random,
  ) {
    val freq = midiToHz(midi)
    val delayLen = (SAMPLE_RATE / freq).roundToInt().coerceIn(20, 2000)
    val ring = DoubleArray(delayLen) { rng.nextDouble(-1.0, 1.0) * (1.0 - damping * 0.35) }
    var ptr = 0
    val sustain = 0.9965 - damping * 0.003
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val current = ring[ptr]
      val next = ring[(ptr + 1) % delayLen]
      val filtered = sustain * (0.5 * current + 0.5 * next)
      ring[ptr] = filtered
      ptr = (ptr + 1) % delayLen
      val release = if (i > length - 500) ((length - i) / 500.0).coerceIn(0.0, 1.0) else 1.0
      out[idx] += (current * gain * release).toFloat()
    }
  }

  private fun addSupersawPad(out: FloatArray, start: Int, length: Int, midis: IntArray, gain: Float) {
    val detunes = doubleArrayOf(0.994, 1.0, 1.006)
    val perVoice = gain / (midis.size * detunes.size).coerceAtLeast(1)
    for (midi in midis) {
      val baseFreq = midiToHz(midi)
      for (d in detunes) {
        val f = baseFreq * d
        var lp = 0.0
        for (i in 0 until length) {
          val idx = start + i
          if (idx !in out.indices) break
          val t = i.toDouble() / SAMPLE_RATE
          val attack = (t / 0.35).coerceAtMost(1.0)
          val release = if (i > length - 4000) ((length - i) / 4000.0).coerceIn(0.0, 1.0) else 1.0
          val phase = (f * t) % 1.0
          val saw = 2.0 * phase - 1.0
          val cutoff = 0.08 + 0.07 * (0.5 + 0.5 * sin(2.0 * PI * 0.25 * t))
          lp += cutoff * (saw - lp)
          out[idx] += (lp * attack * release * perVoice * 2.2).toFloat()
        }
      }
    }
  }

  private fun addWarmBass(out: FloatArray, start: Int, length: Int, midi: Int, gain: Float) {
    val f0 = midiToHz(midi)
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val attack = (t / 0.015).coerceAtMost(1.0)
      val decay = exp(-t * 2.4)
      val release = if (i > length - 600) ((length - i) / 600.0).coerceIn(0.0, 1.0) else 1.0
      val wave = sin(2.0 * PI * f0 * t) * 0.8 + sin(2.0 * PI * f0 * 2.0 * t) * 0.2
      out[idx] += (tanh(wave * 1.3) * attack * decay * release * gain).toFloat()
    }
  }

  private fun addAnalogSawBass(out: FloatArray, start: Int, length: Int, midi: Int, gain: Float) {
    val f0 = midiToHz(midi)
    var lp = 0.0
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val attack = (t / 0.004).coerceAtMost(1.0)
      val decay = exp(-t * 6.0)
      val phase = (f0 * t) % 1.0
      val saw = 2.0 * phase - 1.0
      val cutoff = 0.09 + 0.24 * exp(-t * 14.0)
      lp += cutoff * (saw - lp)
      out[idx] += (tanh(lp * 1.8) * attack * (0.35 + 0.65 * decay) * gain).toFloat()
    }
  }

  private fun add808SubHit(out: FloatArray, start: Int, length: Int, midi: Int, gain: Float) {
    val f0 = midiToHz(midi)
    var phase = 0.0
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val pitchSweep = f0 * (1.0 + 1.4 * exp(-t * 32.0))
      phase += 2.0 * PI * pitchSweep / SAMPLE_RATE
      val env = (t / 0.005).coerceAtMost(1.0) * exp(-t * 3.0)
      out[idx] += (tanh(sin(phase) * 1.45) * env * gain).toFloat()
    }
  }

  private fun addSynthPluck(out: FloatArray, start: Int, length: Int, midi: Int, gain: Float) {
    val f0 = midiToHz(midi)
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val env = (t / 0.003).coerceAtMost(1.0) * exp(-t * 9.5)
      val wave = sin(2.0 * PI * f0 * t) * 0.65 + sin(2.0 * PI * f0 * 2.0 * t) * 0.35 * exp(-t * 12.0)
      out[idx] += (wave * env * gain).toFloat()
    }
  }

  private fun addBellNote(out: FloatArray, start: Int, length: Int, midi: Int, gain: Float) {
    val f0 = midiToHz(midi)
    for (i in 0 until length) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val env = (t / 0.004).coerceAtMost(1.0) * exp(-t * 2.4)
      val s = sin(2.0 * PI * f0 * t) * 0.65 +
        sin(2.0 * PI * f0 * 2.756 * t) * 0.25 * exp(-t * 4.5) +
        sin(2.0 * PI * f0 * 5.404 * t) * 0.10 * exp(-t * 8.0)
      out[idx] += (s * env * gain).toFloat()
    }
  }

  private fun addPunchyKick(out: FloatArray, start: Int, baseHz: Double, gain: Float) {
    val len = (SAMPLE_RATE * 0.28).toInt()
    var phase = 0.0
    for (i in 0 until len) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val freq = baseHz + 115.0 * exp(-t * 36.0)
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val env = exp(-t * 11.5)
      val click = exp(-t * 240.0) * 0.35
      out[idx] += (tanh((sin(phase) + click) * 1.35) * env * gain).toFloat()
    }
  }

  private fun addWarmSnare(out: FloatArray, start: Int, gain: Float, rng: Random, crispness: Float) {
    val len = (SAMPLE_RATE * 0.22).toInt()
    var prev = 0.0
    for (i in 0 until len) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val body = sin(2.0 * PI * 185.0 * t) * exp(-t * 26.0) * 0.55
      val white = rng.nextDouble(-1.0, 1.0)
      val hp = white - prev * 0.6
      prev = white
      val noise = hp * exp(-t * 17.0) * crispness * 0.75
      out[idx] += ((body + noise) * gain).toFloat()
    }
  }

  private fun addHiHat(out: FloatArray, start: Int, gain: Float, decay: Double, rng: Random) {
    val len = (SAMPLE_RATE * 0.12).toInt()
    var prev = 0.0
    for (i in 0 until len) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val white = rng.nextDouble(-1.0, 1.0)
      val hp = white - prev * 0.85
      prev = white
      out[idx] += (hp * exp(-t * decay) * gain).toFloat()
    }
  }

  private fun addShakerHit(out: FloatArray, start: Int, gain: Float, rng: Random) {
    val len = (SAMPLE_RATE * 0.065).toInt()
    var prev = 0.0
    for (i in 0 until len) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val env = sin(PI * (i.toDouble() / len)) * exp(-t * 22.0)
      val white = rng.nextDouble(-1.0, 1.0)
      val bp = white - prev * 0.72
      prev = white
      out[idx] += (bp * env * gain).toFloat()
    }
  }

  private fun addRimshot(out: FloatArray, start: Int, gain: Float) {
    val len = (SAMPLE_RATE * 0.045).toInt()
    for (i in 0 until len) {
      val idx = start + i
      if (idx !in out.indices) break
      val t = i.toDouble() / SAMPLE_RATE
      val env = exp(-t * 75.0)
      val tone = sin(2.0 * PI * 480.0 * t) * 0.6 + sin(2.0 * PI * 1650.0 * t) * 0.4
      out[idx] += (tone * env * gain).toFloat()
    }
  }

  private fun applyLowPassWarmth(input: FloatArray, alpha: Float): FloatArray {
    val out = FloatArray(input.size)
    var y = 0f
    for (i in input.indices) {
      y += alpha * (input[i] - y)
      out[i] = y
    }
    return out
  }

  private fun applyCrossfadeEdges(input: FloatArray, fadeSamples: Int): FloatArray {
    val fade = fadeSamples.coerceAtMost(input.size / 4)
    for (i in 0 until fade) {
      val w = (0.5 * (1.0 - cos(PI * i / fade))).toFloat()
      input[i] *= w
      input[input.size - 1 - i] *= w
    }
    return input
  }

  private fun floatToPcm16(samples: FloatArray): ShortArray {
    var peak = 0.001f
    for (s in samples) {
      val a = abs(s)
      if (a > peak) peak = a
    }
    val scale = if (peak > 0.92f) 0.92f / peak else 1.0f
    return ShortArray(samples.size) { i ->
      val v = tanh((samples[i] * scale * 1.08f).toDouble()).toFloat().coerceIn(-1f, 1f)
      (v * 32767f).roundToInt().toShort()
    }
  }
}
