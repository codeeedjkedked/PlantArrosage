package fr.plantarrosage.app.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.ui.common.EmptyState
import fr.plantarrosage.app.ui.common.ErrorState
import fr.plantarrosage.core.matching.SpeciesListEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesSearchScreen(
    viewModel: SpeciesSearchViewModel,
    onBack: () -> Unit,
    onOpenSpecies: (perenualId: Int, scientificName: String, commonName: String) -> Unit,
    onAddManually: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
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
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search_field_label)) },
                placeholder = { Text(stringResource(R.string.search_field_hint)) },
                supportingText = { Text(stringResource(R.string.search_field_help)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            viewModel.search()
                        },
                        enabled = state.canSearch,
                    ) {
                        Icon(Icons.Default.Search, stringResource(R.string.search_action))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    viewModel.search()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            when {
                state.searching -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }

                state.error != null -> ErrorState(
                    message = state.error!!.messageFr,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::search,
                )

                state.results.isEmpty() && state.hasSearched -> EmptyState(
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body),
                    actionLabel = stringResource(R.string.search_add_manually),
                    onAction = onAddManually,
                )

                state.results.isEmpty() -> EmptyState(
                    title = stringResource(R.string.search_intro_title),
                    body = stringResource(R.string.search_intro_body),
                    actionLabel = stringResource(R.string.search_add_manually),
                    onAction = onAddManually,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.results, key = { it.id }) { entry ->
                        SpeciesCard(
                            entry = entry,
                            onClick = {
                                onOpenSpecies(
                                    entry.id,
                                    entry.scientificNames.firstOrNull() ?: entry.commonName.orEmpty(),
                                    entry.commonName.orEmpty(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeciesCard(entry: SpeciesListEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.commonName ?: entry.scientificNames.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
                entry.scientificNames.firstOrNull()?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
