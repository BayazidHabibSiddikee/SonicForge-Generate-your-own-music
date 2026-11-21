package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GeneratedTrack
import java.io.File

@Composable
fun SaveStudioProjectDialog(
  defaultName: String,
  defaultNotes: String,
  availableTracks: List<GeneratedTrack>,
  defaultSelectedClipIds: Set<String>,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onConfirmSave: (name: String, notes: String, selectedClipIds: List<String>) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  var name by remember { mutableStateOf(defaultName) }
  var notes by remember { mutableStateOf(defaultNotes) }
  val selectedIds = remember {
    mutableStateListOf<String>().apply {
      addAll(defaultSelectedClipIds.filter { id -> availableTracks.any { it.id == id } })
    }
  }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    modifier = Modifier.testTag("save_project_dialog"),
    title = {
      Text(
        text = stringResource(R.string.save_project_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.save_project_name_label)) },
          placeholder = { Text(stringResource(R.string.save_project_name_placeholder)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("project_name_input"),
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text(stringResource(R.string.save_project_notes_label)) },
          maxLines = 2,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("project_notes_input"),
        )

        Text(
          text = stringResource(R.string.save_project_includes_fx),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.secondary,
        )

        Text(
          text = stringResource(R.string.save_project_clips_label, selectedIds.size),
          style = MaterialTheme.typography.labelMedium,
          color = colorScheme.onSurfaceVariant,
        )

        if (availableTracks.isNotEmpty()) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            availableTracks.forEach { track ->
              val checked = track.id in selectedIds
              Surface(
                onClick = {
                  if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                },
                shape = MaterialTheme.shapes.small,
                color = if (checked) colorScheme.primaryContainer.copy(alpha = 0.35f) else colorScheme.surfaceContainerLow,
                modifier = Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("project_clip_select_${track.id}"),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = track.title,
                      style = MaterialTheme.typography.bodySmall,
                      color = colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${track.bpm} BPM · ${formatDurationMs(track.durationMs)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmSave(name, notes, selectedIds.toList()) },
        enabled = !isSaving && name.isNotBlank(),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("confirm_save_project_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.save_project_saving_button))
        } else {
          Text(stringResource(R.string.save_project_confirm_button))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isSaving,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("cancel_save_project_button"),
      ) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}

@Composable
fun RenameProjectDialog(
  initialName: String,
  fileExtension: String? = null,
  onDismiss: () -> Unit,
  onConfirmRename: (String) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  var name by remember(initialName) { mutableStateOf(initialName) }
  val previewFileName = remember(name, fileExtension) {
    fileExtension?.let { ext ->
      val slug = name.trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.')
        .ifBlank { "project" }
        .take(48)
      "$slug.$ext"
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("rename_project_dialog"),
    title = {
      Text(
        text = stringResource(R.string.projects_rename_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.projects_rename_field_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("rename_project_input"),
        )
        if (!previewFileName.isNullOrBlank()) {
          Text(
            text = stringResource(R.string.projects_rename_file_preview, previewFileName),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("rename_project_file_preview"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmRename(name.trim()) },
        enabled = name.trim().isNotEmpty(),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("confirm_rename_project_button"),
      ) {
        Text(stringResource(R.string.projects_rename_confirm_button))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("cancel_rename_project_button"),
      ) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}

@Composable
fun DeleteAudioProjectConfirmDialog(
  track: GeneratedTrack,
  onDismiss: () -> Unit,
  onConfirmDelete: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val fileName = remember(track.filePath) {
    File(track.filePath).name.ifBlank { "${track.title}.wav" }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("delete_audio_project_dialog"),
    title = {
      Text(
        text = stringResource(R.string.projects_delete_audio_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Text(
        text = stringResource(R.string.projects_delete_audio_dialog_body, track.title, fileName),
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmDelete,
        colors = ButtonDefaults.buttonColors(
          containerColor = colorScheme.error,
          contentColor = colorScheme.onError,
        ),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("confirm_delete_audio_project_button"),
      ) {
        Text(stringResource(R.string.projects_delete_confirm_button))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("cancel_delete_audio_project_button"),
      ) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}
