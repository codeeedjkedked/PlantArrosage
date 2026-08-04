package fr.plantarrosage.app.ui.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.common.EmptyState
import fr.plantarrosage.app.ui.common.InfoBanner
import fr.plantarrosage.app.ui.common.ScoreBadge
import fr.plantarrosage.core.model.IdentificationCandidate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onBack: () -> Unit,
    onSelectCandidate: (Int) -> Unit,
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (candidates.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.results_empty_title),
                body = stringResource(R.string.results_empty_body),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onBack,
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.results_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                // Rappel systématique : une identification est une proposition. C'est la
                // protection la moins chère contre des conseils d'entretien appliqués à la
                // mauvaise espèce.
                InfoBanner(text = stringResource(R.string.results_disclaimer))
            }
            itemsIndexed(candidates, key = { _, c -> c.scientificName }) { index, candidate ->
                CandidateCard(candidate = candidate, onClick = { onSelectCandidate(index) })
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: IdentificationCandidate, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = candidate.relatedImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    candidate.bestCommonName ?: candidate.scientificName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    candidate.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidate.family?.let { family ->
                    Text(
                        stringResource(R.string.results_family, family),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(
                    scorePercent = (candidate.score * 100).roundToInt(),
                    label = candidate.confidence.labelFr,
                )
            }
        }
    }
}
