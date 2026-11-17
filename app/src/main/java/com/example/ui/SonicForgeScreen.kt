package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.PlaceholderTrackAutoTagger
import com.example.ai.TrackAutoTagResult
import com.example.audio.AudioExportBitrateOption
import com.example.audio.AudioExportFormat
import com.example.audio.EqPreset
import com.example.audio.LoudnessTarget
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import com.example.audio.ReverbEngineMode
import com.example.audio.ReverbPreset
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.TrackAudioEnvelope
import com.example.audio.WaveformInteractionMode
import com.example.model.BulkRenameConfig
import com.example.model.GeneratedTrack
import com.example.model.Playlist
import com.example.ui.components.AudioUrlDownloadDialog
import com.example.ui.components.BulkRenameDialog
import com.example.ui.components.BulkSelectionBar
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.CustomAudioPlayer
import com.example.ui.components.MetronomeCard
import com.example.ui.components.PlaylistsShelfSection
import com.example.ui.components.SaveStudioProjectDialog
import com.example.ui.components.StepSequencerCard
import com.example.ui.components.StudioProjectsCard
import com.example.ui.components.TrackExportDialog
import com.example.ui.components.TrackPlaylistsDialog
import com.example.ui.components.TrackTrimDialog
import com.example.ui.components.WaveformVisualizer
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.ThemeMode

private val quickPresets = listOf(
  "Rainy lo-fi rhodes study beat 84 bpm",
  "Neon midnight synthwave drive 112 bpm",
  "Deep space ambient meditation pad 72 bpm",
  "Energetic cyber techno bassline 128 bpm",
  "Emotional cinematic piano theme 94 bpm",
)

private val suggestedCategories = listOf(
  "Lo-Fi Chill",
  "Synthwave",
  "Ambient",
  "Electronic",
  "Cinematic",
  "Focus",
  "Sleep",
  "Imported",
)

private val suggestedTags = listOf(
  "Chill",
  "Focus",
  "Sleep",
  "Night Drive",
  "Study",
  "Bass",
  "Ambient",
)

