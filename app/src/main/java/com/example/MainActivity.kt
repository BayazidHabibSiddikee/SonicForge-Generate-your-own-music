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

        SonicForgeScreen(
          uiState = uiState,
          onSelectThemeMode = viewModel::setThemeMode,
          onSelectEngine = viewModel::selectEngine,
          onPromptChanged = viewModel::onPromptChanged,
          onApplyPreset = viewModel::applyPresetPrompt,
          onGenerateTrack = viewModel::generateTrackFromPrompt,
          onImportAudioClick = {
            audioPickerLauncher.launch(arrayOf("audio/*"))
          },
          onSelectCategory = viewModel::selectCategory,
          onSetShowFavoritesOnly = viewModel::setShowFavoritesOnly,
          onSearchQueryChanged = viewModel::onSearchQueryChanged,
          onSelectPlaylistFilter = viewModel::selectPlaylistFilter,
          onOpenCreatePlaylistDialog = { viewModel.openCreatePlaylistDialog() },
          onDismissCreatePlaylistDialog = viewModel::dismissCreatePlaylistDialog,
          onCreatePlaylist = viewModel::createPlaylist,
          onPlayPlaylist = viewModel::playPlaylist,
          onDeletePlaylist = viewModel::deletePlaylist,
          onOpenTrackPlaylistsDialog = viewModel::openTrackPlaylistsDialog,
          onDismissTrackPlaylistsDialog = viewModel::dismissTrackPlaylistsDialog,
          onSetTrackPlaylistMembership = viewModel::setTrackPlaylistMembership,
          onCreatePlaylistForTrack = { trackId -> viewModel.openCreatePlaylistDialog(trackId) },
          onToggleTrackFavorite = viewModel::toggleTrackFavorite,
          onEditOrganizationClick = viewModel::openEditOrganizationDialog,
          onDismissEditOrganization = viewModel::dismissEditOrganizationDialog,
          onSaveOrganization = viewModel::saveTrackOrganization,
          onTrimTrackClick = viewModel::openTrimDialog,
          onDismissTrimDialog = viewModel::dismissTrimDialog,
          onConfirmTrim = viewModel::confirmTrimTrack,
          onExportMp3Click = viewModel::openExportDialog,
          onDismissExportDialog = viewModel::dismissExportDialog,
          onSelectExportFormat = viewModel::selectExportFormat,
          onSelectExportBitrateOption = viewModel::selectExportBitrateOption,
          onToggleExportNormalizeGain = viewModel::setExportNormalizeGain,
          onExportMp3ToMusic = { track ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              viewModel.exportTrackToMusic(track)
            } else {
              pendingSafExportTrack = track
              createMp3Launcher.launch(
                Mp3AudioExporter.suggestedFileName(track.title, uiState.exportConfig.format)
              )
            }
          },
          onExportMp3ToFolder = { track ->
            pendingSafExportTrack = track
            createMp3Launcher.launch(
              Mp3AudioExporter.suggestedFileName(track.title, uiState.exportConfig.format)
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
          onSelectTrack = { track -> viewModel.selectTrack(track, autoPlay = true) },
          onDeleteTrack = viewModel::deleteTrack,
          onTogglePlayPause = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
              uiState.notificationPermission == NotificationPermissionStatus.NEEDS_REQUEST &&
              !hasPromptedNotifications
            ) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.togglePlayPause()
          },
          onBeginSeek = viewModel::beginSeek,
          onPreviewSeek = viewModel::previewSeek,
          onCommitSeek = viewModel::commitSeek,
          onSeekBy = viewModel::seekBy,
          onSkipPrevious = viewModel::skipPrevious,
          onSkipNext = viewModel::skipNext,
          onToggleLoop = viewModel::toggleLoop,
          onVolumeChange = viewModel::setVolume,
          onToggleMute = viewModel::toggleMute,
          onPitchChange = viewModel::setPitchSemitones,
          onStepPitch = viewModel::stepPitchSemitones,
          onResetPitch = viewModel::resetPitch,
          onSelectReverbPreset = viewModel::selectReverbPreset,
          onReverbWetMixChange = viewModel::setReverbWetMix,
          onReverbRoomSizeChange = viewModel::setReverbRoomSize,
          onReverbDampingChange = viewModel::setReverbDamping,
          onReverbEngineModeChange = viewModel::setReverbEngineMode,
          onResetReverb = viewModel::resetReverb,
          onSaveReverbCopy = viewModel::saveReverbCopy,
          onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
          onSelectEqualizerPreset = viewModel::selectEqualizerPreset,
          onEqualizerBandGainChange = viewModel::setEqualizerBandGain,
          onResetEqualizer = viewModel::resetEqualizer,
          onAutoPostGainEnabledChange = viewModel::setAutoPostGainEnabled,
          onSelectLoudnessTarget = viewModel::selectLoudnessTarget,
          onNormalizeActiveTrack = viewModel::normalizeActiveTrack,
          onNormalizeLibrary = viewModel::normalizeLibraryTracks,
          onToggleSelectionMode = viewModel::toggleSelectionMode,
          onEnterSelectionWithTrack = viewModel::enterSelectionWithTrack,
          onToggleTrackSelection = viewModel::toggleTrackSelection,
          onToggleSelectAllVisible = viewModel::toggleSelectAllVisibleTracks,
          onExitSelectionMode = viewModel::exitSelectionMode,
          onOpenBulkRenameDialog = viewModel::openBulkRenameDialog,
          onDismissBulkRenameDialog = viewModel::dismissBulkRenameDialog,
          onConfirmBulkRename = viewModel::confirmBulkRename,
          onSuggestAutoTags = viewModel::suggestTagsForTrack,
          onDismissAutoTagBanner = viewModel::dismissAutoTagBanner,
          onOpenUrlDownloadDialog = viewModel::openUrlDownloadDialog,
          onDismissUrlDownloadDialog = viewModel::dismissUrlDownloadDialog,
          onStartSegmentedUrlDownload = viewModel::startSegmentedUrlDownload,
          onSubmitAria2RpcDownload = viewModel::submitAria2RpcDownload,
          onCrossfadeEnabledChange = viewModel::setCrossfadeEnabled,
          onCrossfadeDurationChange = viewModel::setCrossfadeDurationSec,
          onSelectCrossfadePreset = viewModel::selectCrossfadePreset,
          onSelectWaveformInteractionMode = viewModel::selectWaveformInteractionMode,
          onPaintTrackEnvelopePoint = viewModel::paintTrackEnvelopePoint,
          onMuteTrackSegment = viewModel::muteTrackSegment,
          onToggleMuteAtTap = viewModel::toggleMuteAtTap,
          onResetTrackAudioEnvelope = viewModel::resetTrackAudioEnvelope,
          onSetBottomWaveformVisible = viewModel::setBottomWaveformVisible,
          onToggleSequencerStep = viewModel::toggleSequencerStep,
          onToggleSequencerPlayStop = viewModel::toggleSequencerPlayStop,
          onClearSequencerPattern = viewModel::clearSequencerPattern,
          onApplySequencerPreset = viewModel::applySequencerPreset,
          onStepSequencerBpm = viewModel::stepSequencerBpm,
          onToggleSequencerBpmSync = viewModel::toggleSequencerBpmSync,
          onCycleSequencerSwing = viewModel::cycleSequencerSwing,
          onRenderSequencerToLibrary = viewModel::renderSequencerToLibrary,
          onToggleMetronomePlayStop = viewModel::toggleMetronomePlayStop,
          onStepMetronomeBpm = viewModel::stepMetronomeBpm,
          onToggleMetronomeBpmSync = viewModel::toggleMetronomeBpmSync,
          onMetronomeVolumeChange = viewModel::setMetronomeVolume,
          onMetronomePitchHzChange = viewModel::setMetronomePitchHz,
          onSelectMetronomeToneStyle = viewModel::selectMetronomeToneStyle,
          onSelectMetronomeTimeSignature = viewModel::selectMetronomeTimeSignature,
          onToggleMetronomeAccentDownbeat = viewModel::toggleMetronomeAccentDownbeat,
          onTapMetronomeTempo = viewModel::tapMetronomeTempo,
          onOpenSaveProjectDialog = viewModel::openSaveProjectDialog,
          onDismissSaveProjectDialog = viewModel::dismissSaveProjectDialog,
          onConfirmSaveProject = { name, notes, selectedClipIds ->
            viewModel.saveProject(name = name, notes = notes, selectedClipIds = selectedClipIds)
          },
          onQuickSaveActiveProject = viewModel::quickSaveActiveProject,
          onLoadProject = viewModel::loadProject,
          onDeleteProject = viewModel::deleteProject,
          onDismissProjectStatus = viewModel::dismissProjectStatus,
        )
      }
    }
  }
}
