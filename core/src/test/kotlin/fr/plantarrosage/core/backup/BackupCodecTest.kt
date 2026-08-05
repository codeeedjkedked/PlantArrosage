package fr.plantarrosage.core.backup

import fr.plantarrosage.core.model.AppError
import fr.plantarrosage.core.util.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {

    private fun plante(
        binome: String = "monstera deliciosa",
        creeLe: Long = 1_700_000_000_000,
        photos: List<String> = listOf("plante_1.jpg"),
        arrosages: List<BackupWateringEvent> = emptyList(),
    ) = BackupPlant(
        nickname = "Mon monstera",
        scientificName = "Monstera deliciosa",
        normalizedBinomial = binome,
        location = "INTERIEUR",
        baseIntervalDays = 9,
        createdAtEpochMillis = creeLe,
        photoFileNames = photos,
        wateringEvents = arrosages,
    )

    private fun sauvegarde(vararg plantes: BackupPlant) = BackupFile(
        exportedAtEpochMillis = 1_800_000_000_000,
        plants = plantes.toList(),
    )

    // ---------- Aller-retour ----------

    @Test
    fun `une sauvegarde se relit à l'identique`() {
        val origine = sauvegarde(
            plante(arrosages = listOf(BackupWateringEvent(1_700_000_100_000, "MANUELLE", null, 9))),
            plante(binome = "ficus lyrata", creeLe = 1_700_000_500_000),
        ).copy(settings = BackupSettings(plantNetKey = "abc", reminderHour = 8))

        val relue = BackupCodec.decode(BackupCodec.encode(origine))

        assertEquals(Outcome.Success(origine), relue)
    }

    @Test
    fun `les champs absents retombent sur leurs valeurs par défaut`() {
        val minimal = """
            {
              "version": 1,
              "exporteLe": 1700000000000,
              "plantes": [
                {
                  "surnom": "Test",
                  "nomScientifique": "Aloe vera",
                  "binomeNormalise": "aloe vera",
                  "emplacement": "INTERIEUR",
                  "intervalleBaseJours": 14,
                  "creeLe": 1700000000000
                }
              ]
            }
        """.trimIndent()

        val file = (BackupCodec.decode(minimal) as Outcome.Success).value
        val plante = file.plants.single()

        assertTrue(plante.photoFileNames.isEmpty())
        assertTrue(plante.wateringEvents.isEmpty())
        assertTrue(plante.remindersEnabled)
        assertEquals(null, file.settings)
    }

    @Test
    fun `un champ inconnu n'empêche pas la lecture`() {
        // Une sauvegarde écrite par une version future mais compatible doit rester importable.
        val avecExtra = """
            {"version":1,"exporteLe":1,"plantes":[],"champInconnu":"peu importe"}
        """.trimIndent()

        assertTrue(BackupCodec.decode(avecExtra) is Outcome.Success)
    }

    // ---------- Refus ----------

    @Test
    fun `un fichier illisible est refusé avec un message clair`() {
        val outcome = BackupCodec.decode("ceci n'est pas du JSON")

        assertTrue((outcome as Outcome.Failure).error is AppError.BackupUnreadable)
        assertTrue(outcome.error.messageFr.contains("sauvegarde"))
    }

    @Test
    fun `un format plus récent est refusé plutôt qu'importé à moitié`() {
        val futur = """{"version":99,"exporteLe":1,"plantes":[]}"""

        val erreur = (BackupCodec.decode(futur) as Outcome.Failure).error

        assertTrue(erreur is AppError.BackupTooRecent)
        assertEquals(99, (erreur as AppError.BackupTooRecent).version)
        assertTrue(erreur.messageFr.contains("Mettez à jour"))
    }

    @Test
    fun `la version courante passe`() {
        val courant = """{"version":${BackupFile.CURRENT_VERSION},"exporteLe":1,"plantes":[]}"""

        assertTrue(BackupCodec.decode(courant) is Outcome.Success)
    }

    // ---------- Identité ----------

    @Test
    fun `deux exemplaires de la même espèce ajoutés à des moments différents restent distincts`() {
        val premier = plante(creeLe = 1_000)
        val second = plante(creeLe = 2_000)

        assertNotEquals(premier.identity, second.identity)
    }

    @Test
    fun `renommer une plante ne change pas son identité`() {
        val avant = plante()
        val apres = avant.copy(nickname = "Grand monstera du salon")

        assertEquals(avant.identity, apres.identity)
    }

    // ---------- Plan d'import ----------

    @Test
    fun `un import sur une collection vide ajoute tout`() {
        val plan = BackupCodec.plan(
            imported = listOf(plante(), plante(binome = "ficus lyrata", creeLe = 2_000)),
            existingIdentities = emptySet(),
        )

        assertEquals(2, plan.addedCount)
        assertEquals(0, plan.alreadyPresent)
    }

    @Test
    fun `réimporter le même fichier n'ajoute rien`() {
        val plantes = listOf(plante(), plante(binome = "ficus lyrata", creeLe = 2_000))

        val plan = BackupCodec.plan(plantes, plantes.map { it.identity }.toSet())

        assertEquals(0, plan.addedCount)
        assertEquals(2, plan.alreadyPresent)
    }

    @Test
    fun `un import partiel n'ajoute que ce qui manque`() {
        val connue = plante()
        val nouvelle = plante(binome = "aloe vera", creeLe = 3_000)

        val plan = BackupCodec.plan(listOf(connue, nouvelle), setOf(connue.identity))

        assertEquals(listOf(nouvelle), plan.toAdd)
        assertEquals(1, plan.alreadyPresent)
    }

    @Test
    fun `un doublon interne à l'archive n'est ajouté qu'une fois`() {
        val plan = BackupCodec.plan(listOf(plante(), plante()), emptySet())

        assertEquals(1, plan.addedCount)
        assertEquals(1, plan.alreadyPresent)
    }

    @Test
    fun `le plan compte les photos à restaurer`() {
        val plan = BackupCodec.plan(
            imported = listOf(
                plante(photos = listOf("a.jpg", "b.jpg")),
                plante(binome = "aloe vera", creeLe = 5_000, photos = listOf("c.jpg")),
            ),
            existingIdentities = emptySet(),
        )

        assertEquals(3, plan.photoCount)
    }

    @Test
    fun `les photos des plantes déjà présentes ne sont pas comptées`() {
        val connue = plante(photos = listOf("a.jpg", "b.jpg"))

        val plan = BackupCodec.plan(listOf(connue), setOf(connue.identity))

        assertEquals(0, plan.photoCount)
    }

    @Test
    fun `l'ordre des plantes importées est conservé`() {
        val a = plante(binome = "aloe vera", creeLe = 1)
        val b = plante(binome = "ficus lyrata", creeLe = 2)
        val c = plante(binome = "monstera deliciosa", creeLe = 3)

        val plan = BackupCodec.plan(listOf(c, a, b), emptySet())

        assertEquals(listOf(c, a, b), plan.toAdd)
    }

    // ---------- Contenu transporté ----------

    @Test
    fun `l'historique d'arrosage traverse l'aller-retour`() {
        val origine = sauvegarde(
            plante(
                arrosages = listOf(
                    BackupWateringEvent(1_000, "MANUELLE", "après rempotage", 9),
                    BackupWateringEvent(2_000, "NOTIFICATION", null, 12),
                )
            )
        )

        val relue = (BackupCodec.decode(BackupCodec.encode(origine)) as Outcome.Success).value

        assertEquals(2, relue.plants.single().wateringEvents.size)
        assertEquals("après rempotage", relue.plants.single().wateringEvents.first().note)
    }

    @Test
    fun `les clés API voyagent avec les réglages`() {
        val origine = sauvegarde().copy(
            settings = BackupSettings(
                plantNetKey = "cle-plantnet",
                perenualKey = "cle-perenual",
                reminderHour = 19,
                defaultOrgan = "LEAF",
            )
        )

        val relue = (BackupCodec.decode(BackupCodec.encode(origine)) as Outcome.Success).value

        assertEquals("cle-plantnet", relue.settings?.plantNetKey)
        assertEquals(19, relue.settings?.reminderHour)
    }

    @Test
    fun `le document exporté est lisible par un humain`() {
        // Les noms de champs sont en français : quelqu'un qui ouvre son fichier doit s'y retrouver.
        val texte = BackupCodec.encode(sauvegarde(plante()))

        assertTrue(texte.contains("\"plantes\""))
        assertTrue(texte.contains("\"nomScientifique\""))
        assertTrue(texte.contains("\n"), "le document devrait être indenté")
    }
}
