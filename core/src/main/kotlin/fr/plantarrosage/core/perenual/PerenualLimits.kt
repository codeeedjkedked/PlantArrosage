package fr.plantarrosage.core.perenual

/** Limites de l'offre gratuite Perenual, traitées comme des données de conception. */
object PerenualLimits {

    /**
     * Au-delà de cet identifiant, `species/details` et `species-care-guide-list` sont réservés
     * aux offres payantes. On ne tente même pas l'appel : ce serait dépenser une requête pour
     * recevoir une erreur.
     */
    const val FREE_TIER_MAX_SPECIES_ID = 3000

    /** Quota journalier annoncé pour une clé gratuite. */
    const val FREE_TIER_DAILY_REQUESTS = 100

    /**
     * Seuil d'arrêt local, volontairement sous le quota réel : mieux vaut un message français
     * clair qu'un 429 brut au milieu d'un parcours.
     */
    const val DAILY_REQUEST_SAFETY_STOP = 95

    /** Durée de validité d'une fiche trouvée. Les données d'entretien ne bougent pas. */
    const val POSITIVE_CACHE_TTL_DAYS = 180L

    /**
     * Durée de validité d'une absence de fiche. Sans ce cache négatif, chaque nouvelle
     * identification d'une espèce inconnue de Perenual redépenserait deux requêtes sur cent.
     */
    const val NEGATIVE_CACHE_TTL_DAYS = 30L

    fun supportsDetails(speciesId: Int): Boolean = speciesId in 1..FREE_TIER_MAX_SPECIES_ID
}
