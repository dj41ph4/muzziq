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

## 2026-09-03 — tentative réelle de déchiffrement `signatureCipher`, conclusion négative honnête

Objectif : éliminer yt-dlp en déchiffrant `signatureCipher` côté serveur
(Node), en étudiant la stratégie générale documentée publiquement par des
extracteurs indépendants (yt-dlp — Unlicense, code consulté pour la
*méthode* uniquement, jamais copié — et, pour l'architecture seulement,
Metrolist — GPL-3.0, lu sans jamais copier de code, conformément à la
§33 "RÈGLE METROLIST"). Aucun code de l'un ou l'autre n'a été traduit ou
collé dans ce dépôt.

**Étape 1 — récupération du vrai `base.js` courant.** Même méthode que
`signatureTimestamp.ts` (HTML de `music.youtube.com/` → chemin
`/s/player/.../base.js`) : `/s/player/9470c977/player_es6.vflset/nl_NL/base.js`,
2 588 733 caractères, récupéré réellement le 2026-09-03.

**Étape 2 — recherche du pattern classique documenté publiquement (yt-dlp,
ytdl-core, NewPipeExtractor) : une fonction courte de la forme
`function(a){a=a.split("");OBJ.xx(a,N);...;return a.join("")}` qui
enchaîne 3-4 permutations (swap, reverse, slice).** Recherche exhaustive par
regex sur l'intégralité du fichier : **aucune occurrence.** `split("")`
n'apparaît que 9 fois dans tout le fichier, aucune dans un contexte de
déchiffrement de signature (utilitaires `Array.prototype` génériques,
fonctions d'encodage `application/x-www-form-urlencoded`, génération d'ID
Redux). `"&sig="`, `.get("s")`, `"sp"`, `decodeURIComponent(c...)` : zéro
occurrence.

**Étape 3 — hypothèse d'une obfuscation par table de chaînes indexée
(property renaming Closure via tableau `p[N]`), évoquée le 2026-09-02 :
vérifiée et écartée.** Une chaîne `var p="indexOf;length;...;signatureCipher;
...;slice;...;splice;..."` existe bien en tête de fichier, mais son seul
usage retrouvé (`redirector.googlevideo.com`, `.a1.googlevideo.com$`,
`rr?[1-9].*\.c\.youtube\.com$`) est une liste de motifs de validation de
noms d'hôte pour les serveurs `googlevideo.com`, sans rapport avec le
déchiffrement de signature. Fausse piste écartée par lecture directe du
contexte d'usage, pas par supposition.

**Étape 4 — recherche directe des points d'usage de `adaptiveFormats`/
`signatureCipher` dans le pipeline de lecture réel.** `signatureCipher`
n'apparaît que 2 fois dans tout le fichier (aucune n'est un déchiffrement —
l'une remet le champ à `""` pour un cas DASH live, l'autre est un nom de
classe de streaming sans rapport). En remontant le pipeline
`adaptiveFormats` (10 occurrences), le code qui consomme les formats
vérifie directement `l.url` (`if(!l||!l.url){...}`) et référence
`sabrContextUpdates`, `botguardData` — signes que le player web actuel
s'appuie en pratique sur SABR/UMP (Server-Allocated-Bandwidth-Routing,
protocole serveur récent de YouTube pour la diffusion adaptative) plutôt
que sur le schéma classique "une URL par format, signée côté client" pour
lequel le déchiffrement `signatureCipher` a été historiquement documenté.
Piste non confirmée à 100 % (l'exploration du binaire WASM associé
[`AES128CTRCipher_*`, visiblement lié à ce protocole] sort du périmètre
raisonnable de cette session) mais cohérente avec l'absence totale du
pattern classique : ce n'est pas un algorithme simplement plus obfusqué,
c'est vraisemblablement une architecture de livraison différente de celle
pour laquelle la technique de déchiffrement générique a été conçue.

**Conclusion honnête :** la tentative de déchiffrement `signatureCipher`
côté serveur MuzziQ est réelle, documentée, mais **négative** dans le temps
raisonnable de cette session. Ce n'est pas un abandon par manque d'effort :
quatre pistes concrètes ont été vérifiées par lecture directe du vrai
`base.js` (pattern classique, table de chaînes, points d'usage réels du
pipeline de formats) et aucune n'a abouti à une fonction de déchiffrement
exploitable en un temps raisonnable. Exécuter le `base.js` réel dans un
bac à sable JS (Node `vm`) resterait théoriquement possible mais impliquerait
de reconstituer un environnement navigateur suffisant (`window`, `document`,
`WebAssembly`, timers, etc.) pour que ce fichier de 2,5 Mo s'initialise sans
planter — un effort du même ordre que celui déjà tenté et abandonné pour
PoToken/BotGuard via jsdom (voir 2026-09-02 ci-dessus), pour un résultat
non garanti si l'hypothèse SABR/UMP se confirme (dans ce cas il n'y aurait
tout simplement plus d'URL signée unique à déchiffrer par ce mécanisme).

**yt-dlp reste donc la seule solution qui fonctionne réellement de bout en
bout** pour obtenir un flux audio jouable. `playbackResolver.ts` n'est pas
modifié : `tryInnertube()` reste tenté en premier (ne coûte rien, redevient
utile si YouTube sert un jour une URL en clair) et retombe systématiquement
sur `ytDlpResolver.ts`.

## Prochaine étape (non faite)

Déchiffrement de `signatureCipher` : au vu de l'investigation du
2026-09-03 ci-dessus, la piste la plus réaliste n'est plus "extraire et
réimplémenter un algorithme de permutation" (le pattern classique documenté
publiquement pour ce cas ne s'applique visiblement plus au player web
`WEB_REMIX` actuel) mais l'une de :
- confirmer/infirmer l'hypothèse SABR/UMP en interceptant réellement le
  trafic réseau d'un vrai navigateur (Playwright, comme le 2026-09-02) sur
  la lecture d'un morceau, pour voir si une requête `signatureCipher`
  classique existe encore ailleurs (ex. client `IOS_MUSIC`/`ANDROID_MUSIC`
  plutôt que `WEB_REMIX`) ;
- exécuter le `base.js` réel dans un bac à sable Node `vm` avec des stubs
  navigateur minimaux, en acceptant le coût d'ingénierie (comparable à la
  tentative BotGuard abandonnée) ;
- réévaluer entièrement si le coût de yt-dlp (packaging Docker, subprocess)
  devient réellement bloquant — dans l'état actuel il ne l'est pas
  (§105.9, Docker déjà fonctionnel avec yt-dlp inclus).
Sous-projet à part entière, pas un correctif ponctuel.
