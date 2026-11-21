package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.StudioSoundResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundResourcesCard(
  generatingResourceId: String?,
  promptInput: String,
  isCustomGenerating: Boolean,
  errorMessage: String?,
  onGenerateResource: (StudioSoundResource) -> Unit,
  onPromptChanged: (String) -> Unit,
  onGenerateCustomPrompt: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  var showCustomSynth by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("sound_resources_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
          Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = colorScheme.secondary,
            modifier = Modifier.size(20.dp),
          )
          Column {
            Text(
              text = stringResource(R.string.sound_resources_title),
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.sound_resources_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Configured on-device sound resource bank (real-time Fourier/FM DSP beds)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioSoundResource.entries.forEach { res ->
          val isThisGenerating = generatingResourceId == res.id
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = res.title,
                    style = MaterialTheme.typography.titleSmall,
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
                      text = res.badge,
                      style = MaterialTheme.typography.labelSmall,
                      color = colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
                Text(
                  text = res.subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              OutlinedButton(
                onClick = { onGenerateResource(res) },
                enabled = generatingResourceId == null && !isCustomGenerating,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sound_resource_btn_${res.id}"),
              ) {
                if (isThisGenerating) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = stringResource(R.string.sound_resource_generate),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }
        }
      }

      // Collapsible custom DSP synth formula input
      TextButton(
        onClick = { showCustomSynth = !showCustomSynth },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("toggle_custom_synth_button"),
      ) {
        Icon(
          imageVector = if (showCustomSynth) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.custom_synth_toggle),
          style = MaterialTheme.typography.labelMedium,
        )
      }

      AnimatedVisibility(visible = showCustomSynth) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = promptInput,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.prompt_input_label)) },
            placeholder = { Text(stringResource(R.string.prompt_input_placeholder)) },
            singleLine = true,
            enabled = !isCustomGenerating,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onGenerateCustomPrompt() }),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = colorScheme.primary,
              unfocusedBorderColor = colorScheme.outline,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("prompt_input_field"),
          )

          errorMessage?.let { msg ->
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.error,
            )
          }

          Button(
            onClick = onGenerateCustomPrompt,
            enabled = !isCustomGenerating && generatingResourceId == null,
            colors = ButtonDefaults.buttonColors(
              containerColor = colorScheme.primary,
              contentColor = colorScheme.onPrimary,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("generate_track_button"),
          ) {
            if (isCustomGenerating) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.generating_track_label))
            } else {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.generate_track_button))
            }
          }
        }
      }
    }
  }
}
