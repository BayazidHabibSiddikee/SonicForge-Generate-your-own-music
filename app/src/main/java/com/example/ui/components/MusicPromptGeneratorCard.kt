package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
  "Lo-Fi Chill" to "Lo-fi chill beat with warm Rhodes piano 84 BPM",
  "Synthwave" to "Neon synthwave night drive arpeggio 112 BPM",
  "Ambient" to "Ambient cinematic atmospheric pads 72 BPM",
  "Funky House" to "Upbeat funky house bassline and groove 122 BPM",
)

/**
 * Clean, distraction-free music prompt generator card with progressive disclosure for engine selection,
 * concise quick-idea chips, and a primary 'Generate' button.
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
  var showEngineSelector by remember { mutableStateOf(false) }

  val activeEngineLabelRes = when (selectedEngine) {
    GenerationEngine.HF_MUSICGEN -> R.string.engine_hf_musicgen
    GenerationEngine.HF_AUDIO_LDM -> R.string.engine_hf_audioldm
    GenerationEngine.HF_STABLE_AUDIO -> R.string.engine_hf_stable_audio
    GenerationEngine.FOURIER_SYNTH -> R.string.engine_fourier_dsp
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("music_prompt_generator_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    tonalElevation = 2.dp,
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
                colorScheme.primary.copy(alpha = if (isGenerating) 0.14f else 0.06f),
                Color.Transparent,
              ),
              center = Offset(size.width * 0.9f, size.height * 0.1f),
              radius = size.maxDimension * 0.7f,
            )
          )
        }
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Clean Single-Line Header + Collapsible Engine Pill
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
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.music_prompt_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = if (isApiEngine) colorScheme.secondaryContainer else colorScheme.surfaceContainerHighest,
          modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = !isGenerating) { showEngineSelector = !showEngineSelector }
            .testTag("music_prompt_mode_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = if (isApiEngine) Icons.Rounded.Cloud else Icons.Rounded.Memory,
              contentDescription = null,
              tint = if (isApiEngine) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = stringResource(activeEngineLabelRes),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = if (isApiEngine) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
            Icon(
              imageVector = if (showEngineSelector) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription = null,
              tint = if (isApiEngine) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      // Progressive Disclosure: Engine Selector Row (hidden until user taps engine pill)
      AnimatedVisibility(visible = showEngineSelector) {
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
            FilterChip(
              selected = selectedEngine == engine,
              enabled = !isGenerating,
              onClick = {
                onSelectEngine(engine)
                showEngineSelector = false
              },
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
      }

      // Text Prompt Input Field
      OutlinedTextField(
        value = promptInput,
        onValueChange = onPromptChanged,
        placeholder = { Text(stringResource(R.string.music_prompt_field_placeholder)) },
        minLines = 2,
        maxLines = 3,
        enabled = !isGenerating,
        shape = RoundedCornerShape(14.dp),
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
          unfocusedBorderColor = colorScheme.outlineVariant,
          focusedContainerColor = colorScheme.surfaceContainerLowest,
          unfocusedContainerColor = colorScheme.surfaceContainerLowest,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("music_prompt_input_field"),
      )

      // Concise Quick Idea Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        quickPromptPresets.forEachIndexed { index, (shortLabel, fullPrompt) ->
          AssistChip(
            onClick = { onApplyPreset(fullPrompt) },
            enabled = !isGenerating,
            label = {
              Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
              )
            },
            colors = AssistChipDefaults.assistChipColors(
              containerColor = colorScheme.surfaceContainerLow,
              labelColor = colorScheme.onSurfaceVariant,
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
        shape = RoundedCornerShape(14.dp),
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
