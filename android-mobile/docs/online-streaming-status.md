# Statut réel du streaming en ligne (YouTube Music) — client Android

Référence complète : `docs/reverse-engineering/youtube-music/README.md` (côté
serveur). Ce document résume ce que ça implique concrètement pour
`android-mobile/`, pour qu'aucune future session ne suppose une autonomie qui
n'existe pas.

## État actuel

Le streaming YouTube Music autonome fonctionne désormais dans le client
Android via `innertubex` : recherche filtrée sur les morceaux, résolution du
flux audio, conservation des en-têtes signés et lecture Media3 par plages
bornées de 512 KiB. Le chemin a été vérifié sur l'émulateur avec un morceau
réel au-delà de l'ancien seuil de 512 KiB, puis après reprise de lecture.

Le serveur reste compatible et constitue toujours le chemin du mode Lié ; il
n'est simplement plus nécessaire pour le mode Standalone.

## Historique du blocage initial

Le serveur MuzziQ n'a **pas résolu** le déchiffrement de `signatureCipher`
YouTube (obfuscation propre à chaque déploiement de `base.js`, surface de
reverse engineering aussi mouvante que le challenge anti-bot BotGuard). 100%
des formats audio/vidéo retournés par InnerTube portent un `signatureCipher`,
aucune URL en clair. Le serveur retombe donc systématiquement sur `yt-dlp`
(subprocess Python) pour obtenir un flux réellement jouable.

## Conséquence pour Android

`yt-dlp` ne tourne pas sur Android (pas de runtime Python embarqué, pas prévu
d'en ajouter un — ce serait un chantier à part entière, pas un correctif).
Cette analyse décrivait l'état avant l'intégration du moteur d'extraction
Android. Elle reste utile comme contexte historique, mais ses conséquences
ne décrivent plus le comportement actuel. Aujourd'hui :

- `StandaloneStreamExtractor` délègue la rotation des signatures et des
  profils clients à `innertubex`, puis transmet l'URL et les métadonnées au
  DataSource Media3.
- `StreamHeaderDataSource` réinjecte les en-têtes du flux actif à chaque
  ouverture et impose des plages bornées pour éviter les 403 après le premier
  segment.
- Mode Lié (`ServerMusicSource`) : fonctionne, parce qu'il délègue la
  résolution au serveur via `/api/recordings/{id}/resolve` puis
  `/api/play/{trackId}` — le serveur absorbe le problème avec yt-dlp,
  Android n'a jamais besoin de connaître InnerTube.
- Mode Standalone (`StandaloneMusicSource`) : recherche et lecture YouTube
  Music directes, avec repli de profil et nouvelle résolution après rejet CDN.

## Ce qui EST réellement autonome sur le téléphone dès aujourd'hui

Sans dépendre de ce blocage, ni d'aucun serveur :

- Bibliothèque locale (scan `MediaStore`, `standalone/LocalLibraryScanner.kt`)
- Lecture locale (Media3/ExoPlayer sur `content://`, aucun réseau)
- Moteur de goût on-device (`standalone/LocalTasteDatabase.kt` — SQLite réel ;
  schéma Room équivalent posé dans `data/room/` pour une migration future,
  voir le commentaire en tête de `data/room/Entities.kt`)
- Android Auto pour ce contenu local (`playback/PlaybackService.kt`,
  `MediaLibraryService`)
- File d'attente / Suivant-Précédent (`PlaybackService.playQueue` /
  `skipNext` / `skipPrevious`)

C'est le périmètre réel de l'"autonomous-first" atteint aujourd'hui.

## Pistes historiques désormais secondaires

Identique à la note du README serveur : soit une réimplémentation
continuellement maintenue du désobfuscateur de `base.js` (coût récurrent
déconseillé), soit l'exécution partielle du `base.js` réel dans un bac à
sable JS pour en extraire la fonction de déchiffrement dynamiquement. Les
deux restent des sous-projets à part entière, à réévaluer plus tard — pas
un correctif Android à faire "vite fait" pour cocher une case du plan.

