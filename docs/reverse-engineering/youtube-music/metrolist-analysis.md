# Analyse de Metrolist — résolution de flux audio YouTube Music sur Android

Rapport de recherche pure (aucun code MuzziQ écrit). Objectif : comprendre comment
Metrolist résout le `signatureCipher`, gère le PoToken, et si/comment il s'est adapté
à la migration YouTube vers SABR/UMP, pour informer la session parallèle qui implémente
MuzziQ Android.

Metrolist (`MetrolistGroup/Metrolist`, GPL-3.0, fork d'InnerTune) est en réalité une
façade fine : toute la logique réseau YouTube Music « lourde » vit dans un second dépôt
séparé, **`MetrolistGroup/innertubex`** (GPL-3.0 également), publié comme bibliothèque
via JitPack et consommé comme dépendance Gradle. C'est ce second dépôt qui contient
quasiment toutes les réponses aux questions posées ici.

## 1. Architecture générale

Deux couches distinctes, comme pressenti :

- **`innertube/`** (dans le dépôt Metrolist) : un module quasi vide qui ne fait que
  ré-exporter les types de la bibliothèque externe (ex. `YouTubeClient.kt` du dépôt
  Metrolist n'est qu'un `typealias` vers `com.metrolist.innertubex.models.YouTubeClient`).
  Il fournit surtout les modèles de réponse JSON (browse/search/playlist/etc.) et les
  parseurs de pages, c'est-à-dire la couche « client InnerTube générique ».
- **`innertubex`** (dépôt séparé, versionné indépendamment, `0.x`) : la couche
  d'extraction de flux à proprement parler. Elle regroupe :
  - `cipher/` — déobfuscation `signatureCipher`/`n`, moteur JS embarqué, sources de
    configuration distante.
  - `extraction/` — sélection de client, sélection de format, transport
    direct/HLS/SABR, contrats de PO-token, health-tracking des clients.
  - `models/` — dont le catalogue des profils `YouTubeClient` (voir §2).

Dans l'app Metrolist elle-même, tout passe par un point d'entrée unique,
`InnerTubeXPlayer` (`app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt`),
qui construit à la demande un « bundle » (`YouTubeCipherService` + `InnerTubeExtractor`)
et expose une seule méthode `playerResponseForPlayback(videoId, ...)`. Le
commentaire en tête de fichier indique sans ambiguïté qu'il s'agit du point d'entrée
unique de résolution de flux pour l'app Android.

Point notable : Metrolist **désactive explicitement SABR côté app**, même si la
bibliothèque le supporte (détail au §2).

## 2. Résolution de flux audio (le cœur du sujet)

### `clientName` / contexte InnerTube utilisé pour `/player`

Le fichier `innertubex/src/commonMain/.../models/YouTubeClient.kt` définit un vrai
catalogue de profils de clients InnerTube (WEB_REMIX, WEB_CREATOR, TVHTML5,
TVHTML5_SIMPLY, WEB_EMBEDDED_PLAYER, MWEB, plusieurs variantes ANDROID_VR, IOS,
ANDROID, ANDROID_MUSIC, ANDROID_CREATOR, WEB_KIDS, plus une variante `_SABR` pour
la plupart d'entre eux). Ce n'est pas un choix figé : le fichier
`extraction/strategy/PlaybackClientCatalog.kt` contient une matrice de sélection
détaillée par profil, avec pour chacun : mode de sélection (`AUTOMATIC`,
`PROBE_ONLY`, `API_ONLY`, `MANUAL_ONLY`, `DISABLED`), cycle de vie
(`STABLE`/`EXPERIMENTAL`/`DEPRECATED`/`BROKEN`/`CANARY`/`UNRELEASED`), capacités de
contenu supportées (normal/explicite/kids/âge-restreint/live/uploads) et des notes de
type journal de bench, datées (des campagnes de test internes mi-août et fin-août
2026 sont référencées à plusieurs endroits). Concrètement :

- Le client **`WEB_REMIX`** (le client web « officiel » de music.youtube.com) est le
  profil `AUTOMATIC` principal, marqué `STABLE`, avec authentification **optionnelle**
  (`AuthenticationPolicy.OPTIONAL`).
- En cas d'échec, une **cascade de clients de repli** est essayée automatiquement :
  `WEB_CREATOR` (connexion requise), `TVHTML5_SIMPLY`, `WEB_EMBEDDED_PLAYER`, la
  variante SABR de `WEB_REMIX`, `WEB_SABR`, `MWEB_SABR`, `WEB_SAFARI_SABR`,
  `TVHTML5_SIMPLY_SABR`, etc.
- Une bonne partie du catalogue (ANDROID natif, ANDROID_MUSIC, ANDROID_CREATOR, IOS,
  IPADOS, TVHTML5 « nu », WEB_SAFARI) est marquée `BROKEN`/`PROBE_ONLY`, avec des
  notes qui expliquent pourquoi : les réponses de ces profils ne contiennent plus que
  des métadonnées SABR sans URL directe exploitable, ou nécessitent une attestation
  native indisponible côté bibliothèque (DroidGuard pour Android, l'équivalent pour
  iOS) — c'est-à-dire que les mainteneurs ont constaté empiriquement que YouTube ne
  renvoie plus de flux directs exploitables pour ces profils sans attestation native,
  et les gardent seulement pour du bench de régression, pas pour la sélection
  automatique.

Il n'y a donc pas « un » `clientName` fixe : la bibliothèque interroge/possède un
répertoire de profils entretenu à la main par les mainteneurs sur la base de tests
réels contre YouTube, avec un fallback automatique en cascade géré par
`extraction/strategy/ContentAwareFallbackStrategy.kt` /
`extraction/strategy/ClientHealthTracker.kt` (non détaillé ligne à ligne ici, mais son
rôle est de suivre la santé de chaque client dans le temps et d'exclure ceux qui
échouent récemment — l'app Metrolist expose d'ailleurs sa propre logique
d'exclusion temporaire, `hasRecentWebRemixFailure`, dans `InnerTubeXPlayer.kt`).

### Compte Google requis ou anonyme ?

Les deux sont supportés, et le comportement dépend du contenu :
- Le profil par défaut `WEB_REMIX` a `loginSupported = true` mais fonctionne aussi
  déconnecté (`AuthenticationPolicy.OPTIONAL` dans le catalogue).
- Un commentaire dans `YouTubeClient.kt` résume la règle empirique observée par les
  mainteneurs : les clients déconnectés échouent le plus souvent sur le contenu
  explicite, et les clients authentifiés doivent en plus être « tokenisés »
  (PoToken) pour lire du contenu destiné aux enfants, faute de quoi la lecture
  échoue même connecté.
- Certains profils (`WEB_CREATOR`, `ANDROID_CREATOR`) exigent une connexion
  (`loginRequired = true` / `AuthenticationPolicy.REQUIRED`).

Donc : anonyme fonctionne pour la majorité du contenu « normal », un compte connecté
améliore la fiabilité et devient nécessaire pour l'explicite/les contenus « kids ».

### Gestion de `signatureCipher` — comment, concrètement

C'est la partie la plus structurée du projet. `YouTubeCipherService`
(`innertubex/src/commonMain/.../cipher/YouTubeCipherService.kt`) empile **plusieurs
niveaux de résolution**, essayés dans cet ordre pour chaque défi de signature/`n` :

1. **Config distante pré-résolue « Zemer »** (`solveWithZemerConfig`) : le service
   télécharge — via `RemotePlayerConfigStore` — un JSON de configuration correspondant
   au hash du script player courant (`RemotePlayerConfigParser.extractPlayerHash`).
   Ce JSON encode, pour cette version précise du player YouTube, les paramètres
   nécessaires à un solveur dédié (`ZemerCipherSolver`) capable de dériver signature
   et `n` sans avoir à re-parser le JS depuis zéro. Deux sources concrètes de ce JSON
   ont été identifiées dans le code :
   - Dans **l'app Metrolist** (`InnerTubeXPlayer.kt`, `AndroidPlayerConfigRepository`) :
     `https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json`
     — un projet **tiers**, distinct des mainteneurs de Metrolist/innertubex.
   - Dans **innertubex lui-même** (`GitHubPlayerConfigClient.kt`,
     `RemotePlayerConfigStore.kt`) : un second registre, **`MetrolistGroup/faraday`**
     (propre projet des mainteneurs de Metrolist), publié via jsDelivr
     (`cdn.jsdelivr.net/gh/MetrolistGroup/faraday@{playerTag}/registry/players/{playerHash}.json`)
     et en repli via une release GitHub
     (`github.com/MetrolistGroup/faraday/releases/download/{playerTag}/{playerHash}.json`).

   Autrement dit : une bonne partie de la charge de « suivre YouTube à chaque
   changement de player » est **externalisée** vers ces deux projets compagnons qui
   publient des configurations pré-calculées par hash de script player, plutôt que
   d'exécuter du JS à chaque lecture.

2. **EJS (« yt-dlp/ejs »), exécuté dans un moteur QuickJS embarqué** (pas de WebView)
   si aucune config distante ne correspond. `EjsChallengeSolver` documente
   explicitement s'inspirer de/porter le projet `yt-dlp/ejs` — c'est l'algorithme
   que yt-dlp utilise pour extraire et exécuter les portions pertinentes du `base.js`
   de YouTube. Le moteur JS est **`quickjs-kt`** (`io.github.dokar3:quickjs-kt`,
   licence Apache-2.0, à ne pas confondre avec le code GPL de Metrolist/innertubex
   lui-même — c'est une dépendance tierce sous licence permissive), une liaison
   Kotlin Multiplatform pour QuickJS. `QuickJsEngine.kt` précise dans son
   commentaire que l'intégration s'inspire directement de l'approche QuickJS de
   yt-dlp. Le script
   player réel (jusqu'à ~2,6 Mio, d'après un commentaire de dimensionnement mémoire
   dans `QuickJsEngine.kt`) est téléchargé, puis EJS en extrait/exécute les fonctions
   de déchiffrement dans cet environnement QuickJS sandboxé (limite mémoire ~192 Mio,
   timeout d'évaluation 30 s).

3. **Repli final par parsing regex** (`PlayerScriptParser` + génération d'un script
   solveur `_solveN`/`_solveSig` exécuté lui aussi dans QuickJS) si EJS échoue —
   c'est l'équivalent d'une extraction par motifs regex classique (proche de
   l'approche historique NewPipeExtractor/yt-dlp avant EJS), gardée comme dernier
   filet de sécurité.

Donc pour répondre précisément à la question posée : **ni** un WebView headless
exécutant tout `base.js`, **ni** un algorithme entièrement codé en dur mis à jour
manuellement à chaque release. C'est un système hybride à trois niveaux : bases de
données distantes pré-calculées (mises à jour en continu par deux projets
communautaires distincts), puis extraction+exécution réelle du JS via un moteur
QuickJS embarqué (pas de moteur JS du navigateur), puis un filet de sécurité par
regex. Le WebView, lui, est réservé exclusivement au PoToken (§ suivant).

### PoToken

Généré via **BotGuard dans un WebView Android headless**, dans
`app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenWebView.kt`. Le flux
observé dans le code :

1. Un asset local `po_token.html` est chargé dans un `WebView` (JavaScript activé,
   chargement réseau bloqué côté WebView — `blockNetworkLoads = true`).
2. Le JS de la page appelle `downloadAndRunBotguard()` (interface JS exposée côté
   Kotlin), qui fait un appel HTTP réel vers
   `https://www.youtube.com/api/jnn/v1/Create` (clé API Google en dur dans le code,
   `x-goog-api-key`), récupère un challenge, l'exécute via `runBotGuard(data)` (script
   BotGuard de Google, chargé/évalué dans la page), puis poste le résultat vers
   `https://www.youtube.com/api/jnn/v1/GenerateIT` pour obtenir un `integrityToken`
   avec une durée d'expiration (marge de sécurité de 10 minutes appliquée côté
   Kotlin).
3. Un « minter » (`createPoTokenMinter`) est ensuite instancié une fois dans la page,
   et chaque appel `obtainPoToken(identifier)` (par vidéo ou par `visitorData`, selon
   `PoTokenBinding` du profil client) redonne un jeton signé.
4. Timeouts défensifs : 45 s pour l'initialisation complète, 15 s par génération de
   token ; toute erreur JS non interceptée après l'initialisation, ou une disparition
   du process de rendu WebView (`onRenderProcessGone`), marque l'instance comme
   « morte » et la fait recréer par l'appelant plutôt que de désactiver
   définitivement le PoToken pour la session.

C'est très exactement la même approche que celle documentée publiquement pour
NewPipeExtractor (BotGuard via WebView headless). Le côté « injectable » de
l'architecture (`TokenProvider` interface dans `innertubex`, capacité déclarée
`WEB_BOTGUARD`) suggère aussi que la bibliothèque `innertubex` prévoit d'autres
mécanismes de PoToken (le catalogue de clients référence aussi
`ANDROID_DROIDGUARD`, `IOS_ATTESTATION`, `WEBPAGE_ATTESTATION`, `EXTERNAL` comme
types de providers), mais dans l'app Metrolist actuelle, seul `WEB_BOTGUARD` (la
WebView ci-dessus) est branché — les autres apparaissent comme non implémentés
(les clients correspondants sont marqués `BROKEN`/`PROBE_ONLY`, avec des notes
indiquant explicitement qu'aucun fournisseur d'attestation DroidGuard ou iOS n'est
disponible).

### SABR/UMP — la piste prioritaire de la demande initiale

**Confirmation nette : oui, la bibliothèque `innertubex` a un support SABR/UMP
explicite et documenté**, mais **l'app Metrolist elle-même le désactive
volontairement à ce jour.**

Preuves concrètes :
- Le README d'`innertubex` annonce, dans sa liste de fonctionnalités principales,
  la prise en charge du streaming audio et vidéo SABR/UMP avec sources segmentées
  permettant le seek, ainsi que la sélection de représentation vidéo SABR pour la
  lecture côté hôte.
- Le catalogue de clients (`YouTubeClient.kt`, `PlaybackClientCatalog.kt`) définit
  des variantes `_SABR` dédiées pour presque chaque profil (`WEB_REMIX_SABR`,
  `WEB_SABR`, `MWEB_SABR`, `TVHTML5_SIMPLY_SABR`, `ANDROID_VR_SABR`, `IOS_SABR`,
  `VISIONOS_SABR`, etc.), avec des `transports = setOf(PlaybackTransport.SABR)` et
  des notes de bench détaillées, datées d'une campagne de tests SABR sur Android
  fin août 2026. Plusieurs de ces profils SABR (`WEB_REMIX_SABR`, `WEB_SABR`,
  `TVHTML5_SIMPLY_SABR`, `MWEB_SABR`, `WEB_SAFARI_SABR`, `VISIONOS_SABR`) sont
  marqués `STABLE`/`AUTOMATIC` au niveau de la bibliothèque — c'est-à-dire jugés
  fonctionnels par les mainteneurs.
- Le `CHANGELOG.md` d'innertubex mentionne SABR explicitement à plusieurs reprises :
  la version 0.4.0 corrige par exemple un cas où un flux SABR doit être considéré
  comme terminé à son dernier segment déclaré quand YouTube omet le marqueur de fin
  de piste, et précise que le client `WEB_REMIX` reste le client SABR automatique
  préféré — preuve que le protocole SABR est réellement exercé en pratique par ce
  projet, pas seulement prévu en théorie.

**Mais** dans l'app Metrolist (`InnerTubeXPlayer.kt`), l'extraction est appelée avec
`hints.withStreamCapabilities(allowHls = false, allowSabr = false, ...)`, et le
résultat est immédiatement validé par une assertion qui échoue explicitement (avec
un message expliquant que le moteur de lecture actuel ne supporte pas SABR) si un
flux SABR est malgré tout renvoyé, ce qui ferait planter la lecture (en mode debug)
ou échouer proprement si jamais un flux SABR remontait malgré tout. Autrement dit :
**Metrolist choisit sciemment de ne consommer que des flux « directs » (URL simple,
avec ou sans HLS) et refuse SABR côté moteur de lecture actuel**, alors même que sa
propre bibliothèque de résolution le supporte. C'est cohérent avec la stratégie de
repli en cascade décrite plus haut : YouTube ne force pas SABR pour tous les clients
simultanément, donc Metrolist reste sur les profils qui renvoient encore des flux
directs classiques (`WEB_REMIX` en tête), et n'active pas le pipeline SABR côté
app — probablement pour limiter la complexité côté lecteur (ExoPlayer consomme une
URL directe beaucoup plus simplement qu'un flux segmenté SABR/UMP).

Je n'ai **pas** trouvé, dans le code lu, de date précise de bascule de YouTube vers
SABR pour la musique, ni de confirmation que YouTube Music impose déjà SABR pour
tous les utilisateurs anonymes — seulement la trace que certains profils clients
(ANDROID natif, IOS natif, etc.) renvoient désormais des métadonnées SABR pures
sans URL directe exploitable, comme le notent explicitement plusieurs entrées du
catalogue, ce qui a poussé les mainteneurs à retirer ces profils de la sélection
automatique et à leur préférer des profils web (`WEB_REMIX`, `WEB_EMBEDDED_PLAYER`)
qui, eux, renvoient encore des flux directs/HLS au moment de la dernière mise à
jour du catalogue (dépôt cloné le 2026-09-03, dernier tag `v0.5.2` du 2026-09-02).

## 3. Fréquence de maintenance

Le `CHANGELOG.md` d'`innertubex` (seule source datée trouvée — je n'ai pas eu accès
à l'historique complet des issues/PR GitHub, uniquement au fichier versionné dans le
dépôt) montre un rythme de publication très soutenu sur la période observée :

| Version | Date | Nature du correctif |
|---|---|---|
| v0.1.0 | 2026-08-21 | version initiale |
| v0.1.1 | 2026-08-21 | correctif de publication JitPack (même jour) |
| v0.1.2 | 2026-08-22 | — |
| v0.2.0–v0.2.6 | 2026-08-25 → 2026-08-27 | extraction réutilisable, PoToken prefetch, cipher/n combinés |
| v0.3.0 | 2026-08-30 | pré-chauffage config authentifiée/anonyme |
| v0.4.0–v0.4.1 | 2026-08-31 | fin de flux SABR, transformation `n` sur uploads |
| v0.5.2 | 2026-09-02 | page player-config sans cookies, temps d'évaluation JS augmenté |

Soit environ **10 versions publiées en 12 jours**, la quasi-totalité des entrées
« Fixed » touchant directement au cipher, au `n`-paramètre, au PoToken ou à SABR.
C'est un signal fort et concret (pas une estimation) : **maintenir un solveur de
cipher/PoToken pour YouTube Music en 2026 exige un rythme de correctifs de l'ordre
de plusieurs fois par semaine**, pas un simple ajustement occasionnel. Cette charge
est en partie mutualisée avec les projets externes cités plus haut
(`ZemerTeam/zemer-cipher`, et le propre projet compagnon `MetrolistGroup/faraday`
des mainteneurs), qui publient des configurations pré-résolues indépendamment des
releases de l'app — donc une partie des mises à jour n'exige même pas de nouvelle
version d'`innertubex` ou de Metrolist, seulement une mise à jour de ces registres
distants consommés à l'exécution.

Je n'ai pas eu accès aux issues/discussions GitHub elles-mêmes (pas de navigateur
web authentifié disponible pour explorer l'onglet Issues en profondeur dans le
cadre de cette recherche) — cette estimation de charge repose uniquement sur le
CHANGELOG versionné, qui est une source fiable mais possiblement incomplète (des
correctifs mineurs peuvent ne pas y figurer).

