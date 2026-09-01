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

## PLAYER — bloqué en anonyme (résultat réel, pas une hypothèse)

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

Conclusion : YouTube exige désormais un **PoToken** (jeton anti-bot généré
par un challenge BotGuard côté client) pour la résolution de flux en mode
anonyme, sur tous les contextes testés. C'est un durcissement récent et
documenté par les projets tiers (yt-dlp, ytmusicapi ont le même constat et
des workarounds dédiés type `bgutil-ytdlp-pot-provider`).

**Ne pas** interpréter ça comme une erreur d'implémentation à corriger
rapidement — c'est un sous-projet à part entière (résoudre un challenge
BotGuard nécessite une VM JS ou un navigateur headless). Voir
`src/providers/youtube-music/playbackResolver.ts` pour l'état actuel
(échec typé et explicite, jamais un flux fabriqué) et le plan pour la suite.

## Prochaine étape (non faite)

`PoTokenManager` (§13 du plan) : générer un PoToken valide avant chaque appel
`/player`. Options à évaluer : VM JS embarquée résolvant le challenge
BotGuard (approche bgutil), ou navigateur headless dédié. Gros morceau,
à traiter comme une phase à part plutôt qu'un correctif.
