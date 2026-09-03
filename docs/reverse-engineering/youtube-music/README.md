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

## 2026-09-03 (suite) — le déchiffrement est réellement résolu ; un nouveau mur découvert après

Reprise de l'investigation avec un objectif explicite : ne pas s'arrêter à la
première piste négative, aller jusqu'à un résultat réel (flux audio jouable,
prouvé par un `HTTP 200`/`206` + `Content-Type: audio/*`), en suivant l'ordre
de pistes suggéré par l'étude d'architecture de Metrolist
(`metrolist-analysis.md`, recherche pure, aucun code copié) : (a) configs
distantes pré-résolues par hash de player, puis (b) exécution JS réelle en
bac à sable si (a) ne suffit pas.

### (a) Registre communautaire pré-résolu (`ZemerTeam/zemer-cipher`) — testé, partiellement exploitable

`metrolist-analysis.md` documente que Metrolist consulte un registre JSON
public tenu par un projet tiers, `ZemerTeam/zemer-cipher`
(`https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json`),
qui associe à chaque hash de `base.js` une formule `"sig":"NOM(v,q,INPUT)"` —
les deux constantes numériques qui sélectionnent la bonne branche dans le
lecteur obfusqué de cette version précise. Récupéré réellement le
2026-09-03 : **le hash du player couramment servi par music.youtube.com
(`9470c977`) y figure bel et bien**, avec l'entrée
`{"sig":"Of(2,137,INPUT)","nClass":"wO","sts":20696,"aliases":["9f1ba9db"]}`
— et son `sts` (20696) correspond exactement à celui extrait indépendamment
par `signatureTimestamp.ts` sur ce même build, ce qui confirme que
l'entrée correspond bien au fichier réellement servi.

**Mais cette formule n'a pas pu être appliquée telle quelle.** Le nom `Of`
qu'elle porte ne correspond à aucune fonction du `base.js` réellement
téléchargé (vérifié : aucune déclaration `Of=function` n'existe dans le
fichier — `Of` y désigne autre chose, une propriété interne sans rapport).
Le registre Zemer est manifestement généré par l'outillage propre de ce
projet tiers (probablement une analyse structurelle/AST, pas une simple
recherche de nom), et le nom qu'il stocke n'est donc utile qu'à *leur*
solveur, pas portable tel quel sans reproduire leur méthode d'extraction —
que ce rapport n'a pas cherché à rétro-ingénierier (hors périmètre : ça
reviendrait à porter leur algorithme, pas à observer un comportement).
Cette piste (a) est donc **une donnée réelle et vérifiable, mais pas
directement exploitable ici sans réimplémenter la méthode d'extraction du
projet tiers** — un axe honnête à retenter plus tard si le besoin
d'éliminer yt-dlp devient prioritaire, pas un échec de principe.

### (b) Déchiffrement réel du `signatureCipher` — RÉSOLU et vérifié

En parallèle, analyse manuelle directe du `base.js` réel (hash `9470c977`,
2 588 733 caractères, `music.youtube.com` et `www.youtube.com` servent
strictement le même fichier — vérifié par comparaison octet à octet).
Contrairement à la conclusion (trop rapide) du 2026-09-03 plus haut, le
pattern classique documenté publiquement (`split("")` littéral) n'a pas
disparu : il est simplement caché derrière deux couches d'obfuscation
combinées jamais rencontrées dans les extracteurs publics de référence
consultés lors de cette investigation :

1. **Une table de chaînes indexée** (`var p="indexOf;length;...;split;...;
   reverse;...".split(";")`) : chaque nom de propriété/méthode réel
   (`"split"`, `"join"`, `"reverse"`, `"splice"`) est remplacé par `p[N]`,
   `N` étant lui-même souvent un XOR (`p[y^2373]`) plutôt qu'un littéral.
2. **Un dispatcher unique par « control-flow flattening »** : une seule
   fonction à 4 paramètres (`P2(v,q,x,X)` dans ce build) sert à des dizaines
   d'usages sans rapport entre eux ; le paramètre `v`, combiné à des
   conditions bit à bit (`(v+2&62)<v&&(v+2^23)>=v`), sélectionne laquelle
   des branches internes s'exécute réellement pour un appel donné.

Après extraction manuelle des trois éléments réels (la table `p`, un objet à
trois méthodes `swap`/`splice`/`reverse` — `HR`/`hC`/`ET` dans ce build —,
et le dispatcher qui les enchaîne), et localisation du point d'appel réel
pour un format (`M.s`/`M.sp`, les noms de champs de l'API InnerTube
elle-même, stables par nature), la séquence d'opérations réelle
s'est révélée être : `reverse(s)` puis `splice(s,0,1)` puis
`swap(s,0,3)` puis `swap(s,0,12)` — exactement la même famille d'opérations
que celle documentée publiquement depuis des années pour ce problème, sous
deux couches d'obfuscation en plus.

