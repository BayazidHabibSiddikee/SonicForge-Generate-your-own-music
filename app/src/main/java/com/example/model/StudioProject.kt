package com.example.model

import com.example.audio.CrossfadeSettings
import com.example.audio.EqualizerSettings
import com.example.audio.EqPreset
import com.example.audio.GraphicEqualizerDsp
import com.example.audio.LoudnessTarget
import com.example.audio.MetronomeDsp
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import com.example.audio.PitchShiftDsp
import com.example.audio.ReverbEngineMode
import com.example.audio.ReverbPreset
import com.example.audio.ReverbSettings
import com.example.audio.RoomReverbDsp
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.StepSequencerDsp
import com.example.audio.TrackAudioEnvelope
import com.example.audio.VolumeEnvelopeDsp
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Complete snapshot of a SonicForge studio session persisted via Room, capturing active audio
 * clips, playback state, full DSP effects chain, 16-step sequencer pattern, and metronome config.
 */
data class StudioProject(
  val id: String,
  val name: String,
  val notes: String = "",
  val activeTrackId: String? = null,
  val activeClipTitle: String = "",
  val clipIds: List<String> = emptyList(),
  val clips: List<GeneratedTrack> = emptyList(),
  val selectedPlaylistId: String? = null,
  val playbackPositionMs: Long = 0L,
  val volume: Float = 0.85f,
  val isMuted: Boolean = false,
  val isLooping: Boolean = false,
  val pitchSemitones: Float = 0f,
  val reverbSettings: ReverbSettings = ReverbSettings(),
  val equalizerSettings: EqualizerSettings = EqualizerSettings(),
  val crossfadeSettings: CrossfadeSettings = CrossfadeSettings(),
  val autoPostGainEnabled: Boolean = true,
  val loudnessTarget: LoudnessTarget = LoudnessTarget.STREAMING_14,
  val activeEnvelope: TrackAudioEnvelope = TrackAudioEnvelope(),
  val sequencerPattern: Map<SequencerLane, List<Boolean>> = SequencerPreset.FOUR_ON_FLOOR.buildGrid(),
  val sequencerBpm: Int = 112,
  val sequencerSwingPercent: Int = 0,
  val sequencerBpmSynced: Boolean = true,
  val sequencerPresetName: String = SequencerPreset.FOUR_ON_FLOOR.label,
  val metronomeBpm: Int = 112,
  val metronomeBpmSynced: Boolean = true,
  val metronomeVolume: Float = 0.80f,
  val metronomePitchHz: Float = MetronomeDsp.DEFAULT_PITCH_HZ,
  val metronomeToneStyle: MetronomeTone = MetronomeTone.DIGITAL_CLICK,
  val metronomeTimeSignature: MetronomeTimeSignature = MetronomeTimeSignature.FOUR_FOUR,
  val metronomeAccentDownbeat: Boolean = true,
  val createdAtEpochMs: Long = System.currentTimeMillis(),
  val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
  val activeSequencerSteps: Int
    get() = sequencerPattern.values.sumOf { lane -> lane.count { it } }

  val totalClipCount: Int
    get() = maxOf(clipIds.size, clips.size)

  fun buildEffectSummaryBadges(): List<String> {
    val badges = mutableListOf<String>()
    if (abs(pitchSemitones) >= 0.05f) {
      badges += "Pitch ${PitchShiftDsp.formatSemitoneBadge(pitchSemitones)}"
    }
    if (reverbSettings.isActive) {
      badges += "Reverb ${reverbSettings.preset.label} (${RoomReverbDsp.formatWetPercent(reverbSettings.roomSize)})"
    }
    if (equalizerSettings.isActive) {
      badges += "EQ ${equalizerSettings.preset.label}"
    }
    if (crossfadeSettings.isActive) {
      badges += "Crossfade ${crossfadeSettings.durationSec.toInt()}s"
    }
    if (activeEnvelope.isEdited) {
      badges += "Envelope"
    }
    if (autoPostGainEnabled) {
      badges += loudnessTarget.label
    }
    return badges
  }
}

object StudioProjectCodec {

  fun serializeReverb(settings: ReverbSettings): String = JSONObject().apply {
    put("preset", settings.preset.name)
    put("wetMix", settings.wetMix.toDouble())
    put("roomSize", settings.roomSize.toDouble())
    put("damping", settings.damping.toDouble())
    put("engineMode", settings.engineMode.name)
  }.toString()

