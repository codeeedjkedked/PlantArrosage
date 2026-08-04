package fr.plantarrosage.app.ui.addplant

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.common.LoadingState
import fr.plantarrosage.core.model.PlantLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlantScreen(
    viewModel: AddPlantViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // La permission de notification est demandée ici, au moment exact où elle prend son sens :
    // l'utilisateur vient d'activer les rappels pour une plante qu'il enregistre. La demander
    // au premier lancement n'aurait aucun contexte.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Un refus n'empêche rien : l'accueil reste la source de vérité. */ }

    LaunchedEffect(state.savedPlantId) {
        state.savedPlantId?.let(onSaved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.nickname,
                onValueChange = viewModel::setNickname,
                label = { Text(stringResource(R.string.add_nickname)) },
                placeholder = { Text(stringResource(R.string.add_nickname_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text(stringResource(R.string.add_location), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.location == PlantLocation.INTERIEUR,
                        onClick = { viewModel.setLocation(PlantLocation.INTERIEUR) },
                        label = { Text(stringResource(R.string.add_location_indoor)) },
                    )
                    FilterChip(
                        selected = state.location == PlantLocation.EXTERIEUR,
                        onClick = { viewModel.setLocation(PlantLocation.EXTERIEUR) },
                        label = { Text(stringResource(R.string.add_location_outdoor)) },
                    )
                }
            }

            Column {
                Text(stringResource(R.string.add_interval), style = MaterialTheme.typography.titleMedium)
                state.plan?.let { plan ->
                    // Le raisonnement complet est affiché : c'est ce qui rend la suggestion
                    // discutable, donc corrigeable.
                    Text(
                        plan.explanationFr(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.customIntervalDays?.toString().orEmpty(),
                    onValueChange = { text ->
                        viewModel.setCustomInterval(text.filter { it.isDigit() }.toIntOrNull())
                    },
                    label = { Text(stringResource(R.string.detail_interval_edit_title)) },
                    supportingText = { Text(stringResource(R.string.detail_interval_edit_body)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SwitchRow(
                label = stringResource(R.string.add_already_watered),
                checked = state.wateredNow,
                onCheckedChange = viewModel::setWateredNow,
            )

            SwitchRow(
                label = stringResource(R.string.add_reminders),
                checked = state.remindersEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setRemindersEnabled(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            if (state.remindersEnabled) {
                Text(
                    stringResource(R.string.notification_permission_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving && state.sheet != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_confirm))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
