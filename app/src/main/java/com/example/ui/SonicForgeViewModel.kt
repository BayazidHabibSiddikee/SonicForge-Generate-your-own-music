package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TrackAutoTagResult
import com.example.audio.AudioDownloadProgress
import com.example.audio.AudioEngine
import com.example.audio.AudioEngineState
import com.example.audio.AudioExportBitrateOption
import com.example.audio.AudioExportConfig
import com.example.audio.AudioExportFormat
import com.example.audio.AudioPlaybackState
import com.example.audio.AudioPlayerController
import com.example.audio.DrumKitPreset
import com.example.audio.DrumPadVoice
import com.example.audio.EqPreset
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.HfMusicSpace
import com.example.audio.InstrumentSynthDsp
import com.example.audio.LiveInstrumentAudioEngine
import com.example.audio.LoudnessTarget
import com.example.audio.MetronomeEngine
import com.example.audio.MetronomeState
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import com.example.audio.MixStemSpec
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.audio.RawBufferDiagnostics
import com.example.audio.RecordedNoteEvent
import com.example.audio.ReverbEngineMode
import com.example.audio.ReverbPreset
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.StepSequencerDsp
import com.example.audio.StepSequencerEngine
import com.example.audio.StepSequencerState
import com.example.audio.StudioSoundResource
import com.example.audio.TrackAudioEnvelope
import com.example.audio.VolumeEnvelopeDsp
import com.example.audio.WaveformInteractionMode
import com.example.audio.GuitarProgressionPreset
import com.example.audio.RecordedDrumHit
import com.example.data.GeneratedTrackRepository
import com.example.data.StudioProjectRepository
import com.example.data.ThemePreferencesRepository
import com.example.data.TrackHistoryRepository
import com.example.data.local.TrackHistoryItem
import com.example.data.local.TrackHistoryTypes
import com.example.model.BulkRenameConfig
import com.example.model.GeneratedTrack
import com.example.model.Playlist
import com.example.model.StudioProject
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NotificationPermissionStatus {
  GRANTED,
  NEEDS_REQUEST,
  DENIED,
}

enum class GenerationEngine(val testTagSuffix: String) {
  HF_AUDIO_LDM("audioldm"),
  HF_MUSICGEN("musicgen"),
  HF_STABLE_AUDIO("stable_audio"),
  FOURIER_SYNTH("fourier"),
}

data class SonicForgeUiState(
  val tracks: List<GeneratedTrack> = emptyList(),
  val allTracks: List<GeneratedTrack> = emptyList(),
  val availableCategories: List<String> = listOf(GeneratedTrackRepository.ALL_CATEGORIES),
  val selectedCategory: String = GeneratedTrackRepository.ALL_CATEGORIES,
  val showFavoritesOnly: Boolean = false,
  val favoriteTrackCount: Int = 0,
  val searchQuery: String = "",
  val playlists: List<Playlist> = emptyList(),
  val selectedPlaylistId: String? = null,
  val showCreatePlaylistDialog: Boolean = false,
  val preselectedPlaylistTrackIds: Set<String> = emptySet(),
  val playlistAssignmentTrack: GeneratedTrack? = null,
  val editingTrack: GeneratedTrack? = null,
  val trimmingTrack: GeneratedTrack? = null,
  val exportingTrack: GeneratedTrack? = null,
  val promptInput: String = "",
  val selectedEngine: GenerationEngine = GenerationEngine.HF_MUSICGEN,
  val activeDestination: StudioDestination = StudioDestination.STUDIO,
  val studioSectionTab: StudioSectionTab = StudioSectionTab.HISTORY,
  val pianoState: PianoStudioState = PianoStudioState(),
  val guitarState: GuitarStudioState = GuitarStudioState(),
  val drumState: DrumStudioState = DrumStudioState(),
  val mergeMixer: MergeMixerState = MergeMixerState(),
  val aiProducer: AiProducerState = AiProducerState(),
  val offlineMusicState: OfflineMusicStudioState = OfflineMusicStudioState(),
  val roomStorageExport: RoomStorageExportState = RoomStorageExportState(),
  val trackHistory: List<TrackHistoryItem> = emptyList(),
  val totalHistoryCount: Int = 0,
  val historyTypeFilter: String = TrackHistoryTypes.ALL,
  val historySearchQuery: String = "",
  val isQuickSavingMp3: Boolean = false,
  val quickMp3StatusMessage: String? = null,
  val generatingResourceId: String? = null,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val isInitializing: Boolean = true,
  val isGenerating: Boolean = false,
  val isTrimming: Boolean = false,
  val isExporting: Boolean = false,
  val isApplyingReverb: Boolean = false,
  val loudnessTarget: LoudnessTarget = LoudnessTarget.STREAMING_14,
  val autoPostGainEnabled: Boolean = true,
  val isNormalizing: Boolean = false,
  val isSelectionMode: Boolean = false,
  val selectedTrackIds: Set<String> = emptySet(),
  val showBulkRenameDialog: Boolean = false,
  val isBulkRenaming: Boolean = false,
  val latestAutoTagResult: TrackAutoTagResult? = null,
  val isSuggestingTags: Boolean = false,
  val showUrlDownloadDialog: Boolean = false,
  val isDownloadingUrl: Boolean = false,
  val downloadProgress: AudioDownloadProgress? = null,
  val downloadStatusMessage: String? = null,
  val downloadErrorMessage: String? = null,
  val exportStatusMessage: String? = null,
  val exportErrorMessage: String? = null,
  val errorMessage: String? = null,
  val notificationPermission: NotificationPermissionStatus = NotificationPermissionStatus.GRANTED,
  val activeBufferDiagnostics: RawBufferDiagnostics? = null,
  val playback: AudioPlaybackState = AudioPlaybackState(),
  val exportConfig: AudioExportConfig = AudioExportConfig(),
  val waveformInteractionMode: WaveformInteractionMode = WaveformInteractionMode.SCRUB,
  val showBottomWaveformBar: Boolean = true,
  val stepSequencer: StepSequencerState = StepSequencerState(),
  val metronome: MetronomeState = MetronomeState(),
  val projects: List<StudioProject> = emptyList(),
  val activeProjectId: String? = null,
  val showSaveProjectDialog: Boolean = false,
  val isSavingProject: Boolean = false,
  val projectStatusMessage: String? = null,
  val clipAudioState: AudioEngineState = AudioEngineState(),
) {
  val selectedTracksForBulkRename: List<GeneratedTrack>
    get() = tracks.filter { it.id in selectedTrackIds }
      .ifEmpty { allTracks.filter { it.id in selectedTrackIds } }
  val selectedPlaylist: Playlist?
    get() = playlists.firstOrNull { it.id == selectedPlaylistId }

  val currentTrack: GeneratedTrack?
    get() = allTracks.firstOrNull { it.id == playback.currentTrackId }
      ?: tracks.firstOrNull()
      ?: allTracks.firstOrNull()

  val canSkipPrevious: Boolean
    get() = allTracks.size > 1

  val canSkipNext: Boolean
    get() = allTracks.size > 1
}

private data class PlaylistUiBundle(
  val playlists: List<Playlist>,
  val selectedPlaylistId: String?,
  val showCreatePlaylistDialog: Boolean,
  val preselectedTrackIds: Set<String>,
  val assignmentTrack: GeneratedTrack?,
)

private data class LoudnessUiBundle(
  val target: LoudnessTarget,
  val autoPostGainEnabled: Boolean,
  val isNormalizing: Boolean,
  val exportConfig: AudioExportConfig,
)

private data class BulkRenameUiBundle(
  val isSelectionMode: Boolean,
  val selectedTrackIds: Set<String>,
  val showBulkRenameDialog: Boolean,
  val isBulkRenaming: Boolean,
)

private data class AiAndDownloadUiBundle(
  val latestAutoTagResult: TrackAutoTagResult?,
  val isSuggestingTags: Boolean,
  val showUrlDownloadDialog: Boolean,
  val isDownloadingUrl: Boolean,
  val downloadProgress: AudioDownloadProgress?,
  val downloadStatusMessage: String?,
  val downloadErrorMessage: String?,
)

private data class VisualPrefsBundle(
  val themeMode: ThemeMode,
  val showBottomWaveformBar: Boolean,
  val waveformInteractionMode: WaveformInteractionMode,
)

private data class ProjectUiBundle(
  val projects: List<StudioProject>,
  val activeProjectId: String?,
  val showSaveProjectDialog: Boolean,
  val isSavingProject: Boolean,
  val projectStatusMessage: String?,
  val clipAudioState: AudioEngineState = AudioEngineState(),
)