  fun deserializeReverb(json: String): ReverbSettings {
    if (json.isBlank()) return ReverbSettings()
    return try {
      val obj = JSONObject(json)
      val preset = ReverbPreset.entries.firstOrNull { it.name == obj.optString("preset") }
        ?: ReverbPreset.OFF
      val engineMode = ReverbEngineMode.entries.firstOrNull { it.name == obj.optString("engineMode") }
        ?: ReverbEngineMode.CONVOLUTION
      ReverbSettings(
        preset = preset,
        wetMix = obj.optDouble("wetMix", 0.0).toFloat().coerceIn(0f, 1f),
        roomSize = obj.optDouble("roomSize", 0.55).toFloat().coerceIn(0.10f, 0.98f),
        damping = obj.optDouble("damping", 0.40).toFloat().coerceIn(0.05f, 0.95f),
        engineMode = engineMode,
      )
    } catch (_: Exception) {
      ReverbSettings()
    }
  }

  fun serializeEqualizer(settings: EqualizerSettings): String = JSONObject().apply {
    put("isEnabled", settings.isEnabled)
    put("preset", settings.preset.name)
    val arr = JSONArray()
    settings.normalizedBands.forEach { arr.put(it.toDouble()) }
    put("bands", arr)
  }.toString()

  fun deserializeEqualizer(json: String): EqualizerSettings {
    if (json.isBlank()) return EqualizerSettings()
    return try {
      val obj = JSONObject(json)
      val preset = EqPreset.entries.firstOrNull { it.name == obj.optString("preset") }
        ?: EqPreset.FLAT
      val arr = obj.optJSONArray("bands")
      val bands = List(GraphicEqualizerDsp.BAND_COUNT) { idx ->
        val fallback = preset.defaultBandsDb.getOrElse(idx) { 0f }.toDouble()
        (arr?.optDouble(idx, fallback) ?: fallback).toFloat()
          .coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB)
      }
      EqualizerSettings(
        isEnabled = obj.optBoolean("isEnabled", false),
        preset = preset,
        bandGainsDb = bands,
      )
    } catch (_: Exception) {
      EqualizerSettings()
    }
  }

  fun serializeCrossfade(settings: CrossfadeSettings): String = JSONObject().apply {
    put("enabled", settings.enabled)
    put("durationSec", settings.durationSec.toDouble())
  }.toString()

  fun deserializeCrossfade(json: String): CrossfadeSettings {
    if (json.isBlank()) return CrossfadeSettings()
    return try {
      val obj = JSONObject(json)
      CrossfadeSettings(
        enabled = obj.optBoolean("enabled", false),
        durationSec = obj.optDouble("durationSec", 4.0).toFloat(),
      ).clamped()
    } catch (_: Exception) {
      CrossfadeSettings()
    }
  }

  fun serializeLoudness(enabled: Boolean, target: LoudnessTarget): String = JSONObject().apply {
    put("enabled", enabled)
    put("target", target.name)
  }.toString()

  fun deserializeLoudness(json: String): Pair<Boolean, LoudnessTarget> {
    if (json.isBlank()) return true to LoudnessTarget.STREAMING_14
    return try {
      val obj = JSONObject(json)
      val enabled = obj.optBoolean("enabled", true)
      val target = LoudnessTarget.entries.firstOrNull { it.name == obj.optString("target") }
        ?: LoudnessTarget.STREAMING_14
      enabled to target
    } catch (_: Exception) {
      true to LoudnessTarget.STREAMING_14
    }
  }

  fun serializeSequencer(
    pattern: Map<SequencerLane, List<Boolean>>,
    bpm: Int,
    swingPercent: Int,
    bpmSynced: Boolean,
    presetName: String,
  ): String = JSONObject().apply {
    put("bpm", bpm.coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM))
    put("swingPercent", swingPercent.coerceIn(0, StepSequencerDsp.MAX_SWING_PERCENT))
    put("bpmSynced", bpmSynced)
    put("presetName", presetName)
    val lanesObj = JSONObject()
    SequencerLane.entries.forEach { lane ->
      val row = pattern[lane].orEmpty()
      val arr = JSONArray()
      for (step in 0 until StepSequencerDsp.STEP_COUNT) {
        arr.put(row.getOrElse(step) { false })
      }
      lanesObj.put(lane.id, arr)
    }
    put("lanes", lanesObj)
  }.toString()

  data class DeserializedSequencer(
    val pattern: Map<SequencerLane, List<Boolean>>,
    val bpm: Int,
    val swingPercent: Int,
    val bpmSynced: Boolean,
    val presetName: String,
  )

  fun deserializeSequencer(json: String): DeserializedSequencer {
    val defaultGrid = SequencerPreset.FOUR_ON_FLOOR.buildGrid()
    if (json.isBlank()) {
      return DeserializedSequencer(
        pattern = defaultGrid,
        bpm = 112,
        swingPercent = 0,
        bpmSynced = true,
        presetName = SequencerPreset.FOUR_ON_FLOOR.label,
      )
    }
    return try {
      val obj = JSONObject(json)
      val lanesObj = obj.optJSONObject("lanes")
      val grid = SequencerLane.entries.associateWith { lane ->
        val arr = lanesObj?.optJSONArray(lane.id)
        if (arr == null) {
          defaultGrid[lane] ?: List(StepSequencerDsp.STEP_COUNT) { false }
        } else {
          List(StepSequencerDsp.STEP_COUNT) { step -> arr.optBoolean(step, false) }
        }
      }
      DeserializedSequencer(
        pattern = grid,
        bpm = obj.optInt("bpm", 112).coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM),
        swingPercent = obj.optInt("swingPercent", 0).coerceIn(0, StepSequencerDsp.MAX_SWING_PERCENT),
        bpmSynced = obj.optBoolean("bpmSynced", true),
        presetName = obj.optString("presetName", SequencerPreset.FOUR_ON_FLOOR.label),
      )
    } catch (_: Exception) {
      DeserializedSequencer(
        pattern = defaultGrid,
        bpm = 112,
        swingPercent = 0,
        bpmSynced = true,
        presetName = SequencerPreset.FOUR_ON_FLOOR.label,
      )
    }
  }

  fun serializeMetronome(
    bpm: Int,
    bpmSynced: Boolean,
    volume: Float,
    pitchHz: Float,
    toneStyle: MetronomeTone,
    timeSignature: MetronomeTimeSignature,
    accentDownbeat: Boolean,
  ): String = JSONObject().apply {
    put("bpm", bpm.coerceIn(MetronomeDsp.MIN_BPM, MetronomeDsp.MAX_BPM))
    put("bpmSynced", bpmSynced)
    put("volume", volume.coerceIn(0f, 1f).toDouble())
    put("pitchHz", pitchHz.coerceIn(MetronomeDsp.MIN_PITCH_HZ, MetronomeDsp.MAX_PITCH_HZ).toDouble())
    put("toneStyle", toneStyle.name)
    put("timeSignature", timeSignature.name)
    put("accentDownbeat", accentDownbeat)
  }.toString()

  data class DeserializedMetronome(
    val bpm: Int,
    val bpmSynced: Boolean,
    val volume: Float,
    val pitchHz: Float,
    val toneStyle: MetronomeTone,
    val timeSignature: MetronomeTimeSignature,
    val accentDownbeat: Boolean,
  )

  fun deserializeMetronome(json: String): DeserializedMetronome {
    if (json.isBlank()) {
      return DeserializedMetronome(
        bpm = 112,
        bpmSynced = true,
        volume = 0.80f,
        pitchHz = MetronomeDsp.DEFAULT_PITCH_HZ,
        toneStyle = MetronomeTone.DIGITAL_CLICK,
        timeSignature = MetronomeTimeSignature.FOUR_FOUR,
        accentDownbeat = true,
      )
    }
    return try {
      val obj = JSONObject(json)
      DeserializedMetronome(
        bpm = obj.optInt("bpm", 112).coerceIn(MetronomeDsp.MIN_BPM, MetronomeDsp.MAX_BPM),
        bpmSynced = obj.optBoolean("bpmSynced", true),
        volume = obj.optDouble("volume", 0.80).toFloat().coerceIn(0f, 1f),
        pitchHz = obj.optDouble("pitchHz", MetronomeDsp.DEFAULT_PITCH_HZ.toDouble())
          .toFloat()
          .coerceIn(MetronomeDsp.MIN_PITCH_HZ, MetronomeDsp.MAX_PITCH_HZ),
        toneStyle = MetronomeTone.entries.firstOrNull { it.name == obj.optString("toneStyle") }
          ?: MetronomeTone.DIGITAL_CLICK,
        timeSignature = MetronomeTimeSignature.entries.firstOrNull { it.name == obj.optString("timeSignature") }
          ?: MetronomeTimeSignature.FOUR_FOUR,
        accentDownbeat = obj.optBoolean("accentDownbeat", true),
      )
    } catch (_: Exception) {
      DeserializedMetronome(
        bpm = 112,
        bpmSynced = true,
        volume = 0.80f,
        pitchHz = MetronomeDsp.DEFAULT_PITCH_HZ,
        toneStyle = MetronomeTone.DIGITAL_CLICK,
        timeSignature = MetronomeTimeSignature.FOUR_FOUR,
        accentDownbeat = true,
      )
    }
  }

  fun serializeEnvelope(envelope: TrackAudioEnvelope): String {
    return envelope.toJsonString()
  }

  fun deserializeEnvelope(spec: String): TrackAudioEnvelope {
    return TrackAudioEnvelope.fromJsonString(spec)
  }
}
