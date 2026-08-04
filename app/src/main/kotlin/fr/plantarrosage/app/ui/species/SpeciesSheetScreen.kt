package fr.plantarrosage.app.ui.species

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.common.BannerTone
import fr.plantarrosage.app.ui.common.CareRow
import fr.plantarrosage.app.ui.common.EmptyState
import fr.plantarrosage.app.ui.common.InfoBanner
import fr.plantarrosage.app.ui.common.LoadingState
import fr.plantarrosage.core.care.FrenchLabels
import fr.plantarrosage.core.care.WateringIntervalCalculator
import fr.plantarrosage.core.model.CareSheet
import fr.plantarrosage.core.model.DetailLevel
import fr.plantarrosage.core.model.SourceLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesSheetScreen(
    viewModel: SpeciesSheetViewModel,
    onBack: () -> Unit,
    onAddToCollection: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.species_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            state.sheet == null -> EmptyState(
                title = stringResource(R.string.results_empty_title),
                body = stringResource(R.string.results_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> SheetContent(
                sheet = state.sheet!!,
                links = state.links,
                stale = state.stale,
                warningText = state.warning?.messageFr,
                onAddToCollection = onAddToCollection,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun SheetContent(
    sheet: CareSheet,
    links: List<SourceLinks.Link>,
    stale: Boolean,
    warningText: String?,
    onAddToCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sheet.imageUrl?.let { url ->
            AsyncImage(
                model = url,
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
                sheet.commonNameFr ?: sheet.scientificName,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                sheet.scientificName,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sheet.family?.let {
                Text(
                    stringResource(R.string.results_family, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Bandeaux d'honnêteté : l'utilisateur doit savoir sur quoi repose ce qu'il lit.
        QualityBanners(sheet = sheet, stale = stale, warningText = warningText)

        SectionCard(title = stringResource(R.string.species_section_watering)) {
            // Le rythme typique de l'espèce, traits intrinsèques compris, et surtout d'où il
            // vient : sans attribution, une valeur par défaut et une donnée réelle se
            // ressemblent trait pour trait.
            val plan = remember(sheet) { WateringIntervalCalculator.speciesTypical(sheet) }

            Text(
                stringResource(R.string.species_watering_interval, plan.effectiveIntervalDays),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                plan.explanationFr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.species_watering_species_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sheet.wateringFr?.let { CareRow(stringResource(R.string.species_section_watering), it) }
        }

        if (sheet.sunlightFr.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.species_section_light)) {
                sheet.sunlightFr.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        val general = buildList {
            sheet.cycleFr?.let { add(stringResource(R.string.species_cycle) to it) }
            sheet.careLevelFr?.let { add(stringResource(R.string.species_care_level) to it) }
            sheet.growthRateFr?.let { add(stringResource(R.string.species_growth_rate) to it) }
            sheet.maintenanceFr?.let { add(stringResource(R.string.species_maintenance) to it) }
            sheet.hardinessFr?.let { add(stringResource(R.string.species_hardiness) to it) }
            if (sheet.indoor == true) add(stringResource(R.string.species_indoor) to "Oui")
            if (sheet.droughtTolerant == true) add(stringResource(R.string.species_drought_tolerant) to "Oui")
        }
        if (general.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.species_section_general)) {
                general.forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider()
                    CareRow(label, value)
                }
            }
        }

        FrenchLabels.toxicity(sheet.poisonousToHumans, sheet.poisonousToPets)?.let { toxicity ->
            SectionCard(title = stringResource(R.string.species_section_toxicity)) {
                Text(toxicity, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (sheet.propagationFr.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.species_section_propagation)) {
                Text(sheet.propagationFr.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (sheet.pruningMonthsFr.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.species_section_pruning)) {
                Text(sheet.pruningMonthsFr.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Textes libres : jamais traduits automatiquement, toujours étiquetés et visuellement
        // en retrait pour qu'on ne les confonde pas avec le contenu français vérifié.
        if (sheet.description != null || sheet.guideSections.isNotEmpty()) {
            SourceTextCard(sheet)
        }

        if (links.isNotEmpty()) {
            SourcesCard(links)
        }

        Button(onClick = onAddToCollection, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.species_add_to_collection))
        }
    }
}

@Composable
private fun QualityBanners(sheet: CareSheet, stale: Boolean, warningText: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            sheet.isFallback -> InfoBanner(
                text = stringResource(R.string.banner_no_care_data),
                tone = BannerTone.WARNING,
            )
            sheet.isGenusApproximation -> InfoBanner(
                text = stringResource(
                    R.string.banner_genus_approximation,
                    sheet.scientificName.substringBefore(' '),
                ),
                tone = BannerTone.WARNING,
            )
            sheet.matchQuality == fr.plantarrosage.core.model.MatchQuality.APPROXIMATE -> InfoBanner(
                text = stringResource(R.string.banner_approximate_match),
                tone = BannerTone.WARNING,
            )
        }

        if (sheet.detailLevel == DetailLevel.SUMMARY) {
            InfoBanner(text = stringResource(R.string.banner_summary_only))
        }

        if (stale) {
            InfoBanner(text = stringResource(R.string.banner_cached_data))
        } else if (warningText != null) {
            InfoBanner(text = warningText, tone = BannerTone.WARNING)
        }
    }
}

@Composable
private fun SourceTextCard(sheet: CareSheet) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.species_section_description),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.species_description_language_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            sheet.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            sheet.guideSections.forEach { section ->
                Text(section.titleFr, style = MaterialTheme.typography.titleSmall)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Renvois vers les bases de référence.
 *
 * Prolongement du parti pris sur la confiance : on affiche un score plutôt qu'une certitude, il
 * faut donc donner les moyens d'aller vérifier ailleurs.
 */
@Composable
private fun SourcesCard(links: List<SourceLinks.Link>) {
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.species_section_sources)) {
        Text(
            stringResource(R.string.species_sources_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        links.forEach { link ->
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(link.sourceFr, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            link.labelFr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