## Spike initial "streaming direct Android" (2026-09-02) — historique

Objectif du spike (priorité 3 du plan de reprise) : `videoId → URL audio
valide → Media3 → son`, sans passer par le serveur. Investigation réelle
menée avant de conclure, appels réseau réels (aucun code de client tiers
consulté ni copié) :

- Rejoué depuis cet environnement le même appel `/youtubei/v1/player`
  (`WEB_REMIX`, `signatureTimestamp: 20684`) documenté côté serveur :
  `playabilityStatus.status = OK`, `streamingData` renvoyé. **19 formats
  reçus (`dQw4w9WgXcQ`), 19/19 portent `signatureCipher`, 0 avec `url` en
  clair** — le blocage documenté côté serveur est confirmé toujours actif
  aujourd'hui, pas une régression datée qui se serait résolue depuis.
- Récupéré le `base.js` réel (`jsUrl` extrait de la page HTML
  `music.youtube.com/watch`, build `e937390a`, ~2,8 Mo) et vérifié
  `signatureTimestamp:20684` littéralement présent dedans — cohérent avec
  la valeur utilisée dans la requête, confirme que `sts` n'est pas
  périmé.
- Cherché la fonction de déchiffrement par la même heuristique que documentée
  côté serveur (motif `X.split("")` isolé, point d'entrée classique avant
  obfuscation Closure) : une seule occurrence dans tout le fichier, noyée
  dans un helper générique polymorphe (`Dr(...)`, sert à convertir tout
  type itérable en tableau, pas spécifique au chiffrement de signature).
  Aucune fonction de déchiffrement isolable par un heuristique simple —
  confirme littéralement la description déjà écrite côté serveur ("code
  compilé Closure avec table de chaînes indexée, pas le schéma classique").

**Conclusion du spike initial : blocage confirmé à cette date.** La piste qui restait plausible et non
essayée — exécuter le `base.js` réel dans un bac à sable JS pour en extraire
la fonction de déchiffrement dynamiquement (mentionnée ci-dessus) —
bénéficierait sur Android d'un avantage que le serveur n'a pas : `WebView`
est un vrai moteur Chromium (V8), pas `jsdom` (dont l'échec documenté côté
serveur pour la génération de PoToken vient précisément du fait que ce n'est
*pas* un vrai navigateur). Mais c'est un sous-projet à part entière, pas un
spike : il faudrait (1) isoler dans `base.js` le point d'entrée réel de
déchiffrement malgré l'obfuscation (pas trouvé par l'heuristique simple
ci-dessus), (2) l'exécuter dans une `WebView` cachée avec les bons stubs
`window`/`document` pour qu'il ne lève pas d'exception sur des API absentes,
(3) vérifier le résultat contre un flux réellement joué — **aucune étape de
ça n'était pas testable dans l'environnement de développement de cette date
(pas de SDK/émulateur/appareil Android, seul le CI GitHub Actions compilait)**.
Écrire
du code Kotlin qui prétendrait résoudre ce point sans jamais avoir pu
l'exécuter une seule fois en conditions réelles serait exactement le "succès
fabriqué" interdit — donc rien n'a été codé pour ce spike. Le module
Le moteur maison n'a finalement pas été retenu : l'application utilise
`innertubex`, intégré et vérifié en lecture réelle.

Prochaine étape si ce chantier est repris : d'abord isoler manuellement (pas
par regex fragile) le point d'entrée de déchiffrement dans un `base.js` réel
téléchargé, en debuggant pas à pas dans un vrai navigateur desktop (Chrome
DevTools sur `music.youtube.com`, poser un breakpoint sur l'appel qui
consomme `signatureCipher`) — étape qui ne nécessite toujours pas de copier
de code tiers, seulement de comprendre où le flot d'exécution mène. Une fois
ce point d'entrée identifié avec certitude, l'exécution en `WebView` Android
devient un vrai test réalisable, mais sur un appareil réel.