**Double vérification indépendante, pas une seule** : (1) réimplémentation
manuelle en TypeScript de cette séquence, et (2) extraction du code source
réel (la table, l'objet à trois méthodes, le dispatcher) et exécution
littérale — pas retranscrite — dans un bac à sable Node (`vm`), sans aucune
réécriture manuelle des opérations. **Les deux méthodes produisent un
résultat strictement identique, caractère pour caractère**, sur plusieurs
échantillons réels de `signatureCipher` capturés en direct. Le résultat a
en plus la même structure/préfixe (`AE0s2JYwR...`) qu'une signature obtenue
via yt-dlp pour la même vidéo au même moment — cohérence forte avec un
déchiffrement correct (une permutation fausse produirait un résultat sans
rapport avec ce préfixe attendu, pas un résultat qui lui ressemble par
hasard).

Ce déchiffrement est implémenté dans `src/providers/youtube-music/
signatureCipher.ts`, avec une extraction **structurelle** (regex sur la
*forme* du code : table `var X="...".split(";")`, objet à trois méthodes
`swap`/`splice`/`reverse`, dispatcher `function(v,q,x,X){var y=q^v;...}` qui
référence cet objet, point d'appel réel via les champs `.s`/`.sp`) plutôt
que sur les noms de variables minifiés du jour (`p`, `C8`, `P2` au moment de
l'écriture) — ces noms changeront au prochain déploiement, la forme du code
beaucoup moins vite. Testé et vérifié : cette extraction structurelle
retrouve d'elle-même exactement les mêmes `p`/`C8`/`P2`/`(69, 2315)` que
l'analyse manuelle, sur le fichier réellement téléchargé en direct (pas
seulement sur une copie locale figée).

### Le nouveau mur : le déchiffrement est correct, la requête média est quand même rejetée (403)

Malgré une signature vérifiée correcte par deux méthodes indépendantes, la
requête HTTP finale vers `googlevideo.com` avec cette signature renvoie
**systématiquement `403`**, testé de façon répétée et honnête (pas
abandonné à la première tentative) :
- sur plusieurs vidéos différentes (dont une vidéo grand public sans aucune
  restriction connue) ;
- sur les deux `clientName` qui exposent un `signatureCipher` classique
  (`WEB_REMIX`, `MWEB`) ;
- avec et sans cookies de session (page d'accueil + page de la vidéo
  chargées au préalable pour obtenir un cookie réel avant l'appel `/player`
  et avant la requête média) ;
- avec et sans les paramètres `n`/`sefc` d'origine.

Pendant ce temps, au même instant, depuis la même IP, une URL obtenue via
`yt-dlp` (qui a choisi de lui-même le client `VISIONOS` lors de ces tests)
**fonctionne** (`206` + `Content-Type: audio/webm`) — ce qui exclut un
problème d'environnement (IP bannie, réseau bloqué) et pointe vers quelque
chose de spécifique aux profils `WEB_REMIX`/`MWEB` en ce moment précis.
Cohérent avec `metrolist-analysis.md` : le catalogue de clients
d'`innertubex` marque justement plusieurs profils `BROKEN`/`PROBE_ONLY`
« parce que les réponses de ces profils ne contiennent plus que des
métadonnées SABR sans URL directe exploitable, ou nécessitent une
attestation native indisponible » — la cause précise ici (validation
côté CDN propre à la requête média, indépendante de la signature ?
empreinte de session/navigateur manquante ?) n'a pas pu être confirmée avec
certitude dans le temps de cette session, mais le symptôme (signature
correcte, requête quand même rejetée, alors qu'un autre profil client
fonctionne) correspond à ce que Metrolist documente lui-même avoir constaté
empiriquement.

### Décision et état du code

Le déchiffrement (`signatureCipher.ts`) est **conservé dans le dépôt mais
non branché dans `playbackResolver.ts`** : il ajouterait une latence réelle
(fetch de `base.js` + appel `/player`) pour un chemin qui, vérifié
honnêtement, échoue systématiquement à l'étape suivante dans l'état actuel
constaté. Il est gardé car (a) c'est un résultat réel et indépendamment
vérifiable (le déchiffrement en lui-même, pas le mur suivant), (b) il
devient immédiatement exploitable sans rien réécrire si ce blocage CDN se
lève ou change de profil client (situation qui, d'après le rythme de
correctifs documenté dans `metrolist-analysis.md` — plusieurs fois par
semaine côté Metrolist/innertubex — change réellement dans le temps), et
(c) l'abandonner sans trace aurait fait perdre ce travail à la prochaine
session. **yt-dlp reste donc, à ce jour, la seule solution qui produit
réellement un flux audio jouable de bout en bout, vérifié par un `HTTP
200`/`206` + `audio/*` réel.** `playbackResolver.ts` n'est pas modifié.

## Prochaine étape (non faite)

- Comprendre précisément la cause du `403` sur la requête média
  `WEB_REMIX`/`MWEB` malgré une signature correcte (interception réseau
  d'un vrai navigateur avec Playwright, comme le 2026-09-02, mais cette
  fois sur la requête `googlevideo.com` elle-même plutôt que sur `/player`,
  pour voir ce qu'un vrai navigateur envoie de plus).
- Explorer d'autres profils client via `/player` directement (au-delà de
  `WEB_REMIX`/`MWEB`/`ANDROID_MUSIC`/`IOS_MUSIC` déjà sondés) pour trouver
  un profil qui expose un `signatureCipher` classique **et** dont la
  requête média n'est pas rejetée — sans les constantes internes exactes de
  clients natifs comme `VISIONOS` (que yt-dlp gère mais que ce rapport n'a
  pas cherché à reproduire), ce qui reviendrait à redécouvrir leur
  catalogue de contextes client par essais successifs.
- Réévaluer si le coût de yt-dlp (packaging Docker, subprocess) devient
  réellement bloquant — dans l'état actuel il ne l'est pas (§105.9, Docker
  déjà fonctionnel avec yt-dlp inclus).
Sous-projet à part entière, pas un correctif ponctuel.
