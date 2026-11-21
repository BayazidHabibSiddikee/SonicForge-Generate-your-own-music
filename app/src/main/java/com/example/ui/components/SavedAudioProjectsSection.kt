package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioEngineState
import com.example.model.GeneratedTrack
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILTER_ALL = "All"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedAudioProjectsSection(
  allTracks: List<GeneratedTrack>,
  currentTrack: GeneratedTrack?,
  clipAudioState: AudioEngineState,
  onSelectAudioFile: (GeneratedTrack) -> Unit,
  onToggleAuditionAudioFile: (GeneratedTrack) -> Unit,
  onRequestRenameAudioFile: (GeneratedTrack) -> Unit,
  onRequestDeleteAudioFile: (GeneratedTrack) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(FILTER_ALL) }

  val categories = remember(allTracks) {
    listOf(FILTER_ALL) + allTracks.map { it.category.ifBlank { "General" } }.distinct().sorted()
  }

  val filteredFiles = remember(allTracks, searchQuery, selectedFilter) {
    val q = searchQuery.trim().lowercase()
    allTracks.filter { track ->
      val cat = track.category.ifBlank { "General" }
      val matchesFilter = selectedFilter == FILTER_ALL || cat.equals(selectedFilter, ignoreCase = true)
      val fileName = File(track.filePath).name
      val matchesQuery = q.isEmpty() ||
        track.title.lowercase().contains(q) ||
        fileName.lowercase().contains(q) ||
        cat.lowercase().contains(q) ||
        track.sourceLabel.lowercase().contains(q)
      matchesFilter && matchesQuery
    }
  }

  val totalBytes = remember(allTracks) { allTracks.sumOf { it.fileSizeBytes.coerceAtLeast(0L) } }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("saved_audio_projects_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.projects_audio_files_title),
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
      )
      Surface(
        shape = MaterialTheme.shapes.small,
        color = colorScheme.secondaryContainer,
      ) {
        Text(
          text = stringResource(
            R.string.projects_audio_files_summary,
            allTracks.size,
            formatStorageSize(totalBytes),
          ),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSecondaryContainer,
          modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("projects_audio_files_summary"),
        )
      }
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = {
        Text(
          text = stringResource(R.string.projects_audio_search_placeholder),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(
            onClick = { searchQuery = "" },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Clear search",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("project_files_search_input"),
    )

    if (categories.size > 1) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        categories.forEach { category ->
          val active = selectedFilter == category
          val label = if (category == FILTER_ALL) {
            stringResource(R.string.projects_audio_filter_all)
          } else {
            category
          }
          FilterChip(
            selected = active,
            onClick = { selectedFilter = category },
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
            modifier = Modifier.testTag("project_file_filter_${category.lowercase().replace(" ", "_")}"),
          )
        }
      }
    }

    if (filteredFiles.isEmpty()) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("audio_projects_empty_state"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AudioFile,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
          )
          Text(
            text = stringResource(R.string.projects_audio_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.projects_audio_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filteredFiles.forEach { track ->
          val isSelected = track.id == currentTrack?.id
          val isAuditioning = clipAudioState.activeClipId == track.id && clipAudioState.isPlaying
          SavedAudioProjectRow(
            track = track,
            isSelected = isSelected,
            isAuditioning = isAuditioning,
            onSelect = { onSelectAudioFile(track) },
            onToggleAudition = { onToggleAuditionAudioFile(track) },
            onRename = { onRequestRenameAudioFile(track) },
            onDelete = { onRequestDeleteAudioFile(track) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedAudioProjectRow(
  track: GeneratedTrack,
  isSelected: Boolean,
  isAuditioning: Boolean,
  onSelect: () -> Unit,
  onToggleAudition: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val file = remember(track.filePath) { File(track.filePath) }
  val fileName = remember(file) { file.name.ifBlank { "${track.title}.wav" } }
  val extBadge = remember(file) { file.extension.ifBlank { "wav" }.uppercase() }
  val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
  val dateText = remember(track.createdAtEpochMs) { dateFormatter.format(Date(track.createdAtEpochMs)) }

  val categoryIcon = when {
    track.category.equals("Piano", ignoreCase = true) -> Icons.Rounded.Piano
    track.category.equals("Drums", ignoreCase = true) -> Icons.Rounded.Album
    track.category.equals("Guitar", ignoreCase = true) -> Icons.Rounded.MusicNote
    else -> Icons.Rounded.GraphicEq
  }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.28f) else colorScheme.surfaceContainerHigh,
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect)
      .testTag("audio_project_item_${track.id}"),
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest,
        ) {
          Icon(
            imageVector = categoryIcon,
            contentDescription = null,
            tint = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.secondary,
            modifier = Modifier
              .padding(8.dp)
              .size(20.dp),
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = track.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            if (isSelected) {
              Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = colorScheme.primary,
                modifier = Modifier.testTag("audio_project_selected_badge_${track.id}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                  )
                  Text(
                    text = stringResource(R.string.projects_audio_selected_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onPrimary,
                  )
                }
              }
            }
          }

          Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("audio_project_filename_${track.id}"),
          )

          Text(
            text = "$extBadge · ${formatStorageSize(track.fileSizeBytes)} · ${formatDurationMs(track.durationMs)} · ${track.bpm} BPM · $dateText",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalIconButton(
          onClick = onToggleAudition,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("audio_project_play_${track.id}"),
        ) {
          Icon(
            imageVector = if (isAuditioning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(
              if (isAuditioning) R.string.cd_stop_audio_project else R.string.cd_audition_audio_project
            ),
          )
        }

        if (isSelected) {
          FilledTonalButton(
            onClick = onSelect,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("audio_project_select_${track.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.projects_audio_selected_badge),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        } else {
          OutlinedButton(
            onClick = onSelect,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("audio_project_select_${track.id}"),
          ) {
            Text(
              text = stringResource(R.string.projects_audio_select_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }

        OutlinedButton(
          onClick = onRename,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("audio_project_rename_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.cd_rename_audio_project),
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.projects_audio_rename_button),
            style = MaterialTheme.typography.labelMedium,
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("audio_project_delete_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_audio_project),
            tint = colorScheme.error,
          )
        }
      }
    }
  }
}

private fun formatStorageSize(bytes: Long): String {
  if (bytes <= 0L) return "0 KB"
  val kb = bytes / 1024.0
  return if (kb >= 1024.0) {
    String.format(Locale.US, "%.1f MB", kb / 1024.0)
  } else {
    "${kb.toInt().coerceAtLeast(1)} KB"
  }
}