@Composable
fun SonicForgeScreen(
  uiState: SonicForgeUiState,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onSelectEngine: (GenerationEngine) -> Unit,
  onPromptChanged: (String) -> Unit,
  onApplyPreset: (String) -> Unit,
  onGenerateTrack: () -> Unit,
  onImportAudioClick: () -> Unit,
  onSelectCategory: (String) -> Unit,
  onSetShowFavoritesOnly: (Boolean) -> Unit = {},
  onSearchQueryChanged: (String) -> Unit,
  onSelectPlaylistFilter: (String?) -> Unit = {},
  onOpenCreatePlaylistDialog: () -> Unit = {},
  onDismissCreatePlaylistDialog: () -> Unit = {},
  onCreatePlaylist: (name: String, description: String, trackIds: List<String>) -> Unit = { _, _, _ -> },
  onPlayPlaylist: (Playlist) -> Unit = {},
  onDeletePlaylist: (String) -> Unit = {},
  onOpenTrackPlaylistsDialog: (GeneratedTrack) -> Unit = {},
  onDismissTrackPlaylistsDialog: () -> Unit = {},
  onSetTrackPlaylistMembership: (playlistId: String, trackId: String, isMember: Boolean) -> Unit = { _, _, _ -> },
  onCreatePlaylistForTrack: (String) -> Unit = {},
  onToggleTrackFavorite: (GeneratedTrack) -> Unit = {},
  onEditOrganizationClick: (GeneratedTrack) -> Unit,
  onDismissEditOrganization: () -> Unit,
  onSaveOrganization: (trackId: String, category: String, tags: List<String>) -> Unit,
  onTrimTrackClick: (GeneratedTrack) -> Unit,
  onDismissTrimDialog: () -> Unit,
  onConfirmTrim: (trackId: String, startMs: Long, endMs: Long, saveAsCopy: Boolean) -> Unit,
  onExportMp3Click: (GeneratedTrack) -> Unit,
  onDismissExportDialog: () -> Unit,
  onSelectExportFormat: (AudioExportFormat) -> Unit = {},
  onSelectExportBitrateOption: (AudioExportBitrateOption) -> Unit = {},
  onToggleExportNormalizeGain: (Boolean) -> Unit = {},
  onExportMp3ToMusic: (GeneratedTrack) -> Unit,
  onExportMp3ToFolder: (GeneratedTrack) -> Unit,
  onShareMp3: (GeneratedTrack) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
  onSelectTrack: (GeneratedTrack) -> Unit,
  onDeleteTrack: (String) -> Unit,
  onTogglePlayPause: () -> Unit,
  onBeginSeek: () -> Unit,
  onPreviewSeek: (Long) -> Unit,
  onCommitSeek: (Long) -> Unit,
  onSeekBy: (Long) -> Unit,
  onSkipPrevious: () -> Unit,
  onSkipNext: () -> Unit,
  onToggleLoop: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onToggleMute: () -> Unit,
  onPitchChange: (Float) -> Unit = {},
  onStepPitch: (Int) -> Unit = {},
  onResetPitch: () -> Unit = {},
  onSelectReverbPreset: (ReverbPreset) -> Unit = {},
  onReverbWetMixChange: (Float) -> Unit = {},
  onReverbRoomSizeChange: (Float) -> Unit = {},
  onReverbDampingChange: (Float) -> Unit = {},
  onReverbEngineModeChange: (ReverbEngineMode) -> Unit = {},
  onResetReverb: () -> Unit = {},
  onSaveReverbCopy: () -> Unit = {},
  onEqualizerEnabledChange: (Boolean) -> Unit = {},
  onSelectEqualizerPreset: (EqPreset) -> Unit = {},
  onEqualizerBandGainChange: (bandIndex: Int, gainDb: Float) -> Unit = { _, _ -> },
  onResetEqualizer: () -> Unit = {},
  onAutoPostGainEnabledChange: (Boolean) -> Unit = {},
  onSelectLoudnessTarget: (LoudnessTarget) -> Unit = {},
  onNormalizeActiveTrack: () -> Unit = {},
  onNormalizeLibrary: () -> Unit = {},
  onToggleSelectionMode: () -> Unit = {},
  onEnterSelectionWithTrack: (String) -> Unit = {},
  onToggleTrackSelection: (String) -> Unit = {},
  onToggleSelectAllVisible: () -> Unit = {},
  onExitSelectionMode: () -> Unit = {},
  onOpenBulkRenameDialog: () -> Unit = {},
  onDismissBulkRenameDialog: () -> Unit = {},
  onConfirmBulkRename: (BulkRenameConfig) -> Unit = {},
  onSuggestAutoTags: (track: GeneratedTrack, onSuggestionReady: (TrackAutoTagResult) -> Unit) -> Unit = { _, _ -> },
  onDismissAutoTagBanner: () -> Unit = {},
  onOpenUrlDownloadDialog: () -> Unit = {},
  onDismissUrlDownloadDialog: () -> Unit = {},
  onStartSegmentedUrlDownload: (url: String, connections: Int) -> Unit = { _, _ -> },
  onSubmitAria2RpcDownload: (rpcUrl: String, sourceUrl: String, secret: String, connections: Int) -> Unit = { _, _, _, _ -> },
  onCrossfadeEnabledChange: (Boolean) -> Unit = {},
  onCrossfadeDurationChange: (Float) -> Unit = {},
  onSelectCrossfadePreset: (Float) -> Unit = {},
  onSelectWaveformInteractionMode: (WaveformInteractionMode) -> Unit = {},
  onPaintTrackEnvelopePoint: (fraction: Float, gain: Float) -> Unit = { _, _ -> },
  onMuteTrackSegment: (startFraction: Float, endFraction: Float) -> Unit = { _, _ -> },
  onToggleMuteAtTap: (fraction: Float) -> Unit = {},
  onResetTrackAudioEnvelope: () -> Unit = {},
  onSetBottomWaveformVisible: (Boolean) -> Unit = {},
  onToggleSequencerStep: (SequencerLane, Int) -> Unit = { _, _ -> },
  onToggleSequencerPlayStop: () -> Unit = {},
  onClearSequencerPattern: () -> Unit = {},
  onApplySequencerPreset: (SequencerPreset) -> Unit = {},
  onStepSequencerBpm: (Int) -> Unit = {},
  onToggleSequencerBpmSync: () -> Unit = {},
  onCycleSequencerSwing: () -> Unit = {},
  onRenderSequencerToLibrary: () -> Unit = {},
  onToggleMetronomePlayStop: () -> Unit = {},
  onStepMetronomeBpm: (Int) -> Unit = {},
  onToggleMetronomeBpmSync: () -> Unit = {},
  onMetronomeVolumeChange: (Float) -> Unit = {},
  onMetronomePitchHzChange: (Float) -> Unit = {},
  onSelectMetronomeToneStyle: (MetronomeTone) -> Unit = {},
  onSelectMetronomeTimeSignature: (MetronomeTimeSignature) -> Unit = {},
  onToggleMetronomeAccentDownbeat: () -> Unit = {},
  onTapMetronomeTempo: () -> Unit = {},
  onOpenSaveProjectDialog: () -> Unit = {},
  onDismissSaveProjectDialog: () -> Unit = {},
  onConfirmSaveProject: (name: String, notes: String, selectedClipIds: List<String>) -> Unit = { _, _, _ -> },
  onQuickSaveActiveProject: () -> Unit = {},
  onLoadProject: (String) -> Unit = {},
  onDeleteProject: (String) -> Unit = {},
  onDismissProjectStatus: () -> Unit = {},
) {
  val colorScheme = MaterialTheme.colorScheme

  uiState.editingTrack?.let { trackToEdit ->
    TrackOrganizationDialog(
      track = trackToEdit,
      isSuggestingTags = uiState.isSuggestingTags,
      onSuggestAutoTags = { onSuggestReady ->
        onSuggestAutoTags(trackToEdit, onSuggestReady)
      },
      onDismiss = onDismissEditOrganization,
      onSave = { category, tags ->
        onSaveOrganization(trackToEdit.id, category, tags)
      },
    )
  }

  uiState.trimmingTrack?.let { trackToTrim ->
    TrackTrimDialog(
      track = trackToTrim,
      isTrimming = uiState.isTrimming,
      onDismiss = onDismissTrimDialog,
      onConfirmTrim = { startMs, endMs, saveAsCopy ->
        onConfirmTrim(trackToTrim.id, startMs, endMs, saveAsCopy)
      },
    )
  }

  uiState.exportingTrack?.let { trackToExport ->
    TrackExportDialog(
      track = trackToExport,
      exportConfig = uiState.exportConfig,
      isExporting = uiState.isExporting,
      exportStatusMessage = uiState.exportStatusMessage,
      exportErrorMessage = uiState.exportErrorMessage,
      onSelectFormat = onSelectExportFormat,
      onSelectBitrateOption = onSelectExportBitrateOption,
      onToggleNormalizeGain = onToggleExportNormalizeGain,
      onDismiss = onDismissExportDialog,
      onSaveToMusic = { onExportMp3ToMusic(trackToExport) },
      onChooseFolder = { onExportMp3ToFolder(trackToExport) },
      onShareMp3 = { onShareMp3(trackToExport) },
    )
  }

  if (uiState.showCreatePlaylistDialog) {
    CreatePlaylistDialog(
      availableTracks = uiState.allTracks,
      preselectedTrackIds = uiState.preselectedPlaylistTrackIds,
      onDismiss = onDismissCreatePlaylistDialog,
      onCreatePlaylist = onCreatePlaylist,
    )
  }

  uiState.playlistAssignmentTrack?.let { assignTrack ->
    TrackPlaylistsDialog(
      track = assignTrack,
      playlists = uiState.playlists,
      onDismiss = onDismissTrackPlaylistsDialog,
      onToggleTrackInPlaylist = onSetTrackPlaylistMembership,
      onCreateNewPlaylistForTrack = onCreatePlaylistForTrack,
    )
  }

  if (uiState.showBulkRenameDialog && uiState.selectedTracksForBulkRename.isNotEmpty()) {
    BulkRenameDialog(
      selectedTracks = uiState.selectedTracksForBulkRename,
      isRenaming = uiState.isBulkRenaming,
      onDismiss = onDismissBulkRenameDialog,
      onConfirmRename = onConfirmBulkRename,
    )
  }

  if (uiState.showUrlDownloadDialog) {
    AudioUrlDownloadDialog(
      isDownloading = uiState.isDownloadingUrl,
      progress = uiState.downloadProgress,
      statusMessage = uiState.downloadStatusMessage,
      errorMessage = uiState.downloadErrorMessage,
      onDismiss = onDismissUrlDownloadDialog,
      onStartDirectDownload = onStartSegmentedUrlDownload,
      onSubmitAria2Rpc = onSubmitAria2RpcDownload,
    )
  }

  if (uiState.showSaveProjectDialog) {
    val activeProject = uiState.projects.firstOrNull { it.id == uiState.activeProjectId }
    val defaultName = activeProject?.let { "${it.name} Copy" }
      ?: uiState.currentTrack?.title?.let { "$it Session" }
      ?: "Studio Session ${uiState.projects.size + 1}"
    val defaultClipIds = buildSet {
      uiState.currentTrack?.id?.let { add(it) }
      if (uiState.tracks.isNotEmpty()) {
        addAll(uiState.tracks.take(4).map { it.id })
      } else {
        addAll(uiState.allTracks.take(4).map { it.id })
      }
    }
    SaveStudioProjectDialog(
      defaultName = defaultName,
      defaultNotes = activeProject?.notes.orEmpty(),
      availableTracks = uiState.allTracks,
      defaultSelectedClipIds = defaultClipIds,
      isSaving = uiState.isSavingProject,
      onDismiss = onDismissSaveProjectDialog,
      onConfirmSave = onConfirmSaveProject,
    )
  }

  Scaffold(
    containerColor = colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = {
      if (uiState.showBottomWaveformBar) {
        BottomWaveformBar(
          track = uiState.currentTrack,
          playbackState = uiState.playback,
          canSkipNext = uiState.canSkipNext,
          onTogglePlayPause = onTogglePlayPause,
          onSkipNext = onSkipNext,
          onBeginSeek = onBeginSeek,
          onPreviewSeek = onPreviewSeek,
          onCommitSeek = onCommitSeek,
          onEliminate = { onSetBottomWaveformVisible(false) },
        )
      }
    },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      val isWideLayout = maxWidth >= 840.dp

      if (isWideLayout) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
          LazyColumn(
            modifier = Modifier
              .weight(1.1f)
              .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            item {
              StudioHeader(
                themeMode = uiState.themeMode,
                onSelectThemeMode = onSelectThemeMode,
                showRestoreWaveformButton = !uiState.showBottomWaveformBar,
                onRestoreBottomWaveform = { onSetBottomWaveformVisible(true) },
              )
            }
            item {
              BackgroundPermissionsCard(
                notificationPermission = uiState.notificationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
              )
            }
            item {
              PromptSynthesizerCard(
                promptInput = uiState.promptInput,
                selectedEngine = uiState.selectedEngine,
                isGenerating = uiState.isGenerating,
                errorMessage = uiState.errorMessage,
                onSelectEngine = onSelectEngine,
                onPromptChanged = onPromptChanged,
                onApplyPreset = onApplyPreset,
                onGenerateTrack = onGenerateTrack,
              )
            }
            item {
              StepSequencerCard(
                state = uiState.stepSequencer,
                onToggleStep = onToggleSequencerStep,
                onTogglePlayStop = onToggleSequencerPlayStop,
                onClearPattern = onClearSequencerPattern,
                onApplyPreset = onApplySequencerPreset,
                onStepBpm = onStepSequencerBpm,
                onToggleBpmSync = onToggleSequencerBpmSync,
                onCycleSwing = onCycleSequencerSwing,
                onRenderToLibrary = onRenderSequencerToLibrary,
              )
            }
            item {
              MetronomeCard(
                state = uiState.metronome,
                onTogglePlayStop = onToggleMetronomePlayStop,
                onStepBpm = onStepMetronomeBpm,
                onToggleBpmSync = onToggleMetronomeBpmSync,
                onVolumeChange = onMetronomeVolumeChange,
                onPitchHzChange = onMetronomePitchHzChange,
                onSelectToneStyle = onSelectMetronomeToneStyle,
                onSelectTimeSignature = onSelectMetronomeTimeSignature,
                onToggleAccentDownbeat = onToggleMetronomeAccentDownbeat,
                onTapTempo = onTapMetronomeTempo,
              )
            }
            item {
              StudioProjectsCard(
                projects = uiState.projects,
                activeProjectId = uiState.activeProjectId,
                currentTrack = uiState.currentTrack,
                allTracks = uiState.allTracks,
                playbackState = uiState.playback,
                sequencerState = uiState.stepSequencer,
                isSavingProject = uiState.isSavingProject,
                statusMessage = uiState.projectStatusMessage,
                onOpenSaveDialog = onOpenSaveProjectDialog,
                onQuickSaveProject = onQuickSaveActiveProject,
                onLoadProject = onLoadProject,
                onDeleteProject = onDeleteProject,
                onDismissStatus = onDismissProjectStatus,
              )
            }
            uiState.latestAutoTagResult?.let { tagResult ->
              item {
                AiAutoTagNoticeCard(
                  result = tagResult,
                  onDismiss = onDismissAutoTagBanner,
                )
              }
            }
            item {
              TrackLibrarySectionHeader(
                trackCount = uiState.tracks.size,
                hasAnyTracks = uiState.allTracks.isNotEmpty(),
                isSelectionMode = uiState.isSelectionMode,
                onToggleSelectionMode = onToggleSelectionMode,
                onDownloadUrlClick = onOpenUrlDownloadDialog,
                onImportAudioClick = onImportAudioClick,
              )
            }
            item {
              PlaylistsShelfSection(
                playlists = uiState.playlists,
                selectedPlaylistId = uiState.selectedPlaylistId,
                onCreatePlaylistClick = onOpenCreatePlaylistDialog,
                onSelectPlaylist = onSelectPlaylistFilter,
                onPlayPlaylist = onPlayPlaylist,
                onDeletePlaylist = onDeletePlaylist,
              )
            }
            item {
              LibraryOrganizationControls(
                categories = uiState.availableCategories,
                selectedCategory = uiState.selectedCategory,
                showFavoritesOnly = uiState.showFavoritesOnly,
                totalTrackCount = uiState.allTracks.size,
                favoriteTrackCount = uiState.favoriteTrackCount,
                searchQuery = uiState.searchQuery,
                onSelectCategory = onSelectCategory,
                onSetShowFavoritesOnly = onSetShowFavoritesOnly,
                onSearchQueryChanged = onSearchQueryChanged,
              )
            }
            if (uiState.isSelectionMode) {
              item {
                val visibleIds = uiState.tracks.map { it.id }
                val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in uiState.selectedTrackIds }
                BulkSelectionBar(
                  selectedCount = uiState.selectedTrackIds.size,
                  allVisibleSelected = allVisibleSelected,
                  onToggleSelectAll = onToggleSelectAllVisible,
                  onOpenBulkRename = onOpenBulkRenameDialog,
                  onExitSelectionMode = onExitSelectionMode,
                )
              }
            }
            if (uiState.isInitializing) {
              item { LoadingTracksPlaceholder() }
            } else if (uiState.tracks.isEmpty()) {
              item {
                EmptyTracksCard(
                  showFavoritesOnly = uiState.showFavoritesOnly,
                  selectedPlaylistName = uiState.selectedPlaylist?.name,
                  onShowAllTracks = {
                    onSelectPlaylistFilter(null)
                    onSetShowFavoritesOnly(false)
                  },
                )
              }
            } else {
              items(uiState.tracks, key = { it.id }) { track ->
                val isSelected = track.id == uiState.currentTrack?.id
                val isPlayingThis = isSelected && uiState.playback.isPlaying
                val isBulkSelected = track.id in uiState.selectedTrackIds
                GeneratedTrackRow(
                  track = track,
                  isSelected = isSelected,
                  isPlaying = isPlayingThis,
                  isSelectionMode = uiState.isSelectionMode,
                  isBulkSelected = isBulkSelected,
                  onToggleBulkSelect = { onToggleTrackSelection(track.id) },
                  onLongClick = { onEnterSelectionWithTrack(track.id) },
                  onClick = {
                    if (isSelected) onTogglePlayPause() else onSelectTrack(track)
                  },
                  onToggleFavorite = { onToggleTrackFavorite(track) },
                  onAddToPlaylist = { onOpenTrackPlaylistsDialog(track) },
                  onEditOrganization = { onEditOrganizationClick(track) },
                  onTrimTrack = { onTrimTrackClick(track) },
                  onExportMp3 = { onExportMp3Click(track) },
                  onDelete = { onDeleteTrack(track.id) },
                )
              }
            }
          }

          Column(
            modifier = Modifier
              .weight(0.95f)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = stringResource(R.string.player_section_title),
              style = MaterialTheme.typography.titleLarge,
              color = colorScheme.onBackground,
            )
            CustomAudioPlayer(
              track = uiState.currentTrack,
              playbackState = uiState.playback,
              bufferDiagnostics = uiState.activeBufferDiagnostics,
              canSkipPrevious = uiState.canSkipPrevious,
              canSkipNext = uiState.canSkipNext,
              onTogglePlayPause = onTogglePlayPause,
              onBeginSeek = onBeginSeek,
              onPreviewSeek = onPreviewSeek,
              onCommitSeek = onCommitSeek,
              onSeekBy = onSeekBy,
              onSkipPrevious = onSkipPrevious,
              onSkipNext = onSkipNext,
              onToggleLoop = onToggleLoop,
              onVolumeChange = onVolumeChange,
              onToggleMute = onToggleMute,
              onPitchChange = onPitchChange,
              onStepPitch = onStepPitch,
              onResetPitch = onResetPitch,
              onSelectReverbPreset = onSelectReverbPreset,
              onReverbWetMixChange = onReverbWetMixChange,
              onReverbRoomSizeChange = onReverbRoomSizeChange,
              onReverbDampingChange = onReverbDampingChange,
              onReverbEngineModeChange = onReverbEngineModeChange,
              onResetReverb = onResetReverb,
              onSaveReverbCopy = onSaveReverbCopy,
              isApplyingReverb = uiState.isApplyingReverb,
              onEqualizerEnabledChange = onEqualizerEnabledChange,
              onSelectEqualizerPreset = onSelectEqualizerPreset,
              onEqualizerBandGainChange = onEqualizerBandGainChange,
              onResetEqualizer = onResetEqualizer,
              loudnessTarget = uiState.loudnessTarget,
              autoPostGainEnabled = uiState.autoPostGainEnabled,
              isNormalizing = uiState.isNormalizing,
              onAutoPostGainEnabledChange = onAutoPostGainEnabledChange,
              onSelectLoudnessTarget = onSelectLoudnessTarget,
              onNormalizeActiveTrack = onNormalizeActiveTrack,
              onNormalizeLibrary = onNormalizeLibrary,
              onCrossfadeEnabledChange = onCrossfadeEnabledChange,
              onCrossfadeDurationChange = onCrossfadeDurationChange,
              onSelectCrossfadePreset = onSelectCrossfadePreset,
              interactionMode = uiState.waveformInteractionMode,
              onSelectInteractionMode = onSelectWaveformInteractionMode,
              onPaintEnvelopePoint = onPaintTrackEnvelopePoint,
              onMuteSegment = onMuteTrackSegment,
              onToggleMuteTap = onToggleMuteAtTap,
              onResetAudioEnvelope = onResetTrackAudioEnvelope,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets(0.dp)),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            StudioHeader(
              themeMode = uiState.themeMode,
              onSelectThemeMode = onSelectThemeMode,
              showRestoreWaveformButton = !uiState.showBottomWaveformBar,
              onRestoreBottomWaveform = { onSetBottomWaveformVisible(true) },
            )
          }

          item {
            CustomAudioPlayer(
              track = uiState.currentTrack,
              playbackState = uiState.playback,
              bufferDiagnostics = uiState.activeBufferDiagnostics,
              canSkipPrevious = uiState.canSkipPrevious,
              canSkipNext = uiState.canSkipNext,
              onTogglePlayPause = onTogglePlayPause,
              onBeginSeek = onBeginSeek,
              onPreviewSeek = onPreviewSeek,
              onCommitSeek = onCommitSeek,
              onSeekBy = onSeekBy,
              onSkipPrevious = onSkipPrevious,
              onSkipNext = onSkipNext,
              onToggleLoop = onToggleLoop,
              onVolumeChange = onVolumeChange,
              onToggleMute = onToggleMute,
              onPitchChange = onPitchChange,
              onStepPitch = onStepPitch,
              onResetPitch = onResetPitch,
              onSelectReverbPreset = onSelectReverbPreset,
              onReverbWetMixChange = onReverbWetMixChange,
              onReverbRoomSizeChange = onReverbRoomSizeChange,
              onReverbDampingChange = onReverbDampingChange,
              onReverbEngineModeChange = onReverbEngineModeChange,
              onResetReverb = onResetReverb,
              onSaveReverbCopy = onSaveReverbCopy,
              isApplyingReverb = uiState.isApplyingReverb,
              onEqualizerEnabledChange = onEqualizerEnabledChange,
              onSelectEqualizerPreset = onSelectEqualizerPreset,
              onEqualizerBandGainChange = onEqualizerBandGainChange,
              onResetEqualizer = onResetEqualizer,
              loudnessTarget = uiState.loudnessTarget,
              autoPostGainEnabled = uiState.autoPostGainEnabled,
              isNormalizing = uiState.isNormalizing,
              onAutoPostGainEnabledChange = onAutoPostGainEnabledChange,
              onSelectLoudnessTarget = onSelectLoudnessTarget,
              onNormalizeActiveTrack = onNormalizeActiveTrack,
              onNormalizeLibrary = onNormalizeLibrary,
              onCrossfadeEnabledChange = onCrossfadeEnabledChange,
              onCrossfadeDurationChange = onCrossfadeDurationChange,
              onSelectCrossfadePreset = onSelectCrossfadePreset,
              interactionMode = uiState.waveformInteractionMode,
              onSelectInteractionMode = onSelectWaveformInteractionMode,
              onPaintEnvelopePoint = onPaintTrackEnvelopePoint,
              onMuteSegment = onMuteTrackSegment,
              onToggleMuteTap = onToggleMuteAtTap,
              onResetAudioEnvelope = onResetTrackAudioEnvelope,
            )
          }

          item {
            BackgroundPermissionsCard(
              notificationPermission = uiState.notificationPermission,
              onRequestNotificationPermission = onRequestNotificationPermission,
              onOpenNotificationSettings = onOpenNotificationSettings,
            )
          }

          item {
            PromptSynthesizerCard(
              promptInput = uiState.promptInput,
              selectedEngine = uiState.selectedEngine,
              isGenerating = uiState.isGenerating,
              errorMessage = uiState.errorMessage,
              onSelectEngine = onSelectEngine,
              onPromptChanged = onPromptChanged,
              onApplyPreset = onApplyPreset,
              onGenerateTrack = onGenerateTrack,
            )
          }

          item {
            StepSequencerCard(
              state = uiState.stepSequencer,
              onToggleStep = onToggleSequencerStep,
              onTogglePlayStop = onToggleSequencerPlayStop,
              onClearPattern = onClearSequencerPattern,
              onApplyPreset = onApplySequencerPreset,
              onStepBpm = onStepSequencerBpm,
              onToggleBpmSync = onToggleSequencerBpmSync,
              onCycleSwing = onCycleSequencerSwing,
              onRenderToLibrary = onRenderSequencerToLibrary,
            )
          }

          item {
            MetronomeCard(
              state = uiState.metronome,
              onTogglePlayStop = onToggleMetronomePlayStop,
              onStepBpm = onStepMetronomeBpm,
              onToggleBpmSync = onToggleMetronomeBpmSync,
              onVolumeChange = onMetronomeVolumeChange,
              onPitchHzChange = onMetronomePitchHzChange,
              onSelectToneStyle = onSelectMetronomeToneStyle,
              onSelectTimeSignature = onSelectMetronomeTimeSignature,
              onToggleAccentDownbeat = onToggleMetronomeAccentDownbeat,
              onTapTempo = onTapMetronomeTempo,
            )
          }

          item {
            StudioProjectsCard(
              projects = uiState.projects,
              activeProjectId = uiState.activeProjectId,
              currentTrack = uiState.currentTrack,
              allTracks = uiState.allTracks,
              playbackState = uiState.playback,
              sequencerState = uiState.stepSequencer,
              isSavingProject = uiState.isSavingProject,
              statusMessage = uiState.projectStatusMessage,
              onOpenSaveDialog = onOpenSaveProjectDialog,
              onQuickSaveProject = onQuickSaveActiveProject,
              onLoadProject = onLoadProject,
              onDeleteProject = onDeleteProject,
              onDismissStatus = onDismissProjectStatus,
            )
          }

          uiState.latestAutoTagResult?.let { tagResult ->
            item {
              AiAutoTagNoticeCard(
                result = tagResult,
                onDismiss = onDismissAutoTagBanner,
              )
            }
          }

          item {
            TrackLibrarySectionHeader(
              trackCount = uiState.tracks.size,
              hasAnyTracks = uiState.allTracks.isNotEmpty(),
              isSelectionMode = uiState.isSelectionMode,
              onToggleSelectionMode = onToggleSelectionMode,
              onDownloadUrlClick = onOpenUrlDownloadDialog,
              onImportAudioClick = onImportAudioClick,
            )
          }

          item {
            PlaylistsShelfSection(
              playlists = uiState.playlists,
              selectedPlaylistId = uiState.selectedPlaylistId,
              onCreatePlaylistClick = onOpenCreatePlaylistDialog,
              onSelectPlaylist = onSelectPlaylistFilter,
              onPlayPlaylist = onPlayPlaylist,
              onDeletePlaylist = onDeletePlaylist,
            )
          }

          item {
            LibraryOrganizationControls(
              categories = uiState.availableCategories,
              selectedCategory = uiState.selectedCategory,
              showFavoritesOnly = uiState.showFavoritesOnly,
              totalTrackCount = uiState.allTracks.size,
              favoriteTrackCount = uiState.favoriteTrackCount,
              searchQuery = uiState.searchQuery,
              onSelectCategory = onSelectCategory,
              onSetShowFavoritesOnly = onSetShowFavoritesOnly,
              onSearchQueryChanged = onSearchQueryChanged,
            )
          }

          if (uiState.isSelectionMode) {
            item {
              val visibleIds = uiState.tracks.map { it.id }
              val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in uiState.selectedTrackIds }
              BulkSelectionBar(
                selectedCount = uiState.selectedTrackIds.size,
                allVisibleSelected = allVisibleSelected,
                onToggleSelectAll = onToggleSelectAllVisible,
                onOpenBulkRename = onOpenBulkRenameDialog,
                onExitSelectionMode = onExitSelectionMode,
              )
            }
          }

          if (uiState.isInitializing) {
            item { LoadingTracksPlaceholder() }
          } else if (uiState.tracks.isEmpty()) {
            item {
              EmptyTracksCard(
                showFavoritesOnly = uiState.showFavoritesOnly,
                selectedPlaylistName = uiState.selectedPlaylist?.name,
                onShowAllTracks = {
                  onSelectPlaylistFilter(null)
                  onSetShowFavoritesOnly(false)
                },
              )
            }
          } else {
            items(uiState.tracks, key = { it.id }) { track ->
              val isSelected = track.id == uiState.currentTrack?.id
              val isPlayingThis = isSelected && uiState.playback.isPlaying
              val isBulkSelected = track.id in uiState.selectedTrackIds
              GeneratedTrackRow(
                track = track,
                isSelected = isSelected,
                isPlaying = isPlayingThis,
                isSelectionMode = uiState.isSelectionMode,
                isBulkSelected = isBulkSelected,
                onToggleBulkSelect = { onToggleTrackSelection(track.id) },
                onLongClick = { onEnterSelectionWithTrack(track.id) },
                onClick = {
                  if (isSelected) onTogglePlayPause() else onSelectTrack(track)
                },
                onToggleFavorite = { onToggleTrackFavorite(track) },
                onAddToPlaylist = { onOpenTrackPlaylistsDialog(track) },
                onEditOrganization = { onEditOrganizationClick(track) },
                onTrimTrack = { onTrimTrackClick(track) },
                onExportMp3 = { onExportMp3Click(track) },
                onDelete = { onDeleteTrack(track.id) },
              )
            }
          }

          item { Spacer(modifier = Modifier.height(12.dp)) }
        }
      }
    }
  }
}

