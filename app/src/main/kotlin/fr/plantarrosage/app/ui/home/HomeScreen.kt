package fr.plantarrosage.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.data.repo.PlantWithSchedule
import fr.plantarrosage.app.ui.common.EmptyState
import fr.plantarrosage.app.ui.theme.PlantTheme
import fr.plantarrosage.core.care.NextWateringCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onIdentify: () -> Unit,
    onSearchSpecies: () -> Unit,
    onAddManually: () -> Unit,
    onOpenPlant: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }

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
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_fab_add)) },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                actionLabel = stringResource(R.string.home_empty_cta),
                onAction = { showAddSheet = true },
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
            plantSection(
                titleToday, Icons.Default.WaterDrop, state.dueToday,
                waterLabel, onOpenPlant, viewModel::recordWatering,
            )
            plantSection(
                titleSoon, Icons.Default.Schedule, state.soon,
                waterLabel, onOpenPlant, viewModel::recordWatering,
            )
            plantSection(
                titleAll, Icons.Default.LocalFlorist, state.later,
                waterLabel, onOpenPlant, viewModel::recordWatering,
            )
        }
    }

    if (showAddSheet) {
        AddPlantSheet(
            onDismiss = { showAddSheet = false },
            onIdentify = onIdentify,
            onSearchSpecies = onSearchSpecies,
            onAddManually = onAddManually,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlantSheet(
    onDismiss: () -> Unit,
    onIdentify: () -> Unit,
    onSearchSpecies: () -> Unit,
    onAddManually: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.home_add_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            AddOption(
                icon = Icons.Default.PhotoCamera,
                title = stringResource(R.string.home_add_by_photo),
                subtitle = stringResource(R.string.home_add_by_photo_help),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { onDismiss(); onIdentify() },
            )
            AddOption(
                icon = Icons.Default.Search,
                title = stringResource(R.string.home_add_by_search),
                subtitle = stringResource(R.string.home_add_by_search_help),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { onDismiss(); onSearchSpecies() },
            )
            AddOption(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.home_add_manually),
                subtitle = stringResource(R.string.home_add_manually_help),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onDismiss(); onAddManually() },
            )
        }
    }
}

@Composable
private fun AddOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.plantSection(
    title: String,
    icon: ImageVector,
    plants: List<PlantWithSchedule>,
    waterLabel: String,
    onOpenPlant: (Long) -> Unit,
    onWater: (Long) -> Unit,
) {
    if (plants.isEmpty()) return

    item(key = "header-$title") {
        Row(
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
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
    // Un liseré coloré sur la tranche gauche plutôt qu'un fond entier : la couleur reste lisible
    // au premier coup d'œil sans écraser le texte ni la photo.
    val watering = PlantTheme.watering
    val (edge, badge, onBadge) = when {
        plant.isOverdue -> Triple(watering.onOverdue, watering.overdue, watering.onOverdue)
        plant.isDueToday -> Triple(watering.onDueToday, watering.dueToday, watering.onDueToday)
        else -> Triple(Color.Transparent, watering.upcoming, watering.onUpcoming)
    }

    Card(
        colors = CardDefaults.cardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(edge),
            )

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlantThumbnail(plant.entity.photoUri, plant.entity.nickname)

                Column(modifier = Modifier.weight(1f)) {
                    Text(plant.entity.nickname, style = MaterialTheme.typography.titleMedium)
                    Text(
                        plant.entity.commonNameFr ?: plant.entity.scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(badge, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = onBadge,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            NextWateringCalculator.humanReadableFr(plant.daysUntilDue),
                            style = MaterialTheme.typography.labelLarge,
                            color = onBadge,
                        )
                    }
                }

                TextButton(onClick = onWater) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null)
                    Text(waterLabel, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/**
 * Vignette de la plante, avec un substitut dessiné quand aucune photo n'existe.
 *
 * Sans lui, une plante ajoutée à la main laissait un carré vide : la liste paraissait cassée
 * alors que tout allait bien.
 */
@Composable
private fun PlantThumbnail(photoUri: String?, nickname: String) {
    val shape = RoundedCornerShape(10.dp)

    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = nickname,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(shape),
        )
        return
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(PlantTheme.watering.leafTint, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.LocalFlorist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}
