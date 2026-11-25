package com.example.ai

import android.util.Log
import com.example.audio.DrumKitPreset
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.audio.ReverbPreset
import com.example.audio.SequencerPreset
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiMusicArranger"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val INFERENCE_TIMEOUT_MS = 60_000L

enum class AiProducerEngine(val id: String, val label: String) {
  OFFLINE_MODEL("offline_model", "Offline Model"),
  GEMINI_CLOUD("gemini_cloud", "Gemini Cloud"),
}

/**
 * Structured multi-track production blueprint returned by either the Offline Arranger Model
 * or Gemini Cloud via Firebase AI Logic. Drives coordinated Piano, Guitar, Drum, Reverb,
 * Mixer, and Built-In Offline Music/Texture layers.
 */
data class MusicArrangementPlan(
  val title: String,
  val summary: String,
  val genre: String,
  val musicalKey: String,
  val bpm: Int,
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
  val suggestedSampleQueries: List<String>,
  val sourcePrompt: String,
  val engineLabel: String = "Gemini AI",
)

interface MusicArrangerService {
  suspend fun generateArrangement(prompt: String): MusicArrangementPlan
}

class GeminiMusicArranger : MusicArrangerService {

  private val arrangementSchema: Schema by lazy {
    Schema.obj(
      properties = mapOf(
        "title" to Schema.string(description = "Short, evocative 2-5 word song title"),
        "summary" to Schema.string(
          description = "Concise 1-2 sentence producer note describing groove, mood, and mix balance",
        ),
        "genre" to Schema.string(description = "Primary music genre, e.g. Lo-Fi Chillhop, Indie Folk, Neo-Soul"),
        "musicalKey" to Schema.string(description = "Musical key such as C Major, A Minor, G Major"),
        "bpm" to Schema.integer(description = "Tempo in beats per minute between 65 and 165"),
        "pianoVoice" to Schema.enumeration(
          values = PianoVoice.entries.map { it.id },
          description = "Best matching piano voice ID",
        ),
        "pianoProgression" to Schema.enumeration(
          values = PianoProgressionPreset.entries.map { it.id },
          description = "Best matching piano chord progression preset ID",
        ),
        "guitarVoice" to Schema.enumeration(
          values = GuitarVoice.entries.map { it.id },
          description = "Best matching guitar voice ID",
        ),
        "guitarChords" to Schema.array(
          items = Schema.enumeration(
            values = GuitarChordShape.entries.map { it.id },
            description = "Guitar chord shape ID",
          ),
          description = "4-chord guitar progression sequence matching the piano harmony",
        ),
        "guitarStrumStyle" to Schema.enumeration(
          values = GuitarStrumStyle.entries.map { it.id },
          description = "Guitar rhythmic strumming or picking style ID",
        ),
        "drumKit" to Schema.enumeration(
          values = DrumKitPreset.entries.map { it.id },
          description = "Best matching drum kit preset ID",
        ),
        "drumPreset" to Schema.enumeration(
          values = SequencerPreset.entries.map { it.id },
          description = "Best matching 16-step drum groove preset ID",
        ),
        "reverbPreset" to Schema.enumeration(
          values = ReverbPreset.entries.map { it.name },
          description = "Room reverb preset name",
        ),
        "pianoGain" to Schema.double(description = "Recommended piano stem mix gain between 0.45 and 1.15"),
        "guitarGain" to Schema.double(description = "Recommended guitar stem mix gain between 0.45 and 1.15"),
        "drumGain" to Schema.double(description = "Recommended drum stem mix gain between 0.55 and 1.25"),
        "sampleGain" to Schema.double(description = "Recommended offline texture/sample mix gain between 0.35 and 1.00"),
        "suggestedSampleQueries" to Schema.array(
          items = Schema.string(description = "Short 1-3 word offline library keyword"),
          description = "3 to 4 focused keywords from the studio offline library (e.g. vinyl crackle, midnight rain, shimmer pad, choir swell, rhodes chords, 808 bass, shaker tambourine, harp arp, lofi, synthwave, acoustic folk, bossa, funk)",
        ),
      ),
    )
  }

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = MODEL_NAME,
      generationConfig = generationConfig {
        temperature = 0.7f
        responseMimeType = "application/json"
        responseSchema = arrangementSchema
      },
      systemInstruction = content {
        text(
          "You are an expert music producer and arranger for SonicForge Studio. " +
            "Given a user's musical idea, mood, or genre description, design a cohesive, " +
            "musically aligned multi-track arrangement combining Piano, Guitar, Drums, Reverb, " +
            "stem mix gains, and 3-4 short keywords matching the built-in offline studio library " +
            "(such as vinyl crackle, midnight rain, shimmer pad, choir swell, rhodes chords, " +
            "808 bass, shaker tambourine, harp arp, lofi, acoustic folk, synthwave, neo-soul, bossa, funk). " +
            "Always choose matching harmonic keys, realistic BPMs (65-165), and balanced mix levels.",
        )
      },
    )
  }

  override suspend fun generateArrangement(prompt: String): MusicArrangementPlan {
    val cleanPrompt = prompt.trim()
    require(cleanPrompt.isNotEmpty()) { "Enter a musical mood, genre, or vibe to arrange." }

    try {
      val response = withTimeout(INFERENCE_TIMEOUT_MS) {
        model.generateContent(
          "Create a complete multi-track studio arrangement and offline studio layer recommendations for:\n$cleanPrompt",
        )
      }
      val jsonText = response.text
        ?: throw IllegalStateException("Gemini returned an empty arrangement response.")
      return parseArrangementJson(jsonText = jsonText, sourcePrompt = cleanPrompt)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini arrangement timed out after ${INFERENCE_TIMEOUT_MS}ms", e)
      throw IllegalStateException("AI arrangement timed out after 60 seconds. Please try again.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini arrangement failed", e)
      throw e
    }
  }

  companion object {
    /**
     * Parses raw structured JSON directly without markdown code fence stripping.
     */
    fun parseArrangementJson(
      jsonText: String,
      sourcePrompt: String,
    ): MusicArrangementPlan {
      val json = JSONObject(jsonText)
      val title = json.optString("title", "").trim().ifBlank { "AI Studio Arrangement" }
      val summary = json.optString("summary", "").trim()
        .ifBlank { "Balanced multi-track arrangement ready to render and layer with offline studio samples." }
      val genre = json.optString("genre", "").trim().ifBlank { "Studio Mix" }
      val musicalKey = json.optString("musicalKey", "").trim().ifBlank { "C Major" }
      val bpm = json.optInt("bpm", 104).coerceIn(65, 165)

      val pianoVoiceId = json.optString("pianoVoice", "")
      val pianoVoice = PianoVoice.entries.firstOrNull { it.id == pianoVoiceId }
        ?: PianoVoice.CONCERT_GRAND

      val pianoProgId = json.optString("pianoProgression", "")
      val pianoProgression = PianoProgressionPreset.entries.firstOrNull { it.id == pianoProgId }
        ?: PianoProgressionPreset.POP_CLASSIC

      val guitarVoiceId = json.optString("guitarVoice", "")
      val guitarVoice = GuitarVoice.entries.firstOrNull { it.id == guitarVoiceId }
        ?: GuitarVoice.ACOUSTIC_STEEL

      val chordsArray = json.optJSONArray("guitarChords")
      val parsedChords = buildList {
        if (chordsArray != null) {
          for (i in 0 until chordsArray.length()) {
            val chordId = chordsArray.optString(i, "")
            GuitarChordShape.entries.firstOrNull { it.id == chordId }?.let { add(it) }
          }
        }
      }.take(6).ifEmpty {
        listOf(
          GuitarChordShape.C_MAJ,
          GuitarChordShape.G_MAJ,
          GuitarChordShape.A_MIN,
          GuitarChordShape.F_MAJ,
        )
      }

      val strumId = json.optString("guitarStrumStyle", "")
      val guitarStrumStyle = GuitarStrumStyle.entries.firstOrNull { it.id == strumId }
        ?: GuitarStrumStyle.ACOUSTIC_STRUM

      val drumKitId = json.optString("drumKit", "")
      val drumKit = DrumKitPreset.entries.firstOrNull { it.id == drumKitId }
        ?: DrumKitPreset.STUDIO_ACOUSTIC

      val drumPresetId = json.optString("drumPreset", "")
      val drumPreset = SequencerPreset.entries.firstOrNull { it.id == drumPresetId }
        ?: SequencerPreset.FOUR_ON_FLOOR

      val reverbName = json.optString("reverbPreset", "")
      val reverbPreset = ReverbPreset.entries.firstOrNull { it.name.equals(reverbName, ignoreCase = true) }
        ?: ReverbPreset.STUDIO

      val pianoGain = json.optDouble("pianoGain", 0.90).toFloat().coerceIn(0.35f, 1.35f)
      val guitarGain = json.optDouble("guitarGain", 0.85).toFloat().coerceIn(0.35f, 1.35f)
      val drumGain = json.optDouble("drumGain", 0.95).toFloat().coerceIn(0.35f, 1.35f)
      val sampleGain = json.optDouble("sampleGain", 0.65).toFloat().coerceIn(0.25f, 1.25f)

      val queriesArray = json.optJSONArray("suggestedSampleQueries")
        ?: json.optJSONArray("freesoundQueries")
      val suggestedQueries = buildList {
        if (queriesArray != null) {
          for (i in 0 until queriesArray.length()) {
            val q = queriesArray.optString(i, "").trim()
            if (q.isNotBlank()) add(q)
          }
        }
      }.distinct().take(4).ifEmpty {
        listOf("vinyl crackle", "shimmer pad", "rhodes chords")
      }

      return MusicArrangementPlan(
        title = title,
        summary = summary,
        genre = genre,
        musicalKey = musicalKey,
        bpm = bpm,
        pianoVoice = pianoVoice,
        pianoProgression = pianoProgression,
        guitarVoice = guitarVoice,
        guitarChords = parsedChords,
        guitarStrumStyle = guitarStrumStyle,
        drumKit = drumKit,
        drumPreset = drumPreset,
        reverbPreset = reverbPreset,
        pianoGain = pianoGain,
        guitarGain = guitarGain,
        drumGain = drumGain,
        sampleGain = sampleGain,
        suggestedSampleQueries = suggestedQueries,
        sourcePrompt = sourcePrompt,
        engineLabel = "Gemini AI",
      )
    }
  }
}
