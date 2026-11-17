package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GeneratedTrack
import com.example.model.Playlist

@Composable
fun PlaylistsShelfSection(
  playlists: List<Playlist>,
  selectedPlaylistId: String?,
  onCreatePlaylistClick: () -> Unit,
  onSelectPlaylist: (String?) -> Unit,
  onPlayPlaylist: (Playlist) -> Unit,
  onDeletePlaylist: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("playlists_shelf_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
          contentDescription = null,
          tint = colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "${stringResource(R.string.playlists_section_title)} (${playlists.size})",
          style = MaterialTheme.typography.titleMedium,
          color = colorScheme.onBackground,
        )
      }

      OutlinedButton(
        onClick = onCreatePlaylistClick,
        border = BorderStroke(1.dp, colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("create_playlist_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = null,
          tint = colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.new_playlist_button),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurface,
        )
      }
    }

    if (playlists.isEmpty()) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.medium)
          .clickable(onClick = onCreatePlaylistClick)
          .testTag("playlists_empty_banner"),
      ) {
        Text(
          text = stringResource(R.string.playlists_empty_hint),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        playlists.forEach { playlist ->
          val isSelected = playlist.id == selectedPlaylistId
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerLow,
            border = BorderStroke(
              width = 1.dp,
              color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
            ),
            modifier = Modifier
              .width(210.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable {
                onSelectPlaylist(if (isSelected) null else playlist.id)
              }
              .testTag("playlist_card_${playlist.id}"),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${playlist.trackCount} tracks · ${formatDurationMs(playlist.totalDurationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                  colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                } else {
                  colorScheme.onSurfaceVariant
                },
                maxLines = 1,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconButton(
                  onClick = { onPlayPlaylist(playlist) },
                  enabled = playlist.tracks.isNotEmpty(),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("play_playlist_${playlist.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_playlist),
                    tint = if (playlist.tracks.isNotEmpty()) {
                      if (isSelected) colorScheme.onPrimaryContainer else colorScheme.primary
                    } else {
                      colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                  )
                }
                IconButton(
                  onClick = { onDeletePlaylist(playlist.id) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_playlist_${playlist.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_playlist),
                    tint = if (isSelected) {
                      colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                      colorScheme.onSurfaceVariant
                    },
                  )
                }
              }
            }
          }
        }
      }
    }

    if (selectedPlaylistId != null) {
      val selectedName = playlists.firstOrNull { it.id == selectedPlaylistId }?.name
      if (selectedName != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Playlist: $selectedName",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          TextButton(
            onClick = { onSelectPlaylist(null) },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("clear_playlist_filter_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.playlist_filter_clear),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
  }
}

@Composable
fun CreatePlaylistDialog(
  availableTracks: List<GeneratedTrack>,
  preselectedTrackIds: Set<String> = emptySet(),
  onDismiss: () -> Unit,
  onCreatePlaylist: (name: String, description: String, trackIds: List<String>) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  val selectedIds = remember(preselectedTrackIds) {
    mutableStateListOf<String>().apply { addAll(preselectedTrackIds) }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colorScheme.surfaceContainer,
    modifier = Modifier.testTag("create_playlist_dialog"),
    title = {
      Text(
        text = stringResource(R.string.create_playlist_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.playlist_name_label)) },
          placeholder = { Text(stringResource(R.string.playlist_name_placeholder)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_name_input"),
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(R.string.playlist_description_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_description_input"),
        )

        if (availableTracks.isNotEmpty()) {
          Text(
            text = stringResource(R.string.playlist_select_tracks_label),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            availableTracks.forEach { track ->
              val checked = track.id in selectedIds
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                  }
                  .padding(vertical = 4.dp, horizontal = 4.dp)
                  .testTag("create_playlist_track_${track.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { isChecked ->
                    if (isChecked) {
                      if (track.id !in selectedIds) selectedIds.add(track.id)
                    } else {
                      selectedIds.remove(track.id)
                    }
                  },
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
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
    },
    confirmButton = {
      Button(
        onClick = { onCreatePlaylist(name, description, selectedIds.toList()) },
        enabled = name.trim().isNotEmpty(),
        modifier = Modifier.testTag("confirm_create_playlist_button"),
      ) {
        Text(stringResource(R.string.create_playlist_confirm))
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
fun TrackPlaylistsDialog(
  track: GeneratedTrack,
  playlists: List<Playlist>,
  onDismiss: () -> Unit,
  onToggleTrackInPlaylist: (playlistId: String, trackId: String, isMember: Boolean) -> Unit,
  onCreateNewPlaylistForTrack: (trackId: String) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colorScheme.surfaceContainer,
    modifier = Modifier.testTag("track_playlists_dialog"),
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.track_playlists_dialog_title),
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
          .heightIn(max = 320.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (playlists.isEmpty()) {
          Text(
            text = stringResource(R.string.track_playlists_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
          )
        } else {
          playlists.forEach { playlist ->
            val isMember = playlist.tracks.any { it.id == track.id }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  onToggleTrackInPlaylist(playlist.id, track.id, !isMember)
                }
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .testTag("assign_playlist_${playlist.id}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Checkbox(
                checked = isMember,
                onCheckedChange = { checked ->
                  onToggleTrackInPlaylist(playlist.id, track.id, checked)
                },
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = playlist.name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${playlist.trackCount} tracks",
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        OutlinedButton(
          onClick = { onCreateNewPlaylistForTrack(track.id) },
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("quick_create_playlist_from_track"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.new_playlist_button))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.export_mp3_close))
      }
    },
  )
}
