package fr.plantarrosage.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Bandeau d'information ou d'avertissement, utilisé pour la qualité d'appariement et les quotas. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.INFO,
) {
    val container = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.surfaceVariant
        BannerTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        BannerTone.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon: ImageVector = when (tone) {
        BannerTone.INFO -> Icons.Default.Info
        BannerTone.WARNING -> Icons.Default.Warning
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

enum class BannerTone { INFO, WARNING }

/** État vide pédagogique : explique quoi faire plutôt que de constater l'absence. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Erreur avec une porte de sortie : on ne laisse jamais l'utilisateur devant un mur. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (retryLabel != null && onRetry != null) {
            Button(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

/** Pastille de score, colorée selon la bande de confiance. */
@Composable
fun ScoreBadge(scorePercent: Int, label: String, modifier: Modifier = Modifier) {
    val (background, foreground) = when {
        scorePercent >= 40 -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        scorePercent >= 15 -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "$scorePercent % · $label",
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
    }
}

/** Ligne « intitulé / valeur » des fiches d'entretien. */
@Composable
fun CareRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * En-tête de section : pictogramme dans une pastille teintée, puis le titre.
 *
 * La pastille porte la couleur du sujet — eau, soleil, toxicité — ce qui permet de retrouver une
 * section en balayant la fiche du regard, sans lire les titres.
 */
@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** Ligne « intitulé + interrupteur », largeur pleine. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Petite étiquette arrondie : un pictogramme, un mot. Sert aux traits d'une espèce. */
@Composable
fun TraitChip(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