## 4. Dépendances clés

- **`io.github.dokar3:quickjs-kt`** (moteur QuickJS pour Kotlin Multiplatform) —
  licence **Apache License 2.0** (vérifiée via la fiche du projet), donc distincte
  de la licence GPL-3.0 du code Metrolist/innertubex qui l'utilise. C'est le
  composant qui exécute réellement le JavaScript extrait du player YouTube.
- **Ktor client** (`io.ktor:ktor-client-*`, HTTP), **kotlinx.serialization**,
  **kotlinx.coroutines** — bibliothèques JetBrains standard, licence Apache-2.0.
- Deux registres de données externes consommés à l'exécution (pas des dépendances
  de build, mais des sources de données distantes) :
  - `ZemerTeam/zemer-cipher` (projet tiers, licence non vérifiée dans le cadre de
    cette recherche — je n'ai lu que l'URL consommée par Metrolist, pas le dépôt
    lui-même).
  - `MetrolistGroup/faraday` (projet compagnon des mainteneurs Metrolist — licence
    non vérifiée non plus, hors périmètre de cette recherche).
- Le concept/algorithme **EJS** est explicitement crédité comme inspiré de/portant
  **`yt-dlp/ejs`** (licence du projet yt-dlp/ejs non vérifiée précisément dans le
  cadre de cette recherche, mais yt-dlp lui-même est Unlicense — déjà noté dans
  `CLAUDE.md` de MuzziQ).

