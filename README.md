# PlantArrosage

Application Android de reconnaissance de plantes : photographiez une plante, obtenez son
identification et sa fiche d'entretien (arrosage, lumière, cycle, toxicité…), puis suivez ses
arrosages avec des rappels.

- **Identification** : [Pl@ntNet](https://my.plantnet.org/) — gratuit, ~500 identifications/jour
- **Données d'entretien** : [Perenual](https://perenual.com/docs/api) — gratuit, 100 requêtes/jour
- **Interface** : française
- **Stockage** : local (Room), sans compte ni serveur

---

## Démarrage

### 1. Obtenir les deux clés API

| Service | Où | Palier gratuit |
|---|---|---|
| Pl@ntNet | <https://my.plantnet.org/> → onglet API | ~500 identifications/jour |
| Perenual | <https://perenual.com/docs/api> | 100 requêtes/jour, fiches détaillées limitées aux espèces 1–3000 |

### 2. Les renseigner

Créez `local.properties` à la racine (le fichier est ignoré par git) :

```properties
sdk.dir=/chemin/vers/Android/Sdk
PLANTNET_API_KEY=votre_cle_plantnet
PERENUAL_API_KEY=votre_cle_perenual
```

Les clés peuvent aussi être saisies directement dans l'écran **Réglages** de l'application, ce qui
les prend prioritairement sur celles du build. Un clone sans clés compile et démarre : l'écran de
capture renvoie alors vers les Réglages au lieu d'échouer sur un 401.

> Une chaîne placée dans `BuildConfig` reste extractible d'un APK. C'est acceptable pour un usage
> personnel avec vos propres clés ; en cas de distribution, elles devraient passer derrière un
> proxy que vous contrôlez.

### 3. Construire

```bash
./gradlew :app:assembleDebug     # APK de debug
./gradlew :core:test             # tests unitaires du module métier
```

Ouvrez le projet dans Android Studio (SDK Platform 36) pour l'exécuter sur appareil ou émulateur.

---

## Architecture

Deux modules, et pas un de plus.

```
:core   Kotlin/JVM pur — aucune dépendance Android
        modèles · DTO · clients HTTP · appariement d'espèces · calculs d'arrosage · traductions
        → compilable et testable sans SDK Android ni émulateur

:app    Android — Compose, Room, DataStore, WorkManager, écrans
        implémente les interfaces que :core déclare (cache, quotas, clés API)
```

Toute la logique non triviale vit dans `:core` : c'est ce qui la rend vérifiable en quelques
secondes sur un JVM ordinaire, sans instrumentation. `:app` reste largement déclaratif.

| Sujet | Choix |
|---|---|
| UI | Jetpack Compose + Material 3, navigation type-safe |
| Caméra | Intent système + `PickVisualMedia` — **aucune permission caméra ni stockage** |
| Réseau | Ktor Client (moteur OkHttp sur Android, `MockEngine` en test) |
| Base | Room (3 tables) |
| Rappels | WorkManager, tâche quotidienne — pas d'alarme exacte |
| Injection | Manuelle, via `AppContainer` |
| minSdk / compileSdk | 26 / 36 |

### Points de conception

**L'appariement Pl@ntNet → Perenual.** Le premier rend un nom scientifique, le second s'interroge
en texte libre. `ScientificNameNormalizer` ramène tout à un binôme (autorité, cultivar, hybride et
rang infra-spécifique retirés), puis `SpeciesMatcher` applique un barème explicite dont la qualité
— exacte, genre, approximative — remonte jusqu'à l'écran sous forme de bandeau.

**Le quota Perenual dimensionne l'architecture.** Cent requêtes par jour, c'est peu. D'où : cache
de 180 jours, **cache négatif** de 30 jours (sans lui, chaque ré-identification d'une espèce
inconnue redépenserait deux requêtes), déduplication des appels concurrents, arrêt local à 95, un
seul repli sur le genre, et récupération de la fiche uniquement à l'ouverture d'une espèce — jamais
pour les cinq candidats.

**Jamais d'impasse.** Sans données d'entretien, une fiche de repli est construite sur les seules
infos Pl@ntNet : la plante reste enregistrable et les rappels fonctionnent, avec un intervalle par
défaut que vous ajustez.

**L'intervalle d'arrosage n'est pas figé.** On stocke la base et on recalcule à chaque évaluation,
si bien que le rythme se détend en hiver et se resserre en été sans migration. L'écran affiche le
raisonnement complet — « base 7 j × 1,6 (hiver) × 0,85 (extérieur) » — pour que la suggestion soit
discutable, donc corrigeable.

**Le français s'arrête où commence la source.** Les champs énumérés de Perenual sont traduits par
tables exhaustives et testées. Les textes libres (description, guides) restent en anglais, dans une
carte étiquetée « Source : Perenual, texte en anglais ». Traduire automatiquement des conseils
botaniques introduirait un risque d'erreur que le confort de lecture ne justifie pas.

---

## Tests

```bash
./gradlew :core:test     # 249 tests
```

Couverture : normalisation des noms scientifiques, cascade d'appariement, parsing des deux APIs sur
fixtures, dérivation de l'intervalle d'arrosage, calcul des échéances (changements d'heure et
années bissextiles compris), traductions, clients HTTP contre `MockEngine`.

> **Les fixtures JSON n'ont pas été enregistrées depuis du trafic réel.** Elles ont été écrites
> d'après la documentation publique, les deux domaines d'API étant inaccessibles depuis
> l'environnement de développement initial. Elles vérifient donc le parseur contre notre lecture du
> schéma, pas contre la réalité. Voir [`docs/fixtures.md`](docs/fixtures.md) pour les
> ré-enregistrer une fois muni des clés — prévoyez une ou deux corrections de DTO à ce moment-là.

Le module `:app` n'a pas de tests automatisés : Compose, Room et WorkManager demandent de
l'instrumentation. À vérifier à la main sur appareil :

1. Réglages → coller les deux clés → « Tester la clé » (Perenual)
2. Photographier une plante → identifier → ouvrir un candidat → lire la fiche
3. « Ajouter à mes plantes » → accorder les notifications → vérifier l'accueil
4. « J'ai arrosé » → vérifier l'historique et la nouvelle échéance
5. Forcer le rappel : `adb shell cmd jobscheduler run -f fr.plantarrosage.app.debug <jobId>`,
   puis tester « Marquer comme arrosé » depuis la notification et le lien profond

Testez au moins une fois en API 33+ (chemin de la permission de notification) et une fois en
API 26–28 (plancher `minSdk`).

---

## Construire sans SDK Android

Le module `:core` se compile seul, ce qui permet de faire tourner les tests sur une machine ou un
CI dépourvu de SDK Android :

```bash
./gradlew -PskipAndroid=true :core:test
```

Sans ce drapeau, Gradle configure `:app` et échoue en réclamant l'Android Gradle Plugin.

Deux détails du build servent précisément cet objectif : `settings.gradle.kts` n'inclut `:app` que
si `skipAndroid` est absent, et le `build.gradle.kts` racine ne déclare **aucun** bloc `plugins {}`
— y déclarer l'AGP, fût-ce avec `apply false`, forcerait sa résolution dès la configuration.

### Locale UTF-8 requise

Les noms de tests sont écrits en français avec accents, et le compilateur Kotlin en dérive des noms
de fichiers `.class`. Sur une machine en locale POSIX/C, le build échoue sur
`InvalidPathException: Malformed input`. Corrigez avec :

```bash
export LANG=C.utf8 LC_ALL=C.utf8
```

---

## Limites connues

| Limite | Détail |
|---|---|
| Quota Perenual | 100 requêtes/jour ; l'application s'arrête d'elle-même à 95 avec un message clair |
| Fiches détaillées Perenual | Réservées aux espèces d'identifiant 1–3000 sur l'offre gratuite ; au-delà, la fiche résumée reste exploitable |
| Quota Pl@ntNet | ~500 identifications/jour ; le reliquat est affiché dans les Réglages |
| Textes libres en anglais | Non traduits, et signalés comme tels |
| Notifications sur OEM agressifs | Xiaomi, Huawei et Samsung retardent les tâches de fond ; la section « À arroser aujourd'hui » de l'accueil fait foi |
| Hémisphère | Les ajustements saisonniers supposent l'hémisphère nord |

## Licence et attributions

Identification fournie par [Pl@ntNet](https://plantnet.org/).
Données d'entretien fournies par [Perenual](https://perenual.com/).
Respectez les conditions d'utilisation de chaque service.