private data class StudioInstrumentsBundle(
  val activeDestination: StudioDestination,
  val studioSectionTab: StudioSectionTab,
  val pianoState: PianoStudioState,
  val guitarState: GuitarStudioState,
  val drumState: DrumStudioState,
  val mergeMixer: MergeMixerState,
  val isQuickSavingMp3: Boolean,
  val quickMp3StatusMessage: String?,
  val generatingResourceId: String?,
  val trackHistory: List<TrackHistoryItem>,
  val totalHistoryCount: Int,
  val historyTypeFilter: String,
  val historySearchQuery: String,
  val aiProducer: AiProducerState,
  val offlineMusicState: OfflineMusicStudioState,
  val roomStorageExport: RoomStorageExportState,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SonicForgeViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = GeneratedTrackRepository(application.applicationContext)
  private val historyRepository = TrackHistoryRepository(application.applicationContext)
  private val projectRepository = StudioProjectRepository(application.applicationContext)
  private val themePreferences = ThemePreferencesRepository(application.applicationContext)
  private val playerController = AudioPlayerController.getInstance(application.applicationContext)
  private val stepSequencerEngine = StepSequencerEngine()
  private val metronomeEngine = MetronomeEngine()
  private val studioClipEngine = AudioEngine()
  private val liveInstrumentEngine = LiveInstrumentAudioEngine(studioClipEngine)
  private val geminiMusicArranger: com.example.ai.MusicArrangerService = com.example.ai.GeminiMusicArranger()
  private val offlineMusicArranger: com.example.ai.MusicArrangerService = com.example.ai.OfflineMusicArranger()
  val clipAudioEngineState: StateFlow<AudioEngineState> = studioClipEngine.state

  private val _promptInput = MutableStateFlow("")
  private val _selectedEngine = MutableStateFlow(GenerationEngine.HF_MUSICGEN)
  private val _activeDestination = MutableStateFlow(StudioDestination.STUDIO)
  private val _studioSectionTab = MutableStateFlow(StudioSectionTab.HISTORY)
  private val _pianoState = MutableStateFlow(PianoStudioState())
  private val _guitarState = MutableStateFlow(GuitarStudioState())
  private val _drumState = MutableStateFlow(DrumStudioState())
  private val _mergeMixerState = MutableStateFlow(MergeMixerState())
  private val _aiProducerState = MutableStateFlow(AiProducerState())
  private val _offlineMusicState = MutableStateFlow(OfflineMusicStudioState())
  private val _roomStorageExportState = MutableStateFlow(RoomStorageExportState())
  private var pendingRoomExportTrackIds: Set<String>? = null
  private val _historyTypeFilter = MutableStateFlow(TrackHistoryTypes.ALL)
  private val _historySearchQuery = MutableStateFlow("")
  private val _isQuickSavingMp3 = MutableStateFlow(false)
  private val _quickMp3StatusMessage = MutableStateFlow<String?>(null)
  private val _generatingResourceId = MutableStateFlow<String?>(null)
  private var pianoRecStartMs: Long = 0L
  private var guitarRecStartMs: Long = 0L
  private var drumRecStartMs: Long = 0L
  private var guitarCellFlashJob: Job? = null
  private val _selectedCategory = MutableStateFlow(GeneratedTrackRepository.ALL_CATEGORIES)
  private val _showFavoritesOnly = MutableStateFlow(false)
  private val _searchQuery = MutableStateFlow("")
  private val _selectedPlaylistId = MutableStateFlow<String?>(null)
  private val _showCreatePlaylistDialog = MutableStateFlow(false)
  private val _preselectedPlaylistTrackIds = MutableStateFlow<Set<String>>(emptySet())
  private val _playlistAssignmentTrack = MutableStateFlow<GeneratedTrack?>(null)
  private val _editingTrack = MutableStateFlow<GeneratedTrack?>(null)
  private val _trimmingTrack = MutableStateFlow<GeneratedTrack?>(null)
  private val _exportingTrack = MutableStateFlow<GeneratedTrack?>(null)
  private val _isInitializing = MutableStateFlow(true)
  private val _isGenerating = MutableStateFlow(false)
  private val _isTrimming = MutableStateFlow(false)
  private val _isExporting = MutableStateFlow(false)
  private val _isApplyingReverb = MutableStateFlow(false)
  private val _loudnessTarget = MutableStateFlow(LoudnessTarget.STREAMING_14)
  private val _autoPostGainEnabled = MutableStateFlow(true)
  private val _isNormalizing = MutableStateFlow(false)
  private val _isSelectionMode = MutableStateFlow(false)
  private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
  private val _showBulkRenameDialog = MutableStateFlow(false)
  private val _isBulkRenaming = MutableStateFlow(false)
  private val _latestAutoTagResult = MutableStateFlow<TrackAutoTagResult?>(null)
  private val _isSuggestingTags = MutableStateFlow(false)
  private val _showUrlDownloadDialog = MutableStateFlow(false)
  private val _isDownloadingUrl = MutableStateFlow(false)
  private val _downloadProgress = MutableStateFlow<AudioDownloadProgress?>(null)
  private val _downloadStatusMessage = MutableStateFlow<String?>(null)
  private val _downloadErrorMessage = MutableStateFlow<String?>(null)
  private val _exportStatusMessage = MutableStateFlow<String?>(null)
  private val _exportErrorMessage = MutableStateFlow<String?>(null)
  private val _errorMessage = MutableStateFlow<String?>(null)
  private val _notificationPermission = MutableStateFlow(NotificationPermissionStatus.GRANTED)
  private val _activeBufferDiagnostics = MutableStateFlow<RawBufferDiagnostics?>(null)
  private val _waveformInteractionMode = MutableStateFlow(WaveformInteractionMode.SCRUB)
  private val _exportConfig = MutableStateFlow(AudioExportConfig())
  private val _activeProjectId = MutableStateFlow<String?>(null)
  private val _showSaveProjectDialog = MutableStateFlow(false)
  private val _isSavingProject = MutableStateFlow(false)
  private val _projectStatusMessage = MutableStateFlow<String?>(null)
  private var envelopeSaveJob: Job? = null

  private val visualPrefsBundleFlow = combine(
    themePreferences.themeModeFlow,
    themePreferences.showBottomWaveformFlow,
    _waveformInteractionMode,
  ) { themeMode, showBottomBar, interactionMode ->
    VisualPrefsBundle(
      themeMode = themeMode,
      showBottomWaveformBar = showBottomBar,
      waveformInteractionMode = interactionMode,
    )
  }

  private val loudnessUiBundleFlow = combine(
    _loudnessTarget,
    _autoPostGainEnabled,
    _isNormalizing,
    _exportConfig,
  ) { target, autoPostGain, normalizing, exportConfig ->
    LoudnessUiBundle(
      target = target,
      autoPostGainEnabled = autoPostGain,
      isNormalizing = normalizing,
      exportConfig = exportConfig,
    )
  }

  private val bulkRenameUiBundleFlow = combine(
    _isSelectionMode,
    _selectedTrackIds,
    _showBulkRenameDialog,
    _isBulkRenaming,
  ) { selectionMode, selectedIds, showDialog, isRenaming ->
    BulkRenameUiBundle(
      isSelectionMode = selectionMode,
      selectedTrackIds = selectedIds,
      showBulkRenameDialog = showDialog,
      isBulkRenaming = isRenaming,
    )
  }

  private val aiTagBundleFlow = combine(
    _latestAutoTagResult,
    _isSuggestingTags,
  ) { autoTag, suggesting ->
    autoTag to suggesting
  }

  private val urlDownloadBundleFlow = combine(
    _showUrlDownloadDialog,
    _isDownloadingUrl,
    _downloadProgress,
    _downloadStatusMessage,
    _downloadErrorMessage,
  ) { showDialog, downloading, progress, statusMsg, errorMsg ->
    Triple(showDialog to downloading, progress, statusMsg to errorMsg)
  }

  private val aiAndDownloadUiBundleFlow = combine(
    aiTagBundleFlow,
    urlDownloadBundleFlow,
  ) { (autoTag, suggesting), (dialogPair, progress, msgPair) ->
    AiAndDownloadUiBundle(
      latestAutoTagResult = autoTag,
      isSuggestingTags = suggesting,
      showUrlDownloadDialog = dialogPair.first,
      isDownloadingUrl = dialogPair.second,
      downloadProgress = progress,
      downloadStatusMessage = msgPair.first,
      downloadErrorMessage = msgPair.second,
    )
  }

  private val playlistsFlow = repository.playlistsFlow

  private val allTracksFlow = repository.tracksFlow.onEach { list ->
    if (_selectedPlaylistId.value == null) {
      playerController.updatePlaylist(list)
    }
  }

  private val baseFilteredTracksFlow = combine(
    _selectedCategory,
    _searchQuery,
    _showFavoritesOnly,
  ) { category, query, favoritesOnly ->
    Triple(category, query, favoritesOnly)
  }.flatMapLatest { (category, query, favoritesOnly) ->
    repository.observeTracksFiltered(
      category = category,
      query = query,
      favoritesOnly = favoritesOnly,
    )
  }

  private val filteredTracksFlow = combine(
    baseFilteredTracksFlow,
    playlistsFlow,
    _selectedPlaylistId,
  ) { tracks, playlists, selectedPlaylistId ->
    if (selectedPlaylistId == null) {
      tracks
    } else {
      val targetPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
      val memberIds = targetPlaylist?.tracks?.map { it.id }?.toSet() ?: emptySet()
      tracks.filter { it.id in memberIds }
    }
  }

  private val playlistUiBundleFlow = combine(
    playlistsFlow,
    _selectedPlaylistId,
    _showCreatePlaylistDialog,
    _preselectedPlaylistTrackIds,
    _playlistAssignmentTrack,
  ) { playlists, selectedId, showCreate, preselected, assignTrack ->
    val activeSelectedId = if (selectedId != null && playlists.any { it.id == selectedId }) {
      selectedId
    } else {
      null
    }
    PlaylistUiBundle(
      playlists = playlists,
      selectedPlaylistId = activeSelectedId,
      showCreatePlaylistDialog = showCreate,
      preselectedTrackIds = preselected,
      assignmentTrack = assignTrack,
    )
  }

  private val categoriesFlow = repository.categoriesFlow

  private val projectUiBundleFlow = combine(
    combine(
      projectRepository.projectsFlow,
      _activeProjectId,
      _showSaveProjectDialog,
      _isSavingProject,
      _projectStatusMessage,
    ) { projects, activeId, showDialog, isSaving, statusMsg ->
      ProjectUiBundle(
        projects = projects,
        activeProjectId = activeId?.takeIf { id -> projects.any { it.id == id } },
        showSaveProjectDialog = showDialog,
        isSavingProject = isSaving,
        projectStatusMessage = statusMsg,
      )
    },
    studioClipEngine.state,
  ) { bundle, clipAudioState ->
    bundle.copy(clipAudioState = clipAudioState)
  }

  private val filteredHistoryFlow = combine(
    _historyTypeFilter,
    _historySearchQuery,
  ) { typeFilter, query ->
    typeFilter to query
  }.flatMapLatest { (typeFilter, query) ->
    historyRepository.observeFilteredHistory(
      instrumentType = typeFilter,
      query = query,
    )
  }

  private val historyUiBundleFlow = combine(
    filteredHistoryFlow,
    historyRepository.historyCountFlow,
    _historyTypeFilter,
    _historySearchQuery,
  ) { items, count, typeFilter, query ->
    listOf(items, count, typeFilter, query)
  }

  private val navAndInstrumentsBundleFlow = combine(
    _activeDestination,
    _studioSectionTab,
    _pianoState,
    _guitarState,
    _mergeMixerState,
  ) { dest, tab, piano, guitar, merge ->
    listOf(dest, tab, piano, guitar, merge)
  }

  private val quickStatusBundleFlow = combine(
    _isQuickSavingMp3,
    _quickMp3StatusMessage,
    _generatingResourceId,
    _drumState,
  ) { savingMp3, statusMsg, resId, drumState ->
    listOf(savingMp3, statusMsg, resId, drumState)
  }

  private val aiAndOfflineMusicBundleFlow = combine(
    _aiProducerState,
    _offlineMusicState,
    _roomStorageExportState,
  ) { ai, offlineMusic, roomExport ->
    Triple(ai, offlineMusic, roomExport)
  }

  private val studioInstrumentsBundleFlow = combine(
    navAndInstrumentsBundleFlow,
    quickStatusBundleFlow,
    historyUiBundleFlow,
    aiAndOfflineMusicBundleFlow,
  ) { navList, statusList, historyList, aiOfflineTriple ->
    @Suppress("UNCHECKED_CAST")
    StudioInstrumentsBundle(
      activeDestination = navList[0] as StudioDestination,
      studioSectionTab = navList[1] as StudioSectionTab,
      pianoState = navList[2] as PianoStudioState,
      guitarState = navList[3] as GuitarStudioState,
      drumState = statusList[3] as DrumStudioState,
      mergeMixer = navList[4] as MergeMixerState,
      isQuickSavingMp3 = statusList[0] as Boolean,
      quickMp3StatusMessage = statusList[1] as String?,
      generatingResourceId = statusList[2] as String?,
      trackHistory = historyList[0] as List<TrackHistoryItem>,
      totalHistoryCount = historyList[1] as Int,
      historyTypeFilter = historyList[2] as String,
      historySearchQuery = historyList[3] as String,
      aiProducer = aiOfflineTriple.first,
      offlineMusicState = aiOfflineTriple.second,
      roomStorageExport = aiOfflineTriple.third,
    )
  }

  val uiState: StateFlow<SonicForgeUiState> =
    combine(
      filteredTracksFlow,
      allTracksFlow,
      categoriesFlow,
      _selectedCategory,
      _showFavoritesOnly,
      _searchQuery,
      playlistUiBundleFlow,
      _editingTrack,
      _trimmingTrack,
      _exportingTrack,
      _promptInput,
      _selectedEngine,
      visualPrefsBundleFlow,
      _isInitializing,
      _isGenerating,
      _isTrimming,
      _isExporting,
      _isApplyingReverb,
      _exportStatusMessage,
      _exportErrorMessage,
      _errorMessage,
      _notificationPermission,
      _activeBufferDiagnostics,
      playerController.state,
      loudnessUiBundleFlow,
      bulkRenameUiBundleFlow,
      aiAndDownloadUiBundleFlow,
      stepSequencerEngine.state,
      metronomeEngine.state,
      projectUiBundleFlow,
      studioInstrumentsBundleFlow,
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      val allTracks = values[1] as List<GeneratedTrack>
      @Suppress("UNCHECKED_CAST")
      val distinctCategories = values[2] as List<String>
      val categoryChips = listOf(GeneratedTrackRepository.ALL_CATEGORIES) + distinctCategories
      val playlistBundle = values[6] as PlaylistUiBundle
      val visualPrefsBundle = values[12] as VisualPrefsBundle
      val loudnessBundle = values[24] as LoudnessUiBundle
      val bulkRenameBundle = values[25] as BulkRenameUiBundle
      val aiAndDownloadBundle = values[26] as AiAndDownloadUiBundle
      val projectBundle = values[29] as ProjectUiBundle
      val studioBundle = values[30] as StudioInstrumentsBundle
      val validTrackIds = allTracks.map { it.id }.toSet()
      val sanitizedMergeIds = studioBundle.mergeMixer.selectedTrackIds.intersect(validTrackIds)
      SonicForgeUiState(
        tracks = values[0] as List<GeneratedTrack>,
        allTracks = allTracks,
        availableCategories = categoryChips.distinct(),
        selectedCategory = values[3] as String,
        showFavoritesOnly = values[4] as Boolean,
        favoriteTrackCount = allTracks.count { it.isFavorite },
        searchQuery = values[5] as String,
        playlists = playlistBundle.playlists,
        selectedPlaylistId = playlistBundle.selectedPlaylistId,
        showCreatePlaylistDialog = playlistBundle.showCreatePlaylistDialog,
        preselectedPlaylistTrackIds = playlistBundle.preselectedTrackIds,
        playlistAssignmentTrack = playlistBundle.assignmentTrack,
        editingTrack = values[7] as GeneratedTrack?,
        trimmingTrack = values[8] as GeneratedTrack?,
        exportingTrack = values[9] as GeneratedTrack?,
        promptInput = values[10] as String,
        selectedEngine = values[11] as GenerationEngine,
        activeDestination = studioBundle.activeDestination,
        studioSectionTab = studioBundle.studioSectionTab,
        pianoState = studioBundle.pianoState,
        guitarState = studioBundle.guitarState,
        drumState = studioBundle.drumState,
        mergeMixer = studioBundle.mergeMixer.copy(selectedTrackIds = sanitizedMergeIds),
        aiProducer = studioBundle.aiProducer,
        offlineMusicState = studioBundle.offlineMusicState,
        roomStorageExport = studioBundle.roomStorageExport,
        trackHistory = studioBundle.trackHistory,
        totalHistoryCount = studioBundle.totalHistoryCount,
        historyTypeFilter = studioBundle.historyTypeFilter,
        historySearchQuery = studioBundle.historySearchQuery,
        isQuickSavingMp3 = studioBundle.isQuickSavingMp3,
        quickMp3StatusMessage = studioBundle.quickMp3StatusMessage,
        generatingResourceId = studioBundle.generatingResourceId,
        themeMode = visualPrefsBundle.themeMode,
        isInitializing = values[13] as Boolean,
        isGenerating = values[14] as Boolean,
        isTrimming = values[15] as Boolean,
        isExporting = values[16] as Boolean,
        isApplyingReverb = values[17] as Boolean,
        loudnessTarget = loudnessBundle.target,
        autoPostGainEnabled = loudnessBundle.autoPostGainEnabled,
        isNormalizing = loudnessBundle.isNormalizing,
        isSelectionMode = bulkRenameBundle.isSelectionMode,
        selectedTrackIds = bulkRenameBundle.selectedTrackIds.intersect(validTrackIds),
        showBulkRenameDialog = bulkRenameBundle.showBulkRenameDialog,
        isBulkRenaming = bulkRenameBundle.isBulkRenaming,
        latestAutoTagResult = aiAndDownloadBundle.latestAutoTagResult,
        isSuggestingTags = aiAndDownloadBundle.isSuggestingTags,
        showUrlDownloadDialog = aiAndDownloadBundle.showUrlDownloadDialog,
        isDownloadingUrl = aiAndDownloadBundle.isDownloadingUrl,
        downloadProgress = aiAndDownloadBundle.downloadProgress,
        downloadStatusMessage = aiAndDownloadBundle.downloadStatusMessage,
        downloadErrorMessage = aiAndDownloadBundle.downloadErrorMessage,
        exportStatusMessage = values[18] as String?,
        exportErrorMessage = values[19] as String?,
        errorMessage = values[20] as String?,
        notificationPermission = values[21] as NotificationPermissionStatus,
        activeBufferDiagnostics = values[22] as RawBufferDiagnostics?,
        playback = values[23] as AudioPlaybackState,
        exportConfig = loudnessBundle.exportConfig,
        waveformInteractionMode = visualPrefsBundle.waveformInteractionMode,
        showBottomWaveformBar = visualPrefsBundle.showBottomWaveformBar,
        stepSequencer = values[27] as StepSequencerState,
        metronome = values[28] as MetronomeState,
        projects = projectBundle.projects,
        activeProjectId = projectBundle.activeProjectId,
        showSaveProjectDialog = projectBundle.showSaveProjectDialog,
        isSavingProject = projectBundle.isSavingProject,
        projectStatusMessage = projectBundle.projectStatusMessage,
        clipAudioState = projectBundle.clipAudioState,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = SonicForgeUiState(),
    )

  init {
    viewModelScope.launch {
      combine(
        themePreferences.autoPostGainEnabledFlow,
        themePreferences.loudnessTargetFlow,
      ) { autoEnabled, target -> autoEnabled to target }
        .distinctUntilChanged()
        .collect { (autoEnabled, target) ->
          _autoPostGainEnabled.value = autoEnabled
          _loudnessTarget.value = target
          playerController.setAutoPostGain(autoEnabled, target.lufs)
          _exportConfig.value = _exportConfig.value.copy(
            normalizeOutputGain = autoEnabled,
            targetOutputLufs = target.lufs,
          )
        }
    }

    viewModelScope.launch {
      try {
        val loaded = repository.ensureSeeded()
        historyRepository.backfillFromExistingTracksIfEmpty()
        playerController.updatePlaylist(loaded)
        loaded.firstOrNull()?.let { firstTrack ->
          if (playerController.state.value.currentTrackId == null) {
            playerController.loadTrack(firstTrack, autoPlay = false)
            refreshBufferDiagnostics(firstTrack)
          }
        }
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to load saved tracks."
      } finally {
        _isInitializing.value = false
      }
    }

    viewModelScope.launch {
      playerController.state
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .collect { activeId ->
          if (activeId != null) {
            repository.getTrackById(activeId)?.let { activeTrack ->
              refreshBufferDiagnostics(activeTrack)
            }
          }
        }
    }

    viewModelScope.launch {
      combine(
        playerController.state,
        _activeBufferDiagnostics,
        allTracksFlow,
      ) { playback, diagnostics, allTracks ->
        val activeTrack = allTracks.firstOrNull { it.id == playback.currentTrackId }
          ?: allTracks.firstOrNull()
        when {
          playback.liveBpm in 55..180 -> playback.liveBpm to "Live Detected"
          diagnostics != null && diagnostics.detectedBpm in 55..180 ->
            diagnostics.detectedBpm to "Buffer Detected"
          activeTrack != null && activeTrack.bpm in 55..180 ->
            activeTrack.bpm to "Track Tempo"
          else -> 0 to ""
        }
      }
        .distinctUntilChanged()
        .collect { (detectedBpm, sourceLabel) ->
          stepSequencerEngine.updateDetectedAppBpm(detectedBpm, sourceLabel)
        }
    }

    viewModelScope.launch {
      combine(
        playerController.state,
        _activeBufferDiagnostics,
        allTracksFlow,
        stepSequencerEngine.state,
      ) { playback, diagnostics, allTracks, sequencer ->
        val activeTrack = allTracks.firstOrNull { it.id == playback.currentTrackId }
          ?: allTracks.firstOrNull()
        when {
          sequencer.isPlaying -> sequencer.bpm to "Sequencer"
          playback.liveBpm in 55..180 -> playback.liveBpm to "Live Detected"
          !sequencer.isBpmSynced -> sequencer.bpm to "Sequencer"
          diagnostics != null && diagnostics.detectedBpm in 55..180 ->
            diagnostics.detectedBpm to "Buffer Detected"
          activeTrack != null && activeTrack.bpm in 55..180 ->
            activeTrack.bpm to "Track Tempo"
          else -> sequencer.bpm to "Sequencer"
        }
      }
        .distinctUntilChanged()
        .collect { (activeBpm, sourceLabel) ->
          metronomeEngine.updateSyncedAppBpm(activeBpm, sourceLabel)
        }
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      themePreferences.setThemeMode(mode)
    }
  }

  fun setBottomWaveformVisible(visible: Boolean) {
    viewModelScope.launch {
      themePreferences.setShowBottomWaveform(visible)
    }
  }

  fun selectWaveformInteractionMode(mode: WaveformInteractionMode) {
    _waveformInteractionMode.value = mode
  }

  fun paintTrackEnvelopePoint(fraction: Float, gain: Float) {
    val activeTrack = uiState.value.currentTrack ?: return
    val currentEnvelope = if (playerController.state.value.currentTrackId == activeTrack.id) {
      playerController.state.value.activeEnvelope
    } else {
      activeTrack.audioEnvelope
    }
    val updated = VolumeEnvelopeDsp.paintEnvelopePoint(currentEnvelope, fraction, gain)
    playerController.updateActiveTrackEnvelope(activeTrack.id, updated)
    envelopeSaveJob?.cancel()
    envelopeSaveJob = viewModelScope.launch {
      delay(160L)
      repository.updateTrackAudioEnvelope(activeTrack.id, updated)
    }
  }

  fun muteTrackSegment(startFraction: Float, endFraction: Float) {
    val activeTrack = uiState.value.currentTrack ?: return
    val currentEnvelope = if (playerController.state.value.currentTrackId == activeTrack.id) {
      playerController.state.value.activeEnvelope
    } else {
      activeTrack.audioEnvelope
    }
    val updated = VolumeEnvelopeDsp.addOrMergeMutedSegment(currentEnvelope, startFraction, endFraction)
    playerController.updateActiveTrackEnvelope(activeTrack.id, updated)
    envelopeSaveJob?.cancel()
    viewModelScope.launch {
      repository.updateTrackAudioEnvelope(activeTrack.id, updated)
    }
  }

  fun toggleMuteAtTap(fraction: Float) {
    val activeTrack = uiState.value.currentTrack ?: return
    val currentEnvelope = if (playerController.state.value.currentTrackId == activeTrack.id) {
      playerController.state.value.activeEnvelope
    } else {
      activeTrack.audioEnvelope
    }
    val updated = VolumeEnvelopeDsp.toggleMuteAtTap(currentEnvelope, fraction)
    playerController.updateActiveTrackEnvelope(activeTrack.id, updated)
    envelopeSaveJob?.cancel()
    viewModelScope.launch {
      repository.updateTrackAudioEnvelope(activeTrack.id, updated)
    }
  }

  fun resetTrackAudioEnvelope() {
    val activeTrack = uiState.value.currentTrack ?: return
    val cleared = TrackAudioEnvelope()
    playerController.updateActiveTrackEnvelope(activeTrack.id, cleared)
    envelopeSaveJob?.cancel()
    viewModelScope.launch {
      repository.updateTrackAudioEnvelope(activeTrack.id, cleared)
    }
  }

  fun updateNotificationPermission(status: NotificationPermissionStatus) {
    _notificationPermission.value = status
  }

  fun onPromptChanged(newPrompt: String) {
    _promptInput.value = newPrompt
    if (_errorMessage.value != null) {
      _errorMessage.value = null
    }
  }

  fun selectCategory(category: String) {
    _selectedCategory.value = category
  }

  fun setShowFavoritesOnly(favoritesOnly: Boolean) {
    _showFavoritesOnly.value = favoritesOnly
  }

  fun toggleShowFavoritesOnly() {
    _showFavoritesOnly.value = !_showFavoritesOnly.value
  }

  fun toggleTrackFavorite(track: GeneratedTrack) {
    viewModelScope.launch {
      repository.setTrackFavorite(trackId = track.id, isFavorite = !track.isFavorite)
    }
  }

  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
  }

  fun selectPlaylistFilter(playlistId: String?) {
    _selectedPlaylistId.value = playlistId
    if (playlistId == null) {
      playerController.updatePlaylist(uiState.value.allTracks)
    } else {
      val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId }
      if (playlist != null) {
        playerController.updatePlaylist(playlist.tracks)
      }
    }
  }

  fun openCreatePlaylistDialog(preselectedTrackId: String? = null) {
    _playlistAssignmentTrack.value = null
    _preselectedPlaylistTrackIds.value = if (preselectedTrackId != null) {
      setOf(preselectedTrackId)
    } else {
      emptySet()
    }
    _showCreatePlaylistDialog.value = true
  }

  fun dismissCreatePlaylistDialog() {
    _showCreatePlaylistDialog.value = false
    _preselectedPlaylistTrackIds.value = emptySet()
  }

  fun createPlaylist(name: String, description: String, trackIds: List<String>) {
    if (name.trim().isEmpty()) return
    viewModelScope.launch {
      val created = repository.createPlaylist(
        name = name,
        description = description,
        initialTrackIds = trackIds,
      )
      _showCreatePlaylistDialog.value = false
      _preselectedPlaylistTrackIds.value = emptySet()
      _selectedPlaylistId.value = created.id
      if (created.tracks.isNotEmpty()) {
        playerController.updatePlaylist(created.tracks)
      }
    }
  }

  fun deletePlaylist(playlistId: String) {
    viewModelScope.launch {
      if (_selectedPlaylistId.value == playlistId) {
        _selectedPlaylistId.value = null
        playerController.updatePlaylist(uiState.value.allTracks)
      }
      repository.deletePlaylist(playlistId)
    }
  }

  fun openTrackPlaylistsDialog(track: GeneratedTrack) {
    _playlistAssignmentTrack.value = track
  }

  fun dismissTrackPlaylistsDialog() {
    _playlistAssignmentTrack.value = null
  }

  fun setTrackPlaylistMembership(playlistId: String, trackId: String, isMember: Boolean) {
    viewModelScope.launch {
      repository.setTrackPlaylistMembership(
        playlistId = playlistId,
        trackId = trackId,
        isMember = isMember,
      )
    }
  }

  fun playPlaylist(playlist: Playlist) {
    val firstTrack = playlist.tracks.firstOrNull() ?: return
    _selectedPlaylistId.value = playlist.id
    playerController.updatePlaylist(playlist.tracks)
    playerController.loadTrack(firstTrack, autoPlay = true)
    refreshBufferDiagnostics(firstTrack)
  }

  fun openEditOrganizationDialog(track: GeneratedTrack) {
    _editingTrack.value = track
  }

  fun dismissEditOrganizationDialog() {
    _editingTrack.value = null
  }

  fun saveTrackOrganization(trackId: String, category: String, tags: List<String>) {
    viewModelScope.launch {
      repository.updateTrackOrganization(trackId = trackId, category = category, tags = tags)
      _editingTrack.value = null
    }
  }

  fun openTrimDialog(track: GeneratedTrack) {
    _trimmingTrack.value = track
    _errorMessage.value = null
  }

  fun dismissTrimDialog() {
    if (!_isTrimming.value) {
      _trimmingTrack.value = null
    }
  }

  fun confirmTrimTrack(trackId: String, startMs: Long, endMs: Long, saveAsCopy: Boolean) {
    if (_isTrimming.value) return
    viewModelScope.launch {
      _isTrimming.value = true
      _errorMessage.value = null
      try {
        val result = repository.trimTrack(
          trackId = trackId,
          startMs = startMs,
          endMs = endMs,
          saveAsCopy = saveAsCopy,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _trimmingTrack.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        playerController.loadTrack(result.track, autoPlay = true)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to trim audio track."
        _trimmingTrack.value = null
      } finally {
        _isTrimming.value = false
      }
    }
  }

  fun openExportDialog(track: GeneratedTrack) {
    _exportingTrack.value = track
    _exportStatusMessage.value = null
    _exportErrorMessage.value = null
  }

  fun dismissExportDialog() {
    if (!_isExporting.value) {
      _exportingTrack.value = null
      _exportStatusMessage.value = null
      _exportErrorMessage.value = null
    }
  }

  fun selectExportFormat(format: AudioExportFormat) {
    _exportConfig.value = AudioExportConfig.defaultFor(
      format = format,
      normalizeOutputGain = _exportConfig.value.normalizeOutputGain,
      targetOutputLufs = _loudnessTarget.value.lufs,
    )
    _exportStatusMessage.value = null
    _exportErrorMessage.value = null
  }

  fun selectExportBitrateOption(option: AudioExportBitrateOption) {
    _exportConfig.value = _exportConfig.value.withOption(option)
    _exportStatusMessage.value = null
    _exportErrorMessage.value = null
  }

  fun setExportNormalizeGain(enabled: Boolean) {
    _exportConfig.value = _exportConfig.value.copy(
      normalizeOutputGain = enabled,
      targetOutputLufs = _loudnessTarget.value.lufs,
    )
    _exportStatusMessage.value = null
    _exportErrorMessage.value = null
  }

  fun exportTrackToMusic(track: GeneratedTrack) {
    if (_isExporting.value) return
    val config = _exportConfig.value
    viewModelScope.launch {
      _isExporting.value = true
      _exportStatusMessage.value = null
      _exportErrorMessage.value = null
      try {
        val outcome = repository.exportTrackToMusicMp3(track, config)
        val kb = (outcome.fileSizeBytes / 1024L).coerceAtLeast(1L)
        val detail = if (outcome.format == AudioExportFormat.MIDI) {
          config.midiExportMode.summaryTag
        } else {
          "${outcome.bitrateKbps} kbps"
        }
        _exportStatusMessage.value =
          "Saved ${outcome.format.displayName} ($kb KB, $detail) to ${outcome.locationLabel}"
      } catch (e: Exception) {
        _exportErrorMessage.value = e.localizedMessage ?: "Failed to export ${config.format.displayName}."
      } finally {
        _isExporting.value = false
      }
    }
  }

  fun exportTrackToSafUri(track: GeneratedTrack, uri: Uri) {
    if (_isExporting.value) return
    val config = _exportConfig.value
    viewModelScope.launch {
      _isExporting.value = true
      _exportStatusMessage.value = null
      _exportErrorMessage.value = null
      try {
        val outcome = repository.exportTrackMp3ToUri(track, uri, config)
        val kb = (outcome.fileSizeBytes / 1024L).coerceAtLeast(1L)
        _exportStatusMessage.value = "Saved ${outcome.fileName} ($kb KB)"
      } catch (e: Exception) {
        _exportErrorMessage.value = e.localizedMessage ?: "Failed to save ${config.format.displayName} file."
      } finally {
        _isExporting.value = false
      }
    }
  }

  fun shareTrackAsMp3(track: GeneratedTrack, onShareIntentReady: (Intent) -> Unit) {
    if (_isExporting.value) return
    val config = _exportConfig.value
    viewModelScope.launch {
      _isExporting.value = true
      _exportStatusMessage.value = null
      _exportErrorMessage.value = null
      try {
        val (shareIntent, outcome) = repository.prepareShareMp3Intent(track, config)
        val kb = (outcome.fileSizeBytes / 1024L).coerceAtLeast(1L)
        val detail = if (outcome.format == AudioExportFormat.MIDI) {
          config.midiExportMode.summaryTag
        } else {
          "${outcome.bitrateKbps} kbps"
        }
        _exportStatusMessage.value =
          "${outcome.format.displayName} ready ($kb KB, $detail) — choose an app to share"
        onShareIntentReady(shareIntent)
      } catch (e: Exception) {
        _exportErrorMessage.value = e.localizedMessage
          ?: "Failed to prepare ${config.format.displayName} for sharing."
      } finally {
        _isExporting.value = false
      }
    }
  }

  fun selectEngine(engine: GenerationEngine) {
    _selectedEngine.value = engine
    if (_errorMessage.value != null) {
      _errorMessage.value = null
    }
  }

  fun applyPresetPrompt(preset: String) {
    _promptInput.value = preset
    _errorMessage.value = null
  }

  fun generateTrackFromPrompt() {
    val prompt = _promptInput.value.trim().ifEmpty { "Warm rainy lo-fi chill beat 84 bpm" }
    if (_isGenerating.value) return
    val engine = _selectedEngine.value

    viewModelScope.launch {
      _isGenerating.value = true
      _errorMessage.value = null
      val targetLufs = _loudnessTarget.value.lufs
      val applyPostGain = _autoPostGainEnabled.value
      try {
        val result = when (engine) {
          GenerationEngine.HF_AUDIO_LDM ->
            repository.generateTrackWithHfSpaceBuffer(
              prompt = prompt,
              space = HfMusicSpace.AUDIO_LDM,
              targetLufs = targetLufs,
              applyPostGain = applyPostGain,
            )
          GenerationEngine.HF_MUSICGEN ->
            repository.generateTrackWithHfSpaceBuffer(
              prompt = prompt,
              space = HfMusicSpace.MUSICGEN,
              targetLufs = targetLufs,
              applyPostGain = applyPostGain,
            )
          GenerationEngine.HF_STABLE_AUDIO ->
            repository.generateTrackWithHfSpaceBuffer(
              prompt = prompt,
              space = HfMusicSpace.STABLE_AUDIO,
              targetLufs = targetLufs,
              applyPostGain = applyPostGain,
            )
          GenerationEngine.FOURIER_SYNTH ->
            repository.generateTrackWithBuffer(
              prompt = prompt,
              targetLufs = targetLufs,
              applyPostGain = applyPostGain,
            )
        }
        _promptInput.value = ""
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _latestAutoTagResult.value = result.autoTagResult
        playerController.loadTrack(result.track, autoPlay = true)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to generate audio track."
      } finally {
        _isGenerating.value = false
      }
    }
  }

  fun importAudioUri(uri: Uri) {
    viewModelScope.launch {
      _errorMessage.value = null
      try {
        val imported = repository.importAudioFromUri(
          uri = uri,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        playerController.loadTrack(imported, autoPlay = true)
        refreshBufferDiagnostics(imported)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to import selected audio file."
      }
    }
  }

  fun selectTrack(track: GeneratedTrack, autoPlay: Boolean = true) {
    if (playerController.state.value.currentTrackId == track.id && playerController.state.value.isPrepared) {
      if (autoPlay && !playerController.state.value.isPlaying) {
        playerController.play()
        viewModelScope.launch { historyRepository.recordTrackPlayed(track.id) }
      }
      return
    }
    playerController.loadTrack(track, autoPlay = autoPlay)
    refreshBufferDiagnostics(track)
    if (autoPlay) {
      viewModelScope.launch { historyRepository.recordTrackPlayed(track.id) }
    }
  }

  fun playTrack() {
    val tracks = uiState.value.allTracks
    val activeId = playerController.state.value.currentTrackId
    val trackToPlay = tracks.firstOrNull { it.id == activeId } ?: tracks.firstOrNull()
    if (trackToPlay == null) return

    if (activeId != trackToPlay.id || !playerController.state.value.isPrepared) {
      playerController.loadTrack(trackToPlay, autoPlay = true)
      refreshBufferDiagnostics(trackToPlay)
    } else {
      playerController.play()
    }
    viewModelScope.launch { historyRepository.recordTrackPlayed(trackToPlay.id) }
  }

  // --- Room Track History Actions ---

  fun selectHistoryTypeFilter(type: String) {
    _historyTypeFilter.value = type
  }

  fun onHistorySearchQueryChanged(query: String) {
    _historySearchQuery.value = query
  }

  fun playHistoryItem(item: TrackHistoryItem) {
    val matching = uiState.value.allTracks.firstOrNull { it.id == item.trackId }
    if (matching != null) {
      if (playerController.state.value.currentTrackId == matching.id && playerController.state.value.isPlaying) {
        pauseTrack()
      } else {
        selectTrack(matching, autoPlay = true)
      }
      return
    }
    viewModelScope.launch {
      val fromDb = repository.getTrackById(item.trackId)
      if (fromDb != null) {
        selectTrack(fromDb, autoPlay = true)
      } else {
        _errorMessage.value = "Audio file for \"${item.title}\" is no longer on disk."
      }
    }
  }

  fun reusePromptFromHistory(prompt: String) {
    val clean = prompt.trim()
    if (clean.isEmpty()) return
    _promptInput.value = clean
    _activeDestination.value = StudioDestination.STUDIO
    _quickMp3StatusMessage.value = "Loaded prompt from Room history into Music Generator"
  }

  fun deleteHistoryEntry(entryId: String) {
    viewModelScope.launch {
      historyRepository.deleteHistoryEntry(entryId)
    }
  }

  fun clearAllTrackHistory() {
    viewModelScope.launch {
      historyRepository.clearAllHistory()
      _quickMp3StatusMessage.value = "Cleared local Room generation history"
    }
  }

  fun pauseTrack() {
    playerController.pause()
  }

  fun stopTrack() {
    studioClipEngine.stop()
    playerController.stopPlayback()
  }

  fun togglePlayPause() {
    if (playerController.state.value.isPlaying) {
      pauseTrack()
    } else {
      playTrack()
    }
  }

  fun skipPrevious() {
    playerController.skipPrevious()
    playerController.currentTrack()?.let { refreshBufferDiagnostics(it) }
  }

  fun skipNext() {
    playerController.skipNext()
    playerController.currentTrack()?.let { refreshBufferDiagnostics(it) }
  }

  private fun refreshBufferDiagnostics(track: GeneratedTrack) {
    viewModelScope.launch {
      try {
        _activeBufferDiagnostics.value = repository.inspectTrackBuffer(track)
      } catch (_: Exception) {
      }
    }
  }

  fun beginSeek() {
    playerController.beginSeek()
  }

  fun previewSeek(positionMs: Long) {
    playerController.previewSeek(positionMs)
  }

  fun commitSeek(positionMs: Long) {
    playerController.commitSeek(positionMs)
  }

  fun seekBy(deltaMs: Long) {
    playerController.seekBy(deltaMs)
  }

  fun setVolume(volume: Float) {
    playerController.setVolume(volume)
  }

  fun toggleMute() {
    playerController.toggleMute()
  }

  fun setPitchSemitones(semitones: Float) {
    playerController.setPitchSemitones(semitones)
  }

  fun stepPitchSemitones(delta: Int) {
    playerController.stepPitchSemitones(delta)
  }

  fun resetPitch() {
    playerController.setPitchSemitones(0f)
  }

  fun selectReverbPreset(preset: ReverbPreset) {
    playerController.selectReverbPreset(preset)
  }

  fun setReverbWetMix(wetMix: Float) {
    playerController.setReverbWetMix(wetMix)
  }

  fun setReverbRoomSize(roomSize: Float) {
    playerController.setReverbRoomSize(roomSize)
  }

  fun setReverbDamping(damping: Float) {
    playerController.setReverbDamping(damping)
  }

  fun setReverbEngineMode(mode: ReverbEngineMode) {
    playerController.setReverbEngineMode(mode)
  }

  fun resetReverb() {
    playerController.resetReverb()
  }

  fun setEqualizerEnabled(enabled: Boolean) {
    playerController.setEqualizerEnabled(enabled)
  }

  fun selectEqualizerPreset(preset: EqPreset) {
    playerController.selectEqualizerPreset(preset)
  }

  fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
    playerController.setEqualizerBandGain(bandIndex, gainDb)
  }

  fun resetEqualizer() {
    playerController.resetEqualizer()
  }

  fun setCrossfadeEnabled(enabled: Boolean) {
    playerController.setCrossfadeEnabled(enabled)
  }

  fun setCrossfadeDurationSec(seconds: Float) {
    playerController.setCrossfadeDurationSec(seconds)
  }

  fun selectCrossfadePreset(seconds: Float) {
    playerController.selectCrossfadePreset(seconds)
  }

  fun saveReverbCopy() {
    val activeTrack = uiState.value.currentTrack ?: return
    val reverb = playerController.state.value.reverb
    if (!reverb.isActive || _isApplyingReverb.value) return
    val pitchSemitones = playerController.state.value.pitchSemitones

    viewModelScope.launch {
      _isApplyingReverb.value = true
      _errorMessage.value = null
      try {
        val result = repository.renderReverbCopy(
          trackId = activeTrack.id,
          reverbSettings = reverb,
          pitchSemitones = pitchSemitones,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _activeBufferDiagnostics.value = result.diagnostics
        playerController.resetReverb()
        playerController.setPitchSemitones(0f)
        playerController.loadTrack(result.track, autoPlay = true)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to render spatial reverb copy."
      } finally {
        _isApplyingReverb.value = false
      }
    }
  }

  fun selectLoudnessTarget(target: LoudnessTarget) {
    _loudnessTarget.value = target
    playerController.setAutoPostGain(_autoPostGainEnabled.value, target.lufs)
    _exportConfig.value = _exportConfig.value.copy(targetOutputLufs = target.lufs)
    viewModelScope.launch {
      themePreferences.setLoudnessTarget(target)
    }
  }

  fun setAutoPostGainEnabled(enabled: Boolean) {
    _autoPostGainEnabled.value = enabled
    playerController.setAutoPostGain(enabled, _loudnessTarget.value.lufs)
    _exportConfig.value = _exportConfig.value.copy(
      normalizeOutputGain = enabled,
      targetOutputLufs = _loudnessTarget.value.lufs,
    )
    viewModelScope.launch {
      themePreferences.setAutoPostGainEnabled(enabled)
    }
  }

  fun normalizeActiveTrack() {
    val activeTrack = uiState.value.currentTrack ?: return
    if (_isNormalizing.value) return
    val targetLufs = _loudnessTarget.value.lufs
    val wasPlaying = playerController.state.value.isPlaying

    viewModelScope.launch {
      _isNormalizing.value = true
      _errorMessage.value = null
      try {
        val result = repository.normalizeTrackToTargetLufs(
          trackId = activeTrack.id,
          targetLufs = targetLufs,
        )
        _activeBufferDiagnostics.value = result.diagnostics
        playerController.loadTrack(result.track, autoPlay = wasPlaying)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to normalize track loudness."
      } finally {
        _isNormalizing.value = false
      }
    }
  }

  fun normalizeLibraryTracks() {
    if (_isNormalizing.value) return
    val targetLufs = _loudnessTarget.value.lufs
    val currentId = playerController.state.value.currentTrackId ?: uiState.value.currentTrack?.id
    val wasPlaying = playerController.state.value.isPlaying

    viewModelScope.launch {
      _isNormalizing.value = true
      _errorMessage.value = null
      try {
        repository.normalizeAllTracksToTargetLufs(targetLufs = targetLufs)
        if (currentId != null) {
          val refreshedTrack = repository.getTrackById(currentId)
          if (refreshedTrack != null) {
            playerController.loadTrack(refreshedTrack, autoPlay = wasPlaying)
            refreshBufferDiagnostics(refreshedTrack)
          }
        }
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to normalize library loudness."
      } finally {
        _isNormalizing.value = false
      }
    }
  }

  fun toggleSelectionMode() {
    if (_isSelectionMode.value) {
      exitSelectionMode()
    } else {
      _isSelectionMode.value = true
      _selectedTrackIds.value = emptySet()
    }
  }

  fun enterSelectionWithTrack(trackId: String) {
    _isSelectionMode.value = true
    _selectedTrackIds.value = _selectedTrackIds.value + trackId
  }

  fun exitSelectionMode() {
    if (_isBulkRenaming.value) return
    _isSelectionMode.value = false
    _selectedTrackIds.value = emptySet()
    _showBulkRenameDialog.value = false
  }

  fun toggleTrackSelection(trackId: String) {
    val current = _selectedTrackIds.value
    _selectedTrackIds.value = if (trackId in current) {
      current - trackId
    } else {
      current + trackId
    }
  }

  fun toggleSelectAllVisibleTracks() {
    val visibleIds = uiState.value.tracks.map { it.id }.toSet()
    if (visibleIds.isEmpty()) return
    val current = _selectedTrackIds.value
    _selectedTrackIds.value = if (visibleIds.all { it in current }) {
      current - visibleIds
    } else {
      current + visibleIds
    }
  }

  fun openBulkRenameDialog() {
    if (uiState.value.selectedTrackIds.isEmpty()) return
    _showBulkRenameDialog.value = true
  }

  fun dismissBulkRenameDialog() {
    if (!_isBulkRenaming.value) {
      _showBulkRenameDialog.value = false
    }
  }

  fun confirmBulkRename(config: BulkRenameConfig) {
    val targetIds = uiState.value.selectedTracksForBulkRename.map { it.id }
    if (targetIds.isEmpty() || _isBulkRenaming.value) return
    viewModelScope.launch {
      _isBulkRenaming.value = true
      _errorMessage.value = null
      try {
        repository.bulkRenameTracks(trackIds = targetIds, config = config)
        _showBulkRenameDialog.value = false
        _isSelectionMode.value = false
        _selectedTrackIds.value = emptySet()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to rename selected tracks."
      } finally {
        _isBulkRenaming.value = false
      }
    }
  }

  fun dismissAutoTagBanner() {
    _latestAutoTagResult.value = null
  }

  fun suggestTagsForTrack(
    track: GeneratedTrack,
    onSuggestionReady: (TrackAutoTagResult) -> Unit,
  ) {
    if (_isSuggestingTags.value) return
    viewModelScope.launch {
      _isSuggestingTags.value = true
      try {
        val suggestion = repository.suggestAutoTags(track)
        _latestAutoTagResult.value = suggestion
        onSuggestionReady(suggestion)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to analyze track tags."
      } finally {
        _isSuggestingTags.value = false
      }
    }
  }

  fun openUrlDownloadDialog() {
    _downloadProgress.value = null
    _downloadStatusMessage.value = null
    _downloadErrorMessage.value = null
    _showUrlDownloadDialog.value = true
  }

  fun dismissUrlDownloadDialog() {
    if (!_isDownloadingUrl.value) {
      _showUrlDownloadDialog.value = false
      _downloadProgress.value = null
      _downloadStatusMessage.value = null
      _downloadErrorMessage.value = null
    }
  }

  fun startSegmentedUrlDownload(url: String, connections: Int) {
    if (_isDownloadingUrl.value) return
    viewModelScope.launch {
      _isDownloadingUrl.value = true
      _downloadProgress.value = null
      _downloadStatusMessage.value = null
      _downloadErrorMessage.value = null
      _errorMessage.value = null
      val targetLufs = _loudnessTarget.value.lufs
      try {
        val result = repository.downloadTrackFromUrl(
          url = url,
          connections = connections,
          targetLufs = targetLufs,
          applyPostGain = _autoPostGainEnabled.value,
          onProgress = { progress ->
            _downloadProgress.value = progress
          },
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _latestAutoTagResult.value = result.autoTagResult
        _showUrlDownloadDialog.value = false
        _downloadProgress.value = null
        playerController.loadTrack(result.track, autoPlay = true)
      } catch (e: Exception) {
        _downloadErrorMessage.value = e.localizedMessage ?: "Failed to download audio from URL."
      } finally {
        _isDownloadingUrl.value = false
      }
    }
  }

  fun submitAria2RpcDownload(
    rpcUrl: String,
    sourceUrl: String,
    secret: String,
    connections: Int,
  ) {
    if (_isDownloadingUrl.value) return
    viewModelScope.launch {
      _isDownloadingUrl.value = true
      _downloadProgress.value = null
      _downloadStatusMessage.value = null
      _downloadErrorMessage.value = null
      try {
        val gid = repository.submitAria2RpcDownload(
          rpcUrl = rpcUrl,
          sourceUrl = sourceUrl,
          secret = secret,
          connections = connections,
          onProgress = { progress ->
            _downloadProgress.value = progress
          },
        )
        _downloadStatusMessage.value = "aria2 JSON-RPC task queued & tracked (GID: $gid)"
      } catch (e: Exception) {
        _downloadErrorMessage.value = e.localizedMessage ?: "aria2 JSON-RPC submission failed."
      } finally {
        _isDownloadingUrl.value = false
      }
    }
  }

  fun toggleLoop() {
    playerController.toggleLoop()
  }

  fun toggleSequencerStep(lane: SequencerLane, stepIndex: Int) {
    stepSequencerEngine.toggleStep(lane, stepIndex)
  }

  fun toggleSequencerPlayStop() {
    stepSequencerEngine.togglePlayStop()
  }

  fun clearSequencerPattern() {
    stepSequencerEngine.clearPattern()
  }

  fun applySequencerPreset(preset: SequencerPreset) {
    stepSequencerEngine.applyPreset(preset)
  }

  fun stepSequencerBpm(delta: Int) {
    stepSequencerEngine.stepManualBpm(delta)
  }

  fun toggleSequencerBpmSync() {
    stepSequencerEngine.toggleBpmSync()
  }

  fun cycleSequencerSwing() {
    stepSequencerEngine.cycleSwing()
  }

  fun renderSequencerToLibrary() {
    val snap = stepSequencerEngine.state.value
    if (snap.activeStepCount == 0 || snap.isRenderingToLibrary) return

    viewModelScope.launch {
      stepSequencerEngine.setRenderingToLibrary(true)
      _errorMessage.value = null
      try {
        val result = repository.renderSequencerPatternToTrack(
          sequencerState = snap,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _latestAutoTagResult.value = result.autoTagResult
        stepSequencerEngine.stop()
        playerController.loadTrack(result.track, autoPlay = true)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to render sequencer pattern."
      } finally {
        stepSequencerEngine.setRenderingToLibrary(false)
      }
    }
  }

  fun deleteTrack(trackId: String) {
    _selectedTrackIds.value = _selectedTrackIds.value - trackId
    viewModelScope.launch {
      val currentList = uiState.value.allTracks
      val wasCurrent = playerController.state.value.currentTrackId == trackId
      repository.deleteTrack(trackId)
      if (wasCurrent) {
        val replacement = currentList.firstOrNull { it.id != trackId }
        if (replacement != null) {
          playerController.loadTrack(replacement, autoPlay = false)
        } else {
          playerController.clearTrack()
        }
      }
    }
  }

  fun toggleMetronomePlayStop() {
    metronomeEngine.togglePlayStop()
  }

  fun stepMetronomeBpm(delta: Int) {
    metronomeEngine.stepManualBpm(delta)
  }

  fun toggleMetronomeBpmSync() {
    metronomeEngine.toggleBpmSync()
  }

  fun setMetronomeVolume(volume: Float) {
    metronomeEngine.setVolume(volume)
  }

  fun setMetronomePitchHz(pitchHz: Float) {
    metronomeEngine.setPitchHz(pitchHz)
  }

  fun selectMetronomeToneStyle(toneStyle: MetronomeTone) {
    metronomeEngine.selectToneStyle(toneStyle)
  }

  fun selectMetronomeTimeSignature(timeSignature: MetronomeTimeSignature) {
    metronomeEngine.selectTimeSignature(timeSignature)
  }

  fun toggleMetronomeAccentDownbeat() {
    metronomeEngine.toggleAccentDownbeat()
  }

  fun tapMetronomeTempo() {
    metronomeEngine.tapTempo()
  }

  fun openSaveProjectDialog() {
    _showSaveProjectDialog.value = true
  }

  fun dismissSaveProjectDialog() {
    if (!_isSavingProject.value) {
      _showSaveProjectDialog.value = false
    }
  }

  fun dismissProjectStatus() {
    _projectStatusMessage.value = null
  }

  fun quickSaveActiveProject() {
    val activeId = _activeProjectId.value
    val activeProject = uiState.value.projects.firstOrNull { it.id == activeId }
    if (activeProject == null) {
      openSaveProjectDialog()
      return
    }
    val clipIds = (activeProject.clipIds + listOfNotNull(uiState.value.currentTrack?.id)).distinct()
    saveProject(
      name = activeProject.name,
      notes = activeProject.notes,
      selectedClipIds = clipIds,
      overwriteProjectId = activeProject.id,
    )
  }

  fun saveProject(
    name: String,
    notes: String,
    selectedClipIds: List<String>,
    overwriteProjectId: String? = null,
  ) {
    if (_isSavingProject.value) return
    val currentSnap = uiState.value
    val playback = playerController.state.value
    val sequencer = stepSequencerEngine.state.value
    val metronome = metronomeEngine.state.value
    val activeTrack = currentSnap.currentTrack
    val targetClipIds = (selectedClipIds + listOfNotNull(activeTrack?.id)).distinct()

    viewModelScope.launch {
      _isSavingProject.value = true
      _errorMessage.value = null
      try {
        val projectId = overwriteProjectId?.takeIf { it.isNotBlank() }
          ?: java.util.UUID.randomUUID().toString()
        val draft = StudioProject(
          id = projectId,
          name = name,
          notes = notes,
          activeTrackId = activeTrack?.id,
          activeClipTitle = activeTrack?.title.orEmpty(),
          clipIds = targetClipIds,
          selectedPlaylistId = _selectedPlaylistId.value,
          playbackPositionMs = playback.positionMs.coerceAtLeast(0L),
          volume = playback.volume,
          isMuted = playback.isMuted,
          isLooping = playback.isLooping,
          pitchSemitones = playback.pitchSemitones,
          reverbSettings = playback.reverb,
          equalizerSettings = playback.equalizer,
          crossfadeSettings = playback.crossfade,
          autoPostGainEnabled = _autoPostGainEnabled.value,
          loudnessTarget = _loudnessTarget.value,
          activeEnvelope = playback.activeEnvelope,
          sequencerPattern = sequencer.pattern,
          sequencerBpm = sequencer.bpm,
          sequencerSwingPercent = sequencer.swingPercent,
          sequencerBpmSynced = sequencer.isBpmSynced,
          sequencerPresetName = sequencer.selectedPresetName,
          metronomeBpm = metronome.bpm,
          metronomeBpmSynced = metronome.isBpmSynced,
          metronomeVolume = metronome.volume,
          metronomePitchHz = metronome.pitchHz,
          metronomeToneStyle = metronome.toneStyle,
          metronomeTimeSignature = metronome.timeSignature,
          metronomeAccentDownbeat = metronome.accentDownbeat,
        )
        val saved = projectRepository.saveProject(draft)
        _activeProjectId.value = saved.id
        _showSaveProjectDialog.value = false
        _projectStatusMessage.value =
          "Saved \"${saved.name}\" (${saved.totalClipCount} clips, ${saved.activeSequencerSteps} seq steps)"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to save studio project."
      } finally {
        _isSavingProject.value = false
      }
    }
  }

  fun loadProject(projectId: String) {
    viewModelScope.launch {
      _errorMessage.value = null
      try {
        val loaded = projectRepository.getProjectById(projectId, requireExistingFiles = true)
        if (loaded == null) {
          _errorMessage.value = "Project could not be found in Room database."
          return@launch
        }

        stepSequencerEngine.stop()
        metronomeEngine.stop()

        _autoPostGainEnabled.value = loaded.autoPostGainEnabled
        _loudnessTarget.value = loaded.loudnessTarget
        themePreferences.setAutoPostGainEnabled(loaded.autoPostGainEnabled)
        themePreferences.setLoudnessTarget(loaded.loudnessTarget)
        _exportConfig.value = _exportConfig.value.copy(
          normalizeOutputGain = loaded.autoPostGainEnabled,
          targetOutputLufs = loaded.loudnessTarget.lufs,
        )

        val playlists = uiState.value.playlists
        val nextPlaylistId = loaded.selectedPlaylistId?.takeIf { pid -> playlists.any { it.id == pid } }
        _selectedPlaylistId.value = nextPlaylistId
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false

        val allAvailableTracks = uiState.value.allTracks
        val restoredQueue = if (loaded.clips.isNotEmpty()) {
          loaded.clips
        } else {
          allAvailableTracks
        }
        playerController.updatePlaylist(restoredQueue)

        val targetTrackBase = loaded.activeTrackId?.let { id ->
          loaded.clips.firstOrNull { it.id == id } ?: repository.getTrackById(id)
        } ?: loaded.clips.firstOrNull() ?: allAvailableTracks.firstOrNull()

        val targetTrack = if (targetTrackBase != null && loaded.activeEnvelope.isEdited) {
          repository.updateTrackAudioEnvelope(targetTrackBase.id, loaded.activeEnvelope)
          targetTrackBase.copy(audioEnvelope = loaded.activeEnvelope)
        } else {
          targetTrackBase
        }

        playerController.restoreProjectSettings(
          volume = loaded.volume,
          isMuted = loaded.isMuted,
          isLooping = loaded.isLooping,
          pitchSemitones = loaded.pitchSemitones,
          reverb = loaded.reverbSettings,
          equalizer = loaded.equalizerSettings,
          crossfade = loaded.crossfadeSettings,
          autoPostGainEnabled = loaded.autoPostGainEnabled,
          targetLoudnessLufs = loaded.loudnessTarget.lufs,
          activeEnvelope = loaded.activeEnvelope,
        )

        stepSequencerEngine.restoreState(
          pattern = loaded.sequencerPattern,
          bpm = loaded.sequencerBpm,
          swingPercent = loaded.sequencerSwingPercent,
          isBpmSynced = loaded.sequencerBpmSynced,
          selectedPresetName = loaded.sequencerPresetName,
        )

        metronomeEngine.restoreState(
          bpm = loaded.metronomeBpm,
          isBpmSynced = loaded.metronomeBpmSynced,
          volume = loaded.metronomeVolume,
          pitchHz = loaded.metronomePitchHz,
          toneStyle = loaded.metronomeToneStyle,
          timeSignature = loaded.metronomeTimeSignature,
          accentDownbeat = loaded.metronomeAccentDownbeat,
        )

        if (targetTrack != null) {
          playerController.loadTrack(
            track = targetTrack,
            autoPlay = false,
            initialPositionMs = loaded.playbackPositionMs,
          )
          refreshBufferDiagnostics(targetTrack)
        } else {
          playerController.clearTrack()
        }

        _activeProjectId.value = loaded.id
        _projectStatusMessage.value =
          "Loaded \"${loaded.name}\" (${loaded.totalClipCount} clips, ${loaded.activeSequencerSteps} seq steps)"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to load studio project."
      }
    }
  }

  fun renameProject(projectId: String, newName: String) {
    val cleanName = newName.trim()
    if (cleanName.isEmpty()) return
    viewModelScope.launch {
      try {
        val renamed = projectRepository.renameProject(projectId, cleanName)
        if (renamed != null) {
          _projectStatusMessage.value = "Renamed session to \"${renamed.name}\""
        }
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to rename studio session."
      }
    }
  }

  fun deleteProject(projectId: String) {
    viewModelScope.launch {
      try {
        projectRepository.deleteProject(projectId)
        if (_activeProjectId.value == projectId) {
          _activeProjectId.value = null
        }
        _projectStatusMessage.value = "Removed project from Room library"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to delete project."
      }
    }
  }

  fun selectAudioProject(track: GeneratedTrack) {
    selectTrack(track, autoPlay = false)
    val fileName = java.io.File(track.filePath).name
    _projectStatusMessage.value = "Selected \"${track.title}\" ($fileName)"
  }

  fun toggleAuditionAudioProject(track: GeneratedTrack) {
    val currentClipState = studioClipEngine.state.value
    if (currentClipState.activeClipId == track.id && currentClipState.isPlaying) {
      studioClipEngine.stop()
    } else {
      studioClipEngine.playTrack(track = track, looping = false)
    }
  }

  fun renameAudioProject(trackId: String, newTitle: String) {
    val cleanTitle = newTitle.trim()
    if (cleanTitle.isEmpty()) return
    viewModelScope.launch {
      _errorMessage.value = null
      try {
        val playbackSnap = playerController.state.value
        val wasCurrent = playbackSnap.currentTrackId == trackId
        val wasPlaying = wasCurrent && playbackSnap.isPlaying
        val savedPosMs = if (wasCurrent) playbackSnap.positionMs else 0L
        if (studioClipEngine.state.value.activeClipId == trackId) {
          studioClipEngine.stop()
        }
        val updated = repository.renameTrackAndFile(trackId = trackId, newTitle = cleanTitle)
        if (wasCurrent) {
          playerController.loadTrack(
            track = updated,
            autoPlay = wasPlaying,
            initialPositionMs = savedPosMs,
          )
          refreshBufferDiagnostics(updated)
        }
        val fileName = java.io.File(updated.filePath).name
        _projectStatusMessage.value = "Renamed to \"${updated.title}\" ($fileName)"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to rename saved audio file."
      }
    }
  }

  fun deleteAudioProject(trackId: String) {
    val targetTitle = uiState.value.allTracks.firstOrNull { it.id == trackId }?.title
    if (studioClipEngine.state.value.activeClipId == trackId) {
      studioClipEngine.stop()
    }
    deleteTrack(trackId)
    _projectStatusMessage.value = if (!targetTitle.isNullOrBlank()) {
      "Deleted saved audio \"$targetTitle\""
    } else {
      "Deleted saved audio file"
    }
  }

  // --- Navigation & Multi-Page Studio Controls ---

  fun selectDestination(destination: StudioDestination) {
    _activeDestination.value = destination
  }

  fun selectStudioSectionTab(tab: StudioSectionTab) {
    _studioSectionTab.value = tab
  }

  fun dismissQuickMp3Status() {
    _quickMp3StatusMessage.value = null
  }

  // --- Sound Resource Bank ---

  fun generateFromSoundResource(resource: StudioSoundResource) {
    if (_isGenerating.value || _generatingResourceId.value != null) return
    viewModelScope.launch {
      _generatingResourceId.value = resource.id
      _errorMessage.value = null
      try {
        val result = repository.generateSoundResourceTrack(
          resource = resource,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _mergeMixerState.value = _mergeMixerState.value.copy(
          selectedTrackIds = _mergeMixerState.value.selectedTrackIds + result.track.id,
        )
        playerController.loadTrack(result.track, autoPlay = true)
        _quickMp3StatusMessage.value = "Created \"${result.track.title}\" — ready to play or merge"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to generate sound resource."
      } finally {
        _generatingResourceId.value = null
      }
    }
  }

  // --- Piano Page Actions ---

  fun selectPianoVoice(voice: PianoVoice) {
    _pianoState.value = _pianoState.value.copy(selectedVoice = voice)
  }

  fun selectPianoProgression(progression: PianoProgressionPreset) {
    _pianoState.value = _pianoState.value.copy(
      selectedProgression = progression,
      bpm = progression.defaultBpm,
    )
  }

  fun shiftPianoOctave(delta: Int) {
    val next = (_pianoState.value.baseOctave + delta).coerceIn(2, 5)
    _pianoState.value = _pianoState.value.copy(baseOctave = next)
  }

  fun stepPianoBpm(delta: Int) {
    val next = (_pianoState.value.bpm + delta).coerceIn(55, 180)
    _pianoState.value = _pianoState.value.copy(bpm = next)
  }

  fun playPianoKey(midiNote: Int) {
    val snap = _pianoState.value
    val voice = snap.selectedVoice

    val now = System.currentTimeMillis()
    val updatedRecorded = if (snap.isRecording) {
      if (snap.recordedNotes.isEmpty()) {
        pianoRecStartMs = now
      }
      val offset = (now - pianoRecStartMs).coerceAtLeast(0L)
      snap.recordedNotes + RecordedNoteEvent(midiNote = midiNote, offsetMs = offset, durationMs = 650L)
    } else {
      snap.recordedNotes
    }
    _pianoState.update {
      it.copy(
        activeMidiNotes = it.activeMidiNotes + midiNote,
        recordedNotes = updatedRecorded,
      )
    }

    // Synthesize off the Main thread and mix polyphonically into live AudioTrack
    viewModelScope.launch {
      val samples = withContext(Dispatchers.Default) {
        InstrumentSynthDsp.synthesizePianoNoteSamples(
          midiNote = midiNote,
          durationSec = 1.15,
          velocity = 0.88f,
          voice = voice,
        )
      }
      liveInstrumentEngine.triggerSamples(samples)
    }

    // Auto-release visual highlight so keys flash cleanly instead of sticking lit forever
    viewModelScope.launch {
      delay(240L)
      _pianoState.update { it.copy(activeMidiNotes = it.activeMidiNotes - midiNote) }
    }
  }

  fun playPianoChord(midiNotes: IntArray) {
    if (midiNotes.isEmpty()) return
    val snap = _pianoState.value
    val voice = snap.selectedVoice
    val noteSet = midiNotes.toSet()

    val now = System.currentTimeMillis()
    val updatedRecorded = if (snap.isRecording) {
      if (snap.recordedNotes.isEmpty()) {
        pianoRecStartMs = now
      }
      val baseOffset = (now - pianoRecStartMs).coerceAtLeast(0L)
      val events = midiNotes.mapIndexed { idx, midi ->
        RecordedNoteEvent(
          midiNote = midi,
          offsetMs = baseOffset + idx * 18L,
          durationMs = 850L,
          velocity = if (idx == 0) 0.88f else 0.76f,
        )
      }
      snap.recordedNotes + events
    } else {
      snap.recordedNotes
    }

    _pianoState.update {
      it.copy(
        activeMidiNotes = it.activeMidiNotes + noteSet,
        recordedNotes = updatedRecorded,
      )
    }

    viewModelScope.launch {
      val chordSamples = withContext(Dispatchers.Default) {
        val durSec = 1.25
        val total = (durSec * InstrumentSynthDsp.SAMPLE_RATE).toInt()
        val mix = FloatArray(total)
        val scale = 0.55f / kotlin.math.sqrt(midiNotes.size.toFloat().coerceAtLeast(1f))
        midiNotes.forEachIndexed { idx, midi ->
          val stagger = (idx * 0.018 * InstrumentSynthDsp.SAMPLE_RATE).toInt()
          val noteBuf = InstrumentSynthDsp.synthesizePianoNoteSamples(
            midiNote = midi,
            durationSec = durSec,
            velocity = if (idx == 0) 0.90f else 0.78f,
            voice = voice,
          )
          val limit = kotlin.math.min(noteBuf.size, total - stagger)
          for (i in 0 until limit) {
            mix[stagger + i] += noteBuf[i] * scale
          }
        }
        mix
      }
      liveInstrumentEngine.triggerSamples(chordSamples)
    }

    viewModelScope.launch {
      delay(300L)
      _pianoState.update { it.copy(activeMidiNotes = it.activeMidiNotes - noteSet) }
    }
  }

  fun togglePianoRecording() {
    val snap = _pianoState.value
    if (!snap.isRecording) {
      pianoRecStartMs = System.currentTimeMillis()
      _pianoState.value = snap.copy(isRecording = true, recordedNotes = emptyList())
    } else {
      _pianoState.value = snap.copy(isRecording = false)
    }
  }

  fun clearPianoRecording() {
    _pianoState.value = _pianoState.value.copy(
      isRecording = false,
      recordedNotes = emptyList(),
      activeMidiNotes = emptySet(),
    )
  }

  fun previewPianoPattern() {
    val snap = _pianoState.value
    if (snap.isPreviewing) {
      liveInstrumentEngine.stopPreview()
      _pianoState.value = snap.copy(isPreviewing = false)
      return
    }
    _pianoState.value = snap.copy(isPreviewing = true)
    viewModelScope.launch {
      val buffer = withContext(Dispatchers.Default) {
        InstrumentSynthDsp.synthesizePianoTrackToBuffer(
          voice = snap.selectedVoice,
          progression = snap.selectedProgression,
          recordedNotes = snap.recordedNotes,
          bpm = snap.bpm,
          bars = 2,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
      }
      liveInstrumentEngine.playPatternPreview(buffer) {
        _pianoState.update { it.copy(isPreviewing = false) }
      }
    }
  }

  fun generatePianoTrack() {
    val snap = _pianoState.value
    if (snap.isGenerating) return
    viewModelScope.launch {
      _pianoState.value = snap.copy(isGenerating = true, isRecording = false)
      _errorMessage.value = null
      try {
        val result = repository.generatePianoTrack(
          voice = snap.selectedVoice,
          progression = snap.selectedProgression,
          recordedNotes = snap.recordedNotes,
          bpm = snap.bpm,
          bars = 4,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _mergeMixerState.value = _mergeMixerState.value.copy(
          selectedTrackIds = _mergeMixerState.value.selectedTrackIds + result.track.id,
        )
        playerController.loadTrack(result.track, autoPlay = true)
        _pianoState.value = _pianoState.value.copy(
          isGenerating = false,
          lastGeneratedTrackTitle = result.track.title,
        )
        _quickMp3StatusMessage.value = "Generated Piano track \"${result.track.title}\" & saved to History"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to generate piano track."
        _pianoState.value = _pianoState.value.copy(isGenerating = false)
      }
    }
  }

  // --- Guitar Page Actions ---

  fun selectGuitarVoice(voice: GuitarVoice) {
    _guitarState.value = _guitarState.value.copy(selectedVoice = voice)
  }

  fun selectGuitarChord(chord: GuitarChordShape) {
    _guitarState.update { it.copy(selectedChord = chord) }
    strumGuitarChord(chord)
  }

  fun addGuitarChordToSequence(chord: GuitarChordShape) {
    _guitarState.update { current ->
      val nextSeq = (current.chordSequence + chord).takeLast(8)
      current.copy(
        selectedChord = chord,
        chordSequence = nextSeq,
      )
    }
  }

  fun removeGuitarChordFromSequenceAt(index: Int) {
    _guitarState.update { current ->
      if (current.chordSequence.size <= 1 || index !in current.chordSequence.indices) {
        current
      } else {
        val nextSeq = current.chordSequence.toMutableList().apply { removeAt(index) }
        current.copy(chordSequence = nextSeq)
      }
    }
  }

  fun applyGuitarProgressionPreset(preset: GuitarProgressionPreset) {
    val first = preset.chords.firstOrNull() ?: GuitarChordShape.C_MAJ
    _guitarState.update {
      it.copy(
        selectedChord = first,
        chordSequence = preset.chords,
      )
    }
    strumGuitarChord(first)
  }

  fun toggleGuitarChordInSequence(chord: GuitarChordShape) {
    addGuitarChordToSequence(chord)
  }

  fun selectGuitarStrumStyle(style: GuitarStrumStyle) {
    _guitarState.value = _guitarState.value.copy(strumStyle = style)
  }

  fun stepGuitarBpm(delta: Int) {
    val next = (_guitarState.value.bpm + delta).coerceIn(55, 180)
    _guitarState.value = _guitarState.value.copy(bpm = next)
  }

  fun pluckGuitarString(stringIndex: Int, fret: Int) {
    if (stringIndex !in 0..5) return
    val safeFret = fret.coerceIn(0, 12)
    val midi = InstrumentSynthDsp.GUITAR_OPEN_MIDI[stringIndex] + safeFret
    val snap = _guitarState.value
    val voice = snap.selectedVoice

    val now = System.currentTimeMillis()
    val updatedRecorded = if (snap.isRecording) {
      if (snap.recordedPlucks.isEmpty()) {
        guitarRecStartMs = now
      }
      val offset = (now - guitarRecStartMs).coerceAtLeast(0L)
      snap.recordedPlucks + RecordedNoteEvent(midiNote = midi, offsetMs = offset, durationMs = 950L)
    } else {
      snap.recordedPlucks
    }
    _guitarState.update {
      it.copy(
        lastPluckedStringIndex = stringIndex,
        activePluckedCell = stringIndex to safeFret,
        recordedPlucks = updatedRecorded,
      )
    }

    // Offload Karplus-Strong synthesis to Dispatchers.Default and mix polyphonically
    viewModelScope.launch {
      val samples = withContext(Dispatchers.Default) {
        InstrumentSynthDsp.synthesizeGuitarStringSamples(
          midiNote = midi,
          durationSec = 1.45,
          velocity = 0.90f,
          voice = voice,
        )
      }
      liveInstrumentEngine.triggerSamples(samples)
    }

    guitarCellFlashJob?.cancel()
    guitarCellFlashJob = viewModelScope.launch {
      delay(280L)
      _guitarState.update { it.copy(activePluckedCell = null) }
    }
  }

  fun strumGuitarChord(chord: GuitarChordShape = _guitarState.value.selectedChord) {
    val snap = _guitarState.value
    val voice = snap.selectedVoice

    if (snap.isRecording) {
      val now = System.currentTimeMillis()
      if (snap.recordedPlucks.isEmpty()) {
        guitarRecStartMs = now
      }
      val baseOffset = (now - guitarRecStartMs).coerceAtLeast(0L)
      val newEvents = chord.activeMidiNotes().mapIndexed { idx, midi ->
        RecordedNoteEvent(
          midiNote = midi,
          offsetMs = baseOffset + idx * 18L,
          durationMs = 1_100L,
        )
      }
      _guitarState.update { it.copy(recordedPlucks = it.recordedPlucks + newEvents) }
    }

    viewModelScope.launch {
      val samples = withContext(Dispatchers.Default) {
        InstrumentSynthDsp.synthesizeGuitarChordStrumSamples(
          frets = chord.frets,
          voice = voice,
          durationSec = 1.5,
        )
      }
      liveInstrumentEngine.triggerSamples(samples)
    }
  }

  fun toggleGuitarRecording() {
    val snap = _guitarState.value
    if (!snap.isRecording) {
      guitarRecStartMs = System.currentTimeMillis()
      _guitarState.value = snap.copy(isRecording = true, recordedPlucks = emptyList())
    } else {
      _guitarState.value = snap.copy(isRecording = false)
    }
  }

  fun clearGuitarRecording() {
    _guitarState.value = _guitarState.value.copy(
      isRecording = false,
      recordedPlucks = emptyList(),
      lastPluckedStringIndex = null,
      activePluckedCell = null,
    )
  }

  fun previewGuitarPattern() {
    val snap = _guitarState.value
    if (snap.isPreviewing) {
      liveInstrumentEngine.stopPreview()
      _guitarState.value = snap.copy(isPreviewing = false)
      return
    }
    _guitarState.value = snap.copy(isPreviewing = true)
    viewModelScope.launch {
      val buffer = withContext(Dispatchers.Default) {
        InstrumentSynthDsp.synthesizeGuitarTrackToBuffer(
          voice = snap.selectedVoice,
          chordSequence = snap.chordSequence,
          strumStyle = snap.strumStyle,
          recordedPlucks = snap.recordedPlucks,
          bpm = snap.bpm,
          bars = 2,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
      }
      liveInstrumentEngine.playPatternPreview(buffer) {
        _guitarState.update { it.copy(isPreviewing = false) }
      }
    }
  }

  fun generateGuitarTrack() {
    val snap = _guitarState.value
    if (snap.isGenerating) return
    viewModelScope.launch {
      _guitarState.value = snap.copy(isGenerating = true, isRecording = false)
      _errorMessage.value = null
      try {
        val result = repository.generateGuitarTrack(
          voice = snap.selectedVoice,
          chordSequence = snap.chordSequence,
          strumStyle = snap.strumStyle,
          recordedPlucks = snap.recordedPlucks,
          bpm = snap.bpm,
          bars = 4,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _mergeMixerState.value = _mergeMixerState.value.copy(
          selectedTrackIds = _mergeMixerState.value.selectedTrackIds + result.track.id,
        )
        playerController.loadTrack(result.track, autoPlay = true)
        _guitarState.value = _guitarState.value.copy(
          isGenerating = false,
          lastGeneratedTrackTitle = result.track.title,
        )
        _quickMp3StatusMessage.value = "Generated Guitar track \"${result.track.title}\" & saved to History"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to generate guitar track."
        _guitarState.value = _guitarState.value.copy(isGenerating = false)
      }
    }
  }

  // --- Drum Page Actions ---

  fun selectDrumKit(kit: DrumKitPreset) {
    stepSequencerEngine.selectDrumKit(kit)
    // Pre-warm all 8 drum pad voices for the newly selected kit in background
    viewModelScope.launch(Dispatchers.Default) {
      DrumPadVoice.entries.forEach { pad ->
        liveInstrumentEngine.getOrSynthesizeDrumPad(pad = pad, kit = kit)
      }
    }
  }

  fun triggerDrumPad(pad: DrumPadVoice) {
    val kit = stepSequencerEngine.state.value.drumKit
    val currentDrums = _drumState.value

    val now = System.currentTimeMillis()
    val updatedHits = if (currentDrums.isRecordingPads) {
      if (currentDrums.recordedHits.isEmpty()) {
        drumRecStartMs = now
      }
      val offset = (now - drumRecStartMs).coerceAtLeast(0L)
      currentDrums.recordedHits + RecordedDrumHit(pad = pad, offsetMs = offset, velocity = 0.94f)
    } else {
      currentDrums.recordedHits
    }

    _drumState.update {
      it.copy(
        activePads = it.activePads + pad,
        recordedHits = updatedHits,
      )
    }

    // Play cached polyphonic hit off the Main thread for zero-latency finger drumming
    viewModelScope.launch {
      val samples = withContext(Dispatchers.Default) {
        liveInstrumentEngine.getOrSynthesizeDrumPad(pad = pad, kit = kit, velocity = 0.94f)
      }
      liveInstrumentEngine.triggerSamples(samples)
    }

    // Auto-clear pad highlight after crisp 180ms pulse
    viewModelScope.launch {
      delay(180L)
      _drumState.update { it.copy(activePads = it.activePads - pad) }
    }
  }

  fun toggleDrumPadRecording() {
    val current = _drumState.value
    if (!current.isRecordingPads) {
      drumRecStartMs = System.currentTimeMillis()
      _drumState.value = current.copy(isRecordingPads = true, recordedHits = emptyList())
    } else {
      _drumState.value = current.copy(isRecordingPads = false)
    }
  }

  fun clearDrumPadRecording() {
    _drumState.value = _drumState.value.copy(
      isRecordingPads = false,
      recordedHits = emptyList(),
      activePads = emptySet(),
    )
  }

  fun previewDrumPadTake() {
    val current = _drumState.value
    if (current.isPreviewingPads) {
      liveInstrumentEngine.stopPreview()
      _drumState.update { it.copy(isPreviewingPads = false) }
      return
    }
    if (current.recordedHits.isEmpty()) return
    val kit = stepSequencerEngine.state.value.drumKit
    val bpm = stepSequencerEngine.state.value.bpm
    _drumState.update { it.copy(isPreviewingPads = true) }
    viewModelScope.launch {
      val buffer = withContext(Dispatchers.Default) {
        InstrumentSynthDsp.synthesizeDrumPadTakeToBuffer(
          kit = kit,
          recordedHits = current.recordedHits,
          bpm = bpm,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
      }
      liveInstrumentEngine.playPatternPreview(buffer) {
        _drumState.update { it.copy(isPreviewingPads = false) }
      }
    }
  }

  fun generateDrumTrack() {
    val snap = stepSequencerEngine.state.value
    if (snap.isRenderingToLibrary) return
    val drumSnap = _drumState.value

    viewModelScope.launch {
      stepSequencerEngine.setRenderingToLibrary(true)
      _drumState.update { it.copy(isRecordingPads = false) }
      _errorMessage.value = null
      try {
        val result = if (drumSnap.recordedHits.isNotEmpty()) {
          repository.generateDrumPadTakeTrack(
            kit = snap.drumKit,
            recordedHits = drumSnap.recordedHits,
            bpm = snap.bpm,
            targetLufs = _loudnessTarget.value.lufs,
            applyPostGain = _autoPostGainEnabled.value,
          )
        } else {
          val effectiveState = if (snap.activeStepCount > 0) {
            snap
          } else {
            snap.copy(
              pattern = SequencerPreset.FOUR_ON_FLOOR.buildGrid(),
              selectedPresetName = SequencerPreset.FOUR_ON_FLOOR.label,
            )
          }
          repository.renderSequencerPatternToTrack(
            sequencerState = effectiveState,
            targetLufs = _loudnessTarget.value.lufs,
            applyPostGain = _autoPostGainEnabled.value,
          )
        }
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        _mergeMixerState.value = _mergeMixerState.value.copy(
          selectedTrackIds = _mergeMixerState.value.selectedTrackIds + result.track.id,
        )
        stepSequencerEngine.stop()
        playerController.loadTrack(result.track, autoPlay = true)
        _drumState.update { it.copy(lastGeneratedTrackTitle = result.track.title) }
        _quickMp3StatusMessage.value = "Generated Drum track \"${result.track.title}\" & saved to History"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to generate drum track."
      } finally {
        stepSequencerEngine.setRenderingToLibrary(false)
      }
    }
  }

  // --- Multi-Track Merger & Quick MP3 Export ---

  fun toggleMergeTrackSelection(trackId: String) {
    val current = _mergeMixerState.value
    val nextSet = if (trackId in current.selectedTrackIds) {
      current.selectedTrackIds - trackId
    } else {
      current.selectedTrackIds + trackId
    }
    _mergeMixerState.value = current.copy(
      selectedTrackIds = nextSet,
      lastStatusMessage = null,
    )
  }

  fun setMergeStemGain(trackId: String, gain: Float) {
    val current = _mergeMixerState.value
    val nextGains = current.stemGains.toMutableMap().apply {
      put(trackId, gain.coerceIn(0f, 1.5f))
    }
    _mergeMixerState.value = current.copy(stemGains = nextGains)
  }

  fun setMergeLoopShorterStems(loop: Boolean) {
    _mergeMixerState.value = _mergeMixerState.value.copy(loopShorterStems = loop)
  }

  fun onMergeTitleChanged(title: String) {
    _mergeMixerState.value = _mergeMixerState.value.copy(customTitle = title)
  }

  fun autoPickBandStems() {
    val tracks = uiState.value.allTracks
    val picked = mutableSetOf<String>()
    tracks.firstOrNull { it.category.equals("Piano", ignoreCase = true) }?.let { picked.add(it.id) }
    tracks.firstOrNull { it.category.equals("Guitar", ignoreCase = true) }?.let { picked.add(it.id) }
    tracks.firstOrNull { it.category.equals("Drums", ignoreCase = true) }?.let { picked.add(it.id) }
    if (picked.size < 2) {
      tracks.filter { it.id !in picked }.take(3 - picked.size).forEach { picked.add(it.id) }
    }
    _mergeMixerState.value = _mergeMixerState.value.copy(
      selectedTrackIds = picked,
      lastStatusMessage = if (picked.size >= 2) {
        "Selected ${picked.size} instrument stems for mixdown"
      } else {
        "Generate at least 2 tracks across Piano, Guitar, or Drums to merge."
      },
    )
  }

  fun mergeSelectedTracks(
    saveAsMp3AfterMerge: Boolean = false,
    onRequireSafFallback: (GeneratedTrack) -> Unit = {},
  ) {
    val mergeSnap = _mergeMixerState.value
    if (mergeSnap.isMerging) return
    val allTracks = uiState.value.allTracks
    val selectedTracks = allTracks.filter { it.id in mergeSnap.selectedTrackIds }
    if (selectedTracks.size < 2) {
      _mergeMixerState.value = mergeSnap.copy(
        lastStatusMessage = "Select at least 2 tracks to merge.",
      )
      return
    }

    viewModelScope.launch {
      _mergeMixerState.value = _mergeMixerState.value.copy(isMerging = true, lastStatusMessage = null)
      _errorMessage.value = null
      try {
        val stemSpecs = selectedTracks.map { track ->
          MixStemSpec(
            track = track,
            gain = mergeSnap.stemGains[track.id] ?: 1.0f,
            loopToMatch = mergeSnap.loopShorterStems,
          )
        }
        val result = repository.mergeTracksToMaster(
          title = mergeSnap.customTitle,
          stems = stemSpecs,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = result.diagnostics
        playerController.loadTrack(result.track, autoPlay = true)

        if (saveAsMp3AfterMerge) {
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val mp3Config = AudioExportConfig(
              format = AudioExportFormat.MP3,
              bitrateKbps = 192,
              normalizeOutputGain = _autoPostGainEnabled.value,
              targetOutputLufs = _loudnessTarget.value.lufs,
            )
            val outcome = repository.exportTrackToMusicMp3(result.track, mp3Config)
            val kb = (outcome.fileSizeBytes / 1024L).coerceAtLeast(1L)
            val msg = "Merged \"${result.track.title}\" & saved MP3 ($kb KB) to ${outcome.locationLabel}"
            _mergeMixerState.value = _mergeMixerState.value.copy(
              isMerging = false,
              customTitle = "",
              lastStatusMessage = msg,
            )
            _quickMp3StatusMessage.value = msg
          } else {
            _mergeMixerState.value = _mergeMixerState.value.copy(
              isMerging = false,
              customTitle = "",
              lastStatusMessage = "Merged \"${result.track.title}\" — pick folder to save MP3",
            )
            onRequireSafFallback(result.track)
          }
        } else {
          val msg = "Merged ${selectedTracks.size} stems into \"${result.track.title}\""
          _mergeMixerState.value = _mergeMixerState.value.copy(
            isMerging = false,
            customTitle = "",
            lastStatusMessage = msg,
          )
          _quickMp3StatusMessage.value = msg
        }
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to merge tracks."
        _mergeMixerState.value = _mergeMixerState.value.copy(isMerging = false)
      }
    }
  }

  fun exportCurrentProjectMergedAudioToMp3(
    onRequireSafFallback: (GeneratedTrack) -> Unit = {},
  ) {
    val mergeSnap = _mergeMixerState.value
    if (mergeSnap.isMerging) return
    val allTracks = uiState.value.allTracks
    val selectedTracks = allTracks.filter { it.id in mergeSnap.selectedTrackIds }
    val stemsToExport = if (selectedTracks.size >= 2) {
      selectedTracks
    } else {
      allTracks.take(3).ifEmpty {
        _mergeMixerState.value = mergeSnap.copy(lastStatusMessage = "Generate or select at least 2 tracks to export merged MP3.")
        return
      }
    }

    viewModelScope.launch {
      _mergeMixerState.value = mergeSnap.copy(isMerging = true, lastStatusMessage = null)
      _errorMessage.value = null
      try {
        val stemSpecs = stemsToExport.map { track ->
          MixStemSpec(
            track = track,
            gain = mergeSnap.stemGains[track.id] ?: 1.0f,
            loopToMatch = mergeSnap.loopShorterStems,
          )
        }
        val config = AudioExportConfig(
          format = AudioExportFormat.MP3,
          bitrateKbps = 192,
          normalizeOutputGain = _autoPostGainEnabled.value,
          targetOutputLufs = _loudnessTarget.value.lufs,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
          val outcome = repository.exportCurrentProjectMergedAudioToMp3(
            title = mergeSnap.customTitle,
            stems = stemSpecs,
            config = config,
            targetLufs = _loudnessTarget.value.lufs,
            applyPostGain = _autoPostGainEnabled.value,
          )
          val kb = (outcome.fileSizeBytes / 1024L).coerceAtLeast(1L)
          val msg = "Exported merged project MP3 ($kb KB) to ${outcome.locationLabel}"
          _mergeMixerState.value = mergeSnap.copy(
            isMerging = false,
            customTitle = "",
            lastStatusMessage = msg,
          )
          _quickMp3StatusMessage.value = msg
        } else {
          val result = repository.mergeTracksToMaster(
            title = mergeSnap.customTitle,
            stems = stemSpecs,
            targetLufs = _loudnessTarget.value.lufs,
            applyPostGain = _autoPostGainEnabled.value,
          )
          _mergeMixerState.value = mergeSnap.copy(
            isMerging = false,
            customTitle = "",
            lastStatusMessage = "Merged project audio — pick folder to save MP3",
          )
          onRequireSafFallback(result.track)
        }
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to export merged project MP3."
        _mergeMixerState.value = mergeSnap.copy(isMerging = false)
      }
    }
  }

  fun quickSaveTrackAsMp3(
    track: GeneratedTrack,
    onRequireSafFallback: (GeneratedTrack) -> Unit = {},
  ) {
    if (_isQuickSavingMp3.value) return
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
      _exportConfig.value = AudioExportConfig(
        format = AudioExportFormat.MP3,
        bitrateKbps = 192,
        normalizeOutputGain = _autoPostGainEnabled.value,
        targetOutputLufs = _loudnessTarget.value.lufs,
      )
      onRequireSafFallback(track)
      return
    }

    viewModelScope.launch {
      _isQuickSavingMp3.value = true
      _errorMessage.value = null
      try {
        val config = AudioExportConfig(
          format = AudioExportFormat.MP3,
          bitrateKbps = 192,
          normalizeOutputGain = _autoPostGainEnabled.value,
          targetOutputLufs = _loudnessTarget.value.lufs,
        )
        val outcome = repository.exportTrackToMusicMp3(track, config)
        val kb = (outcome.fileSizeBytes / 1024L).coerceAtLeast(1L)
        _quickMp3StatusMessage.value = "Saved ${outcome.fileName} ($kb KB · 192 kbps MP3) to ${outcome.locationLabel}"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to save MP3."
      } finally {
        _isQuickSavingMp3.value = false
      }
    }
  }

  fun playSoundClipWithAudioEngine(track: GeneratedTrack, looping: Boolean = false) {
    studioClipEngine.playTrack(track = track, looping = looping)
  }

  fun stopSoundClipAudioEngine() {
    studioClipEngine.stop()
  }

  // ---------------------------------------------------------------------------
  // AI & Offline Music Producer (On-Device Offline Model + Gemini Cloud AI)
  // ---------------------------------------------------------------------------

  fun selectAiProducerEngine(engine: com.example.ai.AiProducerEngine) {
    _aiProducerState.value = _aiProducerState.value.copy(
      selectedEngine = engine,
      errorMessage = null,
    )
  }

  fun onAiProducerPromptChanged(prompt: String) {
    _aiProducerState.value = _aiProducerState.value.copy(
      promptInput = prompt,
      errorMessage = null,
    )
  }

  fun arrangeWithSelectedEngine() {
    val prompt = _aiProducerState.value.promptInput.trim()
    if (prompt.isEmpty() || _aiProducerState.value.isArranging) return
    val selectedEngine = _aiProducerState.value.selectedEngine

    viewModelScope.launch {
      _aiProducerState.value = _aiProducerState.value.copy(
        isArranging = true,
        errorMessage = null,
        statusMessage = null,
      )
      try {
        val arranger = when (selectedEngine) {
          com.example.ai.AiProducerEngine.OFFLINE_MODEL -> offlineMusicArranger
          com.example.ai.AiProducerEngine.GEMINI_CLOUD -> geminiMusicArranger
        }
        val plan = arranger.generateArrangement(prompt)
        _aiProducerState.value = _aiProducerState.value.copy(
          isArranging = false,
          currentPlan = plan,
          errorMessage = null,
          statusMessage = "${plan.engineLabel} arranged \"${plan.title}\" (${plan.bpm} BPM · ${plan.musicalKey})",
        )
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.e("SonicForgeViewModel", "Music arrangement failed ($selectedEngine)", e)
        _aiProducerState.value = _aiProducerState.value.copy(
          isArranging = false,
          errorMessage = e.localizedMessage ?: "Arrangement failed. Please try again or switch to Offline Model.",
        )
      }
    }
  }

  fun applyAiPlanToInstruments() {
    val plan = _aiProducerState.value.currentPlan ?: return
    _pianoState.value = _pianoState.value.copy(
      selectedVoice = plan.pianoVoice,
      selectedProgression = plan.pianoProgression,
      bpm = plan.bpm,
    )
    _guitarState.value = _guitarState.value.copy(
      selectedVoice = plan.guitarVoice,
      selectedChord = plan.guitarChords.firstOrNull() ?: GuitarChordShape.C_MAJ,
      chordSequence = plan.guitarChords,
      strumStyle = plan.guitarStrumStyle,
      bpm = plan.bpm,
    )
    stepSequencerEngine.selectDrumKit(plan.drumKit)
    stepSequencerEngine.applyPreset(plan.drumPreset)
    stepSequencerEngine.setManualBpm(plan.bpm)
    selectReverbPreset(plan.reverbPreset)
    _aiProducerState.value = _aiProducerState.value.copy(
      statusMessage = "Loaded ${plan.title} into Piano, Guitar, Drums & Reverb (${plan.bpm} BPM)",
    )
  }

  fun renderAiArrangementFullMix() {
    val plan = _aiProducerState.value.currentPlan ?: return
    if (_aiProducerState.value.isRenderingMix) return

    applyAiPlanToInstruments()
    viewModelScope.launch {
      _aiProducerState.value = _aiProducerState.value.copy(
        isRenderingMix = true,
        errorMessage = null,
        statusMessage = null,
      )
      try {
        val attachedTrackId = _aiProducerState.value.attachedSampleTrackId
        val attachedSampleTrack = attachedTrackId?.let { id ->
          uiState.value.allTracks.firstOrNull { it.id == id }
        }
        val session = repository.renderAiArrangementSession(
          plan = plan,
          attachedSampleTrack = attachedSampleTrack,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        _selectedCategory.value = GeneratedTrackRepository.ALL_CATEGORIES
        _showFavoritesOnly.value = false
        _selectedPlaylistId.value = null
        _activeBufferDiagnostics.value = session.masterDiagnostics
        _mergeMixerState.value = _mergeMixerState.value.copy(
          selectedTrackIds = session.stemTrackIds.toSet(),
          stemGains = _mergeMixerState.value.stemGains + session.stemGains,
          customTitle = plan.title,
          lastStatusMessage = "${plan.engineLabel} mix \"${session.masterTrack.title}\" rendered (${session.stemTrackIds.size} stems)",
        )
        playerController.loadTrack(session.masterTrack, autoPlay = true)
        viewModelScope.launch {
          historyRepository.recordTrackPlayed(session.masterTrack.id)
        }
        _aiProducerState.value = _aiProducerState.value.copy(
          isRenderingMix = false,
          statusMessage = "Rendered & playing \"${session.masterTrack.title}\" (${session.stemTrackIds.size} synced stems)",
        )
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.e("SonicForgeViewModel", "Failed to render AI arrangement mix", e)
        _aiProducerState.value = _aiProducerState.value.copy(
          isRenderingMix = false,
          errorMessage = e.localizedMessage ?: "Failed to render multi-track mix.",
        )
      }
    }
  }

  fun detachSampleFromAiMix() {
    _aiProducerState.value = _aiProducerState.value.copy(
      attachedSampleTrackId = null,
      attachedSampleTitle = null,
    )
  }

  // ---------------------------------------------------------------------------
  // Built-In Offline Music & Studio Samples Library (100% On-Device, Zero Keys)
  // ---------------------------------------------------------------------------

  fun onOfflineLibraryQueryChanged(query: String) {
    val category = _offlineMusicState.value.selectedCategory
    _offlineMusicState.value = _offlineMusicState.value.copy(
      searchQuery = query,
      items = com.example.audio.OfflineMusicLibrary.search(query = query, category = category),
      errorMessage = null,
    )
  }

  fun selectOfflineLibraryCategory(category: com.example.audio.OfflineSampleCategory) {
    val query = _offlineMusicState.value.searchQuery
    _offlineMusicState.value = _offlineMusicState.value.copy(
      selectedCategory = category,
      items = com.example.audio.OfflineMusicLibrary.search(query = query, category = category),
      errorMessage = null,
    )
  }

  fun previewOfflineMusicAsset(asset: com.example.audio.OfflineMusicAsset) {
    if (_offlineMusicState.value.previewingAssetId == asset.id) {
      studioClipEngine.stop()
      _offlineMusicState.value = _offlineMusicState.value.copy(previewingAssetId = null)
      return
    }
    viewModelScope.launch {
      _offlineMusicState.value = _offlineMusicState.value.copy(
        previewingAssetId = asset.id,
        errorMessage = null,
      )
      try {
        val cacheDir = java.io.File(getApplication<Application>().cacheDir, "offline_music_pack")
        val wavFile = com.example.audio.OfflineMusicSynthesizerDsp.ensureRenderedWav(
          asset = asset,
          cacheDir = cacheDir,
        )
        studioClipEngine.playFile(
          file = wavFile,
          clipId = "offline-${asset.id}",
          title = asset.title,
          looping = false,
          onCompleted = {
            if (_offlineMusicState.value.previewingAssetId == asset.id) {
              _offlineMusicState.value = _offlineMusicState.value.copy(previewingAssetId = null)
            }
          },
        )
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.e("SonicForgeViewModel", "Failed to preview offline music asset", e)
        _offlineMusicState.value = _offlineMusicState.value.copy(
          previewingAssetId = null,
          errorMessage = e.localizedMessage ?: "Unable to preview offline music sample.",
        )
      }
    }
  }

  fun importOfflineMusicAssetToMix(asset: com.example.audio.OfflineMusicAsset) {
    if (_offlineMusicState.value.importingAssetId != null) return
    viewModelScope.launch {
      _offlineMusicState.value = _offlineMusicState.value.copy(
        importingAssetId = asset.id,
        errorMessage = null,
        statusMessage = null,
      )
      try {
        val cacheDir = java.io.File(getApplication<Application>().cacheDir, "offline_music_pack")
        val wavFile = com.example.audio.OfflineMusicSynthesizerDsp.ensureRenderedWav(
          asset = asset,
          cacheDir = cacheDir,
        )
        val imported = repository.importOfflineMusicAsset(
          asset = asset,
          renderedWavFile = wavFile,
          targetLufs = _loudnessTarget.value.lufs,
          applyPostGain = _autoPostGainEnabled.value,
        )
        val recommendedGain = if (asset.category == com.example.audio.OfflineSampleCategory.TEXTURES) {
          0.55f
        } else {
          0.78f
        }
        _activeBufferDiagnostics.value = imported.diagnostics
        _mergeMixerState.value = _mergeMixerState.value.copy(
          selectedTrackIds = _mergeMixerState.value.selectedTrackIds + imported.track.id,
          stemGains = _mergeMixerState.value.stemGains + (imported.track.id to recommendedGain),
          lastStatusMessage = "Added offline track \"${imported.track.title}\" to Multi-Track Merger",
        )
        _aiProducerState.value = _aiProducerState.value.copy(
          attachedSampleTrackId = imported.track.id,
          attachedSampleTitle = imported.track.title,
        )
        _offlineMusicState.value = _offlineMusicState.value.copy(
          importingAssetId = null,
          importedAssetIds = _offlineMusicState.value.importedAssetIds + asset.id,
          statusMessage = "Added \"${imported.track.title}\" to Library & attached to AI/Multi-Track Mixer",
        )
        playerController.loadTrack(imported.track, autoPlay = true)
        viewModelScope.launch {
          historyRepository.recordTrackPlayed(imported.track.id)
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.e("SonicForgeViewModel", "Failed to import offline music asset", e)
        _offlineMusicState.value = _offlineMusicState.value.copy(
          importingAssetId = null,
          errorMessage = e.localizedMessage ?: "Failed to add offline music to mix.",
        )
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Room Database -> Device Storage Directory Export (Scoped MediaStore & SAF)
  // ---------------------------------------------------------------------------

  fun setRoomExportFormat(format: AudioExportFormat) {
    _roomStorageExportState.value = _roomStorageExportState.value.copy(
      format = format,
      errorMessage = null,
    )
  }

  fun dismissRoomExportStatus() {
    _roomStorageExportState.value = _roomStorageExportState.value.copy(
      statusMessage = null,
      errorMessage = null,
    )
  }

  fun prepareRoomDirectoryPickerExport(trackIds: Set<String>? = null) {
    pendingRoomExportTrackIds = trackIds?.takeIf { it.isNotEmpty() }
  }

  fun exportRoomTracksToDeviceMusic(
    trackIds: Set<String>? = null,
    onRequireDirectoryPicker: () -> Unit = {},
  ) {
    if (_roomStorageExportState.value.isExporting) return
    val targetIds = trackIds?.takeIf { it.isNotEmpty() }
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
      pendingRoomExportTrackIds = targetIds
      onRequireDirectoryPicker()
      return
    }

    val format = _roomStorageExportState.value.format
    viewModelScope.launch {
      _roomStorageExportState.value = _roomStorageExportState.value.copy(
        isExporting = true,
        exportedCount = 0,
        totalCount = 0,
        currentTrackTitle = "",
        statusMessage = null,
        errorMessage = null,
      )
      try {
        val config = AudioExportConfig(
          format = format,
          bitrateKbps = 192,
          normalizeOutputGain = _autoPostGainEnabled.value,
          targetOutputLufs = _loudnessTarget.value.lufs,
        )
        val result = repository.exportRoomTracksToDeviceMusicDirectory(
          trackIds = targetIds,
          config = config,
          onProgress = { progress ->
            _roomStorageExportState.value = _roomStorageExportState.value.copy(
              exportedCount = progress.exportedCount,
              totalCount = progress.totalCount,
              currentTrackTitle = progress.currentTrackTitle,
            )
          },
        )
        val sizeLabel = com.example.audio.RoomTracksStorageExporter.formatTotalSizeLabel(result.totalBytes)
        val plural = if (result.exportedCount == 1) "track" else "tracks"
        val failSuffix = if (result.failedCount > 0) " (${result.failedCount} failed)" else ""
        _roomStorageExportState.value = _roomStorageExportState.value.copy(
          isExporting = false,
          exportedCount = result.exportedCount,
          totalCount = result.exportedCount,
          statusMessage = "Exported ${result.exportedCount} $plural (${result.format.displayName} · $sizeLabel) to ${result.directoryLabel}$failSuffix",
          errorMessage = null,
        )
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.e("SonicForgeViewModel", "Room tracks MediaStore export failed", e)
        _roomStorageExportState.value = _roomStorageExportState.value.copy(
          isExporting = false,
          errorMessage = e.localizedMessage ?: "Failed to export Room tracks to device storage.",
        )
      }
    }
  }

  fun exportPendingRoomTracksToDirectoryTree(treeUri: Uri) {
    if (_roomStorageExportState.value.isExporting) return
    val targetIds = pendingRoomExportTrackIds
    pendingRoomExportTrackIds = null
    val format = _roomStorageExportState.value.format

    viewModelScope.launch {
      _roomStorageExportState.value = _roomStorageExportState.value.copy(
        isExporting = true,
        exportedCount = 0,
        totalCount = 0,
        currentTrackTitle = "",
        statusMessage = null,
        errorMessage = null,
      )
      try {
        val config = AudioExportConfig(
          format = format,
          bitrateKbps = 192,
          normalizeOutputGain = _autoPostGainEnabled.value,
          targetOutputLufs = _loudnessTarget.value.lufs,
        )
        val result = repository.exportRoomTracksToStorageTreeUri(
          treeUri = treeUri,
          trackIds = targetIds,
          config = config,
          onProgress = { progress ->
            _roomStorageExportState.value = _roomStorageExportState.value.copy(
              exportedCount = progress.exportedCount,
              totalCount = progress.totalCount,
              currentTrackTitle = progress.currentTrackTitle,
            )
          },
        )
        val sizeLabel = com.example.audio.RoomTracksStorageExporter.formatTotalSizeLabel(result.totalBytes)
        val plural = if (result.exportedCount == 1) "track" else "tracks"
        val failSuffix = if (result.failedCount > 0) " (${result.failedCount} failed)" else ""
        _roomStorageExportState.value = _roomStorageExportState.value.copy(
          isExporting = false,
          exportedCount = result.exportedCount,
          totalCount = result.exportedCount,
          statusMessage = "Exported ${result.exportedCount} $plural (${result.format.displayName} · $sizeLabel) to ${result.directoryLabel}$failSuffix",
          errorMessage = null,
        )
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.e("SonicForgeViewModel", "Room tracks SAF directory export failed", e)
        _roomStorageExportState.value = _roomStorageExportState.value.copy(
          isExporting = false,
          errorMessage = e.localizedMessage ?: "Failed to export Room tracks to selected directory.",
        )
      }
    }
  }

  fun exportHistoryItemToDeviceStorage(
    item: TrackHistoryItem,
    onRequireDirectoryPicker: () -> Unit = {},
  ) {
    exportRoomTracksToDeviceMusic(
      trackIds = setOf(item.trackId),
      onRequireDirectoryPicker = onRequireDirectoryPicker,
    )
  }

  override fun onCleared() {
    stepSequencerEngine.stop()
    metronomeEngine.stop()
    liveInstrumentEngine.release()
    studioClipEngine.release()
    super.onCleared()
  }
}
