package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioExportBitrateOption
import com.example.audio.AudioExportConfig
import com.example.audio.AudioExportFormat
import com.example.model.GeneratedTrack

@Composable
fun TrackExportDialog(
  track: GeneratedTrack,
  exportConfig: AudioExportConfig = AudioExportConfig(),
  isExporting: Boolean,
  exportStatusMessage: String?,
  exportErrorMessage: String?,
  onSelectFormat: (AudioExportFormat) -> Unit = {},
  onSelectBitrateOption: (AudioExportBitrateOption) -> Unit = {},
  onToggleNormalizeGain: (Boolean) -> Unit = {},
  onDismiss: () -> Unit,
  onSaveToMusic: () -> Unit,
  onChooseFolder: () -> Unit,
  onShareMp3: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val formatName = exportConfig.format.displayName
  val bitrateOptions = AudioExportConfig.optionsFor(exportConfig.format)

  AlertDialog(
    onDismissRequest = { if (!isExporting) onDismiss() },
    containerColor = colorScheme.surfaceContainer,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.AudioFile,
          contentDescription = null,
          tint = colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.export_audio_title),
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
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .testTag("export_mp3_dialog"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = exportConfig.summaryLabel,
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.secondary,
              modifier = Modifier
                .weight(1f)
                .testTag("export_format_summary"),
            )
            Text(
              text = formatDurationMs(track.durationMs),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.export_format_label),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AudioExportFormat.entries.forEach { format ->
              FilterChip(
                selected = exportConfig.format == format,
                enabled = !isExporting,
                onClick = { onSelectFormat(format) },
                label = {
                  Text(
                    text = "${format.displayName} (.${format.extension})",
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.primaryContainer,
                  selectedLabelColor = colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.testTag("export_format_${format.name.lowercase()}"),
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = if (exportConfig.format == AudioExportFormat.MIDI) {
              stringResource(R.string.export_midi_scope_label)
            } else {
              stringResource(R.string.export_bitrate_label)
            },
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            bitrateOptions.forEach { option ->
              FilterChip(
                selected = exportConfig.selectedOptionId == option.id,
                enabled = !isExporting,
                onClick = { onSelectBitrateOption(option) },
                label = {
                  Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.secondaryContainer,
                  selectedLabelColor = colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.testTag("export_bitrate_${option.id}"),
              )
            }
          }
        }

        if (exportConfig.format != AudioExportFormat.MIDI) {
          val targetLufsFormatted = "%.0f LUFS".format(exportConfig.targetOutputLufs)
          FilterChip(
            selected = exportConfig.normalizeOutputGain,
            enabled = !isExporting,
            onClick = { onToggleNormalizeGain(!exportConfig.normalizeOutputGain) },
            leadingIcon = if (exportConfig.normalizeOutputGain) {
              {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
              }
            } else {
              null
            },
            label = {
              Text(
                text = stringResource(R.string.export_normalize_gain_label, targetLufsFormatted),
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.tertiaryContainer,
              selectedLabelColor = colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier.testTag("export_normalize_gain_toggle"),
          )
        }

        if (isExporting) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = colorScheme.primary,
            )
            Text(
              text = stringResource(R.string.export_encoding_format, formatName),
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }

        Button(
          onClick = onSaveToMusic,
          enabled = !isExporting,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("export_mp3_music_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (exportConfig.format == AudioExportFormat.MIDI) {
              stringResource(R.string.export_midi_save_button)
            } else {
              stringResource(R.string.export_music_format_button, formatName)
            },
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onChooseFolder,
            enabled = !isExporting,
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("export_mp3_folder_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.FolderOpen,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.export_folder_format_button, formatName),
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          OutlinedButton(
            onClick = onShareMp3,
            enabled = !isExporting,
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("share_mp3_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Share,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.export_share_format_button, formatName),
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        exportStatusMessage?.let { status ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
            modifier = Modifier.testTag("export_mp3_status_banner"),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        exportErrorMessage?.let { err ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.tertiaryContainer,
            border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.45f)),
          ) {
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onTertiaryContainer,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isExporting,
        modifier = Modifier.testTag("export_mp3_done_button"),
      ) {
        Text(stringResource(R.string.export_mp3_close))
      }
    },
  )
}
