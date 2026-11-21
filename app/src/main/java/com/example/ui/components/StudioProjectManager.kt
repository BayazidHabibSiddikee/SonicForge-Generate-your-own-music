package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioEngineState
import com.example.audio.AudioPlaybackState
import com.example.audio.PitchShiftDsp
import com.example.audio.RoomReverbDsp
import com.example.audio.StepSequencerState
import com.example.model.GeneratedTrack
import com.example.model.StudioProject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private sealed interface RenameTarget {
  data class AudioFile(val track: GeneratedTrack) : RenameTarget
  data class StudioSession(val project: StudioProject) : RenameTarget
}

/**
 * Room-backed Studio Project Management view allowing users to browse, select, preview,
 * rename, and delete saved recorded audio files as well as full studio session snapshots.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioProjectsCard(
  projects: List<StudioProject>,
  activeProjectId: String?,
  currentTrack: GeneratedTrack?,
  allTracks: List<GeneratedTrack>,
  playbackState: AudioPlaybackState,
  sequencerState: StepSequencerState,
  isSavingProject: Boolean,
  statusMessage: String?,
  onOpenSaveDialog: () -> Unit,
  onQuickSaveProject: () -> Unit,
  onLoadProject: (String) -> Unit,
  onDeleteProject: (String) -> Unit,
  onDismissStatus: () -> Unit,
  modifier: Modifier = Modifier,
  clipAudioState: AudioEngineState = AudioEngineState(),
  onSelectAudioFile: (GeneratedTrack) -> Unit = {},
  onToggleAuditionAudioFile: (GeneratedTrack) -> Unit = {},
  onRenameAudioFile: (trackId: String, newTitle: String) -> Unit = { _, _ -> },
  onDeleteAudioFile: (trackId: String) -> Unit = {},
  onRenameProject: (projectId: String, newName: String) -> Unit = { _, _ -> },
) {
  val colorScheme = MaterialTheme.colorScheme
  val activeProject = remember(projects, activeProjectId) {
    projects.firstOrNull { it.id == activeProjectId }
  }

  var renameTarget by remember { mutableStateOf<RenameTarget?>(null) }
  var pendingDeleteAudioTrack by remember { mutableStateOf<GeneratedTrack?>(null) }

  renameTarget?.let { target ->
    val (initialName, fileExt) = when (target) {
      is RenameTarget.AudioFile -> {
        val ext = File(target.track.filePath).extension.ifBlank { "wav" }
        target.track.title to ext
      }
      is RenameTarget.StudioSession -> target.project.name to null
    }
    RenameProjectDialog(
      initialName = initialName,
      fileExtension = fileExt,
      onDismiss = { renameTarget = null },
      onConfirmRename = { updatedName ->
        when (target) {
          is RenameTarget.AudioFile -> onRenameAudioFile(target.track.id, updatedName)
          is RenameTarget.StudioSession -> onRenameProject(target.project.id, updatedName)
        }
        renameTarget = null
      },
    )
  }

  pendingDeleteAudioTrack?.let { trackToDelete ->
    DeleteAudioProjectConfirmDialog(
      track = trackToDelete,
      onDismiss = { pendingDeleteAudioTrack = null },
      onConfirmDelete = {
        onDeleteAudioFile(trackToDelete.id)
        pendingDeleteAudioTrack = null
      },
    )
  }

  Surface(
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
    modifier = modifier
      .fillMaxWidth()
      .testTag("studio_projects_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Header + Save Actions
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
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = colorScheme.primaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.FolderOpen,
              contentDescription = null,
              tint = colorScheme.onPrimaryContainer,
              modifier = Modifier
                .padding(8.dp)
                .size(20.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.projects_section_title),
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (activeProject != null) {
                stringResource(R.string.projects_active_badge, activeProject.name)
              } else {
                stringResource(R.string.projects_section_subtitle)
              },
              style = MaterialTheme.typography.bodySmall,
              color = if (activeProject != null) colorScheme.primary else colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("project_active_badge"),
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (activeProject != null) {
            FilledTonalButton(
              onClick = onQuickSaveProject,
              enabled = !isSavingProject,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("project_quick_save_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.projects_quick_save_button),
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }

          Button(
            onClick = onOpenSaveDialog,
            enabled = !isSavingProject,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("project_save_as_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.BookmarkAdd,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.projects_save_as_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }

      if (!statusMessage.isNullOrBlank()) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = colorScheme.secondaryContainer,
          border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.4f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("project_status_banner"),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = statusMessage,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(
              onClick = onDismissStatus,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("project_dismiss_status_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_dismiss_project_status),
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }

      // Saved Audio Files Manager (select, audition, rename, delete)
      SavedAudioProjectsSection(
        allTracks = allTracks,
        currentTrack = currentTrack,
        clipAudioState = clipAudioState,
        onSelectAudioFile = onSelectAudioFile,
        onToggleAuditionAudioFile = onToggleAuditionAudioFile,
        onRequestRenameAudioFile = { track -> renameTarget = RenameTarget.AudioFile(track) },
        onRequestDeleteAudioFile = { track -> pendingDeleteAudioTrack = track },
      )

      HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

      // Current Session Live Snapshot Summary
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("project_current_snapshot_summary"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.projects_current_session_label),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = colorScheme.onSurfaceVariant,
            )
            Text(
              text = stringResource(
                R.string.projects_clip_count_fmt,
                allTracks.size,
                sequencerState.activeStepCount,
                sequencerState.bpm,
              ),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.secondary,
            )
          }

          Text(
            text = currentTrack?.title ?: stringResource(R.string.projects_no_active_clip),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          val liveBadges = buildList {
            if (abs(playbackState.pitchSemitones) >= 0.05f) {
              add("Pitch ${PitchShiftDsp.formatSemitoneBadge(playbackState.pitchSemitones)}")
            }
            if (playbackState.reverb.isActive) {
              add("Reverb ${playbackState.reverb.preset.label} (${RoomReverbDsp.formatWetPercent(playbackState.reverb.roomSize)})")
            }
            if (playbackState.equalizer.isActive) {
              add("EQ ${playbackState.equalizer.preset.label}")
            }
            if (playbackState.crossfade.isActive) {
              add("Crossfade ${playbackState.crossfade.durationSec.toInt()}s")
            }
            if (playbackState.activeEnvelope.isEdited) {
              add("Envelope")
            }
            add("Seq ${sequencerState.selectedPresetName}")
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            liveBadges.forEach { badge ->
              ProjectMetaPill(text = badge)
            }
          }
        }
      }

      // Saved Studio Sessions List
      Text(
        text = stringResource(R.string.projects_sessions_title),
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
      )

      if (projects.isEmpty()) {
        Text(
          text = stringResource(R.string.projects_empty_hint),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("projects_empty_hint"),
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          projects.forEach { project ->
            SavedProjectItemRow(
              project = project,
              isActive = project.id == activeProjectId,
              onLoad = { onLoadProject(project.id) },
              onRename = { renameTarget = RenameTarget.StudioSession(project) },
              onDelete = { onDeleteProject(project.id) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedProjectItemRow(
  project: StudioProject,
  isActive: Boolean,
  onLoad: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val timeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
  val updatedText = remember(project.updatedAtEpochMs) {
    timeFormatter.format(Date(project.updatedAtEpochMs))
  }
  val fxBadges = remember(project) { project.buildEffectSummaryBadges() }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (isActive) colorScheme.primaryContainer.copy(alpha = 0.30f) else colorScheme.surfaceContainerHigh,
    border = BorderStroke(
      width = 1.dp,
      color = if (isActive) colorScheme.primary.copy(alpha = 0.65f) else colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("project_item_${project.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = project.name,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = buildString {
              if (project.activeClipTitle.isNotBlank()) {
                append(project.activeClipTitle)
                append(" · ")
              }
              append("${project.totalClipCount} clips · ${project.activeSequencerSteps} steps (${project.sequencerBpm} BPM) · $updatedText")
            },
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
          OutlinedButton(
            onClick = onLoad,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("project_load_button_${project.id}"),
          ) {
            Text(
              text = stringResource(R.string.projects_load_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
          IconButton(
            onClick = onRename,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("project_rename_button_${project.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Edit,
              contentDescription = stringResource(R.string.cd_rename_studio_session),
              tint = colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("project_delete_button_${project.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = stringResource(R.string.cd_delete_project),
              tint = colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      if (project.notes.isNotBlank()) {
        Text(
          text = project.notes,
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (fxBadges.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          fxBadges.forEach { badge ->
            ProjectMetaPill(text = badge)
          }
        }
      }
    }
  }
}

@Composable
private fun ProjectMetaPill(text: String) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.small,
    color = colorScheme.surfaceContainerHighest,
    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
