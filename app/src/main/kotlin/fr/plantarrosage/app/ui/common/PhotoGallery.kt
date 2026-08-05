package fr.plantarrosage.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Galerie de photos : une grande vue, et la bande de vignettes quand il y en a plusieurs.
 *
 * Toutes les photos d'une identification sont conservées, pas seulement celle qui a servi de
 * couverture — c'est souvent la deuxième ou la troisième, prise sous un autre angle, qui permet
 * de reconnaître la plante des mois plus tard. La grande vue s'ouvre en plein écran d'une
 * pression : les détails d'une feuille ne se voient pas dans deux cents pixels de haut.
 */
@Composable
fun PhotoGallery(
    photos: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (photos.isEmpty()) return

    var selected by remember(photos) { mutableIntStateOf(0) }
    var enlarged by remember { mutableStateOf(false) }
    val current = photos.getOrElse(selected) { photos.first() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AsyncImage(
            model = current,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { enlarged = true },
        )

        if (photos.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos) { index, uri ->
                    val isSelected = index == selected
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { selected = index },
                    )
                }
            }
        }
    }

    if (enlarged) {
        FullScreenPhoto(
            uri = current,
            caption = if (photos.size > 1) "${selected + 1} / ${photos.size}" else null,
            onDismiss = { enlarged = false },
        )
    }
}

@Composable
private fun FullScreenPhoto(uri: String, caption: String?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }

            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
}
