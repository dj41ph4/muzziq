# Statut réel du streaming en ligne (YouTube Music) — client Android

Référence complète : `docs/reverse-engineering/youtube-music/README.md` (côté
serveur). Ce document résume ce que ça implique concrètement pour
`android-mobile/`, pour qu'aucune future session ne suppose une autonomie qui
n'existe pas.

## Ce qui bloque, en une phrase

Le serveur MuzziQ n'a **pas résolu** le déchiffrement de `signatureCipher`
YouTube (obfuscation propre à chaque déploiement de `base.js`, surface de
reverse engineering aussi mouvante que le challenge anti-bot BotGuard). 100%
des formats audio/vidéo retournés par InnerTube portent un `signatureCipher`,
aucune URL en clair. Le serveur retombe donc systématiquement sur `yt-dlp`
(subprocess Python) pour obtenir un flux réellement jouable.

## Conséquence pour Android

`yt-dlp` ne tourne pas sur Android (pas de runtime Python embarqué, pas prévu
d'en ajouter un — ce serait un chantier à part entière, pas un correctif).
Donc :

- **Le client Android ne peut pas, aujourd'hui, résoudre un flux YouTube Music
  jouable sans un serveur MuzziQ qui fait le travail à sa place** (lequel
  s'appuie lui-même sur yt-dlp). Ce n'est pas une limite d'implémentation
  Android — c'est le même mur que le serveur a rencontré, hérité tel quel.
- Écrire un module `InnerTubeClient`/`CipherResolver` côté Kotlin sans
  résoudre ce problème produirait un client qui répond `UNPLAYABLE` sur
  100% des morceaux réels — un bouton Play mort déguisé en fonctionnalité,
  explicitement interdit. Personne n'a donc commencé ce module ici.
- Mode Lié (`ServerMusicSource`) : fonctionne, parce qu'il délègue la
  résolution au serveur via `/api/recordings/{id}/resolve` puis
  `/api/play/{trackId}` — le serveur absorbe le problème avec yt-dlp,
  Android n'a jamais besoin de connaître InnerTube.
- Mode Standalone (`StandaloneMusicSource`) : n'essaie pas de streamer
  YouTube Music du tout — `STREAMING_UNAVAILABLE_NOTICE` l'annonce
  explicitement dans l'UI recherche plutôt que de laisser un bouton Play
  échouer silencieusement.

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

C'est le périmètre réel de l'"autonomous-first" atteignable aujourd'hui —
tout le reste (catalogue en ligne, streaming YouTube Music sans serveur)
reste bloqué en amont, pas par manque de travail côté Android.

## Prochaine étape possible (non commencée)

Identique à la note du README serveur : soit une réimplémentation
continuellement maintenue du désobfuscateur de `base.js` (coût récurrent
déconseillé), soit l'exécution partielle du `base.js` réel dans un bac à
sable JS pour en extraire la fonction de déchiffrement dynamiquement. Les
deux restent des sous-projets à part entière, à réévaluer plus tard — pas
un correctif Android à faire "vite fait" pour cocher une case du plan.
