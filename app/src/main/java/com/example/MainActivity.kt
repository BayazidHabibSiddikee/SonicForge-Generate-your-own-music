package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AudioExportFormat
import com.example.audio.Mp3AudioExporter
import com.example.model.GeneratedTrack
import com.example.ui.NotificationPermissionStatus
import com.example.ui.SonicForgeScreen
import com.example.ui.SonicForgeViewModel
import com.example.ui.theme.SonicForgeTheme
import com.example.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: SonicForgeViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val isDarkTheme = resolveDarkTheme(uiState.themeMode)

      LaunchedEffect(isDarkTheme) {
        val barStyle = if (isDarkTheme) {
          SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
          SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
          )
        }
        enableEdgeToEdge(
          statusBarStyle = barStyle,
          navigationBarStyle = barStyle,
        )
      }

      SonicForgeTheme(darkTheme = isDarkTheme) {
        val lifecycleOwner = LocalLifecycleOwner.current
        var hasPromptedNotifications by rememberSaveable { mutableStateOf(false) }

        fun refreshNotificationPermissionStatus() {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.updateNotificationPermission(NotificationPermissionStatus.GRANTED)
          } else {
            val granted = ContextCompat.checkSelfPermission(
              this,
              Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            val status = when {
              granted -> NotificationPermissionStatus.GRANTED
              hasPromptedNotifications -> NotificationPermissionStatus.DENIED
              else -> NotificationPermissionStatus.NEEDS_REQUEST
            }
            viewModel.updateNotificationPermission(status)
          }
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
          hasPromptedNotifications = true
          viewModel.updateNotificationPermission(
            if (granted) NotificationPermissionStatus.GRANTED else NotificationPermissionStatus.DENIED
          )
        }

        val audioPickerLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
          if (uri != null) {
            viewModel.importAudioUri(uri)
          }
        }

        var pendingSafExportTrack by remember { mutableStateOf<GeneratedTrack?>(null) }
        val createMp3Launcher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.CreateDocument("audio/*"),
        ) { uri ->
          val targetTrack = pendingSafExportTrack
          pendingSafExportTrack = null
          if (uri != null && targetTrack != null) {
            viewModel.exportTrackToSafUri(targetTrack, uri)
          }
        }

        LaunchedEffect(Unit) {
          refreshNotificationPermissionStatus()
        }

        DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
              refreshNotificationPermissionStatus()
            }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
          }
        }

        val bindings = remember(viewModel) {
          SonicForgeScreenBindings(
            viewModel = viewModel,
            onImportAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
            onExportToMusic = { track ->
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                viewModel.exportTrackToMusic(track)
              } else {
                pendingSafExportTrack = track
                createMp3Launcher.launch(
                  Mp3AudioExporter.suggestedFileName(track.title, viewModel.uiState.value.exportConfig.format)
                )
              }
            },
            onExportToFolder = { track ->
              pendingSafExportTrack = track
              createMp3Launcher.launch(
                Mp3AudioExporter.suggestedFileName(track.title, viewModel.uiState.value.exportConfig.format)
              )
            },
            onShareMp3 = { track ->
              viewModel.shareTrackAsMp3(track) { chooserIntent ->
                startActivity(chooserIntent)
              }
            },
            onRequestNotificationPermission = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onOpenNotificationSettings = {
              val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
              }
              startActivity(settingsIntent)
            },
            onPromptNotificationsIfNeeded = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                viewModel.uiState.value.notificationPermission == NotificationPermissionStatus.NEEDS_REQUEST &&
                !hasPromptedNotifications
              ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            launchSafMp3Save = { fallbackTrack ->
              pendingSafExportTrack = fallbackTrack
              createMp3Launcher.launch(
                Mp3AudioExporter.suggestedFileName(fallbackTrack.title, AudioExportFormat.MP3)
              )
            },
          )
        }

        SonicForgeScreen(
          uiState = uiState,
          onSelectThemeMode = bindings.onSelectThemeMode,
          onSelectEngine = bindings.onSelectEngine,
          onPromptChanged = bindings.onPromptChanged,
          onApplyPreset = bindings.onApplyPreset,
          onGenerateTrack = bindings.onGenerateTrack,
          onImportAudioClick = bindings.onImportAudioClick,
          onSelectCategory = bindings.onSelectCategory,
          onSetShowFavoritesOnly = bindings.onSetShowFavoritesOnly,
          onSearchQueryChanged = bindings.onSearchQueryChanged,
          onSelectPlaylistFilter = bindings.onSelectPlaylistFilter,
          onOpenCreatePlaylistDialog = bindings.onOpenCreatePlaylistDialog,
          onDismissCreatePlaylistDialog = bindings.onDismissCreatePlaylistDialog,
          onCreatePlaylist = bindings.onCreatePlaylist,
          onPlayPlaylist = bindings.onPlayPlaylist,
          onDeletePlaylist = bindings.onDeletePlaylist,
          onOpenTrackPlaylistsDialog = bindings.onOpenTrackPlaylistsDialog,
          onDismissTrackPlaylistsDialog = bindings.onDismissTrackPlaylistsDialog,
          onSetTrackPlaylistMembership = bindings.onSetTrackPlaylistMembership,
          onCreatePlaylistForTrack = bindings.onCreatePlaylistForTrack,
          onToggleTrackFavorite = bindings.onToggleTrackFavorite,
          onEditOrganizationClick = bindings.onEditOrganizationClick,
          onDismissEditOrganization = bindings.onDismissEditOrganization,
          onSaveOrganization = bindings.onSaveOrganization,
          onTrimTrackClick = bindings.onTrimTrackClick,
          onDismissTrimDialog = bindings.onDismissTrimDialog,
          onConfirmTrim = bindings.onConfirmTrim,
          onExportMp3Click = bindings.onExportMp3Click,
          onDismissExportDialog = bindings.onDismissExportDialog,
          onSelectExportFormat = bindings.onSelectExportFormat,
          onSelectExportBitrateOption = bindings.onSelectExportBitrateOption,
          onToggleExportNormalizeGain = bindings.onToggleExportNormalizeGain,
          onExportMp3ToMusic = bindings.onExportMp3ToMusic,
          onExportMp3ToFolder = bindings.onExportMp3ToFolder,
          onShareMp3 = bindings.onShareMp3,
          onRequestNotificationPermission = bindings.onRequestNotificationPermission,
          onOpenNotificationSettings = bindings.onOpenNotificationSettings,
          onSelectTrack = bindings.onSelectTrack,
          onDeleteTrack = bindings.onDeleteTrack,
          onTogglePlayPause = bindings.onTogglePlayPause,
          onPlay = bindings.onPlay,
          onPause = bindings.onPause,
          onStop = bindings.onStop,
          onBeginSeek = bindings.onBeginSeek,
          onPreviewSeek = bindings.onPreviewSeek,
          onCommitSeek = bindings.onCommitSeek,
          onSeekBy = bindings.onSeekBy,
          onSkipPrevious = bindings.onSkipPrevious,
          onSkipNext = bindings.onSkipNext,
          onToggleLoop = bindings.onToggleLoop,
          onVolumeChange = bindings.onVolumeChange,
          onToggleMute = bindings.onToggleMute,
          onPitchChange = bindings.onPitchChange,
          onStepPitch = bindings.onStepPitch,
          onResetPitch = bindings.onResetPitch,
          onSelectReverbPreset = bindings.onSelectReverbPreset,
          onReverbWetMixChange = bindings.onReverbWetMixChange,
          onReverbRoomSizeChange = bindings.onReverbRoomSizeChange,
          onReverbDampingChange = bindings.onReverbDampingChange,
          onReverbEngineModeChange = bindings.onReverbEngineModeChange,
          onResetReverb = bindings.onResetReverb,
          onSaveReverbCopy = bindings.onSaveReverbCopy,
          onEqualizerEnabledChange = bindings.onEqualizerEnabledChange,
          onSelectEqualizerPreset = bindings.onSelectEqualizerPreset,
          onEqualizerBandGainChange = bindings.onEqualizerBandGainChange,
          onResetEqualizer = bindings.onResetEqualizer,
          onAutoPostGainEnabledChange = bindings.onAutoPostGainEnabledChange,
          onSelectLoudnessTarget = bindings.onSelectLoudnessTarget,
          onNormalizeActiveTrack = bindings.onNormalizeActiveTrack,
          onNormalizeLibrary = bindings.onNormalizeLibrary,
          onToggleSelectionMode = bindings.onToggleSelectionMode,
          onEnterSelectionWithTrack = bindings.onEnterSelectionWithTrack,
          onToggleTrackSelection = bindings.onToggleTrackSelection,
          onToggleSelectAllVisible = bindings.onToggleSelectAllVisible,
          onExitSelectionMode = bindings.onExitSelectionMode,
          onOpenBulkRenameDialog = bindings.onOpenBulkRenameDialog,
          onDismissBulkRenameDialog = bindings.onDismissBulkRenameDialog,
          onConfirmBulkRename = bindings.onConfirmBulkRename,
          onSuggestAutoTags = bindings.onSuggestAutoTags,
          onDismissAutoTagBanner = bindings.onDismissAutoTagBanner,
          onOpenUrlDownloadDialog = bindings.onOpenUrlDownloadDialog,
          onDismissUrlDownloadDialog = bindings.onDismissUrlDownloadDialog,
          onStartSegmentedUrlDownload = bindings.onStartSegmentedUrlDownload,
          onSubmitAria2RpcDownload = bindings.onSubmitAria2RpcDownload,
          onCrossfadeEnabledChange = bindings.onCrossfadeEnabledChange,
          onCrossfadeDurationChange = bindings.onCrossfadeDurationChange,
          onSelectCrossfadePreset = bindings.onSelectCrossfadePreset,
          onSelectWaveformInteractionMode = bindings.onSelectWaveformInteractionMode,
          onPaintTrackEnvelopePoint = bindings.onPaintTrackEnvelopePoint,
          onMuteTrackSegment = bindings.onMuteTrackSegment,
          onToggleMuteAtTap = bindings.onToggleMuteAtTap,
          onResetTrackAudioEnvelope = bindings.onResetTrackAudioEnvelope,
          onSetBottomWaveformVisible = bindings.onSetBottomWaveformVisible,
          onToggleSequencerStep = bindings.onToggleSequencerStep,
          onToggleSequencerPlayStop = bindings.onToggleSequencerPlayStop,
          onClearSequencerPattern = bindings.onClearSequencerPattern,
          onApplySequencerPreset = bindings.onApplySequencerPreset,
          onStepSequencerBpm = bindings.onStepSequencerBpm,
          onToggleSequencerBpmSync = bindings.onToggleSequencerBpmSync,
          onCycleSequencerSwing = bindings.onCycleSequencerSwing,
          onRenderSequencerToLibrary = bindings.onRenderSequencerToLibrary,
          onToggleMetronomePlayStop = bindings.onToggleMetronomePlayStop,
          onStepMetronomeBpm = bindings.onStepMetronomeBpm,
          onToggleMetronomeBpmSync = bindings.onToggleMetronomeBpmSync,
          onMetronomeVolumeChange = bindings.onMetronomeVolumeChange,
          onMetronomePitchHzChange = bindings.onMetronomePitchHzChange,
          onSelectMetronomeToneStyle = bindings.onSelectMetronomeToneStyle,
          onSelectMetronomeTimeSignature = bindings.onSelectMetronomeTimeSignature,
          onToggleMetronomeAccentDownbeat = bindings.onToggleMetronomeAccentDownbeat,
          onTapMetronomeTempo = bindings.onTapMetronomeTempo,
          onOpenSaveProjectDialog = bindings.onOpenSaveProjectDialog,
          onDismissSaveProjectDialog = bindings.onDismissSaveProjectDialog,
          onConfirmSaveProject = bindings.onConfirmSaveProject,
          onQuickSaveActiveProject = bindings.onQuickSaveActiveProject,
          onLoadProject = bindings.onLoadProject,
          onDeleteProject = bindings.onDeleteProject,
          onDismissProjectStatus = bindings.onDismissProjectStatus,
          onSelectDestination = bindings.onSelectDestination,
          onSelectStudioSectionTab = bindings.onSelectStudioSectionTab,
          onGenerateSoundResource = bindings.onGenerateSoundResource,
          onQuickSaveCurrentMp3 = bindings.onQuickSaveCurrentMp3,
          onDismissQuickMp3Status = bindings.onDismissQuickMp3Status,
          onSelectPianoVoice = bindings.onSelectPianoVoice,
          onSelectPianoProgression = bindings.onSelectPianoProgression,
          onShiftPianoOctave = bindings.onShiftPianoOctave,
          onStepPianoBpm = bindings.onStepPianoBpm,
          onPlayPianoKey = bindings.onPlayPianoKey,
          onPlayPianoChord = bindings.onPlayPianoChord,
          onTogglePianoRecording = bindings.onTogglePianoRecording,
          onClearPianoRecording = bindings.onClearPianoRecording,
          onPreviewPianoPattern = bindings.onPreviewPianoPattern,
          onGeneratePianoTrack = bindings.onGeneratePianoTrack,
          onSelectGuitarVoice = bindings.onSelectGuitarVoice,
          onSelectGuitarChord = bindings.onSelectGuitarChord,
          onToggleGuitarChordInSequence = bindings.onToggleGuitarChordInSequence,
          onAddGuitarChordToSequence = bindings.onAddGuitarChordToSequence,
          onRemoveGuitarChordFromSequenceAt = bindings.onRemoveGuitarChordFromSequenceAt,
          onApplyGuitarProgressionPreset = bindings.onApplyGuitarProgressionPreset,
          onSelectGuitarStrumStyle = bindings.onSelectGuitarStrumStyle,
          onStepGuitarBpm = bindings.onStepGuitarBpm,
          onPluckGuitarString = bindings.onPluckGuitarString,
          onStrumGuitarChord = bindings.onStrumGuitarChord,
          onToggleGuitarRecording = bindings.onToggleGuitarRecording,
          onClearGuitarRecording = bindings.onClearGuitarRecording,
          onPreviewGuitarPattern = bindings.onPreviewGuitarPattern,
          onGenerateGuitarTrack = bindings.onGenerateGuitarTrack,
          onSelectDrumKit = bindings.onSelectDrumKit,
          onTriggerDrumPad = bindings.onTriggerDrumPad,
          onToggleDrumPadRecording = bindings.onToggleDrumPadRecording,
          onClearDrumPadRecording = bindings.onClearDrumPadRecording,
          onPreviewDrumPadTake = bindings.onPreviewDrumPadTake,
          onGenerateDrumTrack = bindings.onGenerateDrumTrack,
          onToggleMergeTrackSelection = bindings.onToggleMergeTrackSelection,
          onMergeStemGainChange = bindings.onMergeStemGainChange,
          onMergeLoopShorterStemsChange = bindings.onMergeLoopShorterStemsChange,
          onMergeTitleChanged = bindings.onMergeTitleChanged,
          onAutoPickBandStems = bindings.onAutoPickBandStems,
          onMergeSelectedTracks = bindings.onMergeSelectedTracks,
          onMergeAndSaveMp3 = bindings.onMergeAndSaveMp3,
          onSelectHistoryTypeFilter = bindings.onSelectHistoryTypeFilter,
          onHistorySearchQueryChanged = bindings.onHistorySearchQueryChanged,
          onPlayHistoryItem = bindings.onPlayHistoryItem,
          onReuseHistoryPrompt = bindings.onReuseHistoryPrompt,
          onDeleteHistoryEntry = bindings.onDeleteHistoryEntry,
          onClearAllHistory = bindings.onClearAllHistory,
        )
      }
    }
  }
}

