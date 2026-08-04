package fr.plantarrosage.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.common.InfoBanner
import fr.plantarrosage.app.work.NotificationHelper
import fr.plantarrosage.core.model.PlantNetProject
import fr.plantarrosage.core.model.PlantOrgan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---------- Clés API ----------
            Section(stringResource(R.string.settings_section_keys)) {
                ApiKeyField(
                    label = stringResource(R.string.settings_plantnet_key),
                    value = state.plantNetKey,
                    onValueChange = viewModel::setPlantNetKey,
                    fromBuild = state.plantNetKeyFromBuild && state.plantNetKey.isBlank(),
                )
                LinkButton(
                    label = stringResource(R.string.settings_get_plantnet_key),
                    url = "https://my.plantnet.org/",
                )

                ApiKeyField(
                    label = stringResource(R.string.settings_perenual_key),
                    value = state.perenualKey,
                    onValueChange = viewModel::setPerenualKey,
                    fromBuild = state.perenualKeyFromBuild && state.perenualKey.isBlank(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::testPerenualKey,
                        enabled = state.perenualTest != KeyTestResult.TESTING,
                    ) {
                        Text(stringResource(R.string.settings_key_test))
                    }
                    when (state.perenualTest) {
                        KeyTestResult.VALID -> Text(stringResource(R.string.settings_key_valid))
                        KeyTestResult.INVALID -> Text(stringResource(R.string.settings_key_invalid))
                        else -> Unit
                    }
                }
                LinkButton(
                    label = stringResource(R.string.settings_get_perenual_key),
                    url = "https://perenual.com/docs/api",
                )
            }

            // ---------- Identification ----------
            Section(stringResource(R.string.settings_section_identification)) {
                Text(stringResource(R.string.settings_project), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlantNetProject.entries.forEach { project ->
                        FilterChip(
                            selected = state.project == project,
                            onClick = { viewModel.setProject(project) },
                            label = { Text(project.labelFr) },
                        )
                    }
                }

                Text(
                    stringResource(R.string.settings_default_organ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlantOrgan.entries.forEach { organ ->
                        FilterChip(
                            selected = state.defaultOrgan == organ,
                            onClick = { viewModel.setDefaultOrgan(organ) },
                            label = { Text(organ.labelFr) },
                        )
                    }
                }
            }

            // ---------- Rappels ----------
            Section(stringResource(R.string.settings_section_reminders)) {
                // Le glissement est suivi localement ; l'écriture en préférences et le réalignement
                // des échéances n'ont lieu qu'au relâchement.
                var draggedHour by remember(state.reminderHour) {
                    mutableStateOf(state.reminderHour.toFloat())
                }
                Text(
                    "${stringResource(R.string.settings_reminder_hour)} : ${draggedHour.toInt()} h",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = draggedHour,
                    onValueChange = { draggedHour = it },
                    onValueChangeFinished = { viewModel.setReminderHour(draggedHour.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                )

                if (NotificationHelper.hasPermission(context)) {
                    Text(stringResource(R.string.settings_notifications_enabled))
                } else {
                    InfoBanner(text = stringResource(R.string.settings_notifications_disabled))
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }) {
                        Text(stringResource(R.string.settings_open_system_settings))
                    }
                }

                Text(
                    stringResource(R.string.settings_battery_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- Quotas ----------
            Section(stringResource(R.string.settings_section_quotas)) {
                Text(
                    state.plantNetRemaining?.let {
                        stringResource(R.string.settings_quota_plantnet, it)
                    } ?: stringResource(R.string.settings_quota_plantnet_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.settings_quota_perenual,
                        state.perenualCallsToday,
                        state.perenualDailyLimit,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // ---------- Cache ----------
            Section(stringResource(R.string.settings_section_cache)) {
                Text(
                    stringResource(R.string.settings_cache_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::clearCache) {
                    Text(stringResource(R.string.settings_clear_cache))
                }
                if (state.cacheCleared) {
                    Text(stringResource(R.string.settings_cache_cleared))
                }
            }

            // ---------- À propos ----------
            Section(stringResource(R.string.settings_section_about)) {
                Text(
                    stringResource(R.string.settings_about_apis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    fromBuild: Boolean,
) {
    var visible by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.settings_key_hint)) },
            singleLine = true,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.settings_key_hide else R.string.settings_key_show
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (fromBuild) {
            Text(
                stringResource(R.string.settings_key_from_build),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }) {
        Text(label)
    }
}
