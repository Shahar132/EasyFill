package com.example.easyfill_project.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.FontSizeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Creates one DataStore instance for the whole application.
 */
private val Context.userSettingsDataStore:
        DataStore<Preferences> by preferencesDataStore(
    name = "user_settings"
)

/**
 * Saves only settings that the user manually changes
 * through the Settings screens.
 */
class UserSettingsRepository(
    context: Context
) {

    private val dataStore =
        context.applicationContext.userSettingsDataStore

    private object Keys {
        val contrastMode =
            stringPreferencesKey("contrast_mode")

        val fontSizeMode =
            stringPreferencesKey("font_size_mode")

        val selectedSound =
            stringPreferencesKey("selected_sound")
    }

    /**
     * Emits the saved settings whenever they change.
     */
    val userSettings: Flow<UserSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(
                        androidx.datastore.preferences.core
                            .emptyPreferences()
                    )
                } else {
                    throw exception
                }
            }
            .map { preferences ->

                val contrastMode =
                    preferences[Keys.contrastMode]
                        .toEnumOrDefault(
                            defaultValue =
                                ContrastMode.DEFAULT
                        )

                val fontSizeMode =
                    preferences[Keys.fontSizeMode]
                        .toEnumOrDefault(
                            defaultValue =
                                FontSizeMode.NORMAL
                        )

                val selectedSound =
                    preferences[Keys.selectedSound]
                        ?: "none"

                UserSettings(
                    contrastMode = contrastMode,
                    fontSizeMode = fontSizeMode,
                    selectedSound = selectedSound
                )
            }

    suspend fun saveContrastMode(
        mode: ContrastMode
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.contrastMode] =
                mode.name
        }
    }

    suspend fun saveFontSizeMode(
        mode: FontSizeMode
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.fontSizeMode] =
                mode.name
        }
    }

    suspend fun saveSelectedSound(
        soundName: String
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.selectedSound] =
                soundName
        }
    }
}

/**
 * Converts a saved enum name safely.
 *
 * If an old or invalid value exists, the default is returned
 * instead of crashing the application.
 */
private inline fun <reified T : Enum<T>>
        String?.toEnumOrDefault(
    defaultValue: T
): T {
    return this
        ?.let { savedName ->
            enumValues<T>().firstOrNull { enumValue ->
                enumValue.name == savedName
            }
        }
        ?: defaultValue
}