## 5. Authentification compte YouTube (périmètre secondaire)

Confirmé dans `app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt` :
authentification par **WebView intégrée** pointant vers
`https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com`,
puis extraction du cookie de session une fois la redirection vers
`music.youtube.com` détectée (`shouldOverrideUrlLoading`), via
`CookieManager.getInstance().getCookie("https://music.youtube.com")`. Le cookie brut
est ensuite parsé (`parseCookieString`, utilitaire du module `innertube`) pour en
extraire les champs pertinents (dont potentiellement SAPISID — non confirmé ligne à
ligne dans ce rapport, je n'ai lu que le point d'extraction du cookie complet, pas
le détail du parsing des sous-champs) et le tout est stocké localement
(`InnerTubeCookieKey`, `DataSyncIdKey`, `VisitorDataKey`, etc., vus dans les imports
du fichier). C'est l'approche « WebView + extraction de cookie » évoquée dans la
demande initiale, confirmée par le code.

## Sources consultées

Dépôts clonés en local (clone superficiel, `--depth 1`) pour lecture directe du
code source — aucune reproduction substantielle de code n'est incluse dans ce
rapport, seules quelques constantes/URLs isolées et de très courts fragments
illustratifs :

- `https://github.com/MetrolistGroup/Metrolist` (branche par défaut, commit du
  2026-09-03) :
  - `app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt`
  - `app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenWebView.kt`
  - `app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt`
  - `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt`
    (simple ré-export)
  - `settings.gradle.kts`, `gradle/libs.versions.toml`,
    `innertube/build.gradle.kts` (résolution de la dépendance `innertubex`)
