# Ré-enregistrer les fixtures d'API

## Pourquoi ce document

Les fichiers de `core/src/test/resources/fixtures/` ont été **écrits à la main d'après la
documentation publique** de Pl@ntNet et Perenual : les deux domaines étaient inaccessibles depuis
l'environnement où le projet a été créé.

Ils vérifient donc que le parseur correspond à *notre lecture du schéma*, pas au trafic réel.
Tant qu'ils n'ont pas été confrontés à de vraies réponses, considérez la couche de décodage comme
plausible et non comme prouvée. Prévoyez une ou deux corrections de DTO au premier passage.

## Prérequis

Deux clés gratuites :

- Pl@ntNet : <https://my.plantnet.org/> → onglet « API », ~500 identifications/jour
- Perenual : <https://perenual.com/docs/api> → 100 requêtes/jour, espèces 1–3000

```bash
export PLANTNET_KEY="votre_cle"
export PERENUAL_KEY="votre_cle"
cd core/src/test/resources/fixtures
```

## Pl@ntNet

Il faut une photo de plante quelconque (`feuille.jpg`) pour les deux premiers appels.

```bash
# identify_ok.json — une identification qui aboutit
curl -s -X POST \
  "https://my-api.plantnet.org/v2/identify/all?api-key=$PLANTNET_KEY&lang=fr&nb-results=5&include-related-images=true&no-reject=false" \
  -F "images=@feuille.jpg" \
  -F "organs=leaf" \
  | python3 -m json.tool > plantnet/identify_ok.json

# identify_no_match.json — une photo qui ne ressemble à rien de connu (un mur, un plafond…)
curl -s -X POST \
  "https://my-api.plantnet.org/v2/identify/all?api-key=$PLANTNET_KEY&lang=fr&nb-results=5" \
  -F "images=@mur.jpg" \
  -F "organs=auto" \
  | python3 -m json.tool > plantnet/identify_no_match.json
```

`identify_minimal.json` est volontairement synthétique : il vérifie que le parseur survit à une
réponse amputée de tous ses champs facultatifs. Ne pas le remplacer.

**À vérifier après ré-enregistrement** : le champ `remainingIdentificationRequests` est-il bien à la
racine de la réponse (et non dans un en-tête HTTP) ? `PlantNetResponseDto` le suppose.

## Perenual

```bash
# species_list_monstera.json
curl -s "https://perenual.com/api/species-list?key=$PERENUAL_KEY&q=monstera%20deliciosa&page=1" \
  | python3 -m json.tool > perenual/species_list_monstera.json

# species_list_empty.json — une requête qui ne ramène rien
curl -s "https://perenual.com/api/species-list?key=$PERENUAL_KEY&q=zzzzznotaplant&page=1" \
  | python3 -m json.tool > perenual/species_list_empty.json

# species_details_monstera.json — reprendre l'id trouvé ci-dessus
curl -s "https://perenual.com/api/species/details/1786?key=$PERENUAL_KEY" \
  | python3 -m json.tool > perenual/species_details_monstera.json

# care_guide_monstera.json
curl -s "https://perenual.com/api/species-care-guide-list?key=$PERENUAL_KEY&species_id=1786" \
  | python3 -m json.tool > perenual/care_guide_monstera.json
```

`species_details_free_tier_quirks.json` rassemble volontairement toutes les incohérences de type
observées ou documentées (`"\"7-10\""`, `poisonous_to_pets: "1"`, `sunlight` en chaîne, sentinelle
`upgrade required`, rusticité numérique). **Ne pas l'écraser** : c'est le test de robustesse du
parseur. En revanche, si une nouvelle incohérence apparaît dans une réponse réelle, ajoutez-la à
ce fichier et complétez `TolerantSerializers.kt`.

**Points à confronter en priorité** — ce sont les hypothèses les plus fragiles :

| Hypothèse | Où | Si elle est fausse |
|---|---|---|
| `watering_general_benchmark` est un objet `{value, unit}` | `PerenualDto.kt` | `BenchmarkSerializer` tolère déjà une chaîne |
| `scientific_name` est un tableau | `PerenualDto.kt` | `StringOrListSerializer` tolère déjà une chaîne |
| Le préfixe est `/api/` et non `/api/v2/` | `PerenualClient.baseUrl` | Paramètre de constructeur, à changer sans recompiler la logique |
| Les détails sont refusés au-delà de l'id 3000 | `PerenualLimits` | Ajuster la constante |

## Relancer la suite

```bash
./gradlew :core:test
```

Un test qui casse après ré-enregistrement est une bonne nouvelle : il indique exactement où notre
lecture du schéma divergeait de la réalité.
