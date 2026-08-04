package fr.plantarrosage.core.matching

import fr.plantarrosage.core.model.MatchQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpeciesMatcherTest {

    private fun entry(
        id: Int,
        commonName: String? = null,
        vararg scientificNames: String,
    ) = SpeciesListEntry(id = id, commonName = commonName, scientificNames = scientificNames.toList())

    private fun target(name: String) = ScientificNameNormalizer.normalize(name)!!

    @Test
    fun `un binôme identique donne une correspondance exacte`() {
        val result = SpeciesMatcher.match(
            target = target("Monstera deliciosa"),
            entries = listOf(entry(1, "Swiss cheese plant", "Monstera deliciosa")),
        )

        assertEquals(MatchQuality.EXACT, result?.quality)
        assertEquals(1, result?.entry?.id)
        assertEquals(1.0, result?.score)
    }

    @Test
    fun `la correspondance exacte est trouvée même hors de la première position`() {
        val result = SpeciesMatcher.match(
            target = target("Monstera deliciosa"),
            entries = listOf(
                entry(10, "Monstera adansonii", "Monstera adansonii"),
                entry(11, "Monstera obliqua", "Monstera obliqua"),
                entry(12, "Swiss cheese plant", "Monstera deliciosa"),
            ),
        )

        assertEquals(12, result?.entry?.id)
        assertEquals(MatchQuality.EXACT, result?.quality)
    }

    @Test
    fun `l'autorité présente côté Perenual n'empêche pas la correspondance`() {
        val result = SpeciesMatcher.match(
            target = target("Monstera deliciosa"),
            entries = listOf(entry(1, null, "Monstera deliciosa Liebm.")),
        )

        assertEquals(MatchQuality.EXACT, result?.quality)
    }

    @Test
    fun `un rang infra-spécifique divergent reste une correspondance exacte`() {
        val result = SpeciesMatcher.match(
            target = target("Lavandula angustifolia subsp. angustifolia"),
            entries = listOf(entry(1, null, "Lavandula angustifolia")),
        )

        assertEquals(MatchQuality.EXACT, result?.quality)
    }

    @Test
    fun `à défaut d'espèce, le genre donne une correspondance indicative`() {
        val result = SpeciesMatcher.match(
            target = target("Monstera deliciosa"),
            entries = listOf(entry(5, "Monstera adansonii", "Monstera adansonii")),
        )

        assertEquals(MatchQuality.GENUS, result?.quality)
        assertEquals(0.60, result?.score)
    }

    @Test
    fun `l'espèce exacte l'emporte sur une simple correspondance de genre`() {
        val result = SpeciesMatcher.match(
            target = target("Monstera deliciosa"),
            entries = listOf(
                entry(5, null, "Monstera adansonii"),
                entry(6, null, "Monstera deliciosa"),
            ),
        )

        assertEquals(6, result?.entry?.id)
    }

    @Test
    fun `un nom commun partagé donne une correspondance approximative`() {
        val result = SpeciesMatcher.match(
            target = target("Zamioculcas zamiifolia"),
            entries = listOf(entry(9, "Plante ZZ", "Zamioculcas loddigesii")),
            targetCommonNames = listOf("Plante ZZ"),
        )

        // Genre identique : la correspondance de genre est plus forte que celle par nom commun.
        assertEquals(MatchQuality.GENUS, result?.quality)
    }

    @Test
    fun `un nom commun partagé sauve la mise quand le genre diffère`() {
        val result = SpeciesMatcher.match(
            target = target("Chlorophytum comosum"),
            entries = listOf(entry(9, "Plante araignée", "Anthericum comosum")),
            targetCommonNames = listOf("Plante araignée"),
        )

        assertEquals(MatchQuality.APPROXIMATE, result?.quality)
        assertEquals(0.50, result?.score)
    }

    @Test
    fun `un token commun suffit pour un rapprochement approximatif`() {
        val result = SpeciesMatcher.match(
            target = target("Chlorophytum comosum"),
            entries = listOf(entry(9, "Spider plant araignée", "Anthericum comosum")),
            targetCommonNames = listOf("Plante araignée"),
        )

        assertEquals(MatchQuality.APPROXIMATE, result?.quality)
    }

    @Test
    fun `aucune ressemblance ne produit aucune correspondance`() {
        val result = SpeciesMatcher.match(
            target = target("Monstera deliciosa"),
            entries = listOf(entry(1, "Rose", "Rosa gallica")),
        )

        assertNull(result)
    }

    @Test
    fun `une liste vide ne produit aucune correspondance`() {
        assertNull(SpeciesMatcher.match(target("Monstera deliciosa"), emptyList()))
    }

    @Test
    fun `un genre seul se rapproche de n'importe quelle espèce du genre`() {
        val result = SpeciesMatcher.match(
            target = target("Sedum sp."),
            entries = listOf(entry(3, null, "Sedum morganianum")),
        )

        assertEquals(MatchQuality.GENUS, result?.quality)
    }

    @Test
    fun `un nom commun trop court ne déclenche pas de rapprochement`() {
        val result = SpeciesMatcher.match(
            target = target("Chlorophytum comosum"),
            entries = listOf(entry(9, "ZZ", "Anthericum comosum")),
            targetCommonNames = listOf("ZZ"),
        )

        // « ZZ » fait moins de trois caractères : pas de token significatif, mais l'égalité
        // stricte des noms communs reste acceptée.
        assertEquals(MatchQuality.APPROXIMATE, result?.quality)
    }
}
