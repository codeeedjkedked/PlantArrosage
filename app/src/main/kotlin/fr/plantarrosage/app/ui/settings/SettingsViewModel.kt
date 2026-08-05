package fr.plantarrosage.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.plantarrosage.app.data.cache.RoomSpeciesCareCache
import fr.plantarrosage.app.data.prefs.SettingsRepository
import fr.plantarrosage.app.data.repo.BackupRepository
import fr.plantarrosage.app.data.repo.ExportSummary
import fr.plantarrosage.app.data.repo.ImportSummary
import fr.plantarrosage.app.data.repo.MyPlantsRepository
import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.model.PlantNetProject
import fr.plantarrosage.core.model.PlantOrgan
import fr.plantarrosage.core.perenual.PerenualClient
import fr.plantarrosage.core.perenual.PerenualLimits
import fr.plantarrosage.core.util.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class KeyTestResult { UNTESTED, TESTING, VALID, INVALID }

data class SettingsUiState(
    val plantNetKey: String = "",
    val perenualKey: String = "",
    val plantNetKeyFromBuild: Boolean = false,
    val perenualKeyFromBuild: Boolean = false,
    val perenualTest: KeyTestResult = KeyTestResult.UNTESTED,
    val project: PlantNetProject = PlantNetProject.ALL,
    val defaultOrgan: PlantOrgan = PlantOrgan.AUTO,
    val reminderHour: Int = SettingsRepository.DEFAULT_REMINDER_HOUR,
    val plantNetRemaining: Int? = null,
    val perenualCallsToday: Int = 0,
    val perenualDailyLimit: Int = PerenualLimits.FREE_TIER_DAILY_REQUESTS,
    val cacheCleared: Boolean = false,
    val backupBusy: Boolean = false,
    val backupResult: BackupResult? = null,
    val includeKeysInBackup: Boolean = true,
    val restoreSettingsOnImport: Boolean = true,
)

/**
 * Issue de la dernière sauvegarde ou restauration.
 *
 * Le modèle ne compose pas la phrase affichée : les chiffres restent bruts et c'est l'écran qui
 * les met en français, comme partout ailleurs dans l'application.
 */
sealed interface BackupResult {
    data class Exported(val summary: ExportSummary) : BackupResult
    data class Imported(val summary: ImportSummary) : BackupResult
    data class Failed(val error: AppError) : BackupResult
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val perenualClient: PerenualClient,
    private val cache: RoomSpeciesCareCache,
    private val plantsRepository: MyPlantsRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            plantNetKeyFromBuild = settings.plantNetKeyFromBuild,
            perenualKeyFromBuild = settings.perenualKeyFromBuild,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.storedPlantNetKey,
                settings.storedPerenualKey,
                settings.project,
                settings.defaultOrgan,
                settings.reminderHour,
            ) { plantNet, perenual, project, organ, hour ->
                Quintuple(plantNet, perenual, project, organ, hour)
            }.collect { (plantNet, perenual, project, organ, hour) ->
                _state.update {
                    it.copy(
                        plantNetKey = plantNet,
                        perenualKey = perenual,
                        project = project,
                        defaultOrgan = organ,
                        reminderHour = hour,
                    )
                }
            }
        }

        viewModelScope.launch {
            settings.plantNetRemainingFlow.collect { remaining ->
                _state.update { it.copy(plantNetRemaining = remaining) }
            }
        }

        viewModelScope.launch {
            settings.perenualCallsFlow.collect { calls ->
                _state.update { it.copy(perenualCallsToday = calls) }
            }
        }
    }

    fun setPlantNetKey(key: String) {
        viewModelScope.launch { settings.setPlantNetKey(key) }
    }

    fun setPerenualKey(key: String) {
        _state.update { it.copy(perenualTest = KeyTestResult.UNTESTED) }
        viewModelScope.launch { settings.setPerenualKey(key) }
    }

    /**
     * Un seul appel bon marché suffit à valider la clé Perenual. Pl@ntNet n'expose pas
     * d'endpoint de test gratuit : sa validation est différée au premier usage réel.
     */
    fun testPerenualKey() {
        _state.update { it.copy(perenualTest = KeyTestResult.TESTING) }
        viewModelScope.launch {
            val outcome = perenualClient.searchSpecies("rosa")
            _state.update {
                it.copy(
                    perenualTest = when (outcome) {
                        is Outcome.Success -> KeyTestResult.VALID
                        is Outcome.Failure -> KeyTestResult.INVALID
                    }
                )
            }
        }
    }

    fun setProject(project: PlantNetProject) {
        viewModelScope.launch { settings.setProject(project) }
    }

    fun setDefaultOrgan(organ: PlantOrgan) {
        viewModelScope.launch { settings.setDefaultOrgan(organ) }
    }

    /**
     * Change l'heure des rappels puis réaligne les échéances existantes : sans cela, les plantes
     * déjà enregistrées continueraient d'être notifiées à l'ancienne heure.
     */
    fun setReminderHour(hour: Int) {
        viewModelScope.launch {
            settings.setReminderHour(hour)
            plantsRepository.refreshAllNextDue()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cache.clear()
            _state.update { it.copy(cacheCleared = true) }
        }
    }

    fun dismissCacheMessage() {
        _state.update { it.copy(cacheCleared = false) }
    }

    // ------------------------------------------------------ sauvegarde

    fun suggestedBackupFileName(): String = backupRepository.suggestedFileName()

    fun setIncludeKeysInBackup(include: Boolean) {
        _state.update { it.copy(includeKeysInBackup = include) }
    }

    fun setRestoreSettingsOnImport(restore: Boolean) {
        _state.update { it.copy(restoreSettingsOnImport = restore) }
    }

    fun export(target: Uri) {
        _state.update { it.copy(backupBusy = true, backupResult = null) }
        viewModelScope.launch {
            val outcome = backupRepository.export(target, _state.value.includeKeysInBackup)
            _state.update {
                it.copy(
                    backupBusy = false,
                    backupResult = when (outcome) {
                        is Outcome.Success -> BackupResult.Exported(outcome.value)
                        is Outcome.Failure -> BackupResult.Failed(outcome.error)
                    },
                )
            }
        }
    }

    fun import(source: Uri) {
        _state.update { it.copy(backupBusy = true, backupResult = null) }
        viewModelScope.launch {
            val outcome = backupRepository.import(source, _state.value.restoreSettingsOnImport)
            _state.update {
                it.copy(
                    backupBusy = false,
                    backupResult = when (outcome) {
                        is Outcome.Success -> BackupResult.Imported(outcome.value)
                        is Outcome.Failure -> BackupResult.Failed(outcome.error)
                    },
                )
            }
        }
    }

    fun dismissBackupResult() {
        _state.update { it.copy(backupResult = null) }
    }

    private data class Quintuple(
        val a: String,
        val b: String,
        val c: PlantNetProject,
        val d: PlantOrgan,
        val e: Int,
    )
}
