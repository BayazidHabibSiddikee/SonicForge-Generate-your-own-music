package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.Album
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.example.audio.DrumKitPreset
import com.example.audio.DrumPadVoice
import com.example.audio.EqPreset
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarProgressionPreset
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.LoudnessTarget
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.audio.ReverbEngineMode
import com.example.audio.ReverbPreset
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.StudioSoundResource
import com.example.audio.TrackAudioEnvelope
import com.example.audio.WaveformInteractionMode
import com.example.data.local.TrackHistoryItem
import com.example.model.BulkRenameConfig
import com.example.model.GeneratedTrack
import com.example.model.Playlist
import com.example.ui.components.AiProducerAndSamplesCard
import com.example.ui.components.AudioUrlDownloadDialog
import com.example.ui.components.BulkRenameDialog
import com.example.ui.components.BulkSelectionBar
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.CustomAudioPlayer
import com.example.ui.components.DrumStudioPage
import com.example.ui.components.GuitarStudioPage
import com.example.ui.components.MultiTrackMergerCard
import com.example.ui.components.MusicPromptGeneratorCard
import com.example.ui.components.PianoStudioPage
import com.example.ui.components.PlaylistsShelfSection
import com.example.ui.components.SaveStudioProjectDialog
import com.example.ui.components.SoundResourcesCard
import com.example.ui.components.StudioProjectsCard
import com.example.ui.components.TrackExportDialog
import com.example.ui.components.RoomStorageExportCard
import com.example.ui.components.TrackHistoryCard
import com.example.ui.components.TrackPlaylistsDialog
import com.example.ui.components.TrackTrimDialog
import com.example.ui.components.WaveformVisualizer
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.ThemeMode

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
  onPlay: () -> Unit = onTogglePlayPause,
  onPause: () -> Unit = onTogglePlayPause,
  onStop: () -> Unit = {},
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
  onSelectDestination: (StudioDestination) -> Unit = {},
  onSelectStudioSectionTab: (StudioSectionTab) -> Unit = {},
  onGenerateSoundResource: (StudioSoundResource) -> Unit = {},
  onQuickSaveCurrentMp3: (GeneratedTrack) -> Unit = {},
  onDismissQuickMp3Status: () -> Unit = {},
  onSelectPianoVoice: (PianoVoice) -> Unit = {},
  onSelectPianoProgression: (PianoProgressionPreset) -> Unit = {},
  onShiftPianoOctave: (Int) -> Unit = {},
  onStepPianoBpm: (Int) -> Unit = {},
  onPlayPianoKey: (Int) -> Unit = {},
  onPlayPianoChord: (IntArray) -> Unit = {},
  onTogglePianoRecording: () -> Unit = {},
  onClearPianoRecording: () -> Unit = {},
  onPreviewPianoPattern: () -> Unit = {},
  onGeneratePianoTrack: () -> Unit = {},
  onSelectGuitarVoice: (GuitarVoice) -> Unit = {},
  onSelectGuitarChord: (GuitarChordShape) -> Unit = {},
  onToggleGuitarChordInSequence: (GuitarChordShape) -> Unit = {},
  onAddGuitarChordToSequence: (GuitarChordShape) -> Unit = onToggleGuitarChordInSequence,
  onRemoveGuitarChordFromSequenceAt: (Int) -> Unit = {},
  onApplyGuitarProgressionPreset: (GuitarProgressionPreset) -> Unit = {},
  onSelectGuitarStrumStyle: (GuitarStrumStyle) -> Unit = {},
  onStepGuitarBpm: (Int) -> Unit = {},
  onPluckGuitarString: (Int, Int) -> Unit = { _, _ -> },
  onStrumGuitarChord: () -> Unit = {},
  onToggleGuitarRecording: () -> Unit = {},
  onClearGuitarRecording: () -> Unit = {},
  onPreviewGuitarPattern: () -> Unit = {},
  onGenerateGuitarTrack: () -> Unit = {},
  onSelectDrumKit: (DrumKitPreset) -> Unit = {},
  onTriggerDrumPad: (DrumPadVoice) -> Unit = {},
  onToggleDrumPadRecording: () -> Unit = {},
  onClearDrumPadRecording: () -> Unit = {},
  onPreviewDrumPadTake: () -> Unit = {},
  onGenerateDrumTrack: () -> Unit = {},
  onToggleMergeTrackSelection: (String) -> Unit = {},
  onMergeStemGainChange: (String, Float) -> Unit = { _, _ -> },
  onMergeLoopShorterStemsChange: (Boolean) -> Unit = {},
  onMergeTitleChanged: (String) -> Unit = {},
  onAutoPickBandStems: () -> Unit = {},
  onMergeSelectedTracks: () -> Unit = {},
  onMergeAndSaveMp3: () -> Unit = {},
  onSelectHistoryTypeFilter: (String) -> Unit = {},
  onHistorySearchQueryChanged: (String) -> Unit = {},
  onPlayHistoryItem: (TrackHistoryItem) -> Unit = {},
  onReuseHistoryPrompt: (String) -> Unit = {},
  onDeleteHistoryEntry: (String) -> Unit = {},
  onClearAllHistory: () -> Unit = {},
  aiProducerActions: AiProducerActions = AiProducerActions(),
  roomStorageExportActions: RoomStorageExportActions = RoomStorageExportActions(),
) {
  val colorScheme = MaterialTheme.colorScheme
  BackHandler(enabled = uiState.activeDestination != StudioDestination.STUDIO) {
    onSelectDestination(StudioDestination.STUDIO)
  }

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

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
  ) {
    val isWideLayout = maxWidth >= 840.dp

    Scaffold(
      containerColor = colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        Column(modifier = Modifier.fillMaxWidth()) {
          val showMiniPlayer = uiState.showBottomWaveformBar &&
            uiState.activeDestination != StudioDestination.STUDIO &&
            uiState.currentTrack != null
          if (showMiniPlayer) {
            BottomWaveformBar(
              track = uiState.currentTrack,
              playbackState = uiState.playback,
              canSkipNext = uiState.canSkipNext,
              applyNavBarPadding = isWideLayout,
              onTogglePlayPause = onTogglePlayPause,
              onSkipNext = onSkipNext,
              onBeginSeek = onBeginSeek,
              onPreviewSeek = onPreviewSeek,
              onCommitSeek = onCommitSeek,
              onEliminate = { onSetBottomWaveformVisible(false) },
            )
          }
          if (!isWideLayout) {
            StudioNavigationBar(
              activeDestination = uiState.activeDestination,
              onSelectDestination = onSelectDestination,
            )
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (isWideLayout) {
          StudioNavigationRail(
            activeDestination = uiState.activeDestination,
            onSelectDestination = onSelectDestination,
          )
        }

        val showDualColumnStudio = isWideLayout && uiState.activeDestination == StudioDestination.STUDIO

        if (showDualColumnStudio) {
          Row(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            LazyColumn(
              modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight(),
              contentPadding = PaddingValues(bottom = 24.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              item {
                StudioHeader(
                  themeMode = uiState.themeMode,
                  onSelectThemeMode = onSelectThemeMode,
                  currentTrack = uiState.currentTrack,
                  isQuickSavingMp3 = uiState.isQuickSavingMp3,
                  onQuickSaveCurrentMp3 = onQuickSaveCurrentMp3,
                  showRestoreWaveformButton = !uiState.showBottomWaveformBar,
                  onRestoreBottomWaveform = { onSetBottomWaveformVisible(true) },
                )
              }
              uiState.quickMp3StatusMessage?.let { status ->
                item {
                  QuickStatusBanner(
                    message = status,
                    onDismiss = onDismissQuickMp3Status,
                  )
                }
              }
              item {
                MusicPromptGeneratorCard(
                  promptInput = uiState.promptInput,
                  selectedEngine = uiState.selectedEngine,
                  isGenerating = uiState.isGenerating,
                  errorMessage = uiState.errorMessage,
                  onPromptChanged = onPromptChanged,
                  onSelectEngine = onSelectEngine,
                  onApplyPreset = onApplyPreset,
                  onGenerateClick = onGenerateTrack,
                  isBusyExternal = uiState.generatingResourceId != null,
                )
              }
              item {
                MultiTrackMergerCard(
                  allTracks = uiState.allTracks,
                  state = uiState.mergeMixer,
                  onToggleTrackSelection = onToggleMergeTrackSelection,
                  onStemGainChange = onMergeStemGainChange,
                  onLoopShorterStemsChange = onMergeLoopShorterStemsChange,
                  onTitleChanged = onMergeTitleChanged,
                  onAutoPickBandStems = onAutoPickBandStems,
                  onMergeTracks = onMergeSelectedTracks,
                  onMergeAndSaveMp3 = onMergeAndSaveMp3,
                )
              }
              item {
                StudioSectionTabRow(
                  selectedTab = uiState.studioSectionTab,
                  historyCount = uiState.totalHistoryCount,
                  trackCount = uiState.allTracks.size,
                  projectCount = uiState.projects.size,
                  onSelectTab = onSelectStudioSectionTab,
                )
              }
              studioTabContentItems(
                uiState = uiState,
                onSelectHistoryTypeFilter = onSelectHistoryTypeFilter,
                onHistorySearchQueryChanged = onHistorySearchQueryChanged,
                onPlayHistoryItem = onPlayHistoryItem,
                onReuseHistoryPrompt = onReuseHistoryPrompt,
                onDeleteHistoryEntry = onDeleteHistoryEntry,
                onClearAllHistory = onClearAllHistory,
                aiProducerActions = aiProducerActions,
                roomStorageExportActions = roomStorageExportActions,
                onGenerateSoundResource = onGenerateSoundResource,
                onPromptChanged = onPromptChanged,
                onGenerateTrack = onGenerateTrack,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenSaveProjectDialog = onOpenSaveProjectDialog,
                onQuickSaveActiveProject = onQuickSaveActiveProject,
                onLoadProject = onLoadProject,
                onDeleteProject = onDeleteProject,
                onDismissProjectStatus = onDismissProjectStatus,
                onDismissAutoTagBanner = onDismissAutoTagBanner,
                onToggleSelectionMode = onToggleSelectionMode,
                onOpenUrlDownloadDialog = onOpenUrlDownloadDialog,
                onImportAudioClick = onImportAudioClick,
                onOpenCreatePlaylistDialog = onOpenCreatePlaylistDialog,
                onSelectPlaylistFilter = onSelectPlaylistFilter,
                onPlayPlaylist = onPlayPlaylist,
                onDeletePlaylist = onDeletePlaylist,
                onSelectCategory = onSelectCategory,
                onSetShowFavoritesOnly = onSetShowFavoritesOnly,
                onSearchQueryChanged = onSearchQueryChanged,
                onToggleSelectAllVisible = onToggleSelectAllVisible,
                onOpenBulkRenameDialog = onOpenBulkRenameDialog,
                onExitSelectionMode = onExitSelectionMode,
                onToggleTrackSelection = onToggleTrackSelection,
                onEnterSelectionWithTrack = onEnterSelectionWithTrack,
                onTogglePlayPause = onTogglePlayPause,
                onSelectTrack = onSelectTrack,
                onToggleTrackFavorite = onToggleTrackFavorite,
                onOpenTrackPlaylistsDialog = onOpenTrackPlaylistsDialog,
                onEditOrganizationClick = onEditOrganizationClick,
                onTrimTrackClick = onTrimTrackClick,
                onExportMp3Click = onExportMp3Click,
                onDeleteTrack = onDeleteTrack,
              )
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
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
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
          when (uiState.activeDestination) {
            StudioDestination.STUDIO -> {
              val studioListState = rememberLazyListState()
              LazyColumn(
                state = studioListState,
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .windowInsetsPadding(WindowInsets(0.dp))
                  .testTag("studio_page_scroll"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                item {
                  StudioHeader(
                    themeMode = uiState.themeMode,
                    onSelectThemeMode = onSelectThemeMode,
                    currentTrack = uiState.currentTrack,
                    isQuickSavingMp3 = uiState.isQuickSavingMp3,
                    onQuickSaveCurrentMp3 = onQuickSaveCurrentMp3,
                    showRestoreWaveformButton = !uiState.showBottomWaveformBar,
                    onRestoreBottomWaveform = { onSetBottomWaveformVisible(true) },
                  )
                }

                uiState.quickMp3StatusMessage?.let { status ->
                  item {
                    QuickStatusBanner(
                      message = status,
                      onDismiss = onDismissQuickMp3Status,
                    )
                  }
                }

                item {
                  MusicPromptGeneratorCard(
                    promptInput = uiState.promptInput,
                    selectedEngine = uiState.selectedEngine,
                    isGenerating = uiState.isGenerating,
                    errorMessage = uiState.errorMessage,
                    onPromptChanged = onPromptChanged,
                    onSelectEngine = onSelectEngine,
                    onApplyPreset = onApplyPreset,
                    onGenerateClick = onGenerateTrack,
                    isBusyExternal = uiState.generatingResourceId != null,
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
                    onPlay = onPlay,
                    onPause = onPause,
                    onStop = onStop,
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
                  MultiTrackMergerCard(
                    allTracks = uiState.allTracks,
                    state = uiState.mergeMixer,
                    onToggleTrackSelection = onToggleMergeTrackSelection,
                    onStemGainChange = onMergeStemGainChange,
                    onLoopShorterStemsChange = onMergeLoopShorterStemsChange,
                    onTitleChanged = onMergeTitleChanged,
                    onAutoPickBandStems = onAutoPickBandStems,
                    onMergeTracks = onMergeSelectedTracks,
                    onMergeAndSaveMp3 = onMergeAndSaveMp3,
                  )
                }

                item {
                  StudioSectionTabRow(
                    selectedTab = uiState.studioSectionTab,
                    historyCount = uiState.totalHistoryCount,
                    trackCount = uiState.allTracks.size,
                    projectCount = uiState.projects.size,
                    onSelectTab = onSelectStudioSectionTab,
                  )
                }

                studioTabContentItems(
                  uiState = uiState,
                  onSelectHistoryTypeFilter = onSelectHistoryTypeFilter,
                  onHistorySearchQueryChanged = onHistorySearchQueryChanged,
                  onPlayHistoryItem = onPlayHistoryItem,
                  onReuseHistoryPrompt = onReuseHistoryPrompt,
                  onDeleteHistoryEntry = onDeleteHistoryEntry,
                  onClearAllHistory = onClearAllHistory,
                  aiProducerActions = aiProducerActions,
                  roomStorageExportActions = roomStorageExportActions,
                  onGenerateSoundResource = onGenerateSoundResource,
                  onPromptChanged = onPromptChanged,
                  onGenerateTrack = onGenerateTrack,
                  onRequestNotificationPermission = onRequestNotificationPermission,
                  onOpenNotificationSettings = onOpenNotificationSettings,
                  onOpenSaveProjectDialog = onOpenSaveProjectDialog,
                  onQuickSaveActiveProject = onQuickSaveActiveProject,
                  onLoadProject = onLoadProject,
                  onDeleteProject = onDeleteProject,
                  onDismissProjectStatus = onDismissProjectStatus,
                  onDismissAutoTagBanner = onDismissAutoTagBanner,
                  onToggleSelectionMode = onToggleSelectionMode,
                  onOpenUrlDownloadDialog = onOpenUrlDownloadDialog,
                  onImportAudioClick = onImportAudioClick,
                  onOpenCreatePlaylistDialog = onOpenCreatePlaylistDialog,
                  onSelectPlaylistFilter = onSelectPlaylistFilter,
                  onPlayPlaylist = onPlayPlaylist,
                  onDeletePlaylist = onDeletePlaylist,
                  onSelectCategory = onSelectCategory,
                  onSetShowFavoritesOnly = onSetShowFavoritesOnly,
                  onSearchQueryChanged = onSearchQueryChanged,
                  onToggleSelectAllVisible = onToggleSelectAllVisible,
                  onOpenBulkRenameDialog = onOpenBulkRenameDialog,
                  onExitSelectionMode = onExitSelectionMode,
                  onToggleTrackSelection = onToggleTrackSelection,
                  onEnterSelectionWithTrack = onEnterSelectionWithTrack,
                  onTogglePlayPause = onTogglePlayPause,
                  onSelectTrack = onSelectTrack,
                  onToggleTrackFavorite = onToggleTrackFavorite,
                  onOpenTrackPlaylistsDialog = onOpenTrackPlaylistsDialog,
                  onEditOrganizationClick = onEditOrganizationClick,
                  onTrimTrackClick = onTrimTrackClick,
                  onExportMp3Click = onExportMp3Click,
                  onDeleteTrack = onDeleteTrack,
                )

                item { Spacer(modifier = Modifier.height(12.dp)) }
              }
            }

            StudioDestination.PIANO -> {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter,
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  uiState.quickMp3StatusMessage?.let { status ->
                    QuickStatusBanner(
                      message = status,
                      onDismiss = onDismissQuickMp3Status,
                    )
                  }
                  PianoStudioPage(
                    state = uiState.pianoState,
                    onSelectVoice = onSelectPianoVoice,
                    onSelectProgression = onSelectPianoProgression,
                    onShiftOctave = onShiftPianoOctave,
                    onStepBpm = onStepPianoBpm,
                    onPlayKey = onPlayPianoKey,
                    onPlayChord = onPlayPianoChord,
                    onToggleRecording = onTogglePianoRecording,
                    onClearRecording = onClearPianoRecording,
                    onPreviewPattern = onPreviewPianoPattern,
                    onGenerateTrack = onGeneratePianoTrack,
                    onOpenStudio = { onSelectDestination(StudioDestination.STUDIO) },
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                }
              }
            }

            StudioDestination.GUITAR -> {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter,
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  uiState.quickMp3StatusMessage?.let { status ->
                    QuickStatusBanner(
                      message = status,
                      onDismiss = onDismissQuickMp3Status,
                    )
                  }
                  GuitarStudioPage(
                    state = uiState.guitarState,
                    onSelectVoice = onSelectGuitarVoice,
                    onSelectChord = onSelectGuitarChord,
                    onAddChordToSequence = onAddGuitarChordToSequence,
                    onRemoveChordFromSequenceAt = onRemoveGuitarChordFromSequenceAt,
                    onApplyProgressionPreset = onApplyGuitarProgressionPreset,
                    onSelectStrumStyle = onSelectGuitarStrumStyle,
                    onStepBpm = onStepGuitarBpm,
                    onPluckString = onPluckGuitarString,
                    onStrumChord = onStrumGuitarChord,
                    onToggleRecording = onToggleGuitarRecording,
                    onClearRecording = onClearGuitarRecording,
                    onPreviewPattern = onPreviewGuitarPattern,
                    onGenerateTrack = onGenerateGuitarTrack,
                    onOpenStudio = { onSelectDestination(StudioDestination.STUDIO) },
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                }
              }
            }

            StudioDestination.DRUMS -> {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter,
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  uiState.quickMp3StatusMessage?.let { status ->
                    QuickStatusBanner(
                      message = status,
                      onDismiss = onDismissQuickMp3Status,
                    )
                  }
                  DrumStudioPage(
                    sequencerState = uiState.stepSequencer,
                    metronomeState = uiState.metronome,
                    drumState = uiState.drumState,
                    onSelectDrumKit = onSelectDrumKit,
                    onTriggerDrumPad = onTriggerDrumPad,
                    onToggleDrumPadRecording = onToggleDrumPadRecording,
                    onClearDrumPadRecording = onClearDrumPadRecording,
                    onPreviewDrumPadTake = onPreviewDrumPadTake,
                    onToggleSequencerStep = onToggleSequencerStep,
                    onToggleSequencerPlayStop = onToggleSequencerPlayStop,
                    onClearSequencerPattern = onClearSequencerPattern,
                    onApplySequencerPreset = onApplySequencerPreset,
                    onStepSequencerBpm = onStepSequencerBpm,
                    onToggleSequencerBpmSync = onToggleSequencerBpmSync,
                    onCycleSequencerSwing = onCycleSequencerSwing,
                    onGenerateDrumTrack = onGenerateDrumTrack,
                    onToggleMetronomePlayStop = onToggleMetronomePlayStop,
                    onStepMetronomeBpm = onStepMetronomeBpm,
                    onToggleMetronomeBpmSync = onToggleMetronomeBpmSync,
                    onMetronomeVolumeChange = onMetronomeVolumeChange,
                    onMetronomePitchHzChange = onMetronomePitchHzChange,
                    onSelectMetronomeToneStyle = onSelectMetronomeToneStyle,
                    onSelectMetronomeTimeSignature = onSelectMetronomeTimeSignature,
                    onToggleMetronomeAccentDownbeat = onToggleMetronomeAccentDownbeat,
                    onTapMetronomeTempo = onTapMetronomeTempo,
                    onOpenStudio = { onSelectDestination(StudioDestination.STUDIO) },
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudioNavigationBar(
  activeDestination: StudioDestination,
  onSelectDestination: (StudioDestination) -> Unit,
) {
  NavigationBar(
    modifier = Modifier.testTag("studio_navigation_bar"),
  ) {
    StudioDestination.entries.forEach { dest ->
      val (labelRes, icon) = destinationMeta(dest)
      NavigationBarItem(
        selected = activeDestination == dest,
        onClick = { onSelectDestination(dest) },
        icon = {
          Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
          )
        },
        label = {
          Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
          )
        },
        modifier = Modifier.testTag(dest.testTag),
      )
    }
  }
}

@Composable
private fun StudioNavigationRail(
  activeDestination: StudioDestination,
  onSelectDestination: (StudioDestination) -> Unit,
) {
  NavigationRail(
    modifier = Modifier.testTag("studio_navigation_rail"),
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    StudioDestination.entries.forEach { dest ->
      val (labelRes, icon) = destinationMeta(dest)
      NavigationRailItem(
        selected = activeDestination == dest,
        onClick = { onSelectDestination(dest) },
        icon = {
          Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
          )
        },
        label = {
          Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
          )
        },
        modifier = Modifier.testTag(dest.testTag),
      )
    }
  }
}

private fun destinationMeta(dest: StudioDestination) = when (dest) {
  StudioDestination.STUDIO -> R.string.nav_studio to Icons.Rounded.Tune
  StudioDestination.PIANO -> R.string.nav_piano to Icons.Rounded.Piano
  StudioDestination.GUITAR -> R.string.nav_guitar to Icons.Rounded.MusicNote
  StudioDestination.DRUMS -> R.string.nav_drums to Icons.Rounded.Album
}

@Composable
private fun QuickStatusBanner(
  message: String,
  onDismiss: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.secondaryContainer,
    border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("quick_mp3_status_banner"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = null,
          tint = colorScheme.onSecondaryContainer,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = message,
          style = MaterialTheme.typography.labelMedium,
          color = colorScheme.onSecondaryContainer,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Dismiss status",
          tint = colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

@Composable
private fun StudioSectionTabRow(
  selectedTab: StudioSectionTab,
  historyCount: Int,
  trackCount: Int,
  projectCount: Int,
  onSelectTab: (StudioSectionTab) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tabs = listOf(
    StudioSectionTab.HISTORY to stringResource(R.string.studio_tab_history),
    StudioSectionTab.LIBRARY to stringResource(R.string.studio_tab_library),
    StudioSectionTab.RESOURCES to stringResource(R.string.studio_tab_ai_packs),
    StudioSectionTab.PROJECTS to stringResource(R.string.studio_tab_projects),
  )

  SingleChoiceSegmentedButtonRow(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("studio_section_tab_row"),
  ) {
    tabs.forEachIndexed { idx, (tab, label) ->
      SegmentedButton(
        selected = selectedTab == tab,
        onClick = { onSelectTab(tab) },
        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
        colors = SegmentedButtonDefaults.colors(
          activeContainerColor = colorScheme.primaryContainer,
          activeContentColor = colorScheme.onPrimaryContainer,
          inactiveContainerColor = colorScheme.surfaceContainerLow,
          inactiveContentColor = colorScheme.onSurfaceVariant,
        ),
        icon = {},
        label = {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        modifier = Modifier.testTag(tab.testTag),
      )
    }
  }
}

private fun LazyListScope.studioTabContentItems(
  uiState: SonicForgeUiState,
  onSelectHistoryTypeFilter: (String) -> Unit,
  onHistorySearchQueryChanged: (String) -> Unit,
  onPlayHistoryItem: (TrackHistoryItem) -> Unit,
  onReuseHistoryPrompt: (String) -> Unit,
  onDeleteHistoryEntry: (String) -> Unit,
  onClearAllHistory: () -> Unit,
  aiProducerActions: AiProducerActions,
  roomStorageExportActions: RoomStorageExportActions,
  onGenerateSoundResource: (StudioSoundResource) -> Unit,
  onPromptChanged: (String) -> Unit,
  onGenerateTrack: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
  onOpenSaveProjectDialog: () -> Unit,
  onQuickSaveActiveProject: () -> Unit,
  onLoadProject: (String) -> Unit,
  onDeleteProject: (String) -> Unit,
  onDismissProjectStatus: () -> Unit,
  onDismissAutoTagBanner: () -> Unit,
  onToggleSelectionMode: () -> Unit,
  onOpenUrlDownloadDialog: () -> Unit,
  onImportAudioClick: () -> Unit,
  onOpenCreatePlaylistDialog: () -> Unit,
  onSelectPlaylistFilter: (String?) -> Unit,
  onPlayPlaylist: (Playlist) -> Unit,
  onDeletePlaylist: (String) -> Unit,
  onSelectCategory: (String) -> Unit,
  onSetShowFavoritesOnly: (Boolean) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onToggleSelectAllVisible: () -> Unit,
  onOpenBulkRenameDialog: () -> Unit,
  onExitSelectionMode: () -> Unit,
  onToggleTrackSelection: (String) -> Unit,
  onEnterSelectionWithTrack: (String) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSelectTrack: (GeneratedTrack) -> Unit,
  onToggleTrackFavorite: (GeneratedTrack) -> Unit,
  onOpenTrackPlaylistsDialog: (GeneratedTrack) -> Unit,
  onEditOrganizationClick: (GeneratedTrack) -> Unit,
  onTrimTrackClick: (GeneratedTrack) -> Unit,
  onExportMp3Click: (GeneratedTrack) -> Unit,
  onDeleteTrack: (String) -> Unit,
) {
  when (uiState.studioSectionTab) {
    StudioSectionTab.HISTORY -> {
      item {
        RoomStorageExportCard(
          state = uiState.roomStorageExport,
          roomTrackCount = uiState.allTracks.size,
          onSelectFormat = roomStorageExportActions.onSelectFormat,
          onExportToDeviceMusic = roomStorageExportActions.onExportAllToDeviceMusic,
          onChooseStorageDirectory = roomStorageExportActions.onChooseStorageDirectoryForAll,
          onDismissStatus = roomStorageExportActions.onDismissStatus,
        )
      }
      item {
        TrackHistoryCard(
          historyItems = uiState.trackHistory,
          totalHistoryCount = uiState.totalHistoryCount,
          selectedTypeFilter = uiState.historyTypeFilter,
          searchQuery = uiState.historySearchQuery,
          currentTrackId = uiState.currentTrack?.id,
          isPlaying = uiState.playback.isPlaying,
          onSelectTypeFilter = onSelectHistoryTypeFilter,
          onSearchQueryChanged = onHistorySearchQueryChanged,
          onPlayHistoryItem = onPlayHistoryItem,
          onReusePrompt = onReuseHistoryPrompt,
          onDeleteHistoryItem = onDeleteHistoryEntry,
          onClearAllHistory = onClearAllHistory,
          onExportHistoryItem = roomStorageExportActions.onExportHistoryItemToStorage,
        )
      }
    }

    StudioSectionTab.RESOURCES -> {
      item {
        AiProducerAndSamplesCard(
          aiState = uiState.aiProducer,
          offlineMusicState = uiState.offlineMusicState,
          onSelectAiEngine = aiProducerActions.onSelectAiEngine,
          onAiPromptChanged = aiProducerActions.onAiPromptChanged,
          onArrangeTrack = aiProducerActions.onArrangeTrack,
          onRenderAiFullMix = aiProducerActions.onRenderAiFullMix,
          onApplyPlanToInstruments = aiProducerActions.onApplyPlanToInstruments,
          onDetachSampleFromAiMix = aiProducerActions.onDetachSampleFromAiMix,
          onOfflineQueryChanged = aiProducerActions.onOfflineQueryChanged,
          onSelectOfflineCategory = aiProducerActions.onSelectOfflineCategory,
          onPreviewOfflineAsset = aiProducerActions.onPreviewOfflineAsset,
          onImportOfflineAsset = aiProducerActions.onImportOfflineAsset,
        )
      }
      item {
        SoundResourcesCard(
          generatingResourceId = uiState.generatingResourceId,
          promptInput = uiState.promptInput,
          isCustomGenerating = uiState.isGenerating,
          errorMessage = uiState.errorMessage,
          onGenerateResource = onGenerateSoundResource,
          onPromptChanged = onPromptChanged,
          onGenerateCustomPrompt = onGenerateTrack,
        )
      }
      if (uiState.notificationPermission != NotificationPermissionStatus.GRANTED) {
        item {
          BackgroundPermissionsCard(
            notificationPermission = uiState.notificationPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
          )
        }
      }
    }

    StudioSectionTab.PROJECTS -> {
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
    }

    StudioSectionTab.LIBRARY -> {
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
      if (uiState.allTracks.isNotEmpty()) {
        item {
          val selectedCount = if (uiState.isSelectionMode) uiState.selectedTrackIds.size else 0
          RoomStorageExportCard(
            state = uiState.roomStorageExport,
            roomTrackCount = uiState.allTracks.size,
            selectedTrackCount = selectedCount,
            onSelectFormat = roomStorageExportActions.onSelectFormat,
            onExportToDeviceMusic = if (selectedCount > 0) {
              roomStorageExportActions.onExportSelectedToDeviceMusic
            } else {
              roomStorageExportActions.onExportAllToDeviceMusic
            },
            onChooseStorageDirectory = if (selectedCount > 0) {
              roomStorageExportActions.onChooseStorageDirectoryForSelected
            } else {
              roomStorageExportActions.onChooseStorageDirectoryForAll
            },
            onDismissStatus = roomStorageExportActions.onDismissStatus,
          )
        }
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
  currentTrack: GeneratedTrack? = null,
  isQuickSavingMp3: Boolean = false,
  onQuickSaveCurrentMp3: (GeneratedTrack) -> Unit = {},
  showRestoreWaveformButton: Boolean = false,
  onRestoreBottomWaveform: () -> Unit = {},
) {
  val colorScheme = MaterialTheme.colorScheme
  val nextThemeMode = when (themeMode) {
    ThemeMode.DARK -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.SYSTEM
    ThemeMode.SYSTEM -> ThemeMode.DARK
  }
  val themeIcon = when (themeMode) {
    ThemeMode.LIGHT -> Icons.Rounded.LightMode
    ThemeMode.DARK -> Icons.Rounded.DarkMode
    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = stringResource(R.string.studio_subtitle),
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (showRestoreWaveformButton) {
        IconButton(
          onClick = onRestoreBottomWaveform,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("restore_bottom_waveform_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = stringResource(R.string.cd_restore_bottom_waveform),
            tint = colorScheme.primary,
          )
        }
      }

      if (currentTrack != null) {
        IconButton(
          onClick = { onQuickSaveCurrentMp3(currentTrack) },
          enabled = !isQuickSavingMp3,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("quick_save_mp3_button"),
        ) {
          if (isQuickSavingMp3) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Rounded.SaveAlt,
              contentDescription = stringResource(R.string.cd_export_mp3),
              tint = colorScheme.primary,
            )
          }
        }
      }

      IconButton(
        onClick = { onSelectThemeMode(nextThemeMode) },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("theme_cycle_button"),
      ) {
        Icon(
          imageVector = themeIcon,
          contentDescription = stringResource(R.string.cd_cycle_theme_mode),
          tint = colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
internal fun BottomWaveformBar(
  track: GeneratedTrack?,
  playbackState: com.example.audio.AudioPlaybackState,
  canSkipNext: Boolean,
  applyNavBarPadding: Boolean = true,
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
        .then(if (applyNavBarPadding) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
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

// Legacy PromptSynthesizerCard removed in favor of SoundResourcesCard on Studio tab.

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
        var menuExpanded by remember(track.id) { mutableStateOf(false) }
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

          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("more_actions_${track.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.cd_more_track_actions),
                tint = colorScheme.onSurfaceVariant,
              )
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.track_menu_add_playlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onAddToPlaylist()
                },
                modifier = Modifier.testTag("add_to_playlist_${track.id}"),
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.track_menu_trim)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onTrimTrack()
                },
                modifier = Modifier.testTag("trim_track_${track.id}"),
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.track_menu_edit_tags)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onEditOrganization()
                },
                modifier = Modifier.testTag("edit_tags_${track.id}"),
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.track_menu_delete), color = colorScheme.error) },
                leadingIcon = {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = colorScheme.error,
                  )
                },
                onClick = {
                  menuExpanded = false
                  onDelete()
                },
                modifier = Modifier.testTag("delete_track_${track.id}"),
              )
            }
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
