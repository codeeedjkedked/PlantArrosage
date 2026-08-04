package fr.plantarrosage.app.ui.capture

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.plantarrosage.app.R
import fr.plantarrosage.app.di.LocalAppContainer
import fr.plantarrosage.app.ui.common.BannerTone
import fr.plantarrosage.app.ui.common.ErrorState
import fr.plantarrosage.app.ui.common.InfoBanner
import fr.plantarrosage.core.model.PlantOrgan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onBack: () -> Unit,
    onResults: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoStorage = LocalAppContainer.current.photoStorage

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    // Capture par intent système : aucune permission CAMERA n'est requise, contrairement à un
    // viseur intégré. Le fichier cible est créé à l'avance et exposé via FileProvider.
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        pendingCaptureUri?.let { uri -> if (success) viewModel.addPhoto(uri) }
        pendingCaptureUri = null
    }

    // Sélecteur photo système : pas de permission de stockage non plus.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::addPhoto) }

    LaunchedEffect(state.navigateToResults) {
        if (state.navigateToResults) {
            viewModel.onNavigatedToResults()
            onResults()
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshKeyState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.hasPlantNetKey) {
                MissingKeysCard(onOpenSettings)
                return@Column
            }

            state.remainingIdentifications?.let { remaining ->
                InfoBanner(
                    text = if (remaining < 20) {
                        stringResource(R.string.capture_quota_low, remaining)
                    } else {
                        stringResource(R.string.capture_quota_remaining, remaining)
                    },
                    tone = if (remaining < 20) BannerTone.WARNING else BannerTone.INFO,
                )
            }

            Text(
                stringResource(R.string.capture_organ_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.capture_organ_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OrganPicker(selected = state.organ, onSelect = viewModel::setOrgan)

            if (state.photos.isNotEmpty()) {
                Text(
                    stringResource(R.string.capture_photos_count, state.photos.size, state.maxPhotos),
                    style = MaterialTheme.typography.labelLarge,
                )
                PhotoStrip(
                    uris = state.photos.map { it.uri },
                    onRemove = viewModel::removePhoto,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val (_, uri) = photoStorage.createCaptureTarget()
                        pendingCaptureUri = uri
                        // Certains appareils n'ont aucune application photo. Attraper l'exception
                        // évite d'avoir à déclarer un bloc <queries> juste pour le détecter.
                        try {
                            takePicture.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            pendingCaptureUri = null
                            cameraUnavailable = true
                        }
                    },
                    enabled = state.canAddPhoto,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Text(
                        stringResource(R.string.capture_take_photo),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                OutlinedButton(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = state.canAddPhoto,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(
                        stringResource(R.string.capture_pick_gallery),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            if (cameraUnavailable) {
                InfoBanner(
                    text = stringResource(R.string.capture_no_camera),
                    tone = BannerTone.WARNING,
                )
            }

            Text(
                stringResource(R.string.capture_photo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.error?.let { error ->
                ErrorState(
                    message = error.messageFr,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::identify,
                )
            }

            Button(
                onClick = viewModel::identify,
                enabled = state.canIdentify,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.identifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.capture_identify))
                }
            }
        }
    }
}

@Composable
private fun MissingKeysCard(onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.capture_missing_keys_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.capture_missing_keys_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.capture_missing_keys_cta))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganPicker(selected: PlantOrgan, onSelect: (PlantOrgan) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(PlantOrgan.entries.toList()) { _, organ ->
            FilterChip(
                selected = organ == selected,
                onClick = { onSelect(organ) },
                label = { Text(organ.labelFr) },
            )
        }
    }
}

@Composable
private fun PhotoStrip(uris: List<Uri>, onRemove: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(uris) { index, uri ->
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.capture_remove_photo))
                }
            }
        }
    }
}
