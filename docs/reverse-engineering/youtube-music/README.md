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

## 2026-09-03 (suite 2) — cause du 403 identifiée avec certitude : PoToken + UMP, pas le paramètre `n`

Reprise du "Prochaine étape" laissée ouverte plus haut, dans l'ordre indiqué par la
consigne de cette session : d'abord le paramètre `n` (piste jugée la plus probable a
priori), puis les headers/cookies/empreinte réseau, puis interception d'un vrai
navigateur sur la requête média elle-même (pas seulement `/player`).

### Le paramètre `n` : vérifié, ce n'est PAS la cause

Recherche exhaustive dans le `base.js` réel (hash `9470c977`, même build que la
section précédente) du point d'usage réel qui construit les URLs finales à partir
des formats retournés par `/player` : la fonction `nix` (nom minifié du jour), qui
pour chaque format avec un `signatureCipher` calcule la signature déchiffrée
(confirmé : mêmes constantes `(69, 2315)` que la section précédente, via une couche
supplémentaire `$l(36,7009,·)`/`wU(2,2722,·)` qui se résout, après lecture directe de
leur corps, à un simple `decodeURIComponent`/`encodeURIComponent` — aucune
transformation de plus sur la signature elle-même) puis fusionne quelques paramètres
supplémentaires (`cpn`, `c`, `cver`, …) dans l'URL. **Aucune fonction de
transformation du paramètre `n` n'existe nulle part dans cette fonction ni ailleurs
dans le fichier** — recherche par plusieurs regex publiquement documentées
(`.get("n")`, `.set("n",`, motifs classiques yt-dlp) : zéro occurrence, seule
occurrence de `.get("n")` trouvée dans tout le fichier concerne un cas HLS sans
rapport (réécriture de segment de chemin `/n/{id}`, pas le paramètre de requête).
Confirmé empiriquement : retirer entièrement le paramètre `n` d'une URL par ailleurs
signée correctement ne change strictement rien au `403` (toujours `403`, corps vide,
identique avec ou sans `n`). **Conclusion : pour ce build du lecteur WEB_REMIX, il
n'y a simplement pas de transformation client du paramètre `n` à faire — l'hypothèse
prioritaire de cette session était donc fausse, mais utile à avoir vérifiée
concrètement plutôt que supposée.**

### Headers, cookies, empreinte TLS/navigateur : vérifiés, ce n'est PAS la cause non plus

Testé méthodiquement avec un vrai Chromium headless (Playwright, même méthode que le
2026-09-02) plutôt que seulement `fetch` Node :
- Requête média rejouée avec cookies réels obtenus après chargement de
  `music.youtube.com/` (page d'accueil) : toujours `403`.
- Sans `Range`, avec `Range`, avec un jeu complet de headers `Sec-Fetch-*`/
  `Origin`/`Referer` imitant un vrai navigateur : toujours `403`, identique.
- **Décisif : la même URL, avec la même signature, rejouée depuis une vraie page
  Chromium (pas Node) via `fetch()` exécuté dans le contexte de la page
  `music.youtube.com/watch?v=…` elle-même (donc même pile TLS, même empreinte
  navigateur, même IP) — toujours `403` identique.** Ceci élimine complètement
  l'hypothèse d'une empreinte réseau/TLS/navigateur : ce n'est pas "Node se fait
  repérer", un vrai navigateur avec la vraie URL signée obtient exactement le même
  rejet.

### La vraie cause, confirmée par comparaison directe avec une requête réelle qui fonctionne

Le blocage précédent ("aucune requête `googlevideo.com` capturée") venait d'abord
d'un mur de consentement RGPD (`consent.youtube.com`, IP de test dans l'UE) puis d'un
écran "navigateur non supporté" (UA par défaut de Playwright trop ancien) — les deux
contournés (clic sur le bouton de consentement néerlandais "Alles accepteren",
`User-Agent` Chrome 130 récent forcé). Une fois la vraie page chargée et la lecture
réellement déclenchée, Playwright a capturé les vraies requêtes média émises par le
lecteur WEB_REMIX authentique. Différences structurelles nettes avec ce que MuzziQ
construisait :

1. **Méthode `POST`, pas `GET`** (corps vide dans ce cas, mais méthode POST).
2. **Paramètre `pot=` (PoToken) présent** — absent de toute URL construite par
   MuzziQ jusqu'ici.
