package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioDownloadMode
import com.example.audio.AudioDownloadProgress

@Composable
fun AudioUrlDownloadDialog(
  isDownloading: Boolean,
  progress: AudioDownloadProgress?,
  statusMessage: String?,
  errorMessage: String?,
  onDismiss: () -> Unit,
  onStartDirectDownload: (url: String, connections: Int) -> Unit,
  onSubmitAria2Rpc: (rpcUrl: String, sourceUrl: String, secret: String, connections: Int) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  var mode by remember { mutableStateOf(AudioDownloadMode.SEGMENTED_HTTP) }
  var audioUrl by remember { mutableStateOf("") }
  var rpcEndpoint by remember { mutableStateOf("http://127.0.0.1:6800/jsonrpc") }
  var rpcSecret by remember { mutableStateOf("") }
  var connections by remember { mutableIntStateOf(4) }

  AlertDialog(
    onDismissRequest = { if (!isDownloading) onDismiss() },
    containerColor = colorScheme.surfaceContainer,
    modifier = Modifier.testTag("audio_url_download_dialog"),
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.CloudDownload,
          contentDescription = null,
          tint = colorScheme.secondary,
        )
        Text(
          text = stringResource(R.string.download_url_dialog_title),
          style = MaterialTheme.typography.titleLarge,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.download_url_policy_note),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = mode == AudioDownloadMode.SEGMENTED_HTTP,
            onClick = { if (!isDownloading) mode = AudioDownloadMode.SEGMENTED_HTTP },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = {
              Text(
                text = stringResource(R.string.download_mode_segmented),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
              )
            },
            modifier = Modifier.testTag("download_mode_segmented"),
          )
          SegmentedButton(
            selected = mode == AudioDownloadMode.ARIA2_JSON_RPC,
            onClick = { if (!isDownloading) mode = AudioDownloadMode.ARIA2_JSON_RPC },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = {
              Text(
                text = stringResource(R.string.download_mode_aria2_rpc),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
              )
            },
            modifier = Modifier.testTag("download_mode_aria2_rpc"),
          )
        }

        OutlinedTextField(
          value = audioUrl,
          onValueChange = { audioUrl = it },
          enabled = !isDownloading,
          label = { Text(stringResource(R.string.download_url_input_label)) },
          placeholder = { Text(stringResource(R.string.download_url_input_placeholder)) },
          leadingIcon = {
            Icon(Icons.Rounded.Link, contentDescription = null)
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("download_url_input"),
        )

        if (mode == AudioDownloadMode.ARIA2_JSON_RPC) {
          OutlinedTextField(
            value = rpcEndpoint,
            onValueChange = { rpcEndpoint = it },
            enabled = !isDownloading,
            label = { Text(stringResource(R.string.aria2_rpc_endpoint_label)) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("aria2_rpc_endpoint_input"),
          )
          OutlinedTextField(
            value = rpcSecret,
            onValueChange = { rpcSecret = it },
            enabled = !isDownloading,
            label = { Text(stringResource(R.string.aria2_rpc_secret_label)) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("aria2_rpc_secret_input"),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Speed,
              contentDescription = null,
              tint = colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stringResource(R.string.download_connections_label, connections),
              style = MaterialTheme.typography.labelMedium,
              color = colorScheme.onSurfaceVariant,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 4, 8).forEach { count ->
              FilterChip(
                selected = connections == count,
                enabled = !isDownloading,
                onClick = { connections = count },
                label = {
                  Text(
                    text = "x$count",
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colorScheme.secondaryContainer,
                  selectedLabelColor = colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("download_conn_x$count"),
              )
            }
          }
        }

        if (isDownloading && progress != null) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
              progress = { progress.fraction },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text = progress.statusMessage,
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.secondary,
            )
          }
        }

        if (!statusMessage.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
            modifier = Modifier.testTag("download_url_status_banner"),
          ) {
            Text(
              text = statusMessage,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSecondaryContainer,
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            )
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.errorContainer,
            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.testTag("download_url_error_banner"),
          ) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onErrorContainer,
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (mode == AudioDownloadMode.SEGMENTED_HTTP) {
            onStartDirectDownload(audioUrl, connections)
          } else {
            onSubmitAria2Rpc(rpcEndpoint, audioUrl, rpcSecret, connections)
          }
        },
        enabled = !isDownloading && audioUrl.trim().isNotEmpty(),
        modifier = Modifier.testTag("confirm_download_url_button"),
      ) {
        if (isDownloading) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.downloading_url_label))
        } else {
          Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            stringResource(
              if (mode == AudioDownloadMode.SEGMENTED_HTTP) {
                R.string.start_download_button
              } else {
                R.string.submit_aria2_rpc_button
              },
            ),
          )
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isDownloading,
      ) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}
