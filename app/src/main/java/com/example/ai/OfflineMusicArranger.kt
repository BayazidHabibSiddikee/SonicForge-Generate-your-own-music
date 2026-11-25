package com.example.ai

import com.example.audio.DrumKitPreset
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.OfflineMusicLibrary
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.audio.ReverbPreset
import com.example.audio.SequencerPreset
import kotlin.math.abs

/**
 * 100% on-device musical arrangement engine that works completely offline with zero network calls
 * or API keys. Uses prompt NLP scoring across genre, mood, tempo, key, and instrument cues to
 * produce a cohesive multi-track [MusicArrangementPlan] and matching offline sample recommendations.
 */
class OfflineMusicArranger : MusicArrangerService {

  private data class GenreProfile(
    val genre: String,
    val keywords: List<String>,
    val defaultBpm: Int,
    val musicalKey: String,
    val pianoVoice: PianoVoice,
    val pianoProgression: PianoProgressionPreset,
    val guitarVoice: GuitarVoice,
    val guitarChords: List<GuitarChordShape>,
    val guitarStrumStyle: GuitarStrumStyle,
    val drumKit: DrumKitPreset,
    val drumPreset: SequencerPreset,
    val reverbPreset: ReverbPreset,
    val pianoGain: Float,
    val guitarGain: Float,
    val drumGain: Float,
    val sampleGain: Float,
    val titleAdjectives: List<String>,
    val titleNouns: List<String>,
  )

