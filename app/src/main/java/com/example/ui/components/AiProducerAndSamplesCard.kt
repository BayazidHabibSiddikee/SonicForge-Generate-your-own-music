package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiProducerEngine
import com.example.ai.MusicArrangementPlan
import com.example.audio.OfflineMusicAsset
import com.example.audio.OfflineSampleCategory
import com.example.ui.AiProducerState
import com.example.ui.OfflineMusicStudioState
import java.util.Locale

private val aiPromptPresets = listOf(
  "Lo-Fi Vinyl Chill" to "Warm rainy midnight lo-fi chillhop with Rhodes chords, soft fingerpicked guitar, and dusty boom-bap drums",
  "Acoustic Folk" to "Uplifting golden hour indie acoustic folk with steel guitar strums, bright upright piano, and crisp live drums",
  "Midnight Synthwave" to "Nostalgic 80s neon synthwave with warm analog pads, driving rock bass guitar, and punchy electronic drums",
  "Neo-Soul Groove" to "Smooth neo-soul R&B pocket at 92 BPM with mellow Rhodes chords, funk guitar chops, and tight crisp snare",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiProducerAndSamplesCard(
  aiState: AiProducerState,
  offlineMusicState: OfflineMusicStudioState,
  onSelectAiEngine: (AiProducerEngine) -> Unit,
  onAiPromptChanged: (String) -> Unit,
  onArrangeTrack: () -> Unit,
  onRenderAiFullMix: () -> Unit,
  onApplyPlanToInstruments: () -> Unit,
  onDetachSampleFromAiMix: () -> Unit,
  onOfflineQueryChanged: (String) -> Unit,
  onSelectOfflineCategory: (OfflineSampleCategory) -> Unit,
  onPreviewOfflineAsset: (OfflineMusicAsset) -> Unit,
  onImportOfflineAsset: (OfflineMusicAsset) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  var selectedMode by remember { mutableIntStateOf(0) } // 0 = AI Producer, 1 = Offline Music Library

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Clean 2-Segment Switcher: AI Producer | Offline Music Library
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("ai_offline_mode_switcher"),
    ) {
      SegmentedButton(
        selected = selectedMode == 0,
        onClick = { selectedMode = 0 },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        icon = {},
        label = {
          Text(
            text = stringResource(R.string.ai_segment_producer),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        },
        modifier = Modifier.testTag("ai_segment_producer"),
      )
      SegmentedButton(
        selected = selectedMode == 1,
        onClick = { selectedMode = 1 },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        icon = {},
        label = {
          Text(
            text = stringResource(R.string.ai_segment_offline_library, offlineMusicState.items.size),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        },
        modifier = Modifier.testTag("ai_segment_offline_library"),
      )
    }

    if (selectedMode == 0) {
    // 1. Dual-Engine AI & Offline Music Producer Card
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.28f)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("ai_producer_card"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .drawBehind {
            drawRect(
              brush = Brush.linearGradient(
                colors = listOf(
                  colorScheme.primary.copy(alpha = 0.11f),
                  colorScheme.tertiary.copy(alpha = 0.06f),
                  colorScheme.surfaceContainer.copy(alpha = 0f),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height * 0.45f),
              ),
            )
          }
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp),
          ) {
            Icon(
              imageVector = if (aiState.selectedEngine == AiProducerEngine.OFFLINE_MODEL) {
                Icons.Rounded.Memory
              } else {
                Icons.Rounded.AutoAwesome
              },
              contentDescription = null,
              tint = colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(8.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.ai_producer_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.ai_producer_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }

        // Engine Selector: Offline On-Device Model vs Gemini Cloud (no API keys needed)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AiProducerEngine.entries.forEach { engine ->
            val selected = aiState.selectedEngine == engine
            FilterChip(
              selected = selected,
              onClick = { onSelectAiEngine(engine) },
              leadingIcon = {
                Icon(
                  imageVector = if (engine == AiProducerEngine.OFFLINE_MODEL) {
                    Icons.Rounded.Memory
                  } else {
                    Icons.Rounded.Cloud
                  },
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = {
                Text(
                  text = engine.label,
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                )
              },
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("ai_engine_${engine.id}"),
            )
          }
        }

        // Quick Style Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          aiPromptPresets.forEachIndexed { idx, (label, promptText) ->
            AssistChip(
              onClick = { onAiPromptChanged(promptText) },
              label = {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              modifier = Modifier.testTag("ai_producer_preset_$idx"),
            )
          }
        }

        // Prompt Input
        OutlinedTextField(
          value = aiState.promptInput,
          onValueChange = onAiPromptChanged,
          placeholder = {
            Text(
              text = stringResource(R.string.ai_producer_prompt_placeholder),
              style = MaterialTheme.typography.bodySmall,
            )
          },
          maxLines = 3,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outlineVariant,
            focusedContainerColor = colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = colorScheme.surfaceContainerLowest,
          ),
          trailingIcon = if (aiState.promptInput.isNotEmpty()) {
            {
              IconButton(
                onClick = { onAiPromptChanged("") },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Clear AI producer prompt",
                )
              }
            }
          } else {
            null
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_producer_prompt_input"),
        )

        // Attached Offline Music/Texture Layer Banner
        aiState.attachedSampleTitle?.let { attachedTitle ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.tertiaryContainer.copy(alpha = 0.72f),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ai_attached_sample_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Rounded.LibraryMusic,
                  contentDescription = null,
                  tint = colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = stringResource(R.string.ai_producer_attached_sample, attachedTitle),
                  style = MaterialTheme.typography.labelMedium,
                  color = colorScheme.onTertiaryContainer,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              OutlinedButton(
                onClick = onDetachSampleFromAiMix,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("ai_detach_sample_button"),
              ) {
                Text(
                  text = stringResource(R.string.ai_producer_detach_sample),
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }
        }

        // Primary Arrange CTA
        Button(
          onClick = onArrangeTrack,
          enabled = aiState.promptInput.isNotBlank() && !aiState.isArranging && !aiState.isRenderingMix,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("ai_arrange_button"),
        ) {
          if (aiState.isArranging) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.ai_producer_arranging))
          } else {
            Icon(
              imageVector = if (aiState.selectedEngine == AiProducerEngine.OFFLINE_MODEL) {
                Icons.Rounded.Memory
              } else {
                Icons.Rounded.AutoAwesome
              },
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (aiState.selectedEngine == AiProducerEngine.OFFLINE_MODEL) {
                stringResource(R.string.ai_producer_arrange_offline_button)
              } else {
                stringResource(R.string.ai_producer_arrange_gemini_button)
              },
            )
          }
        }

        // Error Banner (surfaces authentic cloud errors transparently)
        aiState.errorMessage?.let { err ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.errorContainer,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ai_producer_error"),
          ) {
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        aiState.statusMessage?.let { status ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.secondaryContainer,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ai_producer_status"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        // Generated Arrangement Blueprint Card
        AnimatedVisibility(visible = aiState.currentPlan != null) {
          aiState.currentPlan?.let { plan ->
            ArrangementBlueprintPanel(
              plan = plan,
              isRenderingMix = aiState.isRenderingMix,
              hasAttachedSample = aiState.attachedSampleTrackId != null,
              onRenderAiFullMix = onRenderAiFullMix,
              onApplyPlanToInstruments = onApplyPlanToInstruments,
              onFilterOfflineLibrary = { query ->
                onSelectOfflineCategory(OfflineSampleCategory.ALL)
                onOfflineQueryChanged(query)
                selectedMode = 1
              },
            )
          }
        }
      }
    }
    } else {
    // 2. Built-In Offline Music & Studio Samples Library Card
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("offline_music_library_card"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = colorScheme.tertiaryContainer,
              modifier = Modifier.size(38.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(8.dp),
              )
            }
            Column {
              Text(
                text = stringResource(R.string.offline_library_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.offline_library_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = colorScheme.secondaryContainer,
            modifier = Modifier.testTag("offline_library_badge"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = stringResource(R.string.offline_library_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        // Category Filter Chips (All / Full Music / Textures / Stems)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OfflineSampleCategory.entries.forEach { cat ->
            FilterChip(
              selected = offlineMusicState.selectedCategory == cat,
              onClick = { onSelectOfflineCategory(cat) },
              label = {
                Text(
                  text = cat.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              modifier = Modifier.testTag("offline_category_${cat.id}"),
            )
          }
        }

        // Instant Local Search Input
        OutlinedTextField(
          value = offlineMusicState.searchQuery,
          onValueChange = onOfflineQueryChanged,
          singleLine = true,
          placeholder = {
            Text(
              text = stringResource(R.string.offline_library_search_placeholder),
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
          trailingIcon = if (offlineMusicState.searchQuery.isNotEmpty()) {
            {
              IconButton(
                onClick = { onOfflineQueryChanged("") },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Clear offline music filter",
                )
              }
            }
          } else {
            null
          },
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.tertiary,
            unfocusedBorderColor = colorScheme.outlineVariant,
            focusedContainerColor = colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = colorScheme.surfaceContainerLowest,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_library_search_input"),
        )

        offlineMusicState.errorMessage?.let { err ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.errorContainer,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("offline_library_error_banner"),
          ) {
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        offlineMusicState.statusMessage?.let { msg ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.secondaryContainer,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("offline_library_status_banner"),
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.labelMedium,
              color = colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }

        // Offline Music & Sample Items
        if (offlineMusicState.items.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = stringResource(R.string.offline_library_empty_results),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            offlineMusicState.items.forEach { asset ->
              OfflineMusicAssetRow(
                asset = asset,
                isPreviewing = offlineMusicState.previewingAssetId == asset.id,
                isImporting = offlineMusicState.importingAssetId == asset.id,
                isImported = asset.id in offlineMusicState.importedAssetIds,
                onPreview = { onPreviewOfflineAsset(asset) },
                onImport = { onImportOfflineAsset(asset) },
              )
            }
          }
        }
      }
    }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrangementBlueprintPanel(
  plan: MusicArrangementPlan,
  isRenderingMix: Boolean,
  hasAttachedSample: Boolean,
  onRenderAiFullMix: () -> Unit,
  onApplyPlanToInstruments: () -> Unit,
  onFilterOfflineLibrary: (String) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("ai_arrangement_plan_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = plan.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = colorScheme.primaryContainer,
        ) {
          Text(
            text = "${plan.bpm} BPM · ${plan.musicalKey}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      Text(
        text = plan.summary,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        BlueprintBadge(
          icon = Icons.Rounded.Piano,
          text = "Piano: ${plan.pianoVoice.label} (${plan.pianoProgression.label})",
        )
        BlueprintBadge(
          icon = Icons.Rounded.MusicNote,
          text = "Guitar: ${plan.guitarVoice.label} · ${plan.guitarChords.joinToString("-") { it.label }}",
        )
        BlueprintBadge(
          icon = Icons.Rounded.GraphicEq,
          text = "Drums: ${plan.drumKit.label} · ${plan.drumPreset.label}",
        )
        BlueprintBadge(
          icon = Icons.Rounded.Tune,
          text = "Reverb: ${plan.reverbPreset.label}",
        )
      }

      HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Suggested Offline Library Layers
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.ai_producer_suggested_samples),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          plan.suggestedSampleQueries.forEachIndexed { idx, query ->
            AssistChip(
              onClick = { onFilterOfflineLibrary(query) },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Rounded.Search,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = query,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              modifier = Modifier.testTag("ai_sample_query_$idx"),
            )
          }
        }
      }

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onRenderAiFullMix,
          enabled = !isRenderingMix,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
          ),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("ai_render_full_mix_button"),
        ) {
          if (isRenderingMix) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.ai_producer_rendering_mix),
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
            )
          } else {
            Text(
              text = if (hasAttachedSample) {
                "Render Mix + Layer"
              } else {
                stringResource(R.string.ai_producer_render_mix_button)
              },
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        OutlinedButton(
          onClick = onApplyPlanToInstruments,
          enabled = !isRenderingMix,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("ai_apply_to_instruments_button"),
        ) {
          Text(
            text = stringResource(R.string.ai_producer_apply_instruments),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun BlueprintBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = colorScheme.surfaceContainerHigh,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colorScheme.primary,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun OfflineMusicAssetRow(
  asset: OfflineMusicAsset,
  isPreviewing: Boolean,
  isImporting: Boolean,
  isImported: Boolean,
  onPreview: () -> Unit,
  onImport: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = colorScheme.surfaceContainerLowest,
    border = BorderStroke(
      width = 1.dp,
      color = if (isImported) colorScheme.tertiary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.5f),
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("offline_asset_${asset.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = asset.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = asset.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val durText = String.format(Locale.US, "%.1fs", asset.durationSec)
          Text(
            text = "${asset.genre} · ${asset.bpm} BPM · ${asset.musicalKey} · $durText",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text = asset.category.label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onPreview,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("offline_preview_${asset.id}"),
        ) {
          Icon(
            imageVector = if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isPreviewing) {
              stringResource(R.string.offline_library_stop_preview_button)
            } else {
              stringResource(R.string.offline_library_preview_button)
            },
            style = MaterialTheme.typography.labelSmall,
          )
        }

        FilledTonalButton(
          onClick = onImport,
          enabled = !isImporting,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("offline_import_${asset.id}"),
        ) {
          if (isImporting) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              strokeWidth = 2.dp,
            )
          } else {
            Icon(
              imageVector = if (isImported) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isImported) {
                stringResource(R.string.offline_library_imported_badge)
              } else {
                stringResource(R.string.offline_library_import_button)
              },
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }
    }
  }
}
