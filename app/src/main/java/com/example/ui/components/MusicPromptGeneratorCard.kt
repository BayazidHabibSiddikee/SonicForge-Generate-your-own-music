package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.GenerationEngine

private val quickPromptPresets = listOf(
  "Lo-fi chill beat with warm Rhodes piano 84 BPM",
  "Neon synthwave night drive arpeggio 112 BPM",
  "Ambient cinematic atmospheric pads 72 BPM",
  "Upbeat funky house bassline and groove 122 BPM",
)

/**
 * Jetpack Compose UI component allowing users to enter a text prompt for music generation,
 * choose the target music generation API/engine, apply inspiration presets, and trigger
 * generation with a dedicated 'Generate' button.
 */
@Composable
fun MusicPromptGeneratorCard(
  promptInput: String,
  selectedEngine: GenerationEngine,
  isGenerating: Boolean,
  errorMessage: String?,
  onPromptChanged: (String) -> Unit,
  onSelectEngine: (GenerationEngine) -> Unit,
  onApplyPreset: (String) -> Unit,
  onGenerateClick: () -> Unit,
  modifier: Modifier = Modifier,
  isBusyExternal: Boolean = false,
) {
  val colorScheme = MaterialTheme.colorScheme
  val isApiEngine = selectedEngine != GenerationEngine.FOURIER_SYNTH
  val canGenerate = promptInput.isNotBlank() && !isGenerating && !isBusyExternal

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("music_prompt_generator_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    tonalElevation = 4.dp,
    border = BorderStroke(
      width = 1.dp,
      color = if (isGenerating) {
        colorScheme.primary.copy(alpha = 0.65f)
      } else {
        colorScheme.outlineVariant
      },
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(
                colorScheme.primary.copy(alpha = if (isGenerating) 0.18f else 0.09f),
                colorScheme.secondary.copy(alpha = 0.04f),
                Color.Transparent,
              ),
              center = Offset(size.width * 0.88f, size.height * 0.12f),
              radius = size.maxDimension * 0.75f,
            )
          )
        }
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header + API / Local Mode Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = colorScheme.primaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = colorScheme.onPrimaryContainer,
              modifier = Modifier
                .padding(8.dp)
                .size(20.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.music_prompt_card_title),
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = stringResource(R.string.music_prompt_card_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isApiEngine) colorScheme.secondaryContainer else colorScheme.surfaceContainerHighest,
          modifier = Modifier.testTag("music_prompt_mode_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = if (isApiEngine) Icons.Rounded.Cloud else Icons.Rounded.Memory,
              contentDescription = null,
              tint = if (isApiEngine) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = stringResource(
                if (isApiEngine) R.string.music_prompt_api_badge else R.string.music_prompt_local_badge
              ),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = if (isApiEngine) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Music Generation Engine Selector (API Endpoints + Local DSP)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val engineOrder = listOf(
          GenerationEngine.HF_MUSICGEN to R.string.engine_hf_musicgen,
          GenerationEngine.HF_AUDIO_LDM to R.string.engine_hf_audioldm,
          GenerationEngine.HF_STABLE_AUDIO to R.string.engine_hf_stable_audio,
          GenerationEngine.FOURIER_SYNTH to R.string.engine_fourier_dsp,
        )
        engineOrder.forEach { (engine, labelRes) ->
          val selected = selectedEngine == engine
          FilterChip(
            selected = selected,
            enabled = !isGenerating,
            onClick = { onSelectEngine(engine) },
            label = {
              Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.primaryContainer,
              selectedLabelColor = colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.testTag("engine_chip_${engine.testTagSuffix}"),
          )
        }
      }

      // Text Prompt Input Field
      OutlinedTextField(
        value = promptInput,
        onValueChange = onPromptChanged,
        label = { Text(stringResource(R.string.music_prompt_field_label)) },
        placeholder = { Text(stringResource(R.string.music_prompt_field_placeholder)) },
        minLines = 2,
        maxLines = 3,
        enabled = !isGenerating,
        trailingIcon = {
          if (promptInput.isNotEmpty() && !isGenerating) {
            IconButton(
              onClick = { onPromptChanged("") },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("music_prompt_clear_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_clear_music_prompt),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
          onDone = {
            if (canGenerate) onGenerateClick()
          },
        ),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = colorScheme.primary,
          unfocusedBorderColor = colorScheme.outline,
          focusedContainerColor = colorScheme.surfaceContainerLow,
          unfocusedContainerColor = colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("music_prompt_input_field"),
      )

      // Quick Prompt Inspiration Presets
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        quickPromptPresets.forEachIndexed { index, preset ->
          AssistChip(
            onClick = { onApplyPreset(preset) },
            enabled = !isGenerating,
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            },
            label = {
              Text(
                text = preset,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            colors = AssistChipDefaults.assistChipColors(
              containerColor = colorScheme.surfaceContainerHigh,
              labelColor = colorScheme.onSurfaceVariant,
              leadingIconContentColor = colorScheme.secondary,
            ),
            modifier = Modifier.testTag("music_prompt_preset_$index"),
          )
        }
      }

      // Error feedback banner
      AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
        errorMessage?.let { msg ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.errorContainer,
            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.4f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("music_prompt_error_banner"),
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onErrorContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      // Primary 'Generate' Action Button
      Button(
        onClick = onGenerateClick,
        enabled = canGenerate,
        colors = ButtonDefaults.buttonColors(
          containerColor = colorScheme.primary,
          contentColor = colorScheme.onPrimary,
          disabledContainerColor = colorScheme.surfaceContainerHighest,
          disabledContentColor = colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("music_prompt_generate_button"),
      ) {
        if (isGenerating) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.music_prompt_generating_button),
            style = MaterialTheme.typography.labelLarge,
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.music_prompt_generate_button),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}