  private val profiles = listOf(
    GenreProfile(
      genre = "Lo-Fi Chillhop",
      keywords = listOf("lofi", "lo-fi", "chill", "rain", "rainy", "vinyl", "study", "midnight", "dusty", "sleep", "cozy", "boom", "bap", "mellow"),
      defaultBpm = 84,
      musicalKey = "C Major",
      pianoVoice = PianoVoice.RHODES_EP,
      pianoProgression = PianoProgressionPreset.LOFI_SEVENTHS,
      guitarVoice = GuitarVoice.JAZZ_HOLLOWBODY,
      guitarChords = listOf(GuitarChordShape.D_MIN, GuitarChordShape.G_MAJ, GuitarChordShape.C_MAJ, GuitarChordShape.A_MIN),
      guitarStrumStyle = GuitarStrumStyle.FINGERSTYLE_ARP,
      drumKit = DrumKitPreset.LOFI_VINYL,
      drumPreset = SequencerPreset.LOFI_BOOM_BAP,
      reverbPreset = ReverbPreset.STUDIO,
      pianoGain = 0.96f,
      guitarGain = 0.78f,
      drumGain = 0.92f,
      sampleGain = 0.68f,
      titleAdjectives = listOf("Midnight", "Velvet", "Rainy", "Dusty"),
      titleNouns = listOf("Vinyl Drift", "Study Haze", "Window Glow", "Tape Echo"),
    ),
    GenreProfile(
      genre = "Synthwave",
      keywords = listOf("synthwave", "synth", "80s", "neon", "retro", "cyber", "cyberpunk", "electronic", "drive", "outrun", "arp", "electro", "edm", "club", "house"),
      defaultBpm = 114,
      musicalKey = "A Minor",
      pianoVoice = PianoVoice.ANALOG_KEYS,
      pianoProgression = PianoProgressionPreset.CINEMATIC_MINOR,
      guitarVoice = GuitarVoice.CLEAN_ELECTRIC,
      guitarChords = listOf(GuitarChordShape.A_MIN, GuitarChordShape.F_MAJ, GuitarChordShape.C_MAJ, GuitarChordShape.G_MAJ),
      guitarStrumStyle = GuitarStrumStyle.ROCK_RIFF,
      drumKit = DrumKitPreset.CYBER_ELECTRO,
      drumPreset = SequencerPreset.SYNTHWAVE_ARP,
      reverbPreset = ReverbPreset.HALL,
      pianoGain = 0.94f,
      guitarGain = 0.80f,
      drumGain = 1.05f,
      sampleGain = 0.62f,
      titleAdjectives = listOf("Neon", "Chrome", "Laser", "Midnight"),
      titleNouns = listOf("Horizon Drive", "Pulse Runner", "Skyline 84", "Overdrive"),
    ),
    GenreProfile(
      genre = "Indie Acoustic Folk",
      keywords = listOf("acoustic", "folk", "indie", "campfire", "sunrise", "golden", "sunset", "organic", "country", "warm", "guitar", "uplifting", "bright"),
      defaultBpm = 102,
      musicalKey = "C Major",
      pianoVoice = PianoVoice.BRIGHT_UPRIGHT,
      pianoProgression = PianoProgressionPreset.POP_CLASSIC,
      guitarVoice = GuitarVoice.ACOUSTIC_STEEL,
      guitarChords = listOf(GuitarChordShape.G_MAJ, GuitarChordShape.C_ADD9, GuitarChordShape.E_MIN, GuitarChordShape.C_MAJ),
      guitarStrumStyle = GuitarStrumStyle.ACOUSTIC_STRUM,
      drumKit = DrumKitPreset.STUDIO_ACOUSTIC,
      drumPreset = SequencerPreset.FOUR_ON_FLOOR,
      reverbPreset = ReverbPreset.CHAMBER,
      pianoGain = 0.84f,
      guitarGain = 1.02f,
      drumGain = 0.86f,
      sampleGain = 0.58f,
      titleAdjectives = listOf("Golden", "Sunlit", "Autumn", "Wildflower"),
      titleNouns = listOf("Meadow Road", "Horizon Folk", "Cedar Morning", "Porchlight"),
    ),
    GenreProfile(
      genre = "Neo-Soul & R&B",
      keywords = listOf("soul", "neosoul", "neo-soul", "rnb", "r&b", "groove", "smooth", "funk", "funky", "pocket", "jazz", "jazzy", "velvet", "cafe", "bossa"),
      defaultBpm = 92,
      musicalKey = "D Major",
      pianoVoice = PianoVoice.RHODES_EP,
      pianoProgression = PianoProgressionPreset.NEO_SOUL_GROOVE,
      guitarVoice = GuitarVoice.CLEAN_ELECTRIC,
      guitarChords = listOf(GuitarChordShape.E_MIN, GuitarChordShape.A_MIN, GuitarChordShape.D_MIN, GuitarChordShape.G_MAJ),
      guitarStrumStyle = GuitarStrumStyle.FUNK_CHOP,
      drumKit = DrumKitPreset.STUDIO_ACOUSTIC,
      drumPreset = SequencerPreset.LOFI_BOOM_BAP,
      reverbPreset = ReverbPreset.CHAMBER,
      pianoGain = 0.98f,
      guitarGain = 0.88f,
      drumGain = 0.94f,
      sampleGain = 0.64f,
      titleAdjectives = listOf("Velvet", "Honey", "Silk", "Amber"),
      titleNouns = listOf("Pocket Groove", "Late Lounge", "Soul Motion", "Chord Sway"),
    ),
    GenreProfile(
      genre = "Cinematic Ambient",
      keywords = listOf("ambient", "cinematic", "epic", "dreamy", "space", "emotional", "sad", "piano", "orchestral", "choir", "calm", "meditation", "dark", "ethereal"),
      defaultBpm = 80,
      musicalKey = "A Minor",
      pianoVoice = PianoVoice.CONCERT_GRAND,
      pianoProgression = PianoProgressionPreset.CINEMATIC_MINOR,
      guitarVoice = GuitarVoice.CLASSICAL_NYLON,
      guitarChords = listOf(GuitarChordShape.A_MIN, GuitarChordShape.F_MAJ, GuitarChordShape.C_MAJ, GuitarChordShape.E_MIN),
      guitarStrumStyle = GuitarStrumStyle.FINGERSTYLE_ARP,
      drumKit = DrumKitPreset.LOFI_VINYL,
      drumPreset = SequencerPreset.LOFI_BOOM_BAP,
      reverbPreset = ReverbPreset.CATHEDRAL,
      pianoGain = 1.05f,
      guitarGain = 0.74f,
      drumGain = 0.72f,
      sampleGain = 0.76f,
      titleAdjectives = listOf("Starlight", "Emerald", "Silver", "Celestial"),
      titleNouns = listOf("Reverie", "Stillness", "Glass Cathedral", "Horizon Bloom"),
    ),
    GenreProfile(
      genre = "Alt Rock & Trap",
      keywords = listOf("rock", "metal", "punk", "trap", "hiphop", "hip-hop", "808", "hard", "heavy", "energetic", "aggressive", "distortion", "overdrive", "power"),
      defaultBpm = 126,
      musicalKey = "E Minor",
      pianoVoice = PianoVoice.VINTAGE_WURLI,
      pianoProgression = PianoProgressionPreset.CINEMATIC_MINOR,
      guitarVoice = GuitarVoice.OVERDRIVE_ROCK,
      guitarChords = listOf(GuitarChordShape.E_POWER, GuitarChordShape.G_MAJ, GuitarChordShape.A_MIN, GuitarChordShape.E_MIN),
      guitarStrumStyle = GuitarStrumStyle.ROCK_RIFF,
      drumKit = DrumKitPreset.TR_808,
      drumPreset = SequencerPreset.FOUR_ON_FLOOR,
      reverbPreset = ReverbPreset.STUDIO,
      pianoGain = 0.76f,
      guitarGain = 1.04f,
      drumGain = 1.10f,
      sampleGain = 0.60f,
      titleAdjectives = listOf("Voltage", "Thunder", "Crimson", "Iron"),
      titleNouns = listOf("Overdrive", "Pulse Riot", "Night Stage", "Breaker"),
    ),
  )