@Composable
internal fun LibraryOrganizationControls(
  categories: List<String>,
  selectedCategory: String,
  showFavoritesOnly: Boolean,
  totalTrackCount: Int,
  favoriteTrackCount: Int,
  searchQuery: String,
  onSelectCategory: (String) -> Unit,
  onSetShowFavoritesOnly: (Boolean) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("library_favorites_filter_row"),
    ) {
      SegmentedButton(
        selected = !showFavoritesOnly,
        onClick = { onSetShowFavoritesOnly(false) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        colors = SegmentedButtonDefaults.colors(
          activeContainerColor = colorScheme.secondaryContainer,
          activeContentColor = colorScheme.onSecondaryContainer,
          inactiveContainerColor = colorScheme.surfaceContainerLow,
          inactiveContentColor = colorScheme.onSurfaceVariant,
        ),
        icon = {
          Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = {
          Text(
            text = "${stringResource(R.string.library_filter_all)} ($totalTrackCount)",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        },
        modifier = Modifier.testTag("filter_all_tracks"),
      )

      SegmentedButton(
        selected = showFavoritesOnly,
        onClick = { onSetShowFavoritesOnly(true) },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        colors = SegmentedButtonDefaults.colors(
          activeContainerColor = colorScheme.tertiaryContainer,
          activeContentColor = colorScheme.onTertiaryContainer,
          inactiveContainerColor = colorScheme.surfaceContainerLow,
          inactiveContentColor = colorScheme.onSurfaceVariant,
        ),
        icon = {
          Icon(
            imageVector = if (showFavoritesOnly || favoriteTrackCount > 0) {
              Icons.Rounded.Favorite
            } else {
              Icons.Rounded.FavoriteBorder
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = {
          Text(
            text = "${stringResource(R.string.library_filter_favorites)} ($favoriteTrackCount)",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        },
        modifier = Modifier.testTag("filter_favorites_tracks"),
      )
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChanged,
      placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = null,
          tint = colorScheme.onSurfaceVariant,
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(
            onClick = { onSearchQueryChanged("") },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Clear search",
            )
          }
        }
      },
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorScheme.secondary,
        unfocusedBorderColor = colorScheme.outlineVariant,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("library_search_field"),
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      categories.forEach { category ->
        FilterChip(
          selected = category == selectedCategory,
          onClick = { onSelectCategory(category) },
          label = {
            Text(
              text = category,
              style = MaterialTheme.typography.labelSmall,
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.secondaryContainer,
            selectedLabelColor = colorScheme.onSecondaryContainer,
          ),
          modifier = Modifier.testTag("category_chip_${category.lowercase().replace(" ", "_")}"),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrackOrganizationDialog(
  track: GeneratedTrack,
  isSuggestingTags: Boolean = false,
  onSuggestAutoTags: (onSuggestionReady: (TrackAutoTagResult) -> Unit) -> Unit = {},
  onDismiss: () -> Unit,
  onSave: (category: String, tags: List<String>) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  var categoryInput by remember(track.id) { mutableStateOf(track.category) }
  var newTagInput by remember(track.id) { mutableStateOf("") }
  var aiNoticeMessage by remember(track.id) { mutableStateOf<String?>(null) }
  val activeTags = remember(track.id) {
    mutableStateListOf<String>().apply { addAll(track.tags) }
  }

  fun addTag(raw: String) {
    val clean = raw.trim().replace(",", "")
    if (clean.isNotEmpty() && activeTags.none { it.equals(clean, ignoreCase = true) }) {
      activeTags.add(clean)
    }
    newTagInput = ""
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colorScheme.surfaceContainer,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.edit_org_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = track.title,
          style = MaterialTheme.typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = {
            onSuggestAutoTags { suggestion ->
              categoryInput = suggestion.suggestedCategory
              suggestion.suggestedTags.forEach { tag -> addTag(tag) }
              aiNoticeMessage = suggestion.noticeMessage
                ?: PlaceholderTrackAutoTagger.FIREBASE_REQUIRED_NOTICE
            }
          },
          enabled = !isSuggestingTags,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("ai_suggest_tags_button"),
        ) {
          if (isSuggestingTags) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
          } else {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(stringResource(R.string.ai_suggest_tags_button))
        }

        aiNoticeMessage?.let { notice ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.tertiaryContainer,
            border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.4f)),
            modifier = Modifier.testTag("org_ai_autotag_notice"),
          ) {
            Text(
              text = notice,
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onTertiaryContainer,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }

        OutlinedTextField(
          value = categoryInput,
          onValueChange = { categoryInput = it },
          label = { Text(stringResource(R.string.edit_org_category_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_category_input"),
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          suggestedCategories.forEach { presetCategory ->
            FilterChip(
              selected = categoryInput.equals(presetCategory, ignoreCase = true),
              onClick = { categoryInput = presetCategory },
              label = { Text(presetCategory, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = newTagInput,
            onValueChange = { newTagInput = it },
            label = { Text(stringResource(R.string.edit_org_new_tag_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addTag(newTagInput) }),
            modifier = Modifier
              .weight(1f)
              .testTag("edit_new_tag_input"),
          )
          OutlinedButton(
            onClick = { addTag(newTagInput) },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("edit_add_tag_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.edit_org_add_tag_button))
          }
        }

        if (activeTags.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            activeTags.forEach { tag ->
              InputChip(
                selected = true,
                onClick = { activeTags.remove(tag) },
                label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove $tag",
                    modifier = Modifier.size(14.dp),
                  )
                },
              )
            }
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          suggestedTags.forEach { suggestion ->
            if (activeTags.none { it.equals(suggestion, ignoreCase = true) }) {
              FilterChip(
                selected = false,
                onClick = { addTag(suggestion) },
                label = { Text("+ $suggestion", style = MaterialTheme.typography.labelSmall) },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(categoryInput, activeTags.toList()) },
        modifier = Modifier.testTag("save_organization_button"),
      ) {
        Text(stringResource(R.string.edit_org_save_button))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}

@Composable
private fun StudioHeader(
  themeMode: ThemeMode,
  onSelectThemeMode: (ThemeMode) -> Unit,
  showRestoreWaveformButton: Boolean = false,
  onRestoreBottomWaveform: () -> Unit = {},
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = stringResource(R.string.studio_subtitle),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.secondary,
        )
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineLarge,
          color = colorScheme.onBackground,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (showRestoreWaveformButton) {
          OutlinedButton(
            onClick = onRestoreBottomWaveform,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("restore_bottom_waveform_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = stringResource(R.string.cd_restore_bottom_waveform),
              tint = colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.restore_bottom_waveform_label),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.primary,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, colorScheme.outline),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Free · Room",
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    ThemeSelectorRow(
      selectedMode = themeMode,
      onSelectThemeMode = onSelectThemeMode,
    )
  }
}

@Composable
internal fun BottomWaveformBar(
  track: GeneratedTrack?,
  playbackState: com.example.audio.AudioPlaybackState,
  canSkipNext: Boolean,
  onTogglePlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onBeginSeek: () -> Unit,
  onPreviewSeek: (Long) -> Unit,
  onCommitSeek: (Long) -> Unit,
  onEliminate: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val hasTrack = track != null
  val durationMs = playbackState.durationMs.coerceAtLeast(track?.durationMs ?: 0L).coerceAtLeast(1L)
  val currentPosMs = playbackState.positionMs.coerceIn(0L, durationMs)
  val activeEnvelope = when {
    track != null && playbackState.currentTrackId == track.id -> playbackState.activeEnvelope
    track != null -> track.audioEnvelope
    else -> TrackAudioEnvelope()
  }

  Surface(
    color = colorScheme.surfaceContainerHigh,
    tonalElevation = 8.dp,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("bottom_waveform_bar"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconButton(
        onClick = onTogglePlayPause,
        enabled = hasTrack && !playbackState.isPreparing,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("bottom_waveform_play_pause"),
      ) {
        if (playbackState.isPreparing) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = colorScheme.primary,
          )
        } else {
          Icon(
            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playbackState.isPlaying) {
              stringResource(R.string.cd_pause_track)
            } else {
              stringResource(R.string.cd_play_track)
            },
            tint = if (hasTrack) colorScheme.primary else colorScheme.outline,
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = track?.title ?: stringResource(R.string.bottom_waveform_idle),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (hasTrack) "${formatDurationMs(currentPosMs)} / ${formatDurationMs(durationMs)}" else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
        }

        WaveformVisualizer(
          peaks = track?.waveformPeaks.orEmpty(),
          positionMs = currentPosMs,
          durationMs = durationMs,
          isPlaying = playbackState.isPlaying,
          enabled = hasTrack && playbackState.isPrepared,
          contentDescriptionText = stringResource(R.string.cd_waveform_visualizer),
          onScrubStart = onBeginSeek,
          onScrubToPosition = onPreviewSeek,
          onScrubEnd = onCommitSeek,
          envelope = activeEnvelope,
          interactionMode = WaveformInteractionMode.SCRUB,
          modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .testTag("bottom_bar_waveform"),
        )
      }

      if (canSkipNext) {
        IconButton(
          onClick = onSkipNext,
          enabled = hasTrack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("bottom_waveform_next"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next_track),
            tint = colorScheme.onSurfaceVariant,
          )
        }
      }

      IconButton(
        onClick = onEliminate,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("eliminate_bottom_waveform_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(R.string.cd_eliminate_bottom_waveform),
          tint = colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun ThemeSelectorRow(
  selectedMode: ThemeMode,
  onSelectThemeMode: (ThemeMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val options = listOf(
    Triple(ThemeMode.LIGHT, stringResource(R.string.theme_mode_light), Icons.Rounded.LightMode),
    Triple(ThemeMode.DARK, stringResource(R.string.theme_mode_dark), Icons.Rounded.DarkMode),
    Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_mode_system), Icons.Rounded.BrightnessAuto),
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .testTag("theme_selector"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = stringResource(R.string.theme_selector_label),
      style = MaterialTheme.typography.labelMedium,
      color = colorScheme.onSurfaceVariant,
    )

    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.width(240.dp),
    ) {
      options.forEachIndexed { index, (mode, label, icon) ->
        SegmentedButton(
          selected = selectedMode == mode,
          onClick = { onSelectThemeMode(mode) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
          colors = SegmentedButtonDefaults.colors(
            activeContainerColor = colorScheme.primaryContainer,
            activeContentColor = colorScheme.onPrimaryContainer,
            inactiveContainerColor = colorScheme.surfaceContainerLow,
            inactiveContentColor = colorScheme.onSurfaceVariant,
          ),
          icon = {
            Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
            )
          },
          modifier = Modifier.testTag("theme_option_${mode.name.lowercase()}"),
        )
      }
    }
  }
}

@Composable
private fun BackgroundPermissionsCard(
  notificationPermission: NotificationPermissionStatus,
  onRequestNotificationPermission: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("background_permissions_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Bedtime,
          contentDescription = null,
          tint = colorScheme.secondary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = stringResource(R.string.permissions_card_title),
          style = MaterialTheme.typography.titleMedium,
          color = colorScheme.onSurface,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = null,
          tint = colorScheme.secondary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = stringResource(R.string.permissions_sleep_active),
          style = MaterialTheme.typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val isGranted = notificationPermission == NotificationPermissionStatus.GRANTED
          Icon(
            imageVector = if (isGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
            contentDescription = null,
            tint = if (isGranted) colorScheme.primary else colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = when (notificationPermission) {
              NotificationPermissionStatus.GRANTED -> stringResource(R.string.permissions_notif_granted)
              NotificationPermissionStatus.NEEDS_REQUEST -> stringResource(R.string.permissions_notif_needed)
              NotificationPermissionStatus.DENIED -> stringResource(R.string.permissions_notif_denied)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
          )
        }

        if (notificationPermission == NotificationPermissionStatus.NEEDS_REQUEST) {
          TextButton(
            onClick = onRequestNotificationPermission,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("enable_notifications_button"),
          ) {
            Text(
              text = stringResource(R.string.permissions_enable_button),
              style = MaterialTheme.typography.labelLarge,
              color = colorScheme.primary,
            )
          }
        } else if (notificationPermission == NotificationPermissionStatus.DENIED) {
          TextButton(
            onClick = onOpenNotificationSettings,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("open_notification_settings_button"),
          ) {
            Text(
              text = stringResource(R.string.permissions_settings_button),
              style = MaterialTheme.typography.labelLarge,
              color = colorScheme.tertiary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PromptSynthesizerCard(
  promptInput: String,
  selectedEngine: GenerationEngine,
  isGenerating: Boolean,
  errorMessage: String?,
  onSelectEngine: (GenerationEngine) -> Unit,
  onPromptChanged: (String) -> Unit,
  onApplyPreset: (String) -> Unit,
  onGenerateTrack: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val engineOptions = listOf(
    GenerationEngine.HF_AUDIO_LDM to stringResource(R.string.engine_hf_audioldm),
    GenerationEngine.HF_MUSICGEN to stringResource(R.string.engine_hf_musicgen),
    GenerationEngine.HF_STABLE_AUDIO to stringResource(R.string.engine_hf_stable_audio),
    GenerationEngine.FOURIER_SYNTH to stringResource(R.string.engine_fourier_dsp),
  )
  val engineDetail = when (selectedEngine) {
    GenerationEngine.HF_AUDIO_LDM -> "Model: haoheliu/audioldm (Latent Diffusion + STFT, free HF Space)"
    GenerationEngine.HF_MUSICGEN -> "Model: facebook/MusicGen (Transformer EnCodec, free HF Space)"
    GenerationEngine.HF_STABLE_AUDIO -> "Model: Stable-Audio-Open (Latent Diffusion, free HF Space)"
    GenerationEngine.FOURIER_SYNTH -> "Offline harmonic Fourier series synthesizer (no network required)"
  }

  Surface(
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          engineOptions.forEach { (engine, label) ->
            FilterChip(
              selected = selectedEngine == engine,
              enabled = !isGenerating,
              onClick = { onSelectEngine(engine) },
              label = {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.secondaryContainer,
                selectedLabelColor = colorScheme.onSecondaryContainer,
              ),
              modifier = Modifier.testTag("engine_chip_${engine.testTagSuffix}"),
            )
          }
        }
        Text(
          text = engineDetail,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      OutlinedTextField(
        value = promptInput,
        onValueChange = onPromptChanged,
        label = { Text(stringResource(R.string.prompt_input_label)) },
        placeholder = { Text(stringResource(R.string.prompt_input_placeholder)) },
        singleLine = true,
        enabled = !isGenerating,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onGenerateTrack() }),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = colorScheme.primary,
          unfocusedBorderColor = colorScheme.outline,
          focusedLabelColor = colorScheme.primary,
          cursorColor = colorScheme.primary,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("prompt_input_field"),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        quickPresets.forEach { preset ->
          val label = preset.split(" ").take(2).joinToString(" ")
          FilterChip(
            selected = promptInput == preset,
            onClick = { onApplyPreset(preset) },
            label = {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.primaryContainer,
              selectedLabelColor = colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.testTag("preset_chip_${label.lowercase().replace(" ", "_")}"),
          )
        }
      }

      AnimatedVisibility(visible = errorMessage != null) {
        errorMessage?.let { msg ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.tertiaryContainer,
            border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.45f)),
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onTertiaryContainer,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      Button(
        onClick = onGenerateTrack,
        enabled = !isGenerating,
        colors = ButtonDefaults.buttonColors(
          containerColor = colorScheme.primary,
          contentColor = colorScheme.onPrimary,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("generate_track_button"),
      ) {
        if (isGenerating) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          val generatingResId = if (selectedEngine == GenerationEngine.FOURIER_SYNTH) {
            R.string.generating_track_label
          } else {
            R.string.generating_hf_space_label
          }
          Text(
            text = stringResource(generatingResId),
            style = MaterialTheme.typography.labelLarge,
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.generate_track_button),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiAutoTagNoticeCard(
  result: TrackAutoTagResult,
  onDismiss: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("ai_autotag_banner"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = colorScheme.secondary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "${stringResource(R.string.ai_autotag_chip_title)}: ${result.suggestedCategory} · ${result.moodLabel}",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss auto-tag notice",
            tint = colorScheme.onSurfaceVariant,
          )
        }
      }

      if (result.suggestedTags.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          result.suggestedTags.forEach { tag ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = colorScheme.secondaryContainer,
            ) {
              Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      val notice = result.noticeMessage ?: stringResource(R.string.ai_autotag_requires_firebase_notice)
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.tertiaryContainer,
      ) {
        Text(
          text = notice,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onTertiaryContainer,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("ai_autotag_notice"),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackLibrarySectionHeader(
  trackCount: Int,
  hasAnyTracks: Boolean = true,
  isSelectionMode: Boolean = false,
  onToggleSelectionMode: () -> Unit = {},
  onDownloadUrlClick: () -> Unit = {},
  onImportAudioClick: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.generated_tracks_header),
          style = MaterialTheme.typography.titleLarge,
          color = colorScheme.onBackground,
        )
        Text(
          text = "$trackCount saved in Room",
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
        )
      }
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (hasAnyTracks || isSelectionMode) {
        OutlinedButton(
          onClick = onToggleSelectionMode,
          border = BorderStroke(
            width = 1.dp,
            color = if (isSelectionMode) colorScheme.primary else colorScheme.outline,
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("toggle_bulk_select_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Checklist,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isSelectionMode) colorScheme.primary else colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = stringResource(
              if (isSelectionMode) R.string.bulk_done_button else R.string.bulk_select_button,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelectionMode) colorScheme.primary else colorScheme.onSurface,
          )
        }
      }

      OutlinedButton(
        onClick = onDownloadUrlClick,
        border = BorderStroke(1.dp, colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("open_url_download_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.CloudDownload,
          contentDescription = stringResource(R.string.cd_download_audio_url),
          modifier = Modifier.size(16.dp),
          tint = colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.download_url_button),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurface,
        )
      }

      OutlinedButton(
        onClick = onImportAudioClick,
        border = BorderStroke(1.dp, colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("import_audio_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.FileOpen,
          contentDescription = stringResource(R.string.cd_import_audio),
          modifier = Modifier.size(16.dp),
          tint = colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.import_audio_button),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurface,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun GeneratedTrackRow(
  track: GeneratedTrack,
  isSelected: Boolean,
  isPlaying: Boolean,
  isSelectionMode: Boolean = false,
  isBulkSelected: Boolean = false,
  onToggleBulkSelect: () -> Unit = {},
  onLongClick: () -> Unit = {},
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit = {},
  onAddToPlaylist: () -> Unit = {},
  onEditOrganization: () -> Unit,
  onTrimTrack: () -> Unit,
  onExportMp3: () -> Unit,
  onDelete: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val highlighted = if (isSelectionMode) isBulkSelected else isSelected
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = when {
      isSelectionMode && isBulkSelected -> colorScheme.primaryContainer.copy(alpha = 0.35f)
      isSelected -> colorScheme.surfaceContainerHigh
      else -> colorScheme.surfaceContainerLow
    },
    border = BorderStroke(
      width = 1.dp,
      color = if (highlighted) colorScheme.primary.copy(alpha = 0.65f) else colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .combinedClickable(
        onClick = {
          if (isSelectionMode) onToggleBulkSelect() else onClick()
        },
        onLongClick = onLongClick,
      )
      .testTag("track_item_${track.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (isSelectionMode) {
          Checkbox(
            checked = isBulkSelected,
            onCheckedChange = { onToggleBulkSelect() },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("select_track_checkbox_${track.id}"),
          )
        } else {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                if (isSelected) colorScheme.primary.copy(alpha = 0.2f)
                else colorScheme.surfaceContainer,
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = if (isSelected) colorScheme.primary else colorScheme.onSurface,
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = track.title,
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = colorScheme.secondaryContainer,
            ) {
              Text(
                text = track.category,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = "${track.sourceLabel} · ${track.bpm} BPM · ${track.musicalKey} · ${formatDurationMs(track.durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) colorScheme.secondary else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (track.tags.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              track.tags.forEach { tag ->
                Surface(
                  shape = RoundedCornerShape(5.dp),
                  color = colorScheme.surfaceContainer,
                  border = BorderStroke(0.5.dp, colorScheme.outlineVariant),
                ) {
                  Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                  )
                }
              }
            }
          }
        }
      }

      if (!isSelectionMode) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("favorite_track_${track.id}"),
        ) {
          Icon(
            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(
              if (track.isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite,
            ),
            tint = if (track.isFavorite) colorScheme.tertiary else colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onAddToPlaylist,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("add_to_playlist_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = stringResource(R.string.cd_add_to_playlist),
            tint = colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onTrimTrack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("trim_track_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCut,
            contentDescription = stringResource(R.string.cd_trim_track),
            tint = colorScheme.primary,
          )
        }

        IconButton(
          onClick = onExportMp3,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("export_mp3_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.cd_export_mp3),
            tint = colorScheme.secondary,
          )
        }

        IconButton(
          onClick = onEditOrganization,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("edit_tags_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Label,
            contentDescription = stringResource(R.string.cd_edit_tags),
            tint = colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("delete_track_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_track),
            tint = colorScheme.onSurfaceVariant,
          )
        }
        }
      }
    }
  }
}

@Composable
private fun LoadingTracksPlaceholder() {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        strokeWidth = 2.5.dp,
        color = colorScheme.primary,
      )
      Text(
        text = stringResource(R.string.generating_track_label),
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyTracksCard(
  showFavoritesOnly: Boolean = false,
  selectedPlaylistName: String? = null,
  onShowAllTracks: () -> Unit = {},
) {
  val colorScheme = MaterialTheme.colorScheme
  val hasPlaylistFilter = !selectedPlaylistName.isNullOrBlank()
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = when {
          hasPlaylistFilter -> Icons.AutoMirrored.Rounded.QueueMusic
          showFavoritesOnly -> Icons.Rounded.FavoriteBorder
          else -> Icons.Rounded.LibraryMusic
        },
        contentDescription = null,
        tint = if (showFavoritesOnly && !hasPlaylistFilter) colorScheme.tertiary else colorScheme.primary,
        modifier = Modifier.size(32.dp),
      )
      Text(
        text = when {
          hasPlaylistFilter -> stringResource(R.string.empty_playlist_tracks_title)
          showFavoritesOnly -> stringResource(R.string.empty_favorites_title)
          else -> stringResource(R.string.empty_tracks_title)
        },
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
      )
      Text(
        text = when {
          hasPlaylistFilter -> stringResource(R.string.empty_playlist_tracks_subtitle)
          showFavoritesOnly -> stringResource(R.string.empty_favorites_subtitle)
          else -> stringResource(R.string.empty_tracks_subtitle)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
      if (showFavoritesOnly || hasPlaylistFilter) {
        TextButton(
          onClick = onShowAllTracks,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("empty_favorites_show_all_button"),
        ) {
          Text(
            text = stringResource(R.string.show_all_tracks_button),
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary,
          )
        }
      }
    }
  }
}
