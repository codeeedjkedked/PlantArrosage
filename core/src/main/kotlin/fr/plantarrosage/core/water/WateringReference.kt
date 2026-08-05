package fr.plantarrosage.core.water

import fr.plantarrosage.core.model.NormalizedName
import fr.plantarrosage.core.net.HttpClientFactory
import fr.plantarrosage.core.util.normalizeForComparison

/**
 * Base d'arrosage locale, embarquée avec l'application.
 *
 * Chargée une seule fois depuis les ressources du module, puis indexée en mémoire. Le fichier
 * pèse une soixantaine de kilo-octets : le coût est négligeable et la consultation instantanée,
 * sans réseau ni quota — contrairement à toutes les sources distantes disponibles.
 */
object WateringReference {

    private const val RESOURCE_PATH = "data/arrosage.json"

    private val file: WateringReferenceFile by lazy { load() }

    /** Index par clé, tous rangs confondus. Les clés sont uniques, un test le garantit. */
    private val byKey: Map<String, WateringReferenceEntry> by lazy {
        file.entries.associateBy { it.key }
    }

    val disclaimerFr: String get() = file.disclaimerFr

    val entries: List<WateringReferenceEntry> get() = file.entries

    /**
     * Cherche l'entrée la plus spécifique disponible.
     *
     * L'ordre compte : une donnée propre à l'espèce vaut mieux qu'une donnée de genre, qui vaut
     * mieux qu'une donnée de famille. On s'arrête au premier niveau qui répond.
     *
     * @param name nom scientifique déjà normalisé, tel que produit par `ScientificNameNormalizer`
     * @param family famille botanique, quand Pl@ntNet la fournit
     * @param typeHint type morphologique déduit d'autres indices, ex. « succulente »
     */
    fun lookup(
        name: NormalizedName?,
        family: String? = null,
        typeHint: String? = null,
    ): WateringReferenceEntry? {
        if (name != null) {
            // 1. Espèce exacte : Ficus lyrata n'a pas les mêmes besoins que Ficus benjamina.
            byKey[name.binomial]?.takeIf { it.rank == WateringRank.ESPECE }?.let { return it }

            // 2. Genre : le niveau qui couvre le plus de terrain.
            byKey[name.genus]?.takeIf { it.rank == WateringRank.GENRE }?.let { return it }
        }

        // 3. Famille : filet large mais encore horticolement pertinent.
        family?.normalizeForComparison()
            ?.let(byKey::get)
            ?.takeIf { it.rank == WateringRank.FAMILLE }
            ?.let { return it }

        // 4. Type morphologique : dernier recours avant l'aveu d'ignorance.
        typeHint?.normalizeForComparison()
            ?.let(byKey::get)
            ?.takeIf { it.rank == WateringRank.TYPE }
            ?.let { return it }

        return null
    }

    /** Libellé d'attribution affiché à l'utilisateur, précisant le niveau de la donnée. */
    fun attribution(entry: WateringReferenceEntry): String = when (entry.rank) {
        WateringRank.ESPECE -> "base PlantArrosage (${entry.key.replaceFirstChar { it.uppercase() }})"
        WateringRank.GENRE -> "base PlantArrosage (genre ${entry.key.replaceFirstChar { it.uppercase() }})"
        WateringRank.FAMILLE -> "base PlantArrosage (famille ${entry.key.replaceFirstChar { it.uppercase() }})"
        WateringRank.TYPE -> "base PlantArrosage (${entry.key.replace('-', ' ')})"
    }

    private fun load(): WateringReferenceFile {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(RESOURCE_PATH)) {
            "Base d'arrosage introuvable : $RESOURCE_PATH"
        }
        val json = stream.bufferedReader().use { it.readText() }
        return HttpClientFactory.json.decodeFromString(WateringReferenceFile.serializer(), json)
    }
}
