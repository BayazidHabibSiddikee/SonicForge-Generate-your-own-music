package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.model.StudioProject
import com.example.model.StudioProjectCodec
import java.io.File

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
  @PrimaryKey val id: String,
  val name: String,
  val notes: String = "",
  val activeTrackId: String? = null,
  val activeClipTitle: String = "",
  val clipIdsCsv: String = "",
  val selectedPlaylistId: String? = null,
  val playbackPositionMs: Long = 0L,
  val volume: Float = 0.85f,
  val isMuted: Boolean = false,
  val isLooping: Boolean = false,
  val pitchSemitones: Float = 0f,
  val reverbSpecJson: String = "",
  val equalizerSpecJson: String = "",
  val crossfadeSpecJson: String = "",
  val loudnessSpecJson: String = "",
  val activeEnvelopeSpec: String = "",
  val sequencerSpecJson: String = "",
  val metronomeSpecJson: String = "",
  val createdAtEpochMs: Long = System.currentTimeMillis(),
  val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "project_clips",
  primaryKeys = ["projectId", "trackId"],
  foreignKeys = [
    ForeignKey(
      entity = StudioProjectEntity::class,
      parentColumns = ["id"],
      childColumns = ["projectId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = GeneratedTrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["projectId"]),
    Index(value = ["trackId"]),
  ],
)
data class ProjectClipCrossRef(
  val projectId: String,
  val trackId: String,
  val sortOrder: Int = 0,
)

data class StudioProjectWithClips(
  @Embedded val project: StudioProjectEntity,
  @Relation(
    parentColumn = "id",
    entityColumn = "id",
    associateBy = Junction(
      value = ProjectClipCrossRef::class,
      parentColumn = "projectId",
      entityColumn = "trackId",
    ),
  )
  val tracks: List<GeneratedTrackEntity>,
)

fun StudioProjectWithClips.toDomain(requireExistingFiles: Boolean = true): StudioProject {
  val orderedIds = project.clipIdsCsv
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

  val trackMap = tracks
    .filter { !requireExistingFiles || File(it.filePath).exists() }
    .associateBy { it.id }

  val orderedClips = if (orderedIds.isNotEmpty()) {
    val fromOrder = orderedIds.mapNotNull { trackMap[it]?.toDomain() }
    val remaining = trackMap.values
      .filter { it.id !in orderedIds }
      .sortedByDescending { it.createdAtEpochMs }
      .map { it.toDomain() }
    fromOrder + remaining
  } else {
    trackMap.values
      .sortedByDescending { it.createdAtEpochMs }
      .map { it.toDomain() }
  }

  val resolvedClipIds = if (orderedIds.isNotEmpty()) {
    orderedIds
  } else {
    orderedClips.map { it.id }
  }

  val reverb = StudioProjectCodec.deserializeReverb(project.reverbSpecJson)
  val equalizer = StudioProjectCodec.deserializeEqualizer(project.equalizerSpecJson)
  val crossfade = StudioProjectCodec.deserializeCrossfade(project.crossfadeSpecJson)
  val (autoPostGain, loudnessTarget) = StudioProjectCodec.deserializeLoudness(project.loudnessSpecJson)
  val activeEnvelope = StudioProjectCodec.deserializeEnvelope(project.activeEnvelopeSpec)
  val sequencer = StudioProjectCodec.deserializeSequencer(project.sequencerSpecJson)
  val metronome = StudioProjectCodec.deserializeMetronome(project.metronomeSpecJson)

  return StudioProject(
    id = project.id,
    name = project.name,
    notes = project.notes,
    activeTrackId = project.activeTrackId,
    activeClipTitle = project.activeClipTitle,
    clipIds = resolvedClipIds,
    clips = orderedClips,
    selectedPlaylistId = project.selectedPlaylistId,
    playbackPositionMs = project.playbackPositionMs.coerceAtLeast(0L),
    volume = project.volume.coerceIn(0f, 1f),
    isMuted = project.isMuted,
    isLooping = project.isLooping,
    pitchSemitones = project.pitchSemitones,
    reverbSettings = reverb,
    equalizerSettings = equalizer,
    crossfadeSettings = crossfade,
    autoPostGainEnabled = autoPostGain,
    loudnessTarget = loudnessTarget,
    activeEnvelope = activeEnvelope,
    sequencerPattern = sequencer.pattern,
    sequencerBpm = sequencer.bpm,
    sequencerSwingPercent = sequencer.swingPercent,
    sequencerBpmSynced = sequencer.bpmSynced,
    sequencerPresetName = sequencer.presetName,
    metronomeBpm = metronome.bpm,
    metronomeBpmSynced = metronome.bpmSynced,
    metronomeVolume = metronome.volume,
    metronomePitchHz = metronome.pitchHz,
    metronomeToneStyle = metronome.toneStyle,
    metronomeTimeSignature = metronome.timeSignature,
    metronomeAccentDownbeat = metronome.accentDownbeat,
    createdAtEpochMs = project.createdAtEpochMs,
    updatedAtEpochMs = project.updatedAtEpochMs,
  )
}

fun StudioProject.toEntity(): StudioProjectEntity = StudioProjectEntity(
  id = id,
  name = name,
  notes = notes,
  activeTrackId = activeTrackId,
  activeClipTitle = activeClipTitle,
  clipIdsCsv = clipIds.distinct().joinToString(","),
  selectedPlaylistId = selectedPlaylistId,
  playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
  volume = volume.coerceIn(0f, 1f),
  isMuted = isMuted,
  isLooping = isLooping,
  pitchSemitones = pitchSemitones,
  reverbSpecJson = StudioProjectCodec.serializeReverb(reverbSettings),
  equalizerSpecJson = StudioProjectCodec.serializeEqualizer(equalizerSettings),
  crossfadeSpecJson = StudioProjectCodec.serializeCrossfade(crossfadeSettings),
  loudnessSpecJson = StudioProjectCodec.serializeLoudness(autoPostGainEnabled, loudnessTarget),
  activeEnvelopeSpec = StudioProjectCodec.serializeEnvelope(activeEnvelope),
  sequencerSpecJson = StudioProjectCodec.serializeSequencer(
    pattern = sequencerPattern,
    bpm = sequencerBpm,
    swingPercent = sequencerSwingPercent,
    bpmSynced = sequencerBpmSynced,
    presetName = sequencerPresetName,
  ),
  metronomeSpecJson = StudioProjectCodec.serializeMetronome(
    bpm = metronomeBpm,
    bpmSynced = metronomeBpmSynced,
    volume = metronomeVolume,
    pitchHz = metronomePitchHz,
    toneStyle = metronomeToneStyle,
    timeSignature = metronomeTimeSignature,
    accentDownbeat = metronomeAccentDownbeat,
  ),
  createdAtEpochMs = createdAtEpochMs,
  updatedAtEpochMs = updatedAtEpochMs,
)
