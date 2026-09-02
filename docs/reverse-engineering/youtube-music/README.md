# YouTube Music — observations InnerTube (mode anonyme)

Procédure suivie : §85 du plan d'architecture. Comportement observé par appels
réels, documenté ici, puis implémenté indépendamment dans
`src/providers/youtube-music/`. Aucun code MetroList consulté ni copié.

## SEARCH — fonctionne en anonyme

- `POST https://music.youtube.com/youtubei/v1/search?key={API_KEY}`
- `API_KEY` = clé publique embarquée dans la page (pas un secret serveur) :
  `AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30`
- Contexte client qui fonctionne : `clientName: "WEB_REMIX"`,
  `clientVersion: "1.20241201.01.00"`
- `params: "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"` filtre sur "Songs" (observé en
  interceptant une recherche filtrée sur le site réel).
- Réponse : `contents.tabbedSearchResultsRenderer.tabs[0].tabRenderer.content
  .sectionListRenderer.contents[]` → chercher la section dont
  `musicShelfRenderer.title.runs[0].text === "Songs"`.
- Chaque résultat : `musicResponsiveListItemRenderer` avec :
  - `videoId` dans `overlay.musicItemThumbnailOverlayRenderer.content
    .musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId`
  - titre dans `flexColumns[0]...text.runs`
  - artiste/album/durée dans `flexColumns[1]...text.runs`, segments séparés
    par `" • "` — **nombre de segments variable** (single sans album = 2
    segments, pas 3) : ne jamais indexer en dur.
  - miniature dans `thumbnail.musicThumbnailRenderer.thumbnail.thumbnails[]`
    (dernier élément = plus grande résolution).

Vérifié le 2026-09-01 : requête "Linkin Park Numb" → résultat exact
("Numb" / "Linkin Park" / "Meteora (Bonus Edition)" / 3:08), structure stable.

## PLAYER — historique des sondes réelles

### 2026-09-01 — 6 contextes testés sans `signatureTimestamp` (conclusion erronée, voir plus bas)

Sondé le 2026-09-01 sur 6 contextes client différents contre un vrai
`videoId` public :

| Client | Résultat |
|---|---|
| `WEB_REMIX` (music.youtube.com) | `UNPLAYABLE` / "Video unavailable" |
| `WEB_REMIX` + `visitorData` d'une recherche précédente | idem |
| `ANDROID_MUSIC` | `LOGIN_REQUIRED` |
| `IOS_MUSIC` | `LOGIN_REQUIRED` |
| `WEB` / `MWEB` (youtube.com) | `UNPLAYABLE` |
| `TVHTML5_SIMPLY_EMBEDDED_PLAYER` | `ERROR` ("no longer supported") |

Conclusion tirée à l'époque : YouTube exigerait un **PoToken** (jeton
anti-bot généré par un challenge BotGuard) pour tout appel `/player` anonyme.
**Cette conclusion s'est révélée fausse** — voir ci-dessous. Elle n'avait
jamais été testée en ajoutant le champ `playbackContext` au corps de la
requête ; l'absence de PoToken n'était pas la cause du blocage.

### 2026-09-02 — reprise de l'investigation, conclusion corrigée par test réel

Objectif : éliminer la dépendance yt-dlp en s'inspirant de la stratégie de
clients tiers YouTube Music open source (étude de comportement uniquement,
aucun code copié — voir l'en-tête de ce fichier).

**Piste PoToken pur-TypeScript (jsdom, sans navigateur) : testée, cassée en
pratique.** Le paquet npm `youtube-po-token-generator` (MIT, `bgutils-js` +
`jsdom`, aucun navigateur réel requis en théorie) a été installé et exécuté
réellement contre YouTube. Résultat reproductible : `OutOfMemory` après
~45-85s, quel que soit `--max-old-space-size` (testé jusqu'à 3 Go) et quelle
que soit la version de Node (25.9.0 et 22.19.0 LTS testées). Cause identifiée
par instrumentation directe : la boucle `while(true)` interne de la
librairie ne s'arrête que sur un poToken de 164 caractères ; en pratique,
chaque tentative produit un jeton de 2736 caractères (donc invalide), avec
des erreurs jsdom systématiques (`HTMLMediaElement.prototype.load`,
`HTMLCanvasElement.prototype.getContext` non implémentés, `writeEmbed is not
defined`) — le challenge BotGuard courant détecte que l'environnement n'est
pas un vrai navigateur et ne mine jamais un jeton valide. Corroboré de façon
indépendante par l'issue GitHub `LuanRT/BgUtils#48` (28 août 2026, quelques
jours avant ce test) : "4.0.3 Generates an invalid poToken. Only works for
the YTMUSIC client." **Conclusion : la génération de PoToken sans navigateur
réel n'est actuellement pas viable**, ni avec ce paquet ni vraisemblablement
avec `bgutils-js` utilisé directement (même mécanisme jsdom sous-jacent).

**Piste navigateur headless réel (Playwright/Chromium) : testée, fonctionne,
mais s'est avérée hors sujet.** Un vrai Chromium headless (Playwright,
~300 Mo, pas de patch anti-détection) chargeant `music.youtube.com/watch?v=…`
obtient bien `playabilityStatus: OK` + `streamingData` sans compte connecté.
Mais en interceptant la requête `/player` réellement émise par la page, son
corps JSON **ne contient aucun `serviceIntegrityDimensions.poToken`** — la
piste PoToken n'était donc pas non plus le bon axe d'investigation pour ce
point précis.