  override suspend fun generateArrangement(prompt: String): MusicArrangementPlan {
    val cleanPrompt = prompt.trim()
    require(cleanPrompt.isNotEmpty()) { "Enter a musical mood, genre, or vibe to arrange." }

    val lower = cleanPrompt.lowercase()
    val tokens = lower.split(Regex("""[\s,.;:!?/\\\-_]+""")).filter { it.isNotBlank() }
    val hash = abs(cleanPrompt.hashCode())

    // 1. Score genre profiles
    val bestProfile = profiles.maxByOrNull { profile ->
      var score = 0
      for (kw in profile.keywords) {
        if (lower.contains(kw)) score += if (kw.length > 4) 4 else 2
        if (tokens.any { it == kw }) score += 3
      }
      score
    } ?: profiles.first()

    // 2. Parse explicit BPM or tempo cue
    val explicitBpm = Regex("""\b(\d{2,3})\s*bpm\b""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val tempoAdjustment = when {
      lower.contains("slow") || lower.contains("sleep") || lower.contains("calm") -> -10
      lower.contains("fast") || lower.contains("upbeat") || lower.contains("energetic") || lower.contains("dance") -> +14
      else -> 0
    }
    val bpm = (explicitBpm ?: (bestProfile.defaultBpm + tempoAdjustment)).coerceIn(65, 165)

    // 3. Parse explicit instrument overrides from prompt
    val pianoVoice = when {
      lower.contains("rhodes") || lower.contains("electric piano") -> PianoVoice.RHODES_EP
      lower.contains("wurli") -> PianoVoice.VINTAGE_WURLI
      lower.contains("grand") || lower.contains("classical") -> PianoVoice.CONCERT_GRAND
      lower.contains("upright") -> PianoVoice.BRIGHT_UPRIGHT
      lower.contains("synth") || lower.contains("analog") -> PianoVoice.ANALOG_KEYS
      else -> bestProfile.pianoVoice
    }
    val guitarVoice = when {
      lower.contains("nylon") || lower.contains("classical") || lower.contains("bossa") -> GuitarVoice.CLASSICAL_NYLON
      lower.contains("overdrive") || lower.contains("rock") || lower.contains("distort") -> GuitarVoice.OVERDRIVE_ROCK
      lower.contains("jazz") || lower.contains("hollow") -> GuitarVoice.JAZZ_HOLLOWBODY
      lower.contains("electric") || lower.contains("funk") -> GuitarVoice.CLEAN_ELECTRIC
      lower.contains("acoustic") || lower.contains("folk") -> GuitarVoice.ACOUSTIC_STEEL
      else -> bestProfile.guitarVoice
    }
    val drumKit = when {
      lower.contains("808") || lower.contains("trap") || lower.contains("hiphop") -> DrumKitPreset.TR_808
      lower.contains("vinyl") || lower.contains("lofi") || lower.contains("lo-fi") -> DrumKitPreset.LOFI_VINYL
      lower.contains("cyber") || lower.contains("electro") || lower.contains("synthwave") -> DrumKitPreset.CYBER_ELECTRO
      lower.contains("live") || lower.contains("acoustic") -> DrumKitPreset.STUDIO_ACOUSTIC
      else -> bestProfile.drumKit
    }
    val reverbPreset = when {
      lower.contains("cathedral") || lower.contains("ethereal") || lower.contains("space") -> ReverbPreset.CATHEDRAL
      lower.contains("hall") || lower.contains("cinematic") -> ReverbPreset.HALL
      lower.contains("dry") || lower.contains("tight") -> ReverbPreset.CHAMBER
      else -> bestProfile.reverbPreset
    }

    val adj = bestProfile.titleAdjectives[hash % bestProfile.titleAdjectives.size]
    val noun = bestProfile.titleNouns[(hash / 7) % bestProfile.titleNouns.size]
    val title = "$adj $noun"

    val suggestedQueries = OfflineMusicLibrary.recommendQueriesForPrompt(
      prompt = cleanPrompt,
      genre = bestProfile.genre,
    )

    val summary = "On-device ${bestProfile.genre} arrangement at $bpm BPM (${bestProfile.musicalKey}) " +
      "pairing ${pianoVoice.label}, ${guitarVoice.label}, and ${drumKit.label} drums in ${reverbPreset.label} space."

    return MusicArrangementPlan(
      title = title,
      summary = summary,
      genre = bestProfile.genre,
      musicalKey = bestProfile.musicalKey,
      bpm = bpm,
      pianoVoice = pianoVoice,
      pianoProgression = bestProfile.pianoProgression,
      guitarVoice = guitarVoice,
      guitarChords = bestProfile.guitarChords,
      guitarStrumStyle = bestProfile.guitarStrumStyle,
      drumKit = drumKit,
      drumPreset = bestProfile.drumPreset,
      reverbPreset = reverbPreset,
      pianoGain = bestProfile.pianoGain,
      guitarGain = bestProfile.guitarGain,
      drumGain = bestProfile.drumGain,
      sampleGain = bestProfile.sampleGain,
      suggestedSampleQueries = suggestedQueries,
      sourcePrompt = cleanPrompt,
      engineLabel = "Offline Model",
    )
  }
}