3. **Paramètre `ump=1`** (+ `srfvp=1`, `alr=yes`, `rn=`/`rbuf=`/`range=` au lieu
   d'un header HTTP `Range`) — confirme concrètement l'hypothèse SABR/UMP déjà
   évoquée dans la section précédente sur la seule base d'indices indirects
   (`sabrContextUpdates`, `botguardData` vus dans le pipeline de formats).
4. **`cver=1.20260901.12.00`**, très éloigné du `clientVersion` codé en dur dans
   `innertubeClient.ts` (`1.20241201.01.00`, plus de 9 mois d'écart) — MuzziQ
   annonce un client obsolète au moment de l'appel `/player`, ce qui n'a pas
   empêché `/player` de répondre `OK` mais peut faire partie des signaux vérifiés
   au moment de la requête média.

**Test décisif, celui qui tranche vraiment** : l'URL réelle capturée (avec son
`pot`, son `sig`, son `n` intacts) a été rejouée **en dehors de toute session
navigateur**, par un script Node `fetch()` nu, sans cookie, sans `Origin`, sans
`Referer`, en `GET` comme en `POST` : **`HTTP 200` dans les deux cas**, avec un vrai
corps binaire de 66176 octets (vérifié par lecture réelle du buffer, pas seulement
le code de statut). Ceci prouve directement, par élimination, que **le seul
ingrédient qui manquait à toutes les tentatives précédentes de cette session est le
`pot` (PoToken)** — ni les headers, ni les cookies, ni l'empreinte réseau, ni la
méthode HTTP, ni `n`, ne sont en cause : une fois le `pot` présent, même un GET nu
depuis un script sans aucun contexte de session fonctionne.

**Nuance importante, pour rester honnête sur ce qui est "vraiment" résolu** : le
`Content-Type` de cette réponse `200` est `application/vnd.yt-ump`, **pas**
`audio/webm` ou un `audio/*` classique — le corps est un flux encapsulé dans le
protocole binaire UMP de YouTube (segmentation/framing propriétaire), pas de l'audio
brut directement lisible. Obtenir un flux réellement décodable demanderait en plus
un parseur UMP (format non documenté publiquement de façon stable, sujet à
changement comme tout ce qui touche SABR d'après `metrolist-analysis.md`) — un
chantier distinct de la seule obtention du `pot`.

### Pourquoi ce n'est toujours pas branché dans MuzziQ malgré une cause désormais certaine

Le `pot` lui-même ne s'obtient, de façon vérifiée dans ce dépôt, que par deux voies :
- **Pure Node/jsdom (`bgutils-js`)** : déjà testé et cassé en pratique le
  2026-09-02 (voir plus haut — `OutOfMemory`, jetons de mauvaise longueur, jsdom
  détecté comme non-navigateur par le challenge BotGuard).
- **Vrai navigateur headless (Playwright/Chromium)** : **fonctionne réellement**,
  vérifié dans cette session (un `pot` valide a été obtenu et son usage confirmé
  par un `200` réel comme démontré ci-dessus). Mais cela veut dire embarquer un
  Chromium complet (~300 Mo) comme dépendance d'exécution du serveur MuzziQ pour
  chaque résolution de flux, plus un solveur UMP pour exploiter le résultat — une
  dépendance et une complexité largement supérieures à yt-dlp (déjà présent,
  déjà packagé en Docker, déjà fiable de bout en bout). Ce n'est plus "corriger le
  403", c'est reconstruire l'équivalent du pipeline PoToken+UMP que `innertubex`
  (Metrolist) implémente sur plusieurs modules dédiés avec un rythme de correctifs
  de plusieurs fois par semaine (`metrolist-analysis.md`, §3) — un sous-projet à
  part entière, pas un correctif ponctuel, et une charge de maintenance récurrente
  que ce rapport ne recommande pas d'engager tant que yt-dlp fonctionne.

**Conclusion honnête et définitive de cette investigation** : la cause du `403` est
identifiée avec certitude (`pot` manquant ; `n` et les headers étaient des fausses
pistes, désormais éliminées par test réel plutôt que supposées). Ce n'est pas un
abandon prématuré — la cause est trouvée, prouvée par un vrai `HTTP 200` reproductible
en dehors de toute session. Mais exploiter cette découverte demanderait d'ajouter une
dépendance Chromium au runtime serveur et un parseur UMP, ce qui n'est pas fait dans
cette session (hors du périmètre "corriger le 403", nouveau sous-projet à évaluer
séparément). `signatureCipher.ts` n'est donc toujours pas branché dans
`playbackResolver.ts` ; yt-dlp reste la seule solution qui produit un flux `audio/*`
réellement jouable de bout en bout, vérifié par un vrai `HTTP 200`/`206`.

## 2026-09-03 (suite 3) — chantier PoToken + UMP engagé, résultat réel mais partiel

Reprise explicite de la "Prochaine étape" ci-dessus : le coût d'un Chromium headless
embarqué a été jugé acceptable (voir mesures ci-dessous) et le chantier a été engagé
jusqu'au bout — génération réelle de PoToken via Playwright, parseur UMP, intégration
dans `playbackResolver.ts`. Résultat honnête : **ça marche réellement, mais seulement
pour une partie des pistes** — voir "Limite non résolue" plus bas avant de considérer
ce chemin comme un remplacement de yt-dlp.

### PoToken via Chromium headless persistant — fonctionne, mais l'hypothèse de départ (un jeton réutilisable, caché plusieurs heures) était fausse

Testé réellement (Playwright, Chromium headless, `music.youtube.com/watch?v=…`,
consentement RGPD géré, lecture déclenchée, requête média réelle interceptée) : le
`pot` obtenu fonctionne (repli sur la preuve déjà apportée le 2026-09-03 plus haut).
**Mais l'idée initiale de ce chantier — mettre un seul `pot` en cache plusieurs heures
comme `signatureTimestamp.ts` le fait pour `sts` — s'est révélée fausse à l'usage**,
vérifié par deux tests indépendants : deux vidéos différentes capturées (a) dans deux
pages séparées de la même session, et (b) l'une après l'autre sur la **même** page
(navigation successive, pas de nouvelle page) produisent chaque fois deux `pot`
différents. Le `pot` est donc lié à la navigation/au contenu, pas seulement à la
session — cohérent avec ce que documente indépendamment `metrolist-analysis.md`
(§ PoToken) : Metrolist appelle `obtainPoToken(identifier)` par vidéo ou par
`visitorData` selon le profil client, jamais un jeton unique valable pour tout.

Ce qui EST réutilisable et mis en cache (`globalThis`, règle #2) dans
`src/providers/youtube-music/poTokenBrowser.ts` : le **processus Chromium**
lui-même, pas une valeur de jeton. Mesuré réellement plusieurs fois sur ce poste,
navigateur déjà chaud : naviguer vers une nouvelle vidéo et capturer sa requête
média réelle prend **environ 1,5 à 3 secondes** — pas les dizaines de secondes d'un
lancement de navigateur à froid. C'est ce qui rend ce chemin praticable comme étape
de résolution de lecture (comparable au coût déjà accepté du sous-processus yt-dlp).

### Parseur UMP minimal — framing confirmé, un plafond serveur découvert par capture réelle

`src/providers/youtube-music/umpParser.ts`. Structure confirmée par décodage réel
(pas supposée) : chaque « part » est `[varint type][varint size][size octets de
payload]`, `varint` en LEB128 standard — confirmé en décodant une vraie part de type
20 dont le contenu protobuf contient littéralement l'ID vidéo demandé en texte clair
et l'`itag` demandé (valeur retrouvée exacte dans l'URL de la requête — coïncidence
exclue). La part de type 21 (« MEDIA ») contient directement les octets bruts du
conteneur (vérifié : nombre magique EBML `1A 45 DF A3` d'un WebM valide).

**Découverte réelle non anticipée** : le champ `size` d'une part MEDIA ne correspond
pas de façon fiable au nombre d'octets réellement présents dans la réponse HTTP en
cours — vérifié sur deux captures indépendantes de tailles très différentes. Le
parseur prend donc, pour la dernière part MEDIA rencontrée, tout ce qu'il reste
réellement dans le buffer plutôt que de se fier à `size` — la vérification de
complétude finale se fait uniquement contre `clen` (le seul champ fiable, dans l'URL
elle-même), avec une tolérance de 16 octets déterminée empiriquement (un écart de
quelques octets a été observé même sur une extraction confirmée intégralement
décodable par `ffmpeg`/`ffprobe`, sans qu'une explication certaine ait été trouvée
dans le temps de cette session — une vraie troncature se chiffre en centaines de
milliers d'octets, jamais dans cet ordre de grandeur, donc cette tolérance ne peut
pas masquer un flux réellement incomplet).

### Vérification réelle de bout en bout — succès, pas fabriqué

Piste courte réelle trouvée via une recherche InnerTube (`nomg-oeSYoE`, "Happy
Birthday (Short Version)", 17,321s, `clen`=283941) :
- `GET http://localhost:9910/api/youtube-music/stream/nomg-oeSYoE` → **`HTTP 200`
  réel, `Content-Type: audio/webm`, 283 944 octets réels.**
- `ffprobe` : flux Opus, 48kHz, stéréo, `duration=17.321000` — **identique** au `dur`
  annoncé par l'URL YouTube d'origine.
- `ffmpeg -i … -f null -` : **code de sortie 0, aucune erreur** ("File ended
  prematurely" absent — contrairement à la première tentative de cette session sur
  une piste plus longue, voir plus bas) : décodage complet, réel, vérifié.

Chemin complet emprunté : `resolveYoutubeMusicPlayback()` → `tryPotUmp()` →
`resolveViaPotUmp()` → `poTokenBrowser.captureAudioPlaybackUrl()` (Chromium réel) →
`fetch()` avec `range=0-{clen-1}` → `umpParser.parseUmpMedia()` → mis en cache → servi
par `/api/youtube-music/stream/[videoId]/route.ts`. Aucune étape simulée ou
court-circuitée pour cette vérification.

### Limite non résolue, honnête : ne fonctionne que pour les pistes courtes

Avant d'arriver au résultat ci-dessus, plusieurs heures d'inspection hexadécimale
manuelle ont été passées à essayer de reconstituer un flux **long** (`dQw4w9WgXcQ`,
~3:33, `clen`=3 433 755 octets pour la piste audio) en suivant plusieurs parts MEDIA
successives. Constat réel et vérifié : au-delà d'un plafond observé identiquement sur
deux captures indépendantes de contenus différents (environ 2 097 105 octets), les
parts suivantes (types réellement rencontrés : 29, 33, 43, une seconde part de type
20 avec une taille déclarée manifestement incohérente) n'ont pas pu être décodées de
façon fiable — soit leur taille déclarée dépasse ce qu'il reste réellement dans la
réponse d'une façon qui ne se résout pas en "prendre le reste du buffer" comme pour
la dernière part MEDIA, soit le point atteint après leur saut ne ressemble plus à un
en-tête de part valide. Extraire uniquement la première part dans ce cas donne un
fichier WebM/Opus **valide mais tronqué** (vérifié : `ffprobe` y lit correctement le
flux et une durée totale exacte embarquée dans les métadonnées du conteneur, mais
`ffmpeg -f null -` s'arrête avec "File ended prematurely" — décodage réel jusqu'au
point de troncature, pas au-delà).

**Décision délibérée, appliquée dans le code** : `parseUmpMedia()` ne déclare
`complete: true` que si le total extrait correspond (à la tolérance de 16 octets
près) à `clen` — jamais par optimisme. `resolveViaPotUmp()` renvoie `null` si
`complete` est faux, et `tryPotUmp()` dans `playbackResolver.ts` retombe alors sur
yt-dlp, exactement comme si ce chemin n'existait pas. **Aucun flux tronqué n'est
jamais servi comme s'il était complet.** Conséquence pratique : ce chemin ne produit
un résultat utilisable que pour des pistes dont la totalité tient dans une seule part
MEDIA — en pratique, l'équivalent d'environ deux minutes à un débit Opus typique
(128 kbps) — donc une minorité des morceaux réels d'une bibliothèque musicale.
`playbackResolver.ts` évite d'ailleurs de payer le coût du navigateur headless pour
les pistes visiblement trop longues : `contentLength` est déjà connu via la réponse
InnerTube (sans PoToken) obtenue juste avant, et sert de garde-fou
(`POT_UMP_SIZE_GUARD_BYTES`) pour ne tenter ce chemin que quand il a une vraie chance
d'aboutir.

### Pourquoi ne pas avoir cherché plus longtemps à résoudre le cas long

Cette limite n'est pas un abandon par manque d'effort — c'est une décision consciente
après un temps raisonnable de rétro-ingénierie manuelle sans résultat certain (voir
détail dans l'historique de session), pour éviter exactement l'écueil déjà rencontré
dans ce rapport (2026-09-01, conclusion tirée trop vite puis corrigée) : mieux vaut un
résultat partiel vérifié et honnêtement borné qu'un parseur "presque bon" qui
fabriquerait un flux incorrect pour la majorité des morceaux réels. yt-dlp reste donc,
pour toute piste de longueur normale, la seule solution qui produit réellement un flux
`audio/*` complet et jouable de bout en bout — ce chemin PoToken/UMP est un
complément réel pour les pistes courtes, pas un remplacement.

### Fichiers

- `src/providers/youtube-music/poTokenBrowser.ts` — Chromium headless persistant
  (`globalThis`), capture de l'URL média réelle par interception réseau.
- `src/providers/youtube-music/umpParser.ts` — parseur UMP minimal (voir plus haut).
- `src/providers/youtube-music/potUmpResolver.ts` — orchestration + cache de
  quelques minutes des octets extraits.
- `src/app/api/youtube-music/stream/[videoId]/route.ts` — sert ces octets avec un
  vrai `Content-Type: audio/*`.
- `playbackResolver.ts` — nouveau niveau `tryPotUmp()`, entre InnerTube et yt-dlp,
  gardé par `POT_UMP_SIZE_GUARD_BYTES`.

## Portabilité Android — ce chantier ne se porte PAS tel quel, et pourquoi

Consigne explicite de la session qui a mené ce chantier : le but final n'est pas que
le serveur MuzziQ seul sache lire YouTube Music, c'est que l'app Android future
puisse le faire **sans serveur**. Un Chromium headless côté serveur Node (Playwright)
n'est **pas** portable à Android tel quel — pas de binaire Playwright/Chromium sur
mobile, et même si un tel binaire existait, l'app Android ne doit pas embarquer un
Chromium complet (~300-400 Mo) pour ce seul usage.

`metrolist-analysis.md` (§ PoToken) documente comment Metrolist résout un problème
équivalent sur Android : **une `WebView` Android embarquée**, pas un navigateur
serveur — un asset HTML local (`po_token.html`) est chargé dans une `WebView`
headless (JavaScript activé, chargement réseau bloqué), qui exécute le même challenge
BotGuard que ce que Playwright fait ici côté serveur. C'est un mécanisme
**potentiellement portable** à un client Android MuzziQ standalone (Phase I du plan,
pas encore commencée — voir CLAUDE.md, "Ce qui n'est PAS encore fait"), car une
`WebView` est un composant Android standard, contrairement à un Chromium serveur.

**Conclusion explicite, à ne pas perdre pour un futur chantier Android** : ce que ce
chantier serveur prouve, c'est le **mécanisme général** (un vrai moteur de rendu
JavaScript peut obtenir un PoToken valide ; jsdom sans moteur de rendu réel ne le
peut pas, voir 2026-09-02 plus haut) — **pas** une implémentation directement
réutilisable sur Android. Un futur chantier Android dédié devra réimplémenter
l'équivalent via `WebView` (asset HTML local + `evaluateJavascript`/pont JS-natif),
en s'inspirant du comportement documenté ci-dessus, **jamais** en copiant le code
Metrolist (GPL-3.0, voir règle Metrolist en tête de ce dossier). Le parseur UMP
(`umpParser.ts`), lui, est un pur TypeScript sans dépendance navigateur — celui-là
est directement réutilisable tel quel (ou porté à Kotlin) une fois un PoToken obtenu
par n'importe quel moyen, serveur ou WebView.

## Poids Docker — coût assumé, non mesuré dans cette session

`packaging/docker/Dockerfile` installe désormais Chromium via le dépôt Alpine (même
correctif déjà appliqué à yt-dlp : le binaire officiel Playwright est lié à glibc et
ne tourne pas sur Alpine/musl) et pointe `MUZZIQ_CHROMIUM_PATH` dessus.
**Avertissement honnête** : contrairement au reste de ce rapport, ce bloc Dockerfile
n'a **pas** été vérifié par un vrai build Docker dans cette session (faute de temps) —
seul le code TypeScript a été vérifié par des appels réels à `music.youtube.com`
depuis ce poste de développement (Windows, Chromium Playwright déjà installé
localement). Chromium ajoute de l'ordre de 300-400 Mo à l'image runtime — coût réel,
assumé, documenté ici plutôt que caché, mais **à vérifier par un vrai build + un vrai
appel avant de considérer ce chemin fiable en production** (règle #5 du projet).
`poTokenBrowser.ts` échoue proprement (capturé, jamais fatal) si Chromium est absent
ou ne se lance pas — la résolution retombe alors sur yt-dlp, jamais de panne totale.

## Prochaine étape (non faite)

- Résoudre la reconstruction des flux longs (au-delà d'une seule part MEDIA UMP) —
  demanderait de comprendre les types de part 29/33/43 rencontrés réellement (voir
  plus haut), pas fait dans cette session malgré plusieurs heures d'essai honnête.
- Vérifier par un vrai build Docker (pas seulement en local) que le Chromium Alpine
  fonctionne réellement dans le conteneur runtime, pas seulement supposé d'après le
  correctif déjà connu pour yt-dlp.
- Décider si un chantier Android dédié (WebView PoToken, voir section ci-dessus)
  devient prioritaire une fois la Phase I (client Android) commencée.
