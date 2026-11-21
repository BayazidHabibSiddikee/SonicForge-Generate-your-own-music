package com.example.ui

import com.example.audio.DrumPadVoice
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.audio.RecordedDrumHit
import com.example.audio.RecordedNoteEvent

enum class StudioDestination(val testTag: String) {
  STUDIO("nav_studio"),
  PIANO("nav_piano"),
  GUITAR("nav_guitar"),
  DRUMS("nav_drums"),
}

enum class StudioSectionTab(val testTag: String) {
  HISTORY("studio_tab_history"),
  RESOURCES("studio_tab_resources"),
  LIBRARY("studio_tab_library"),
  PROJECTS("studio_tab_projects"),
}

data class PianoStudioState(
  val selectedVoice: PianoVoice = PianoVoice.CONCERT_GRAND,
  val selectedProgression: PianoProgressionPreset = PianoProgressionPreset.POP_CLASSIC,
  val baseOctave: Int = 4,
  val bpm: Int = 96,
  val isRecording: Boolean = false,
  val recordedNotes: List<RecordedNoteEvent> = emptyList(),
  val activeMidiNotes: Set<Int> = emptySet(),
  val isGenerating: Boolean = false,
  val isPreviewing: Boolean = false,
  val lastGeneratedTrackTitle: String? = null,
) {
  val activeMidiNote: Int?
    get() = activeMidiNotes.lastOrNull()
}

data class GuitarStudioState(
  val selectedVoice: GuitarVoice = GuitarVoice.ACOUSTIC_STEEL,
  val selectedChord: GuitarChordShape = GuitarChordShape.C_MAJ,
  val chordSequence: List<GuitarChordShape> = listOf(
    GuitarChordShape.C_MAJ,
    GuitarChordShape.G_MAJ,
    GuitarChordShape.A_MIN,
    GuitarChordShape.F_MAJ,
  ),
  val strumStyle: GuitarStrumStyle = GuitarStrumStyle.ACOUSTIC_STRUM,
  val bpm: Int = 96,
  val isRecording: Boolean = false,
  val recordedPlucks: List<RecordedNoteEvent> = emptyList(),
  val lastPluckedStringIndex: Int? = null,
  val activePluckedCell: Pair<Int, Int>? = null,
  val isGenerating: Boolean = false,
  val isPreviewing: Boolean = false,
  val lastGeneratedTrackTitle: String? = null,
)

data class DrumStudioState(
  val activePads: Set<DrumPadVoice> = emptySet(),
  val isRecordingPads: Boolean = false,
  val recordedHits: List<RecordedDrumHit> = emptyList(),
  val isPreviewingPads: Boolean = false,
  val lastGeneratedTrackTitle: String? = null,
)

data class MergeMixerState(
  val selectedTrackIds: Set<String> = emptySet(),
  val stemGains: Map<String, Float> = emptyMap(),
  val loopShorterStems: Boolean = true,
  val customTitle: String = "",
  val isMerging: Boolean = false,
  val lastStatusMessage: String? = null,
)
