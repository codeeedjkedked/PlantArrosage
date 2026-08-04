package fr.plantarrosage.core.model

/**
 * Toutes les défaillances que l'application sait nommer. Chaque variante porte le message
 * français affiché à l'utilisateur : la couche UI n'a aucune traduction à faire.
 */
sealed class AppError(val messageFr: String) {

    /** Aucune clé API renseignée pour le service demandé. */
    data class MissingApiKey(val service: String) :
        AppError("Aucune clé API renseignée pour $service. Ajoutez-la dans les Réglages.")

    /** La clé existe mais le service la refuse (401 / 403). */
    data class InvalidApiKey(val service: String) :
        AppError("La clé API $service est invalide ou expirée. Vérifiez-la dans les Réglages.")

    /** Quota journalier atteint, côté service (429) ou côté compteur local. */
    data class QuotaExceeded(val service: String) :
        AppError("Quota journalier $service atteint. Réessayez demain.")

    /** Le service n'a reconnu aucune espèce sur les photos fournies. */
    data object NoMatch :
        AppError("Aucune espèce reconnue. Essayez une photo plus nette, en cadrant un seul organe.")

    /** Image refusée par le service parce qu'elle est trop lourde. */
    data object ImageTooLarge :
        AppError("Photo trop volumineuse. Réessayez avec une image plus légère.")

    /** Paramètres de requête rejetés (400). */
    data class BadRequest(val detail: String) :
        AppError("Requête invalide : $detail")

    /** Panne réseau, DNS, TLS, ou délai dépassé. */
    data class Network(val cause: String) :
        AppError("Connexion impossible. Vérifiez votre réseau puis réessayez.")

    /** 5xx ou réponse illisible. */
    data class ServiceUnavailable(val service: String) :
        AppError("Le service $service est momentanément indisponible. Réessayez plus tard.")

    /** La réponse a bien été reçue mais ne correspond pas au schéma attendu. */
    data class Parsing(val detail: String) :
        AppError("Réponse inattendue du service. Signalez-le si le problème persiste.")
}