- `https://github.com/MetrolistGroup/innertubex` (tag `v0.5.2`, pin utilisé par
  Metrolist au moment du clone) :
  - `README.md`, `CHANGELOG.md`
  - `src/commonMain/kotlin/com/metrolist/innertubex/cipher/YouTubeCipherService.kt`
  - `src/commonMain/kotlin/com/metrolist/innertubex/cipher/QuickJsEngine.kt`
    (en-tête/commentaires uniquement)
  - `src/commonMain/kotlin/com/metrolist/innertubex/cipher/GitHubPlayerConfigClient.kt`
    (URLs uniquement)
  - `src/commonMain/kotlin/com/metrolist/innertubex/cipher/RemotePlayerConfigStore.kt`
    (URLs uniquement)
  - `src/commonMain/kotlin/com/metrolist/innertubex/models/YouTubeClient.kt`
  - `src/commonMain/kotlin/com/metrolist/innertubex/extraction/strategy/PlaybackClientCatalog.kt`
  - `build.gradle.kts`, `gradle/libs.versions.toml` (dépendances et leurs versions)
- Recherche web pour localiser le dépôt et vérifier la licence de `quickjs-kt`
  (résultats de recherche uniquement, pas de code lu sur ces pages) :
  [MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist),
  [dokar3/quickjs-kt](https://github.com/dokar3/quickjs-kt).

Non consultés / hors périmètre de cette recherche : les issues et discussions
GitHub des deux dépôts (pas de navigation web interactive effectuée), le dépôt
`ZemerTeam/zemer-cipher` lui-même (seule l'URL consommée par Metrolist a été
notée), le dépôt `MetrolistGroup/faraday` lui-même, le dépôt `yt-dlp/ejs` lui-même,
et le détail complet du parsing du cookie de connexion (SAPISID précisément) dans
`LoginScreen.kt`/`parseCookieString`.
