package fr.plantarrosage.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.plantarrosage.app.BuildConfig
import fr.plantarrosage.core.model.PlantNetProject
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.port.ApiKeyProvider
import fr.plantarrosage.core.port.QuotaTracker
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reglages")

/**
 * Réglages persistants : clés API, préférences d'identification, heure de rappel, compteurs de quota.
 */
class SettingsRepository(context: Context) : ApiKeyProvider, QuotaTracker {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val PLANTNET = stringPreferencesKey("plantnet_api_key")
        val PERENUAL = stringPreferencesKey("perenual_api_key")
        val PROJECT = stringPreferencesKey("plantnet_project")
        val DEFAULT_ORGAN = stringPreferencesKey("default_organ")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val PERENUAL_CALLS = intPreferencesKey("perenual_calls_today")
        val PERENUAL_CALLS_DATE = stringPreferencesKey("perenual_calls_date")
        val PLANTNET_REMAINING = intPreferencesKey("plantnet_remaining")
    }

    companion object {
        const val DEFAULT_REMINDER_HOUR = 9
    }

    // ---------------------------------------------------------------- clés API

    /**
     * Priorité à la clé saisie dans l'application, repli sur celle injectée au build.
     * Cet ordre permet à l'utilisateur de remplacer une clé compilée sans recompiler.
     */
    override suspend fun plantNetKey(): String? =
        storedPlantNetKey.first().ifBlank { BuildConfig.PLANTNET_API_KEY }.takeIf { it.isNotBlank() }

    override suspend fun perenualKey(): String? =
        storedPerenualKey.first().ifBlank { BuildConfig.PERENUAL_API_KEY }.takeIf { it.isNotBlank() }

    val storedPlantNetKey: Flow<String> = store.data.map { it[Keys.PLANTNET].orEmpty() }
    val storedPerenualKey: Flow<String> = store.data.map { it[Keys.PERENUAL].orEmpty() }

    /** Vrai quand une clé provient de `local.properties` et non d'une saisie utilisateur. */
    val plantNetKeyFromBuild: Boolean = BuildConfig.PLANTNET_API_KEY.isNotBlank()
    val perenualKeyFromBuild: Boolean = BuildConfig.PERENUAL_API_KEY.isNotBlank()

    suspend fun setPlantNetKey(key: String) {
        store.edit { it[Keys.PLANTNET] = key.trim() }
    }

    suspend fun setPerenualKey(key: String) {
        store.edit { it[Keys.PERENUAL] = key.trim() }
    }

    // ------------------------------------------------------- identification

    val project: Flow<PlantNetProject> = store.data.map { prefs ->
        prefs[Keys.PROJECT]
            ?.let { name -> runCatching { PlantNetProject.valueOf(name) }.getOrNull() }
            ?: PlantNetProject.ALL
    }

    suspend fun setProject(project: PlantNetProject) {
        store.edit { it[Keys.PROJECT] = project.name }
    }

    val defaultOrgan: Flow<PlantOrgan> = store.data.map { prefs ->
        prefs[Keys.DEFAULT_ORGAN]
            ?.let { name -> runCatching { PlantOrgan.valueOf(name) }.getOrNull() }
            ?: PlantOrgan.AUTO
    }

    suspend fun setDefaultOrgan(organ: PlantOrgan) {
        store.edit { it[Keys.DEFAULT_ORGAN] = organ.name }
    }

    // -------------------------------------------------------------- rappels

    val reminderHour: Flow<Int> = store.data.map { it[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR }

    suspend fun setReminderHour(hour: Int) {
        store.edit { it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun currentReminderHour(): Int = reminderHour.first()

    // --------------------------------------------------------------- quotas

    override suspend fun perenualCallsToday(): Int {
        val prefs = store.data.first()
        val storedDate = prefs[Keys.PERENUAL_CALLS_DATE]
        // Le compteur se remet à zéro au changement de jour, sans tâche planifiée.
        return if (storedDate == today()) prefs[Keys.PERENUAL_CALLS] ?: 0 else 0
    }

    override suspend fun recordPerenualCall() {
        store.edit { prefs ->
            val today = today()
            val current = if (prefs[Keys.PERENUAL_CALLS_DATE] == today) prefs[Keys.PERENUAL_CALLS] ?: 0 else 0
            prefs[Keys.PERENUAL_CALLS] = current + 1
            prefs[Keys.PERENUAL_CALLS_DATE] = today
        }
    }

    override suspend fun plantNetRemaining(): Int? = store.data.first()[Keys.PLANTNET_REMAINING]

    override suspend fun recordPlantNetRemaining(remaining: Int?) {
        if (remaining == null) return
        store.edit { it[Keys.PLANTNET_REMAINING] = remaining }
    }

    val perenualCallsFlow: Flow<Int> = store.data.map { prefs ->
        if (prefs[Keys.PERENUAL_CALLS_DATE] == today()) prefs[Keys.PERENUAL_CALLS] ?: 0 else 0
    }

    val plantNetRemainingFlow: Flow<Int?> = store.data.map { it[Keys.PLANTNET_REMAINING] }

    private fun today(): String = LocalDate.now().toString()
}