private class SonicForgeScreenBindings(
  viewModel: SonicForgeViewModel,
  onImportAudio: () -> Unit,
  onExportToMusic: (GeneratedTrack) -> Unit,
  onExportToFolder: (GeneratedTrack) -> Unit,
  onShareMp3: (GeneratedTrack) -> Unit,
  val onRequestNotificationPermission: () -> Unit,
  val onOpenNotificationSettings: () -> Unit,
  onPromptNotificationsIfNeeded: () -> Unit,
  launchSafMp3Save: (GeneratedTrack) -> Unit,
) {
  val onSelectThemeMode = viewModel::setThemeMode
  val onSelectEngine = viewModel::selectEngine
  val onPromptChanged = viewModel::onPromptChanged
  val onApplyPreset = viewModel::applyPresetPrompt
  val onGenerateTrack = viewModel::generateTrackFromPrompt
  val onImportAudioClick = onImportAudio
  val onSelectCategory = viewModel::selectCategory
  val onSetShowFavoritesOnly = viewModel::setShowFavoritesOnly
  val onSearchQueryChanged = viewModel::onSearchQueryChanged
  val onSelectPlaylistFilter = viewModel::selectPlaylistFilter
  val onOpenCreatePlaylistDialog: () -> Unit = { viewModel.openCreatePlaylistDialog() }
  val onDismissCreatePlaylistDialog = viewModel::dismissCreatePlaylistDialog
  val onCreatePlaylist = viewModel::createPlaylist
  val onPlayPlaylist = viewModel::playPlaylist
  val onDeletePlaylist = viewModel::deletePlaylist
  val onOpenTrackPlaylistsDialog = viewModel::openTrackPlaylistsDialog
  val onDismissTrackPlaylistsDialog = viewModel::dismissTrackPlaylistsDialog
  val onSetTrackPlaylistMembership = viewModel::setTrackPlaylistMembership
  val onCreatePlaylistForTrack: (String) -> Unit = { trackId -> viewModel.openCreatePlaylistDialog(trackId) }
  val onToggleTrackFavorite = viewModel::toggleTrackFavorite
  val onEditOrganizationClick = viewModel::openEditOrganizationDialog
  val onDismissEditOrganization = viewModel::dismissEditOrganizationDialog
  val onSaveOrganization = viewModel::saveTrackOrganization
  val onTrimTrackClick = viewModel::openTrimDialog
  val onDismissTrimDialog = viewModel::dismissTrimDialog
  val onConfirmTrim = viewModel::confirmTrimTrack
  val onExportMp3Click = viewModel::openExportDialog
  val onDismissExportDialog = viewModel::dismissExportDialog
  val onSelectExportFormat = viewModel::selectExportFormat
  val onSelectExportBitrateOption = viewModel::selectExportBitrateOption
  val onToggleExportNormalizeGain = viewModel::setExportNormalizeGain
  val onExportMp3ToMusic = onExportToMusic
  val onExportMp3ToFolder = onExportToFolder
  val onShareMp3 = onShareMp3
  val onSelectTrack: (GeneratedTrack) -> Unit = { track -> viewModel.selectTrack(track, autoPlay = true) }
  val onDeleteTrack = viewModel::deleteTrack
  val onTogglePlayPause: () -> Unit = {
    onPromptNotificationsIfNeeded()
    viewModel.togglePlayPause()
  }
  val onPlay: () -> Unit = {
    onPromptNotificationsIfNeeded()
    viewModel.playTrack()
  }
  val onPause = viewModel::pauseTrack
  val onStop = viewModel::stopTrack
  val onBeginSeek = viewModel::beginSeek
  val onPreviewSeek = viewModel::previewSeek
  val onCommitSeek = viewModel::commitSeek
  val onSeekBy = viewModel::seekBy
  val onSkipPrevious = viewModel::skipPrevious
  val onSkipNext = viewModel::skipNext
  val onToggleLoop = viewModel::toggleLoop
  val onVolumeChange = viewModel::setVolume
  val onToggleMute = viewModel::toggleMute
  val onPitchChange = viewModel::setPitchSemitones
  val onStepPitch = viewModel::stepPitchSemitones
  val onResetPitch = viewModel::resetPitch
  val onSelectReverbPreset = viewModel::selectReverbPreset
  val onReverbWetMixChange = viewModel::setReverbWetMix
  val onReverbRoomSizeChange = viewModel::setReverbRoomSize
  val onReverbDampingChange = viewModel::setReverbDamping
  val onReverbEngineModeChange = viewModel::setReverbEngineMode
  val onResetReverb = viewModel::resetReverb
  val onSaveReverbCopy = viewModel::saveReverbCopy
  val onEqualizerEnabledChange = viewModel::setEqualizerEnabled
  val onSelectEqualizerPreset = viewModel::selectEqualizerPreset
  val onEqualizerBandGainChange = viewModel::setEqualizerBandGain
  val onResetEqualizer = viewModel::resetEqualizer
  val onAutoPostGainEnabledChange = viewModel::setAutoPostGainEnabled
  val onSelectLoudnessTarget = viewModel::selectLoudnessTarget
  val onNormalizeActiveTrack = viewModel::normalizeActiveTrack
  val onNormalizeLibrary = viewModel::normalizeLibraryTracks
  val onToggleSelectionMode = viewModel::toggleSelectionMode
  val onEnterSelectionWithTrack = viewModel::enterSelectionWithTrack
  val onToggleTrackSelection = viewModel::toggleTrackSelection
  val onToggleSelectAllVisible = viewModel::toggleSelectAllVisibleTracks
  val onExitSelectionMode = viewModel::exitSelectionMode
  val onOpenBulkRenameDialog = viewModel::openBulkRenameDialog
  val onDismissBulkRenameDialog = viewModel::dismissBulkRenameDialog
  val onConfirmBulkRename = viewModel::confirmBulkRename
  val onSuggestAutoTags = viewModel::suggestTagsForTrack
  val onDismissAutoTagBanner = viewModel::dismissAutoTagBanner
  val onOpenUrlDownloadDialog = viewModel::openUrlDownloadDialog
  val onDismissUrlDownloadDialog = viewModel::dismissUrlDownloadDialog
  val onStartSegmentedUrlDownload = viewModel::startSegmentedUrlDownload
  val onSubmitAria2RpcDownload = viewModel::submitAria2RpcDownload
  val onCrossfadeEnabledChange = viewModel::setCrossfadeEnabled
  val onCrossfadeDurationChange = viewModel::setCrossfadeDurationSec
  val onSelectCrossfadePreset = viewModel::selectCrossfadePreset
  val onSelectWaveformInteractionMode = viewModel::selectWaveformInteractionMode
  val onPaintTrackEnvelopePoint = viewModel::paintTrackEnvelopePoint
  val onMuteTrackSegment = viewModel::muteTrackSegment
  val onToggleMuteAtTap = viewModel::toggleMuteAtTap
  val onResetTrackAudioEnvelope = viewModel::resetTrackAudioEnvelope
  val onSetBottomWaveformVisible = viewModel::setBottomWaveformVisible
  val onToggleSequencerStep = viewModel::toggleSequencerStep
  val onToggleSequencerPlayStop = viewModel::toggleSequencerPlayStop
  val onClearSequencerPattern = viewModel::clearSequencerPattern
  val onApplySequencerPreset = viewModel::applySequencerPreset
  val onStepSequencerBpm = viewModel::stepSequencerBpm
  val onToggleSequencerBpmSync = viewModel::toggleSequencerBpmSync
  val onCycleSequencerSwing = viewModel::cycleSequencerSwing
  val onRenderSequencerToLibrary = viewModel::renderSequencerToLibrary
  val onToggleMetronomePlayStop = viewModel::toggleMetronomePlayStop
  val onStepMetronomeBpm = viewModel::stepMetronomeBpm
  val onToggleMetronomeBpmSync = viewModel::toggleMetronomeBpmSync
  val onMetronomeVolumeChange = viewModel::setMetronomeVolume
  val onMetronomePitchHzChange = viewModel::setMetronomePitchHz
  val onSelectMetronomeToneStyle = viewModel::selectMetronomeToneStyle
  val onSelectMetronomeTimeSignature = viewModel::selectMetronomeTimeSignature
  val onToggleMetronomeAccentDownbeat = viewModel::toggleMetronomeAccentDownbeat
  val onTapMetronomeTempo = viewModel::tapMetronomeTempo
  val onOpenSaveProjectDialog = viewModel::openSaveProjectDialog
  val onDismissSaveProjectDialog = viewModel::dismissSaveProjectDialog
  val onConfirmSaveProject: (String, String, List<String>) -> Unit = { name, notes, selectedClipIds ->
    viewModel.saveProject(name = name, notes = notes, selectedClipIds = selectedClipIds)
  }
  val onQuickSaveActiveProject = viewModel::quickSaveActiveProject
  val onLoadProject = viewModel::loadProject
  val onDeleteProject = viewModel::deleteProject
  val onDismissProjectStatus = viewModel::dismissProjectStatus
  val onSelectDestination = viewModel::selectDestination
  val onSelectStudioSectionTab = viewModel::selectStudioSectionTab
  val onGenerateSoundResource = viewModel::generateFromSoundResource
  val onQuickSaveCurrentMp3: (GeneratedTrack) -> Unit = { track ->
    viewModel.quickSaveTrackAsMp3(
      track = track,
      onRequireSafFallback = launchSafMp3Save,
    )
  }
  val onDismissQuickMp3Status = viewModel::dismissQuickMp3Status
  val onSelectPianoVoice = viewModel::selectPianoVoice
  val onSelectPianoProgression = viewModel::selectPianoProgression
  val onShiftPianoOctave = viewModel::shiftPianoOctave
  val onStepPianoBpm = viewModel::stepPianoBpm
  val onPlayPianoKey = viewModel::playPianoKey
  val onPlayPianoChord = viewModel::playPianoChord
  val onTogglePianoRecording = viewModel::togglePianoRecording
  val onClearPianoRecording = viewModel::clearPianoRecording
  val onPreviewPianoPattern = viewModel::previewPianoPattern
  val onGeneratePianoTrack = viewModel::generatePianoTrack
  val onSelectGuitarVoice = viewModel::selectGuitarVoice
  val onSelectGuitarChord = viewModel::selectGuitarChord
  val onToggleGuitarChordInSequence = viewModel::toggleGuitarChordInSequence
  val onAddGuitarChordToSequence = viewModel::addGuitarChordToSequence
  val onRemoveGuitarChordFromSequenceAt = viewModel::removeGuitarChordFromSequenceAt
  val onApplyGuitarProgressionPreset = viewModel::applyGuitarProgressionPreset
  val onSelectGuitarStrumStyle = viewModel::selectGuitarStrumStyle
  val onStepGuitarBpm = viewModel::stepGuitarBpm
  val onPluckGuitarString = viewModel::pluckGuitarString
  val onStrumGuitarChord: () -> Unit = { viewModel.strumGuitarChord() }
  val onToggleGuitarRecording = viewModel::toggleGuitarRecording
  val onClearGuitarRecording = viewModel::clearGuitarRecording
  val onPreviewGuitarPattern = viewModel::previewGuitarPattern
  val onGenerateGuitarTrack = viewModel::generateGuitarTrack
  val onSelectDrumKit = viewModel::selectDrumKit
  val onTriggerDrumPad = viewModel::triggerDrumPad
  val onToggleDrumPadRecording = viewModel::toggleDrumPadRecording
  val onClearDrumPadRecording = viewModel::clearDrumPadRecording
  val onPreviewDrumPadTake = viewModel::previewDrumPadTake
  val onGenerateDrumTrack = viewModel::generateDrumTrack
  val onToggleMergeTrackSelection = viewModel::toggleMergeTrackSelection
  val onMergeStemGainChange = viewModel::setMergeStemGain
  val onMergeLoopShorterStemsChange = viewModel::setMergeLoopShorterStems
  val onMergeTitleChanged = viewModel::onMergeTitleChanged
  val onAutoPickBandStems = viewModel::autoPickBandStems
  val onMergeSelectedTracks: () -> Unit = {
    viewModel.mergeSelectedTracks(
      saveAsMp3AfterMerge = false,
      onRequireSafFallback = launchSafMp3Save,
    )
  }
  val onMergeAndSaveMp3: () -> Unit = {
    viewModel.exportCurrentProjectMergedAudioToMp3(
      onRequireSafFallback = launchSafMp3Save,
    )
  }
  val onSelectHistoryTypeFilter = viewModel::selectHistoryTypeFilter
  val onHistorySearchQueryChanged = viewModel::onHistorySearchQueryChanged
  val onPlayHistoryItem = viewModel::playHistoryItem
  val onReuseHistoryPrompt = viewModel::reusePromptFromHistory
  val onDeleteHistoryEntry = viewModel::deleteHistoryEntry
  val onClearAllHistory = viewModel::clearAllTrackHistory
}

