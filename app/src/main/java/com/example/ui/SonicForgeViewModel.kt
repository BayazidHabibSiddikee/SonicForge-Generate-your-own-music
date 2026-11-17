package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TrackAutoTagResult
import com.example.audio.AudioDownloadProgress
import com.example.audio.AudioExportBitrateOption
import com.example.audio.AudioExportConfig
import com.example.audio.AudioExportFormat
import com.example.audio.AudioPlaybackState
import com.example.audio.AudioPlayerController
import com.example.audio.EqPreset
import com.example.audio.HfMusicSpace
import com.example.audio.LoudnessTarget
import com.example.audio.MetronomeEngine
import com.example.audio.MetronomeState
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import com.example.audio.RawBufferDiagnostics
import com.example.audio.ReverbEngineMode
import com.example.audio.ReverbPreset
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.StepSequencerEngine
import com.example.audio.StepSequencerState
import com.example.audio.TrackAudioEnvelope
import com.example.audio.VolumeEnvelopeDsp
import com.example.audio.WaveformInteractionMode
import com.example.data.GeneratedTrackRepository
import com.example.data.StudioProjectRepository
import com.example.data.ThemePreferencesRepository
import com.example.model.BulkRenameConfig
import com.example.model.GeneratedTrack
import com.example.model.Playlist
import com.example.model.StudioProject
import com.example.ui.theme.ThemeMode
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
import kotlinx.coroutines.launch

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
  val selectedEngine: GenerationEngine = GenerationEngine.HF_AUDIO_LDM,
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
)

@OptIn(ExperimentalCoroutinesApi::class)
class SonicForgeViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = GeneratedTrackRepository(application.applicationContext)
  private val projectRepository = StudioProjectRepository(application.applicationContext)
  private val themePreferences = ThemePreferencesRepository(application.applicationContext)
  private val playerController = AudioPlayerController.getInstance(application.applicationContext)
  private val stepSequencerEngine = StepSequencerEngine()
  private val metronomeEngine = MetronomeEngine()

  private val _promptInput = MutableStateFlow("")
  private val _selectedEngine = MutableStateFlow(GenerationEngine.HF_AUDIO_LDM)
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
      val validTrackIds = allTracks.map { it.id }.toSet()
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
        playerController.togglePlayPause()
      }
      return
    }
    playerController.loadTrack(track, autoPlay = autoPlay)
    refreshBufferDiagnostics(track)
  }

  fun togglePlayPause() {
    val tracks = uiState.value.allTracks
    val activeId = playerController.state.value.currentTrackId
    val trackToPlay = tracks.firstOrNull { it.id == activeId } ?: tracks.firstOrNull()
    if (trackToPlay == null) return

    if (activeId != trackToPlay.id || !playerController.state.value.isPrepared) {
      playerController.loadTrack(trackToPlay, autoPlay = true)
      refreshBufferDiagnostics(trackToPlay)
    } else {
      playerController.togglePlayPause()
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

  override fun onCleared() {
    stepSequencerEngine.stop()
    metronomeEngine.stop()
    super.onCleared()
  }
}
