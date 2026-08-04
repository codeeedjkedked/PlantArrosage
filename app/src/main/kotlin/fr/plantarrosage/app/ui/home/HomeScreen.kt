package fr.plantarrosage.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.data.repo.PlantWithSchedule
import fr.plantarrosage.app.ui.common.EmptyState
import fr.plantarrosage.core.care.NextWateringCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onIdentify: () -> Unit,
    onOpenPlant: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Les libellés sont résolus ici : le contenu d'un LazyColumn est une lambda LazyListScope,
    // pas un contexte @Composable, et stringResource n'y est pas appelable.
    val titleToday = stringResource(R.string.home_section_today)
    val titleSoon = stringResource(R.string.home_section_soon)
    val titleAll = stringResource(R.string.home_section_all)
    val waterLabel = stringResource(R.string.home_mark_watered)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onIdentify,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_fab_identify)) },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                actionLabel = stringResource(R.string.home_empty_cta),
                onAction = onIdentify,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Les plantes en retard ou dues aujourd'hui viennent en tête : c'est la seule
            // information qui justifie d'ouvrir l'application un matin donné.
            plantSection(titleToday, state.dueToday, waterLabel, onOpenPlant, viewModel::recordWatering)
            plantSection(titleSoon, state.soon, waterLabel, onOpenPlant, viewModel::recordWatering)
            plantSection(titleAll, state.later, waterLabel, onOpenPlant, viewModel::recordWatering)
        }
    }
}

private fun LazyListScope.plantSection(
    title: String,
    plants: List<PlantWithSchedule>,
    waterLabel: String,
    onOpenPlant: (Long) -> Unit,
    onWater: (Long) -> Unit,
) {
    if (plants.isEmpty()) return

    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
    }
    items(plants, key = { it.entity.id }) { plant ->
        PlantCard(
            plant = plant,
            waterLabel = waterLabel,
            onOpen = { onOpenPlant(plant.entity.id) },
            onWater = { onWater(plant.entity.id) },
        )
    }
}

@Composable
private fun PlantCard(
    plant: PlantWithSchedule,
    waterLabel: String,
    onOpen: () -> Unit,
    onWater: () -> Unit,
) {
    val colors = if (plant.isOverdue) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = plant.entity.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(plant.entity.nickname, style = MaterialTheme.typography.titleMedium)
                Text(
                    plant.entity.commonNameFr ?: plant.entity.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    NextWateringCalculator.humanReadableFr(plant.daysUntilDue),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            TextButton(onClick = onWater) {
                Icon(Icons.Default.WaterDrop, contentDescription = null)
                Text(waterLabel, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
