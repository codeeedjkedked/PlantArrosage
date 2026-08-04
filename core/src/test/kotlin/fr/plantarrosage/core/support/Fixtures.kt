package fr.plantarrosage.core.support

/**
 * Chargement des réponses d'API enregistrées.
 *
 * Ces fichiers sont écrits d'après la documentation publique des deux services : les domaines
 * `my-api.plantnet.org` et `perenual.com` étaient inaccessibles au moment de leur rédaction.
 * Ils testent donc le parseur contre notre compréhension du schéma, pas contre le trafic réel —
 * voir `docs/fixtures.md` pour les ré-enregistrer.
 */
object Fixtures {

    fun load(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$path")) {
            "Fixture introuvable : fixtures/$path"
        }.bufferedReader().use { it.readText() }

    fun plantNet(name: String): String = load("plantnet/$name")

    fun perenual(name: String): String = load("perenual/$name")
}
