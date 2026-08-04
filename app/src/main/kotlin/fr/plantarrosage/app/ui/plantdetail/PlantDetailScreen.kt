package fr.plantarrosage.app.ui.plantdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.common.CareRow
import fr.plantarrosage.app.ui.common.LoadingState
import fr.plantarrosage.core.care.FrenchLabels
import fr.plantarrosage.core.care.NextWateringCalculator
import fr.plantarrosage.core.model.PlantLocation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    viewModel: PlantDetailViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.plant?.entity?.nickname ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val plant = state.plant
        if (state.loading || plant == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        val entity = plant.entity
        val zone = ZoneId.systemDefault()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            entity.photoUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            Column {
                Text(
                    stringResource(R.string.detail_identified_as, entity.scientificName),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Bloc d'arrosage : l'information qui justifie d'ouvrir cet écran.
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.detail_next_due),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        NextWateringCalculator.humanReadableFr(plant.daysUntilDue),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        entity.lastWateredAt?.let {
                            stringResource(
                                R.string.detail_last_watered,
                                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(dateFormat),
                            )
                        } ?: stringResource(R.string.detail_never_watered),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(onClick = viewModel::water, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null)
                        Text(
                            stringResource(R.string.detail_water_now),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            // Explication du rythme : d'où sort le chiffre, et comment le corriger.
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.detail_interval_edit_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(plant.plan.explanationFr(), style = MaterialTheme.typography.bodyMedium)

                    OutlinedTextField(
                        value = entity.customIntervalDays?.toString().orEmpty(),
                        onValueChange = { text ->
                            viewModel.setCustomInterval(text.filter { it.isDigit() }.toIntOrNull())
                        },
                        label = { Text(stringResource(R.string.action_modify)) },
                        supportingText = { Text(stringResource(R.string.detail_interval_edit_body)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = viewModel.locationOf(entity) == PlantLocation.INTERIEUR,
                            onClick = { viewModel.setLocation(PlantLocation.INTERIEUR) },
                            label = { Text(stringResource(R.string.add_location_indoor)) },
                        )
                        FilterChip(
                            selected = viewModel.locationOf(entity) == PlantLocation.EXTERIEUR,
                            onClick = { viewModel.setLocation(PlantLocation.EXTERIEUR) },
                            label = { Text(stringResource(R.string.add_location_outdoor)) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.detail_reminders_on))
                        Switch(
                            checked = entity.remindersEnabled,
                            onCheckedChange = viewModel::setRemindersEnabled,
                        )
                    }
                }
            }

            // Fiche d'entretien telle qu'elle était à l'enregistrement. Elle vient de l'instantané
            // stocké avec la plante : consultable hors ligne, indépendante du cache et des quotas.
            state.careSheet?.let { sheet ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.detail_care_summary),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        sheet.wateringFr?.let {
                            CareRow(stringResource(R.string.species_section_watering), it)
                        }
                        if (sheet.sunlightFr.isNotEmpty()) {
                            CareRow(
                                stringResource(R.string.species_section_light),
                                sheet.sunlightFr.joinToString(", "),
                            )
                        }
                        sheet.cycleFr?.let { CareRow(stringResource(R.string.species_cycle), it) }
                        sheet.careLevelFr?.let {
                            CareRow(stringResource(R.string.species_care_level), it)
                        }
                        sheet.hardinessFr?.let {
                            CareRow(stringResource(R.string.species_hardiness), it)
                        }
                        FrenchLabels.toxicity(sheet.poisonousToHumans, sheet.poisonousToPets)?.let {
                            CareRow(stringResource(R.string.species_section_toxicity), it)
                        }
                        if (sheet.isFallback) {
                            Text(
                                stringResource(R.string.banner_no_care_data),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.detail_history),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.history.isEmpty()) {
                        Text(
                            stringResource(R.string.detail_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.history.forEachIndexed { index, event ->
                            if (index > 0) HorizontalDivider()
                            Text(
                                Instant.ofEpochMilli(event.wateredAt)
                                    .atZone(zone).toLocalDate().format(dateFormat),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