**Le vrai obstacle, identifié par diff du corps de requête réel vs notre
client existant : `playbackContext.contentPlaybackContext.signatureTimestamp`
("sts"), absent de `innertubeClient.ts` avant ce correctif.** Vérifié par
ablation méthodique (retrait de champs un par un du corps réel capturé, en
Node `fetch` pur, sans navigateur) :

| Corps de requête testé | Résultat |
|---|---|
| Corps minimal (`context.client` seul, comme avant ce correctif) | `UNPLAYABLE` |
| + `playbackContext.contentPlaybackContext` complet | `OK` + `streamingData` |
| + `signatureTimestamp` **seul** (sans `referer`/`html5Preference`/…), sans cookie, sans `visitorData`, avec l'ancien `clientVersion` déjà présent dans le code (`1.20241201.01.00`, périmé de plus d'un an) | `OK` + `streamingData`, sur un `videoId` jamais touché par un navigateur |

`sts` est un entier en clair embarqué dans le lecteur JS courant
(`signatureTimestamp:20684` trouvé littéralement dans `base.js` par une
simple regex) — aucune VM, aucun sandbox, aucun BotGuard. Il change à chaque
déploiement du lecteur ; `src/providers/youtube-music/signatureTimestamp.ts`
le récupère et le cache (~6h) en conséquence.

**Mais ce correctif ne suffit pas à éliminer yt-dlp.** Sur l'échantillon de
morceaux testés (recherche réelle "Linkin Park Numb" + résultats liés),
**100% des formats retournés (audio et vidéo, adaptatifs et progressifs,
tous itags observés : 133-137, 140, 160, 242-251, 278, 18) portent
`signatureCipher` et aucun `url` en clair.** Le flux n'est donc jamais
directement jouable sans déchiffrer la signature — un algorithme propre à
chaque version de `base.js`, obfusqué (code compilé Closure avec table de
chaînes indexée, pas le schéma classique `a.split("");X.Y(a,n);…`), qui
change à chaque déploiement du lecteur YouTube. C'est une surface de reverse
engineering aussi mouvante que BotGuard, pas un correctif ponctuel — yt-dlp
la maintient déjà pour de vrai (équipe dédiée, mises à jour continues).
Tenter de la réimplémenter ici aurait produit un code fragile, cassé au
prochain déploiement YouTube, en violation de la règle §87.4 du plan ("ne
pas réinventer une librairie mature").

**Conclusion actuelle, honnête :** `signatureTimestamp` corrige une erreur
de diagnostic réelle (le blocage n'était pas un mur BotGuard infranchissable
comme conclu le 2026-09-01) et rapproche InnerTube anonyme d'un
fonctionnement complet, mais le déchiffrement de signature reste un morceau
non résolu ici, délibérément laissé à yt-dlp. `tryInnertube()` dans
`playbackResolver.ts` reste tenté en premier (utile si YouTube sert un jour
un `url` en clair pour un format donné) mais retombe systématiquement sur le
repli yt-dlp dans l'état actuel constaté. yt-dlp reste donc la seule
solution qui fonctionne réellement de bout en bout pour obtenir un flux
audio jouable.

## Prochaine étape (non faite)

Déchiffrement de `signatureCipher` (et probablement le paramètre `n` de
limitation de débit, non exploré ici puisque le blocage se situe avant) :
nécessiterait soit une réimplémentation continuellement mise à jour de
l'algorithme de désobfuscation de `base.js` (coût de maintenance récurrent,
déconseillé par §87.4), soit l'exécution partielle du `base.js` réel dans un
bac à sable JS pour en extraire la fonction de déchiffrement dynamiquement.
Les deux options restent un sous-projet à part entière, pas un correctif —
à réévaluer si le coût de yt-dlp (packaging Docker, subprocess) devient
bloquant.
