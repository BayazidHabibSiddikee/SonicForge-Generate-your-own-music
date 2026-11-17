package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.LoudnessTarget
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "sonicforge_prefs")

class ThemePreferencesRepository(private val context: Context) {

  private val themeModeKey = stringPreferencesKey("theme_mode")
  private val showBottomWaveformKey = booleanPreferencesKey("show_bottom_waveform")
  private val autoPostGainKey = booleanPreferencesKey("auto_post_gain_enabled")
  private val loudnessTargetKey = stringPreferencesKey("loudness_target")

  val themeModeFlow: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
    val raw = prefs[themeModeKey]
    ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
  }

  val showBottomWaveformFlow: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
    prefs[showBottomWaveformKey] ?: true
  }

  val autoPostGainEnabledFlow: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
    prefs[autoPostGainKey] ?: true
  }

  val loudnessTargetFlow: Flow<LoudnessTarget> = context.themeDataStore.data.map { prefs ->
    val raw = prefs[loudnessTargetKey]
    LoudnessTarget.entries.firstOrNull { it.name == raw } ?: LoudnessTarget.STREAMING_14
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.themeDataStore.edit { prefs ->
      prefs[themeModeKey] = mode.name
    }
  }

  suspend fun setShowBottomWaveform(visible: Boolean) {
    context.themeDataStore.edit { prefs ->
      prefs[showBottomWaveformKey] = visible
    }
  }

  suspend fun setAutoPostGainEnabled(enabled: Boolean) {
    context.themeDataStore.edit { prefs ->
      prefs[autoPostGainKey] = enabled
    }
  }

  suspend fun setLoudnessTarget(target: LoudnessTarget) {
    context.themeDataStore.edit { prefs ->
      prefs[loudnessTargetKey] = target.name
    }
  }
}
