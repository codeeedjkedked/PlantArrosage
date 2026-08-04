package fr.plantarrosage.core.model

import java.net.URLEncoder

/**
 * Liens vers les bases de référence, pour que l'utilisateur puisse vérifier une identification
 * ailleurs que dans l'application.
 *
 * C'est le prolongement naturel du parti pris sur la confiance : on affiche un score plutôt qu'une
 * certitude, et on donne les moyens d'aller confronter le résultat à une source.
 */
object SourceLinks {

    /** Un lien sortant, prêt à afficher. */
    data class Link(val labelFr: String, val url: String, val sourceFr: String)

    fun gbif(gbifId: String?): Link? = gbifId
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.let {
            Link(
                labelFr = "Fiche taxonomique",
                url = "https://www.gbif.org/species/$it",
                sourceFr = "GBIF",
            )
        }

    /** Plants of the World Online, référence de nomenclature de Kew. */
    fun powo(powoId: String?): Link? = powoId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            Link(
                labelFr = "Nomenclature de référence",
                url = "https://powo.science.kew.org/taxon/$it",
                sourceFr = "POWO (Kew)",
            )
        }

    fun perenual(perenualId: Int?): Link? = perenualId
        ?.takeIf { it > 0 }
        ?.let {
            Link(
                labelFr = "Fiche d'entretien complète",
                url = "https://perenual.com/plant-species-database-search-finder/species/$it",
                sourceFr = "Perenual",
            )
        }

    fun wikipediaFr(scientificName: String?): Link? = scientificName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            Link(
                labelFr = "Article encyclopédique",
                url = "https://fr.wikipedia.org/wiki/${it.replace(' ', '_').encodePath()}",
                sourceFr = "Wikipédia",
            )
        }

    fun plantNet(scientificName: String?): Link? = scientificName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            Link(
                labelFr = "Photos et observations",
                url = "https://identify.plantnet.org/fr/k-world-flora/species/${it.encodePath()}/data",
                sourceFr = "Pl@ntNet",
            )
        }

    /** Tous les liens disponibles pour une fiche, dans l'ordre d'utilité. */
    fun forSheet(
        scientificName: String?,
        perenualId: Int?,
        gbifId: String?,
        powoId: String?,
    ): List<Link> = listOfNotNull(
        plantNet(scientificName),
        wikipediaFr(scientificName),
        gbif(gbifId),
        powo(powoId),
        perenual(perenualId),
    )

    /**
     * Encodage de segment d'URL. `URLEncoder` cible les formulaires : il transforme l'espace en
     * `+`, ce qui est faux dans un chemin, d'où la reprise en `%20`.
     */
    private fun String.encodePath(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
}
