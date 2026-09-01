# MUZZIK — Architecture cible

> **Statut :** document d’architecture initial  
> **Nom de travail :** MUZZIK  
> **Objectif :** construire une plateforme musicale personnelle qui mélange l’immédiateté de MetroList, la qualité d’expérience de Plexamp et la logique d’automatisation de Movviz, sans placer Plex, YouTube Music, Lidarr ou un autre service externe au cœur du système.

---

## 0. Décision fondatrice

MUZZIK n’est **pas** :

- un fork de MetroList ;
- un skin de YouTube Music ;
- un frontend pour Lidarr ;
- un frontend pour Plex ;
- un simple lecteur de fichiers locaux ;
- un assemblage de plusieurs applications externes.

MUZZIK est **le système maître**.

Il possède lui-même :

- son catalogue logique ;
- sa bibliothèque ;
- ses utilisateurs ;
- ses préférences ;
- son historique d’écoute ;
- ses favoris ;
- ses playlists ;
- ses recommandations ;
- son moteur de recherche ;
- son moteur d’acquisition ;
- son moteur de scoring ;
- son pipeline d’import musical ;
- son lecteur ;
- ses métadonnées internes ;
- son moteur IA ;
- sa configuration ;
- son ordonnanceur ;
- ses files d’attente ;
- son état de disponibilité.

Les systèmes externes sont des **providers** ou des **intégrations**.

```text
YouTube Music ─┐
MusicBrainz ───┤
LRCLIB ────────┤
Last.fm ───────┤
Plex ──────────┤
ListenBrainz ──┤
Torrent/indexer┤
               ▼
        ┌───────────────┐
        │  MUZZIK CORE  │
        └───────────────┘
               │
       source de vérité
```

La suppression d’une intégration ne doit jamais rendre MUZZIK inutilisable.

---

# 1. Sources d’inspiration et règle de réutilisation

## 1.1 Movviz

Movviz appartient au même auteur que MUZZIK.

La réutilisation directe est donc autorisée et souhaitée lorsque le code est pertinent.

MUZZIK doit reprendre ou adapter autant que possible :

- architecture générale backend/frontend ;
- gestion de configuration ;
- authentification ;
- utilisateurs ;
- rôles ;
- TOTP si pertinent ;
- moteur IA ;
- abstraction des providers ;
- moteur de recherche/acquisition ;
- scoring ;
- queue ;
- scheduler ;
- gestion des erreurs ;
- health checks ;
- notifications ;
- logs ;
- système de settings ;
- système de téléchargements ;
- abstraction des backends torrent ;
- composants UI génériques ;
- design system ;
- mécanismes Docker ;
- packaging ;
- patterns Android déjà éprouvés ;
- gestion des fallbacks ;
- principes de non-régression.

Le dépôt Movviz actuel possède déjà un moteur de téléchargement avec abstraction de backend. MUZZIK doit repartir de cette génération et non d’anciennes branches utilisant aria2.

### Backends à considérer depuis Movviz

- `AbstractBackend`
- `LibtorrentBackend`
- `NativeTorrentBackend`
- `WebTorrentBackend`

**aria2 est explicitement hors scope pour MUZZIK.**

MUZZIK ne doit pas introduire aria2, même comme fallback historique.

---

## 1.2 MetroList

MetroList sert principalement de **référence comportementale et technique** pour comprendre comment un client moderne dialogue avec YouTube Music / InnerTube.

Aucun code MetroList ne doit être copié dans MUZZIK.

Méthode :

1. observer les appels ;
2. comprendre les responsabilités ;
3. documenter les entrées/sorties ;
4. reproduire les comportements nécessaires ;
5. écrire une implémentation MUZZIK indépendante.

MetroList est particulièrement pertinent pour :

- requêtes InnerTube ;
- recherche YouTube Music ;
- navigation artiste ;
- navigation album ;
- navigation playlist ;
- recommandations ;
- Quick Picks ;
- récupération de métadonnées ;
- résolution des flux au moment de la lecture ;
- gestion des contextes clients YouTube ;
- données visiteur ;
- signature/cipher ;
- PO Token lorsque nécessaire ;
- connexion YouTube Music optionnelle ;
- synchronisation éventuelle des playlists ;
- paroles et providers annexes ;
- gestion des erreurs et changements de protocole.

Le dépôt MetroList sépare actuellement un module `innertube`, ce qui confirme que cette responsabilité doit aussi être isolée dans MUZZIK.

---

## 1.3 Lidarr

Lidarr **ne devient pas une dépendance** de MUZZIK.

Il sert uniquement de **référence fonctionnelle** pour les problèmes spécifiques à la musique qui n’existent pas ou peu dans le cinéma.

À étudier :

- notion d’artiste surveillé ;
- album surveillé ;
- sorties futures ;
- release/edition ;
- qualité ;
- upgrade ;
- import après téléchargement ;
- matching album ↔ fichiers ;
- tracklist ;
- multi-disques ;
- bonus tracks ;
- renommage ;
- rejet d’un résultat ;
- retry ;
- release blacklist ;
- recherche manuelle ;
- gestion des fichiers existants.

Toute fonctionnalité pertinente doit être réécrite dans le modèle MUZZIK/Movviz.

---

## 1.4 Plexamp

Plexamp est une **référence UX et musicale**.

Ne jamais construire MUZZIK autour d’une API Plexamp ou d’un serveur Plex.

À observer :

- qualité du player ;
- fluidité de la queue ;
- radio d’artiste ;
- radio de morceau ;
- découverte ;
- continuité de lecture ;
- mix ;
- transitions ;
- loudness ;
- navigation album/artiste ;
- lecture locale ;
- affichage des qualités ;
- expérience mobile ;
- comportement hors connexion.

---

# 2. Principe absolu : MUZZIK fonctionne sans Plex

Installation minimale :

```text
Docker MUZZIK
     +
/music
     +
base SQLite/PostgreSQL
     +
provider catalogue/stream
```

Résultat :

```text
MUZZIK = fonctionnel
```

Plex peut être absent.

Lidarr peut être absent.

YouTube Music peut être non connecté.

Un compte Google ne doit pas être obligatoire pour démarrer.

### Test obligatoire d’architecture

À chaque release majeure :

```text
1. désactiver Plex
2. désactiver toute intégration externe optionnelle
3. conserver uniquement MUZZIK + fichiers locaux
4. vérifier que :
   - login fonctionne
   - bibliothèque fonctionne
   - recherche locale fonctionne
   - player fonctionne
   - playlists fonctionnent
   - historique fonctionne
   - administration fonctionne
```

Si ce test échoue, une dépendance optionnelle est devenue accidentellement structurelle.

---

# 3. Architecture générale

```text
┌──────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                 │
│                                                                      │
│ Web/Desktop      Android Mobile       Android TV       API clients   │
└───────────────┬───────────────┬───────────────┬──────────────────────┘
                │               │               │
                └───────────────┴───────┬───────┘
                                        │
                              REST / WS / SSE
                                        │
┌───────────────────────────────────────▼──────────────────────────────┐
│                             MUZZIK API                               │
│                                                                      │
│ Auth  Users  Library  Search  Player  Queue  AI  Acquisition         │
└───────────────────────────────────────┬──────────────────────────────┘
                                        │
┌───────────────────────────────────────▼──────────────────────────────┐
│                             MUZZIK CORE                              │
│                                                                      │
│ Identity Resolver      Availability Engine      Recommendation       │
│ Metadata Aggregator    Library Manager          Scheduler            │
│ Playback Resolver      Acquisition Engine       Event Bus            │
│ Import Pipeline        Quality Engine           Health Manager       │
└───────────┬────────────────────┬──────────────────┬──────────────────┘
            │                    │                  │
            ▼                    ▼                  ▼
    ┌──────────────┐      ┌───────────────┐   ┌──────────────┐
    │  PROVIDERS   │      │ LOCAL STORAGE │   │ ACQUISITION  │
    │              │      │               │   │              │
    │ YT Music     │      │ Audio files   │   │ Indexers     │
    │ MusicBrainz  │      │ Artwork       │   │ Torrent      │
    │ LRCLIB       │      │ Cache         │   │ Backends     │
    │ etc.         │      │ Waveforms     │   │ Import       │
    └──────────────┘      └───────────────┘   └──────────────┘
            │
            ▼
    ┌──────────────┐
    │ INTEGRATIONS │
    │ Plex         │
    │ Last.fm      │
    │ ListenBrainz │
    └──────────────┘
```

---

# 4. Stack recommandée

Pour rester proche de Movviz et maximiser la réutilisation :

## Backend

**Correction (vérifiée sur le dépôt Movviz réel) :** Movviz n'utilise ni NestJS ni Prisma.
C'est une app Next.js unique (App Router) avec des routes `/api/*`, un store JSON fichier
avec cache mémoire (`fsJsonCache.ts`), et un service `engine/` séparé (process Node.js
distinct) pour le téléchargement. Pas de monorepo à outillage lourd (pas de Turborepo, pas
de pnpm workspaces) — juste un `package.json` racine et des sous-dossiers `engine/`,
`resolver/` avec leur propre `package.json`, lancés comme process séparés.

Repartir sur NestJS/Prisma pour MUZZIK signifierait réécrire toute la logique
d'acquisition/scheduler/jobs de Movviz dans un paradigme différent (modules/DI Nest,
ORM Prisma) avant même d'avoir un produit qui tourne. Ça contredit directement la
règle §87 ("ne pas réinventer la roue") et §89 (ne pas extraire de packages partagés
avant d'avoir identifié une vraie duplication stable).

**Stack V1 retenue** :

- Node.js LTS
- TypeScript strict
- Next.js (App Router) — même génération que Movviz
- Store JSON fichier + cache mémoire (`fsJsonCache.ts` porté depuis Movviz) par défaut ;
  migration vers SQLite/Prisma envisageable plus tard si la volumétrie ou les requêtes
  relationnelles (recherche croisée artiste/album/qualité) le justifient — pas avant.
- WebSocket/SSE pour états temps réel
- process `engine/` séparé pour le torrent engine (comme Movviz), spawné via
  `instrumentation.ts`
- workers `worker_threads` séparables pour tâches coûteuses (fingerprint, scan massif) —
  seulement une fois qu'un besoin réel de volumétrie apparaît (voir §103.3)

## Frontend

- React
- TypeScript
- PWA
- responsive desktop/tablette/mobile
- design system partagé avec Movviz lorsque pertinent

## Android

Préférer un vrai client Android pour la lecture longue durée.

- Kotlin
- Jetpack Compose
- Media3 / ExoPlayer
- MediaSession
- notification média
- Android Auto à terme
- cache/offline natif

Le client Android ne doit pas implémenter lui-même le catalogue YouTube Music.

Le serveur MUZZIK reste maître.

## Déploiement

- Docker en priorité ;
- Windows/Linux possibles ;
- volumes distincts :
  - `/config`
  - `/music`
  - `/downloads`
  - `/cache`
  - `/transcode` si nécessaire.

---

# 5. Monorepo cible

```text
muzzik/
│
├── apps/
│   ├── web/
│   ├── api/
│   ├── worker/
│   ├── android-mobile/
│   └── android-tv/              # plus tard
│
├── packages/
│   ├── core/
│   ├── database/
│   ├── contracts/
│   ├── auth/
│   ├── library/
│   ├── metadata/
│   ├── identity/
│   ├── playback/
│   ├── recommendations/
│   ├── acquisition/
│   ├── import/
│   ├── torrent-engine/
│   ├── scheduler/
│   ├── ai/
│   ├── notifications/
│   ├── shared-ui/
│   └── telemetry/
│
├── providers/
│   ├── youtube-music/
│   ├── musicbrainz/
│   ├── lrclib/
│   ├── fanart/
│   └── local/
│
├── integrations/
│   ├── plex/
│   ├── lastfm/
│   └── listenbrainz/
│
├── docker/
├── docs/
└── tests/
```

Cette structure est indicative.

La règle importante est la séparation des responsabilités.

---

# 6. Domaine musical interne

MUZZIK ne doit jamais utiliser un `videoId` YouTube comme identifiant principal d’un morceau.

Même règle pour Plex.

## Entités principales

```text
Artist
Album
AlbumEdition
Track
TrackRecording
Playlist
PlaylistItem
LibraryItem
MediaFile
ProviderMapping
Availability
QualityProfile
AcquisitionJob
DownloadJob
ImportJob
PlaybackEvent
UserTaste
Recommendation
```

---

# 7. Identité canonique

C’est un des composants les plus importants.

Un même morceau peut exister :

- sur YouTube Music ;
- en vidéo YouTube ;
- dans un FLAC local ;
- dans Plex ;
- dans MusicBrainz ;
- dans une playlist ;
- dans plusieurs éditions d’un album.

MUZZIK doit comprendre qu’il s’agit du **même enregistrement** ou d’une variante.

## Exemple

```text
TrackRecording
  id: muzzik:recording:abc123

  title: Numb
  artist: Linkin Park
  duration: 185.4

  mappings:
    musicbrainzRecordingId: ...
    youtubeMusicVideoId: ...
    youtubeVideoId: ...
    plexRatingKey: ...

  files:
    - FLAC 16/44.1
    - MP3 320
```

## IdentityResolver

Créer :

```ts
interface IdentityResolver {
  resolve(input: ExternalTrack): Promise<IdentityMatch>;
}
```

Matching basé sur :

1. MusicBrainz ID si fiable ;
2. ISRC ;
3. artiste normalisé ;
4. titre normalisé ;
5. durée avec tolérance ;
6. album ;
7. numéro de piste ;
8. fingerprint audio si nécessaire.

Ne jamais faire confiance à un seul champ texte.

---

# 8. Track vs Recording

Prévoir dès le départ la différence.

## Recording

La chanson/enregistrement conceptuel.

Exemple :

```text
Linkin Park — Numb
```

## Track

L’apparition de cet enregistrement dans une édition précise.

```text
Meteora — track 13
Meteora 20th Anniversary — track X
Best Of — track Y
```

Cela évite une quantité énorme de problèmes futurs.

---

# 9. Album et édition

Ne pas modéliser seulement :

```text
Artist → Album → Track
```

Modèle recommandé :

```text
Artist
  ↓
AlbumGroup
  ↓
AlbumEdition
  ↓
Track
  ↓
Recording
```

Exemple :

```text
Meteora
├── Original 2003
├── Deluxe
├── 20th Anniversary
├── EU edition
├── US edition
└── Digital
```

La bibliothèque peut posséder une édition mais le catalogue peut en connaître plusieurs.

---

# 10. Availability Engine

L’utilisateur ne doit jamais avoir à réfléchir à la provenance technique d’un morceau.

Pour chaque recording :

```text
Availability
├── LOCAL
├── CACHED
├── STREAM
├── REMOTE_SERVER
└── UNAVAILABLE
```

Exemple :

```json
{
  "recordingId": "abc",
  "sources": [
    {
      "type": "LOCAL",
      "quality": "FLAC_16_44",
      "preferred": true
    },
    {
      "type": "YOUTUBE_MUSIC",
      "quality": "AAC",
      "preferred": false
    }
  ]
}
```

---

# 11. Ordre de préférence de lecture

Par défaut :

```text
1. fichier local conforme au profil
2. fichier local de qualité inférieure
3. cache MUZZIK
4. provider de streaming principal
5. provider de streaming fallback
6. intégration distante explicitement autorisée
```

Plex ne doit pas être prioritaire simplement parce qu’il est connecté.

---

# 12. Playback Resolver

Le frontend demande :

```text
PLAY recordingId=abc
```

et non :

```text
PLAY youtubeVideoId=xyz
```

Le serveur décide ensuite.

```text
request
  ↓
Identity
  ↓
Availability
  ↓
PlaybackPolicy
  ↓
SourceResolver
  ↓
PlayableSource
```

## Contrat

```ts
interface PlaybackSource {
  type: 'LOCAL' | 'CACHE' | 'PROVIDER';
  url: string;
  expiresAt?: Date;
  codec?: string;
  bitrate?: number;
  sampleRate?: number;
  bitDepth?: number;
}
```

---

# 13. YouTubeMusicProvider

C’est le module à construire grâce au reverse engineering fonctionnel de MetroList.

```text
providers/youtube-music/
│
├── YoutubeMusicProvider
├── InnerTubeClient
├── ClientContextManager
├── VisitorDataManager
├── BrowseService
├── SearchService
├── ArtistService
├── AlbumService
├── PlaylistService
├── RecommendationService
├── PlayerService
├── StreamResolver
├── CipherResolver
├── PoTokenManager
├── AccountSession
├── ResponseMapper
├── ProviderCache
└── ProviderHealth
```

## Interface provider

```ts
interface MusicProvider {
  capabilities(): ProviderCapabilities;

  search(query: SearchQuery): Promise<SearchResult>;

  getArtist(id: string): Promise<ExternalArtist>;
  getAlbum(id: string): Promise<ExternalAlbum>;
  getTrack(id: string): Promise<ExternalTrack>;
  getPlaylist(id: string): Promise<ExternalPlaylist>;

  getHome?(context: UserContext): Promise<HomeFeed>;
  getRecommendations?(seed: RecommendationSeed): Promise<ExternalTrack[]>;

  resolvePlayback?(
    trackId: string,
    context: PlaybackContext
  ): Promise<ExternalPlaybackSource>;
}
```

---

# 14. Aucun détail InnerTube dans le frontend

Interdit :

```text
React → youtubei/v1/player
```

Correct :

```text
React
  ↓
MUZZIK API
  ↓
Playback Resolver
  ↓
YoutubeMusicProvider
  ↓
InnerTube
```

Avantages :

- changement de protocole centralisé ;
- aucun secret/session exposé inutilement ;
- contrôle des fallbacks ;
- logs ;
- throttling ;
- cache ;
- observabilité ;
- protection contre les régressions.

---

# 15. Sessions YouTube Music

Le compte YouTube Music est **optionnel**.

Modes :

```text
ANONYMOUS
AUTHENTICATED
```

## Anonymous

Doit permettre autant que techniquement possible :

- recherche ;
- browse ;
- albums ;
- artistes ;
- lecture ;
- suggestions génériques.

## Authenticated

Ajoute éventuellement :

- bibliothèque YTM ;
- playlists ;
- recommandations personnalisées ;
- likes ;
- historique ;
- synchronisation.

### Règle

Les données YTM importées deviennent des **mappings/sources**, pas la base de vérité MUZZIK.

---

# 16. Protection contre les changements YouTube

Créer une couche de compatibilité dédiée.

```text
ProviderHealth
├── search probe
├── browse probe
├── player probe
├── cipher probe
└── auth probe
```

Chaque probe doit exposer :

```text
OK
DEGRADED
BROKEN
AUTH_REQUIRED
RATE_LIMITED
```

L’UI admin doit immédiatement montrer :

```text
YouTube Music
Catalogue: OK
Search: OK
Playback: DEGRADED
Account sync: OK
```

---

# 17. Catalogue MUZZIK

Le catalogue n’est pas la bibliothèque.

## Catalogue

Tout ce que MUZZIK connaît.

## Bibliothèque

Ce que l’utilisateur a explicitement ajouté ou ce que le serveur possède.

Exemple :

```text
Taylor Swift
  connue dans le catalogue
  mais pas forcément dans Library
```

Cette distinction est indispensable pour les suggestions.

---

# 18. Bibliothèque MUZZIK

La bibliothèque doit être indépendante du stockage physique.

```text
LibraryItem
├── type: ARTIST | ALBUM | TRACK
├── monitored
├── addedAt
├── addedBy
├── preferredEdition
├── qualityProfile
└── acquisitionPolicy
```

Un album peut être :

```text
KNOWN
LIBRARY
WANTED
SEARCHING
DOWNLOADING
IMPORTING
LOCAL
UPGRADABLE
UNAVAILABLE
```

---

# 19. États machine

Ne pas gérer ces états avec des booléens dispersés.

Créer une machine d’état explicite.

```text
DISCOVERED
    ↓
LIBRARY
    ↓
WANTED
    ↓
SEARCHING
    ↓
CANDIDATE_FOUND
    ↓
QUEUED
    ↓
DOWNLOADING
    ↓
VERIFYING
    ↓
IMPORTING
    ↓
LOCAL
```

Branches :

```text
SEARCHING → NOT_FOUND
DOWNLOADING → FAILED
VERIFYING → REJECTED
IMPORTING → FAILED
LOCAL → UPGRADABLE
```

Chaque transition doit être journalisée.

---

# 20. Acquisition Engine

Le moteur de Movviz doit servir de base.

MUZZIK adapte les concepts vidéo au domaine musical.

```text
AcquisitionRequest
      ↓
SearchCoordinator
      ↓
IndexerProviders
      ↓
NormalizeResults
      ↓
MusicReleaseParser
      ↓
MusicReleaseScorer
      ↓
DecisionEngine
      ↓
TorrentEngine
      ↓
DownloadMonitor
      ↓
MusicImportPipeline
```

---

# 21. Aucun Lidarr dans le chemin

Architecture interdite :

```text
MUZZIK → Lidarr → Prowlarr → qBittorrent
```

Architecture cible :

```text
MUZZIK
├── Indexer layer
├── Search
├── Parser
├── Scoring
├── Grab
├── Torrent engine
├── Verification
└── Import
```

Lidarr peut seulement servir de référence lors du développement.

---

# 22. Torrent Engine

Reprendre directement le moteur moderne Movviz lorsque possible.

Interface logique :

```ts
interface TorrentBackend {
  init(): Promise<void>;

  add(input: AddTorrentInput): Promise<TorrentJob>;
  remove(id: string, deleteData?: boolean): Promise<void>;

  pause(id: string): Promise<void>;
  resume(id: string): Promise<void>;

  get(id: string): Promise<TorrentJob | null>;
  list(): Promise<TorrentJob[]>;

  getStats(): Promise<TorrentStats>;
}
```

### Backends

MUZZIK ne doit pas dépendre fonctionnellement d’un backend.

Le backend sélectionné est un réglage serveur.

```text
torrent.backend = native | libtorrent | webtorrent
```

**aria2 exclu.**

---

# 23. Indexer abstraction

Ne pas coupler la recherche à un fournisseur précis.

```ts
interface ReleaseIndexer {
  searchAlbum(query: AlbumSearchQuery): Promise<ReleaseCandidate[]>;
  searchTrack?(query: TrackSearchQuery): Promise<ReleaseCandidate[]>;
}
```

Chaque résultat est converti en modèle interne.

```text
ReleaseCandidate
├── title
├── source
├── seeders
├── leechers
├── size
├── publishedAt
├── magnet/torrent ref
├── parsedArtist
├── parsedAlbum
├── parsedEdition
├── parsedFormat
├── parsedBitDepth
├── parsedSampleRate
├── parsedYear
├── parsedTrackCount
└── confidence
```

---

# 24. Music Release Parser

Le parser cinéma de Movviz ne suffit pas.

Créer un parser spécialisé musique.

Doit comprendre notamment :

```text
Artist - Album (2024) FLAC
Artist - Album [24bit 96kHz]
Artist Discography 1999-2025 FLAC
Artist - Album Deluxe Edition WEB FLAC
Artist - Album CD-FLAC
Artist - Album 2CD
Artist - Album Remastered
Artist - Album 320kbps
```

Il doit extraire :

- artiste ;
- album ;
- année ;
- édition ;
- remaster ;
- source ;
- codec ;
- lossless/lossy ;
- bit depth ;
- sample rate ;
- bitrate ;
- CD count ;
- track count si présent ;
- WEB/CD/VINYL ;
- éventuels tags de scène.

---

# 25. Scoring musique

Réutiliser le moteur de scoring de Movviz, mais avec critères spécialisés.

Exemple conceptuel :

```text
Exact artist                         +40
Exact album                          +50
Correct year                         +15
Preferred edition                    +25
FLAC                                 +30
24-bit                               +10
Preferred sample rate                +5
Correct track count                  +25
MusicBrainz-compatible edition       +25
Good seed count                      +10
Fresh release                        +5

Wrong album                          -1000
Wrong artist                         -1000
Incomplete tracklist                 -100
Suspicious transcode                 -80
Wrong edition                        -20
Compilation when studio album wanted -30
```

Les valeurs doivent être configurables.

---

# 26. Quality Profiles

Créer une vraie notion de profil.

Exemples :

```text
Standard
  min: AAC/MP3 256
  target: MP3 320
  upgrade: FLAC

Lossless
  min: FLAC 16/44.1
  target: FLAC 16/44.1

Hi-Res
  min: FLAC
  target: 24-bit
```

Ne pas considérer automatiquement 192 kHz supérieur pour tous les usages.

Le profil décide.

---

# 27. Cutoff

Même principe que les Arr :

```text
Profile = Lossless
Cutoff = FLAC 16/44.1
```

Une fois atteint :

```text
pas de recherche d’upgrade inutile
```

Ou :

```text
Profile = Hi-Res
Cutoff = 24/96
```

Un FLAC 16/44 reste alors `UPGRADABLE`.

---

# 28. Import Pipeline musical

C’est une nouvelle pièce majeure par rapport à Movviz.

Torrent terminé ≠ média acquis.

```text
DOWNLOAD_COMPLETE
        ↓
enumerate files
        ↓
detect audio files
        ↓
read tags
        ↓
read codec/quality
        ↓
match expected album
        ↓
validate track count
        ↓
detect multi-disc
        ↓
detect duplicates
        ↓
optional fingerprint
        ↓
quality validation
        ↓
rename
        ↓
move/copy/hardlink
        ↓
artwork handling
        ↓
rescan
        ↓
LOCAL
```

---

# 29. Types de fichiers à connaître

Le pipeline doit savoir gérer ou ignorer correctement :

```text
.flac
.mp3
.m4a
.aac
.ogg
.opus
.wav
.alac
.cue
.log
.m3u
.m3u8
.jpg
.jpeg
.png
.webp
.pdf
.nfo
```

Les fichiers annexes ne doivent pas casser l’import.

---

# 30. Tags musicaux

Lire au minimum :

- artist ;
- album artist ;
- title ;
- album ;
- track number ;
- disc number ;
- date/year ;
- genre ;
- MusicBrainz IDs ;
- ISRC si présent ;
- embedded artwork.

Ne jamais utiliser uniquement les noms de fichiers pour identifier un album.

---

# 31. Fingerprinting

Prévoir une interface dès V1 même si l’implémentation complète arrive plus tard.

```ts
interface AudioFingerprintService {
  fingerprint(file: string): Promise<Fingerprint>;
  match(fingerprint: Fingerprint): Promise<RecordingMatch[]>;
}
```

Usages :

- détecter mauvais tags ;
- retrouver un recording ;
- détecter doublons ;
- sécuriser les imports ;
- fusionner plusieurs sources.

---

# 32. Organisation des fichiers

Exemple configurable :

```text
/music/
  Linkin Park/
    Meteora (2003)/
      01 - Foreword.flac
      02 - Don't Stay.flac
      ...
```

Multi-disc :

```text
Album/
  CD1/
  CD2/
```

ou numérotation continue selon réglage.

Ne jamais imposer un format unique.

---

# 33. Bibliothèque existante

MUZZIK doit pouvoir démarrer sur une bibliothèque existante sans rien renommer.

Modes :

```text
READ_ONLY_DISCOVERY
MANAGED
```

### READ_ONLY_DISCOVERY

- scan ;
- identification ;
- aucune modification de fichier.

### MANAGED

- import ;
- rename ;
- move ;
- artwork ;
- upgrades.

---

# 34. Scanner local

Pipeline :

```text
filesystem watcher
      +
periodic reconciliation
      ↓
MediaFile discovery
      ↓
Metadata extraction
      ↓
IdentityResolver
      ↓
Library association
```

Ne pas dépendre uniquement du filesystem watcher.

Un scan complet doit pouvoir reconstruire l’état.

---

# 35. Source de vérité

La base MUZZIK est la source de vérité applicative.

Mais elle ne doit pas inventer la présence d’un fichier.

Donc :

```text
DB says LOCAL
     +
file missing
     ↓
reconciliation
     ↓
MISSING
```

Inversement :

```text
file exists
DB doesn't know
     ↓
scan/import candidate
```

---

# 36. Métadonnées

Créer `MetadataAggregator`.

```text
MusicBrainz
YouTube Music
Local tags
Provider artwork
Last.fm éventuellement
```

Le cœur ne doit jamais dépendre de la disponibilité d’un seul service.

---

# 37. Priorités metadata

Exemple :

```text
Identity IDs       → MusicBrainz / tags fiables
Local file tags    → vérité fichier
Editorial artwork  → provider configuré
Streaming IDs      → mappings uniquement
Popularity         → provider externe/cache
```

Les règles doivent être explicites.

---

# 38. Paroles

Provider abstraction :

```ts
interface LyricsProvider {
  search(track: TrackIdentity): Promise<LyricsResult | null>;
}
```

Prévoir :

- synced lyrics ;
- plain lyrics ;
- langue ;
- source ;
- cache ;
- offset utilisateur éventuel.

Ne pas coupler au provider YouTube Music.

---

# 39. Player

Le player MUZZIK doit être une fonction de premier niveau, pas une intégration Plex.

Fonctions de base :

- play ;
- pause ;
- seek ;
- next ;
- previous ;
- shuffle ;
- repeat ;
- queue ;
- gapless si possible ;
- crossfade configurable ;
- replay gain / loudness ;
- volume normalization ;
- quality display ;
- source display ;
- device handoff à terme.

---

# 40. Queue centralisée

La queue appartient à MUZZIK.

```text
PlaybackSession
├── userId
├── deviceId
├── queue
├── currentIndex
├── position
├── shuffleState
├── repeatMode
└── radioContext
```

Le client peut mourir/recharger sans perdre forcément la session.

---

# 41. Historique d’écoute

Éviter :

```text
play lancé = morceau écouté
```

Créer des événements.

```text
PLAY_START
PLAY_PROGRESS
PLAY_COMPLETE
SKIP
REPEAT
LIKE
DISLIKE
QUEUE_ADD
SEARCH_PLAY
RADIO_PLAY
```

---

# 42. Règles statistiques

Exemple :

```text
< 10 s       → preview / accidental
10-30 s      → partial
> 50 %       → meaningful listen
> 85 %       → completed
repeat < 10m → strong affinity signal
skip < 20 s  → negative signal
```

Ces valeurs doivent rester configurables.

---

# 43. UserTaste

MUZZIK doit construire son propre profil de goût.

```text
UserTaste
├── artists
├── recordings
├── albums
├── genres
├── decades
├── moods
├── tempo preference
├── discovery tolerance
├── skip patterns
└── recency weighting
```

Ne pas dépendre d’un profil YouTube ou Plex.

---

# 44. Recommandation

Créer deux moteurs complémentaires.

## DeterministicRecommendationEngine

Basé sur :

- historique ;
- likes ;
- artistes similaires ;
- metadata ;
- genres ;
- années ;
- popularité ;
- diversité ;
- pénalité répétition ;
- disponibilité.

## AIRecommendationLayer

L’IA interprète l’intention.

Exemple :

```text
"mets-moi quelque chose d’énergique mais pas de métal,
avec surtout mes morceaux et quelques découvertes"
```

L’IA ne doit pas générer directement une liste arbitraire.

Elle produit une intention structurée.

```json
{
  "energy": "high",
  "excludeGenres": ["metal"],
  "knownRatio": 0.8,
  "discoveryRatio": 0.2
}
```

Le moteur de recommandation exécute ensuite.

---

# 45. MUZZIK AI

Réutiliser autant que possible l’architecture Movviz AI.

Concepts à conserver :

- providers IA interchangeables ;
- plusieurs clés ;
- fallback ;
- rotation ;
- intent JSON validé ;
- Action Engine ;
- mémoire utilisateur ;
- outils internes ;
- aucune hallucination sur la bibliothèque.

Actions typiques :

```text
play
queue
search
add_to_library
download
upgrade
create_playlist
recommend
like
rate
show_artist
show_album
```

Avant de dire qu’un morceau n’est pas disponible, l’IA doit interroger MUZZIK.

---

# 46. Home / Discover

Le home ne doit pas être uniquement un miroir YouTube Music.

Composition possible :

```text
Continuer l'écoute
Écoutés récemment
Albums récemment ajoutés
Parce que vous aimez X
Redécouvrir
Nouveautés des artistes suivis
Mix du jour
Découverte
Disponible en meilleure qualité
À télécharger
Tendances provider
```

Chaque rangée indique sa provenance en interne, mais l’UI reste cohérente.

---

# 47. Recherche unifiée

Une seule barre de recherche.

```text
Search
 ├── local DB
 ├── library
 ├── YouTubeMusicProvider
 ├── MusicBrainz
 └── autres providers
```

Puis fusion :

```text
IdentityResolver
      ↓
dedupe
      ↓
rank
      ↓
SearchResult
```

Un album présent localement + sur YouTube ne doit apparaître qu’une fois.

---

# 48. Ajout à la bibliothèque

Cliquer `+ Bibliothèque` ne signifie pas forcément télécharger.

Paramètre utilisateur/serveur :

```text
addPolicy:
  STREAM_ONLY
  MONITOR
  ACQUIRE_IMMEDIATELY
```

Exemple :

```text
STREAM_ONLY
→ on mémorise l’album

MONITOR
→ on mémorise + surveille

ACQUIRE_IMMEDIATELY
→ on mémorise + lance acquisition
```

---

# 49. Monitoring

Inspiré du comportement Movviz/Arr.

```text
Artist monitored
  ↓
new album detected
  ↓
release date reached
  ↓
search
  ↓
score
  ↓
grab if policy permits
```

Ne pas lancer inutilement des recherches avant date de sortie.

---

# 50. Scheduler

Réutiliser la philosophie Movviz.

Jobs possibles :

```text
provider-health
library-reconcile
metadata-refresh
monitored-releases
wanted-search
failed-download-retry
quality-upgrade-search
lyrics-refresh
recommendation-refresh
cache-cleanup
db-maintenance
```

Chaque job doit avoir :

- lock ;
- timeout ;
- retry ;
- métriques ;
- last run ;
- next run ;
- status ;
- logs.

---

# 51. Event Bus

Éviter les dépendances circulaires.

Exemples :

```text
library.item.added
acquisition.started
download.completed
import.completed
track.played
track.liked
provider.degraded
library.file.missing
quality.upgrade.available
```

Les modules écoutent les événements au lieu de s’appeler partout entre eux.

---

# 52. Jobs lourds

Les opérations suivantes doivent pouvoir tourner dans un worker :

- fingerprint ;
- analyse audio ;
- scan massif ;
- import ;
- waveform ;
- metadata bulk refresh ;
- recherche automatique ;
- IA lourde.

Le processus API doit rester réactif.

---

# 53. Plex integration

Plex se trouve uniquement dans :

```text
integrations/plex/
```

Fonctions possibles :

- découvrir une bibliothèque Plex existante ;
- mapper les ratingKeys ;
- importer éventuellement historique/favoris ;
- synchroniser scrobbles si souhaité ;
- signaler un nouveau fichier ;
- déclencher un rescan Plex ;
- éventuellement utiliser Plex comme source distante.

Mais jamais :

```text
MUZZIK Track ID = Plex ratingKey
```

et jamais :

```text
playback requires Plex
```

---

# 54. Synchronisation Plex

Chaque sync doit avoir une politique.

```text
OFF
IMPORT_ONLY
EXPORT_ONLY
BIDIRECTIONAL
```

Avec résolution de conflit explicite.

Par défaut : prudence.

---

# 55. Client web

Le client web parle uniquement à l’API MUZZIK.

Il ne parle directement ni à :

- YouTube Music ;
- Plex ;
- torrent backend ;
- indexers ;
- MusicBrainz.

Exceptions uniquement pour ressources publiques sans état si elles apportent un bénéfice clair, mais même cela doit rester rare.

---

# 56. Client Android

Le client Android est un **player/client MUZZIK**, pas un fork MetroList.

Responsabilités :

```text
UI
Media3
MediaSession
local playback cache
downloads offline autorisés
notification media
headset controls
Bluetooth
Android Auto
mise à jour automatique de l'application
```

Le serveur reste maître de :

- catalogue ;
- identité ;
- recommandations ;
- sources ;
- bibliothèque ;
- acquisition.

## 56.1 Langage visuel — inspiré de Spotify, jamais copié

Objectif explicite du produit : un effet "wow", une app qui donne l'impression d'un
produit fini et premium dès la V1, pas un client fonctionnel mais austère. Spotify sert
de référence de langage visuel au même titre que Plexamp sert de référence
d'expérience musicale (§1.4) — observation du langage d'interface publiquement visible
dans l'app, jamais de code, d'assets ou de ressources Spotify copiés.

Éléments du langage Spotify à reproduire (dans l'esprit, pas au pixel) :

- **Hero header dynamique par écran** : en haut d'une fiche album/artiste/playlist, la
  pochette occupe toute la largeur, avec un dégradé de couleur extrait de l'image
  dominante qui infuse progressivement le fond de l'écran vers le noir en descendant
  (palette dynamique — `Palette` API Android ou équivalent Compose, calculée une fois
  par pochette et mise en cache). Aucun écran ne doit avoir un fond plat identique à
  tous les autres.
- **Mini-player persistant en bas** : barre fine avec pochette miniature, titre/artiste
  qui défile si trop long, play/pause, toujours visible pendant la navigation. Tap =
  ouverture du plein écran avec une transition d'élément partagé (shared element) sur
  la pochette — jamais un simple modal qui apparaît d'un coup.
- **Plein écran lecteur** : pochette large et centrée, contrôles secondaires (paroles,
  file d'attente, appareils) accessibles par swipe horizontal ou onglets en haut du
  lecteur plutôt que des boutons empilés. Barre de progression fine, waveform ou
  simple ligne selon la densité d'info voulue.
- **Grilles et rangées horizontales scrollables** pour le Home/Discover (§46) — jamais
  une simple liste verticale uniforme comme seul mode de navigation.
- **Swipe pour retirer** un morceau de la file d'attente, swipe pour liker (bord
  gauche/droit configurable).
- **Thème sombre par défaut**, jamais de flash blanc au démarrage (splash screen avec
  fond déjà sombre, cohérent avec le thème final — API Splash Screen Android 12+).
- **Micro-animations** : le bouton play qui morph en pause, l'icône like qui pulse au
  tap, la pochette qui reprend une légère rotation/échelle pendant la lecture active —
  discret, jamais distrayant, mais présent partout où Spotify en met.

Techniquement : Jetpack Compose (déjà dans la stack §4) + Material 3 avec un thème
entièrement custom (jamais le Material par défaut visible tel quel), `Palette` pour
l'extraction de couleur dominante, `AnimatedContent`/`SharedTransitionLayout` de
Compose pour les transitions.

## 56.2 Android Auto

Intégration native via `MediaLibraryService` (Media3), pas une réimplémentation
maison — c'est exactement le rôle prévu de la stack Media3 déjà choisie (§4).

- Arborescence exposée à Android Auto : Continuer l'écoute, Bibliothèque, Playlists,
  Artistes suivis, Radios, Récemment ajoutés — même structure logique que le Home
  mobile (§46), pas une hiérarchie différente à maintenir en parallèle.
- Recherche vocale ("Joue [artiste/titre] sur MUZZIK") routée vers l'API de recherche
  unifiée du serveur (§47), résolue par le Playback Resolver (§12) exactement comme
  une recherche manuelle — aucune logique de lecture dupliquée pour Android Auto.
- UI simplifiée et conforme aux contraintes de sécurité Android Auto (grosses cibles
  tactiles, pas de texte dense, pas d'interaction complexe pendant la conduite) —
  gérée automatiquement par le template `MediaLibraryService`, pas de vue custom à
  construire à la main.
- Le mini-player/notification média (déjà nécessaire pour le téléphone) alimente
  directement Android Auto via la même `MediaSession` — pas un second pipeline de
  lecture à synchroniser.

## 56.3 Mise à jour automatique de l'application

Même philosophie que la mise à jour automatique du serveur Movviz
(`src/lib/settings/autoUpdate.ts` côté serveur — vérification périodique d'une version
distante, activable/désactivable) transposée à l'app Android, puisque MUZZIK n'est pas
distribué sur le Play Store (auto-hébergé, comme Movviz) :

```text
App Android
  ↓ périodique (ou au lancement)
GET /api/updates/android → { latestVersionCode, apkUrl, changelog }
  ↓ si latestVersionCode > version installée
Bannière "Mise à jour disponible" (jamais un blocage forcé)
  ↓ utilisateur accepte
Téléchargement APK en arrière-plan (barre de progression)
  ↓ téléchargement terminé
FileProvider + Intent ACTION_VIEW (PackageInstaller) → écran d'installation Android
```

- Le serveur MUZZIK sert l'APK (endpoint `/api/updates/android/latest.apk`) — pas de
  dépendance à un store tiers, cohérent avec le principe d'autonomie du plan (§2).
- Vérification de signature APK par le système Android lui-même (le mécanisme standard
  d'installation) — MUZZIK n'a pas à réimplémenter de vérification cryptographique.
  Toujours signer l'APK avec la même clé entre versions, sinon l'installation
  systeme refuse la mise à jour (écrase au lieu de mettre à jour).
- Jamais de mise à jour forcée/silencieuse sans confirmation utilisateur (Android ne
  permet de toute façon pas l'installation silencieuse sans permission `REQUEST_INSTALL_PACKAGES`
  explicitement accordée) — cohérent avec le principe "explicit permission required"
  déjà appliqué ailleurs dans l'écosystème de l'auteur.
- Avant de construire ce flux, vérifier l'implémentation réelle déjà en place côté
  Movviz Android (`android-mobile/`) si elle existe au moment de la Phase I — réutiliser
  plutôt que réécrire (§87.4) si un mécanisme équivalent y est déjà mature.

---

# 57. Offline mobile

Ne pas confondre :

```text
bibliothèque locale serveur
```

et :

```text
download offline appareil
```

Créer :

```text
DeviceOfflineItem
```

avec :

- deviceId ;
- recordingId ;
- downloadedAt ;
- quality ;
- expiration éventuelle ;
- storage size.

---

# 58. Sécurité

- aucune URL provider sensible persistée inutilement ;
- tokens chiffrés au repos si possible ;
- secrets hors frontend ;
- sessions séparées ;
- rate limit ;
- audit admin ;
- validation stricte des chemins ;
- prévention path traversal ;
- aucun nom torrent utilisé directement comme chemin final ;
- téléchargement isolé avant import.

---

# 59. Répertoire temporaire obligatoire

Un torrent ne doit jamais écrire directement dans `/music`.

```text
/downloads/incomplete
/downloads/complete
        ↓
verification
        ↓
/music
```

Cela évite les médias semi-téléchargés dans la bibliothèque.

---

# 60. Atomicité d’import

L’import final doit être autant que possible atomique.

```text
prepare
verify
stage
commit
```

En cas d’erreur :

```text
rollback
```

La DB ne doit jamais indiquer `LOCAL` avant la validation finale.

---

# 61. Duplicate Engine

Détecter :

- même recording en MP3 + FLAC ;
- même album deux fois ;
- deux éditions différentes ;
- doublon réel ;
- bonus track légitime.

Ne jamais supprimer automatiquement sans politique explicite.

---

# 62. Upgrade Engine

Exemple :

```text
Meteora
current = MP3 320
profile = Lossless
status = UPGRADABLE
```

Le moteur peut rechercher.

Après import FLAC :

```text
old MP3:
KEEP
ARCHIVE
DELETE_AFTER_VERIFY
```

Selon politique.

---

# 63. API

Exemples de domaines :

```text
/api/auth
/api/users
/api/search
/api/artists
/api/albums
/api/tracks
/api/library
/api/playback
/api/queue
/api/history
/api/playlists
/api/recommendations
/api/acquisition
/api/downloads
/api/import
/api/providers
/api/integrations
/api/settings
/api/ai
/api/admin/health
```

Versionner l’API.

```text
/api/v1/...
```

---

# 64. Contrats partagés

Toutes les apps utilisent :

```text
packages/contracts
```

Pour éviter des modèles différents entre web/API/Android.

Générer si nécessaire :

- OpenAPI ;
- clients TypeScript ;
- modèles Kotlin.

---

# 65. Database

SQLite par défaut est cohérent avec une installation personnelle.

Prévoir dès le schéma :

- clés UUID ;
- indexes ;
- migrations ;
- timestamps ;
- soft delete lorsque pertinent ;
- contraintes uniques sur mappings externes.

Ne pas mettre de blobs audio dans SQLite.

---

# 66. Tables conceptuelles minimales

```text
users
devices
artists
artist_aliases
album_groups
album_editions
tracks
recordings
provider_mappings
media_files
library_items
quality_profiles
playlists
playlist_items
playback_events
user_taste
availability
acquisition_jobs
release_candidates
download_jobs
import_jobs
provider_accounts
provider_health
integration_configs
scheduler_jobs
ai_memory
```

---

# 67. Provider Mapping

Table cruciale :

```text
provider_mappings
-----------------
entityType
entityId
provider
externalId
externalType
metadataJson
lastVerifiedAt
```

Permet de changer de provider sans changer les IDs MUZZIK.

---

# 68. Cache

Séparer :

```text
metadata cache
provider response cache
artwork cache
stream URL cache
audio cache
mobile offline
```

Les stream URLs expirables ne doivent jamais être considérées permanentes.

---

# 69. Observabilité

Le panneau admin doit pouvoir dire :

```text
API                OK
Database           OK
Music folder       OK
Torrent backend    OK
YouTube catalogue  OK
YouTube playback   DEGRADED
MusicBrainz        OK
LRCLIB              OK
Plex                DISABLED
```

---

# 70. Logs structurés

Format recommandé :

```json
{
  "level": "info",
  "module": "youtube-music",
  "event": "playback.resolve",
  "trackId": "...",
  "providerId": "...",
  "durationMs": 214
}
```

Masquer :

- cookies ;
- tokens ;
- secrets ;
- URLs signées sensibles.

---

# 71. Diagnostic bundles

Prévoir dès le début une fonction :

```text
Generate diagnostic bundle
```

Contenu :

- version ;
- config anonymisée ;
- health ;
- derniers logs pertinents ;
- migrations ;
- état providers ;
- état jobs.

Très utile quand une API YouTube casse.

---

# 72. Feature flags

Fonctionnalités fragiles derrière flags.

Exemples :

```text
youtubeAuthSync
youtubeRecommendations
audioFingerprint
plexSync
hiresAnalysis
aiActions
```

Une régression provider ne doit pas forcer un rollback complet de MUZZIK.

---

# 73. Fallbacks

Les fallbacks doivent être déclaratifs.

Exemple lecture :

```text
playback.policy:
  - local
  - cache
  - youtube_music
```

Exemple metadata :

```text
artwork.policy:
  - local_embedded
  - youtube_music
  - musicbrainz
```

Pas de `if provider === ...` dispersés dans le code.

---

# 74. Circuit breaker

Provider externe en panne :

```text
5 erreurs consécutives
      ↓
DEGRADED
      ↓
temporarily reduce calls
      ↓
probe
      ↓
restore
```

Évite de marteler une API cassée.

---

# 75. Rate limiting providers

Créer un rate limiter par provider.

```text
youtube_music
musicbrainz
lrclib
```

Avec queue et backoff.

---

# 76. Résilience

Aucune tâche externe ne doit bloquer une requête utilisateur plus longtemps que nécessaire.

Exemple :

```text
album page
├── DB immédiatement
├── artwork cache
└── metadata refresh async
```

Ne pas rafraîchir tout le catalogue avant d’afficher une page.

---

# 77. UX : une fiche unique

Un album ne doit jamais avoir :

```text
fiche YouTube
fiche local
fiche Plex
```

Il possède une seule fiche MUZZIK.

Elle montre ensuite :

```text
Disponible localement
FLAC 16/44.1

Streaming
YouTube Music

Plex
Synchronisé
```

---

# 78. UX : actions contextuelles

Exemple album :

```text
▶ Lire
+ Bibliothèque
Télécharger
Améliorer la qualité
Ajouter à une playlist
Démarrer une radio
Infos techniques
```

Les boutons s’adaptent à l’état réel.

---

# 79. UX : téléchargement invisible quand inutile

Si l’utilisateur veut juste écouter, il clique Play.

MUZZIK résout automatiquement la meilleure source.

L’acquisition est un choix supplémentaire.

C’est précisément ce qui distingue MUZZIK d’un frontend Arr.

---

# 80. UX : qualité visible sans envahir

Player :

```text
FLAC • 16-bit • 44.1 kHz • Local
```

ou :

```text
AAC • Streaming
```

Un clic ouvre les détails.

---

# 81. Flux complet — morceau streaming

```text
User clicks Play
      ↓
recordingId
      ↓
Availability Engine
      ↓
no local file
      ↓
YoutubeMusicProvider
      ↓
resolve current playable stream
      ↓
PlaybackSource
      ↓
client player
      ↓
PlaybackEvents
```

---

# 82. Flux complet — album local

```text
User clicks Play
      ↓
recordingId
      ↓
Availability Engine
      ↓
local FLAC found
      ↓
MUZZIK media endpoint
      ↓
HTTP range
      ↓
client player
```

Pas de Plex.

---

# 83. Flux complet — acquisition

```text
User clicks Download
      ↓
AcquisitionRequest
      ↓
SearchCoordinator
      ↓
Indexer(s)
      ↓
Candidates
      ↓
Parser
      ↓
Scorer
      ↓
Decision
      ↓
TorrentBackend.add
      ↓
Download monitor
      ↓
Complete
      ↓
Import pipeline
      ↓
Identity validation
      ↓
Library commit
      ↓
Availability = LOCAL
```

---

# 84. Flux complet — upgrade

```text
Current: MP3 320
Profile target: FLAC
      ↓
Upgrade scheduler
      ↓
search
      ↓
FLAC candidate
      ↓
download/import
      ↓
verify
      ↓
activate new MediaFile
      ↓
old file policy
```

---

# 85. Reverse engineering MetroList — procédure

Créer un document séparé pendant l’implémentation :

```text
docs/reverse-engineering/metrolist/
```

Pour chaque capacité :

```text
SEARCH
BROWSE
PLAYER
HOME
RECOMMENDATIONS
AUTH
PLAYLIST
```

Documenter uniquement :

- endpoint observé ;
- type de requête ;
- paramètres nécessaires ;
- contexte client ;
- classe de réponse ;
- erreurs ;
- conditions ;
- expirations ;
- comportement fonctionnel.

Puis implémenter côté MUZZIK.

Ne pas transcrire des classes MetroList.

---

# 86. Reverse engineering Lidarr — procédure

Même approche, mais principalement fonctionnelle.

Créer :

```text
docs/reverse-engineering/lidarr/
```

Checklist :

```text
monitoring
release decisions
quality
cutoff
failed download
manual search
import
track matching
multi-disc
rename
upgrade
```

Puis comparer au moteur Movviz.

Si Movviz couvre déjà le besoin :

```text
réutiliser Movviz
```

Sinon :

```text
ajouter la capacité au module MUZZIK correspondant
```

---

# 87. Règle "ne pas réinventer la roue"

Pour chaque fonctionnalité :

```text
1. Movviz possède-t-il déjà le mécanisme ?
   YES → réutiliser/refactoriser

2. Est-ce spécifique à YouTube Music ?
   YES → étudier MetroList puis implémenter provider MUZZIK

3. Est-ce spécifique aux bibliothèques musicales ?
   YES → étudier Lidarr/Plexamp/standards puis implémenter

4. Existe-t-il une librairie mature avec licence adaptée ?
   YES → utiliser la librairie plutôt que la réécrire
```

---

# 88. Mais ne pas importer une architecture étrangère entière

Exemple :

Besoin :

```text
Lidarr sait importer un album
```

Mauvaise décision :

```text
embarquer Lidarr
```

Bonne décision :

```text
comprendre les cas métier
+
écrire MusicImportPipeline
dans le core MUZZIK
```

---

# 89. Code partagé Movviz / MUZZIK

À terme, si plusieurs composants deviennent réellement communs, créer éventuellement des packages indépendants.

Exemples :

```text
@movviz/core-jobs
@movviz/torrent-engine
@movviz/provider-runtime
@movviz/ai-engine
@movviz/auth
```

Mais ne pas lancer cette extraction avant d’avoir identifié une vraie duplication stable.

Éviter un refactor massif de Movviz juste pour MUZZIK V1.

---

# 90. Isolation des domaines

Interdit :

```text
YoutubeMusicProvider imports TorrentBackend
TorrentBackend imports PlaylistService
PlexIntegration modifies Track table directly
```

Correct :

```text
Provider → contracts
Acquisition → domain services
Integration → application API
```

---

# 91. Tests indispensables

## Unit

- parsers ;
- scoring ;
- identity ;
- quality ;
- state machine ;
- policy.

## Integration

- provider responses enregistrées ;
- DB ;
- local filesystem ;
- torrent backend mock ;
- import.

## End-to-end

```text
search → play streaming
search → add library
download → import → play local
upgrade
provider failure → fallback
Plex disabled
```

---

# 92. Contract fixtures pour YouTube Music

Les APIs internes changent.

Conserver des réponses anonymisées comme fixtures de test.

```text
fixtures/youtube-music/search/
fixtures/youtube-music/player/
fixtures/youtube-music/browse/
```

Lorsque YouTube change :

```text
nouvelle fixture
→ mapper fix
→ tests
```

---

# 93. Golden tests du parser torrent

Conserver des centaines de noms de releases réelles anonymisées ou publiques.

```text
input
expected parsed structure
expected score
```

Chaque correction enrichit la suite de tests.

---

# 94. Non-régression absolue

Une modification d’un provider ne doit pas casser :

- bibliothèque locale ;
- player local ;
- acquisition ;
- autres providers ;
- login ;
- playlists.

Une modification du torrent engine ne doit pas casser le streaming.

Une modification Plex ne doit pas casser MUZZIK.

---

# 95. Ordre d’implémentation recommandé

## Phase A — Fondation

Construire :

- monorepo ;
- DB ;
- auth ;
- users ;
- settings ;
- event bus ;
- scheduler ;
- contracts ;
- provider runtime ;
- health.

Livrable :

```text
MUZZIK boot
login
settings
health
empty library
```

---

## Phase B — Catalogue YouTube Music

Construire le provider par reverse engineering fonctionnel.

Minimum :

- search ;
- artist ;
- album ;
- track ;
- playlist ;
- playback resolution.

Livrable :

```text
search song
open album
press play
hear audio
```

Sans compte Google.

C’est le premier jalon produit majeur.

---

## Phase C — Bibliothèque locale

- scanner ;
- tags ;
- files ;
- identity ;
- mapping catalogue/local ;
- HTTP range player ;
- artwork.

Livrable :

```text
same song:
stream source + local source
MUZZIK chooses local
```

---

## Phase D — Library UX

- add/remove ;
- monitored ;
- album states ;
- artist states ;
- favorites ;
- playlists ;
- history.

---

## Phase E — Acquisition Movviz

Porter/réutiliser :

- torrent engine ;
- backend abstraction ;
- queue ;
- search ;
- indexers ;
- scheduler patterns ;
- retry ;
- logs.

Puis ajouter :

- MusicReleaseParser ;
- MusicReleaseScorer ;
- QualityProfile.

---

## Phase F — Import musique

- tags ;
- track matching ;
- multi-disc ;
- validation ;
- staging ;
- rename ;
- move ;
- duplicate handling ;
- rescan.

Livrable :

```text
Download album
→ automatic import
→ album becomes LOCAL
```

---

## Phase G — Recommendations

- taste model ;
- radio ;
- artist mix ;
- discovery ;
- deterministic engine.

---

## Phase H — MUZZIK AI

Réutiliser moteur Movviz AI.

Donner accès aux outils MUZZIK.

---

## Phase I — Android

Seulement lorsque les contrats serveur/player sont suffisamment stables.

---

## Phase J — Plex

**Après** que MUZZIK fonctionne parfaitement sans Plex.

Ajouter Plex en intégration.

Cela force architecturalement Plex à rester optionnel.

---

# 96. Critères de V1

V1 n’a pas besoin de tout.

Elle doit cependant prouver l’architecture.

MUZZIK V1 est valide si :

```text
✓ installation standalone
✓ aucun Plex nécessaire
✓ recherche catalogue
✓ lecture YouTube Music
✓ lecture fichiers locaux
✓ bibliothèque propre
✓ identité fusionnée
✓ ajout album
✓ acquisition torrent
✓ import album
✓ qualité
✓ playlists
✓ historique
✓ plusieurs utilisateurs
✓ provider health
✓ Docker
```

---

# 97. Ce qui peut attendre V1.x

- Plex ;
- Last.fm ;
- ListenBrainz ;
- Android TV ;
- Android Auto ;
- fingerprint avancé ;
- sonic similarity ;
- waveform ;
- casting ;
- multi-server federation ;
- hi-res DSP ;
- lyrics editing ;
- social/listen together.

---

# 98. Règles interdites

Ces règles doivent être données à tout agent travaillant sur MUZZIK.

## INTERDIT 1

Ne jamais faire de Plex une dépendance.

## INTERDIT 2

Ne jamais stocker un ID YouTube/Plex comme ID canonique MUZZIK.

## INTERDIT 3

Ne jamais appeler InnerTube directement depuis l’UI.

## INTERDIT 4

Ne jamais copier du code MetroList ou Lidarr.

## INTERDIT 5

Ne jamais ajouter aria2.

## INTERDIT 6

Ne jamais écrire un téléchargement directement dans `/music`.

## INTERDIT 7

Ne jamais considérer `torrent completed` comme `album imported`.

## INTERDIT 8

Ne jamais dupliquer la même entité parce qu’elle vient de deux providers.

## INTERDIT 9

Ne jamais introduire un provider sans interface/adapter.

## INTERDIT 10

Ne jamais casser le fonctionnement local lorsqu’un provider externe tombe.

---

# 99. Definition of Done d’un module

Un module est terminé seulement si :

```text
code
+
tests
+
health
+
logs
+
config
+
error states
+
fallback behavior
+
documentation
```

Pas seulement si le happy path fonctionne.

---

# 100. Vision cible

L’expérience finale doit être :

```text
Je cherche n’importe quel artiste
        ↓
MUZZIK le trouve
        ↓
Je peux écouter immédiatement
        ↓
Je peux l’ajouter à ma bibliothèque
        ↓
Je peux demander une vraie copie locale
        ↓
MUZZIK cherche la meilleure release
        ↓
MUZZIK télécharge
        ↓
MUZZIK vérifie et importe
        ↓
La prochaine lecture utilise automatiquement le fichier local
        ↓
MUZZIK apprend mes goûts
        ↓
MUZZIK construit mes radios et recommandations
```

Sans que l’utilisateur ait besoin de savoir si, derrière, le morceau provient de :

- YouTube Music ;
- un FLAC local ;
- un cache ;
- un torrent nouvellement importé ;
- Plex ;
- ou un futur provider.

La provenance est une propriété technique.

**Le morceau, l’album, la bibliothèque et l’expérience appartiennent à MUZZIK.**

---

# 101. Architecture résumée en une phrase

> **MUZZIK doit reprendre le cerveau, l’automatisation et les briques réutilisables de Movviz, réimplémenter proprement un provider YouTube Music à partir de l’observation de MetroList, utiliser Lidarr comme documentation des cas métier musicaux, s’inspirer de Plexamp pour l’expérience, et rester entièrement autonome vis-à-vis de Plex et de toute autre application externe.**

---

# 102. Références techniques étudiées

- Movviz : `https://github.com/dj41ph4/movviz`
- MetroList : `https://github.com/MetrolistGroup/Metrolist`
- Lidarr : `https://github.com/Lidarr/Lidarr`

Ces projets sont des références techniques/fonctionnelles.  
Pour MetroList et Lidarr, MUZZIK doit utiliser une approche de **réimplémentation indépendante** et ne pas copier leur code source.

---

# 103. Import Providers (Spotify, Deezer, Apple Music...)

Au même titre que YouTube Music est un `MusicProvider` (catalogue + lecture), MUZZIK doit
prévoir une famille de **providers d'import** : des services tiers dont on ne consomme
que la bibliothèque personnelle de l'utilisateur (playlists, titres likés, artistes
suivis, historique si l'API l'expose), pas le catalogue ni la lecture.

## Pourquoi une catégorie séparée

Un import Spotify/Deezer ne doit **jamais** devenir une source de lecture ni une source
de catalogue au même titre que YouTube Music. Ces services n'exposent pas de flux audio
public/gratuit exploitable par MUZZIK. Leur seul rôle : **peupler la bibliothèque MUZZIK
à partir de ce que l'utilisateur possédait déjà ailleurs**, puis laisser
l'`IdentityResolver` et le `Playback Resolver` habituels décider comment chaque morceau
sera réellement lu (YouTube Music, local, etc.).

## Interface

```ts
interface LibraryImportProvider {
  id: "spotify" | "deezer" | "applemusic" | "youtubemusic" | string;

  capabilities(): ImportCapabilities; // playlists, likedTracks, followedArtists, history?

  authenticate(context: UserContext): Promise<ImportSession>;

  listPlaylists(session: ImportSession): Promise<ExternalPlaylist[]>;
  listLikedTracks(session: ImportSession): Promise<ExternalTrack[]>;
  listFollowedArtists?(session: ImportSession): Promise<ExternalArtist[]>;
}
```

## Règles

1. Authentification via OAuth officiel de chaque plateforme uniquement — jamais de
   scraping de session privée ni de reverse engineering d'API non publique pour ces
   providers (contrairement à YouTube Music, qui est traité §1.2/§13 comme un cas à part
   parce qu'il est aussi la source de lecture principale de MUZZIK).
2. Chaque titre importé passe par l'`IdentityResolver` (§7) exactement comme un résultat
   de recherche — un ID Spotify/Deezer devient un `provider_mappings` de plus, jamais un
   identifiant canonique MUZZIK (mêmes règles que INTERDIT 2).
3. Un titre non résolu avec confiance suffisante reste `UNRESOLVED` plutôt que d'être
   fusionné à l'aveugle (règle IdentityResolver, voir INTERDIT 11 §104).
4. L'import crée des `LibraryItem` avec `addPolicy = STREAM_ONLY` par défaut (§48) — un
   import Spotify de 3000 titres ne doit pas déclencher 3000 recherches torrent
   automatiques. L'acquisition reste un choix explicite de l'utilisateur, même après
   import massif.
5. Import incrémental : une resynchronisation périodique optionnelle détecte les ajouts
   côté service tiers, jamais les suppressions (même prudence que la règle de sync Plex
   §53 — ne jamais supprimer automatiquement une entrée MUZZIK parce qu'elle a disparu
   d'une playlist Spotify).
6. Un rapport d'import doit être visible : X titres importés, Y résolus automatiquement,
   Z non résolus à traiter manuellement. Ne jamais importer silencieusement.

## Emplacement dans le monorepo

```text
providers/
  spotify-import/
  deezer-import/
  applemusic-import/
```

Isolés comme n'importe quel autre provider (règle §90 — un provider ne doit jamais être
imcompatible avec l'absence des autres).

## Phasage

Ce n'est pas un prérequis du vertical slice V1 (§95 Phase B/C), mais s'intègre
naturellement à la **Phase D — Library UX** une fois que `LibraryItem`, l'`IdentityResolver`
et l'écran de bibliothèque existent réellement — importer avant d'avoir un moteur de
résolution fiable ne ferait que produire des doublons en masse.

---

# 104. Clarifications issues de la revue d'architecture

## 104.1 MusicBrainz = identité, pas catalogue principal

Le catalogue MUZZIK agrège plusieurs sources, chacune avec un rôle distinct :

```text
            MUZZIK ENTITY
                 │
      ┌──────────┼──────────┐
      │          │          │
 MusicBrainz    YTM       Local tags
 identité     éditorial    vérité fichier
 releases     découverte
```

MusicBrainz sert de référentiel d'identité canonique et de releases, YouTube Music sert
de contenu éditorial/découverte/lecture, les tags locaux font foi pour ce que
l'utilisateur possède réellement. Aucune des trois ne doit devenir *la* source unique —
c'est précisément le rôle du `MetadataAggregator` (§36) et de l'`IdentityResolver` (§7)
de réconcilier les trois sans en privilégier une par défaut.

## INTERDIT 11 — IdentityResolver et fusion incertaine

Ajout à la liste §98 : **ne jamais fusionner deux identités sous le seuil de confiance
configuré.** "Numb", "Numb - 2003 Remaster", "Numb (Official Audio)", "Numb [Explicit]"
ne doivent pas être fusionnés aveuglément parce que titre + durée sont proches.
L'`IdentityResolver` doit :

- retourner un score de confiance explicite, jamais un simple booléen `match`/`no match` ;
- accepter qu'une identité reste `UNRESOLVED` plutôt que de risquer une mauvaise fusion ;
- exposer les cas `UNRESOLVED` dans une file de révision (admin ou utilisateur), pas les
  cacher silencieusement.

Une mauvaise fusion coûte plus cher qu'un doublon visible : un doublon se nettoie, une
fusion incorrecte pollue durablement l'historique d'écoute et les recommandations.

---

# 105. Leçons apprises de Movviz — à ne pas reproduire

Constats tirés du code et de l'historique réels de Movviz (`les règles opérationnelles du projet`, `AGENTS.md`,
`CHANGELOG.md`, `TODO_POST_MOTEUR_LECTURE.md`). Chaque point a coûté un bug en
production ou un incident réel — MUZZIK part avec ces règles dès le premier commit au
lieu de les redécouvrir.

## 105.1 Store JSON : jamais d'accès fichier direct

Movviz a découvert (douloureusement) que chaque route API qui re-lit et re-parse son
fichier JSON à chaque appel devient, à l'échelle d'une vraie bibliothèque, un goulot
CPU qui bloque tout le process. Règle : toute lecture passe par `readJsonCached()`
(cache mémoire validé par mtime/size), toute écriture par `writeJsonCached()` (écriture
atomique tmp+rename, jamais `fs.writeFileSync` direct). Ce module est porté tel quel
depuis Movviz (`src/lib/fsJsonCache.ts`) — voir §105.9.

Un deuxième piège découvert en production : ne jamais réécrire un store avec une valeur
de fallback après un échec de lecture (JSON corrompu, glitch I/O sur NAS) — ça écrase
silencieusement les vraies données. Le module distingue "fichier absent" de "échec de
parsing" (`jsonCacheReadFailed()`), et un store ne doit jamais persister le fallback
dans ce second cas.

## 105.2 État partagé : toujours ancré sur `globalThis`

Next.js compile chaque route API dans un bundle séparé — une variable de module normale
existe une fois par bundle, pas une fois par process. Tout cache, tout ring buffer de
logs, tout compteur partagé doit être ancré `globalThis.__muzzikXxx ??= ...`, jamais une
simple variable de module.

## 105.3 Pas de worker/complexité avant qu'un vrai besoin de volumétrie existe

Movviz n'a ajouté un pool `worker_threads` pour les écritures JSON qu'une fois qu'un
store réel a dépassé le seuil où `JSON.stringify` bloquait le thread principal
plusieurs dizaines de ms. MUZZIK part avec la version simple (écriture inline coalescée)
et n'introduit l'offload worker que si un store dépasse effectivement ~1 Mo — ne pas
préconstruire cette complexité pour une bibliothèque musicale qui démarre à zéro morceau.

## 105.4 `next.config.ts` : le piège du tracing de build (bug réel, 446 Go)

Tout appel `fs.readdirSync`/`fs.readFile` avec un chemin non statique (construit depuis
`CONFIG_DIR`/`MUSIC_DIR`) fait que le traceur de fichiers de Next.js inclut TOUT le
dossier contenant comme dépendance du bundle standalone. Sur Movviz, ça a produit un
build de 446 Go (bibliothèque Plex entière aspirée dans `.next/standalone`) avant que
`outputFileTracingExcludes` couvre explicitement `/music`, `/downloads`, `/muzzik-data`,
`engine/`, à toutes les profondeurs relatives (`../`, `../../`, etc. — la résolution est
relative au dossier de chaque route, pas à la racine du repo). MUZZIK doit configurer ces
exclusions dès le `next.config.ts` initial, pas après avoir eu le même incident.

## 105.5 Vérification post-modification : le typecheck ne suffit pas

Règle absolue adoptée par Movviz après plusieurs bugs qui passaient `tsc --noEmit` sans
problème mais cassaient en usage réel : après toute modification, tracer le flux complet
sur un exemple concret (un vrai morceau, un vrai fichier) avant de dire "c'est fait". Si
une seule étape du tracé échoue, le code est cassé — pas de correctif déclaré terminé sur
la seule foi du compilateur. Cette règle s'ajoute à la Definition of Done (§99).

## 105.6 Ne jamais faire confiance à une capacité "compilée" plutôt que "réellement disponible"

Bug réel : `serverCapabilities.detect.ts` listait les encodeurs compilés dans le binaire
ffmpeg sans vérifier si le matériel derrière existait vraiment (ex. `av1_qsv` compilé
mais aucun périphérique Quick Sync exposé au conteneur Docker) — le moteur choisissait
l'encodeur, puis plantait au runtime, sans repli. Corrigé en testant une exécution réelle
minimale (micro-encodage) plutôt que la seule liste déclarée. Pour MUZZIK : la même
prudence s'applique à toute détection de capacité (fingerprint audio, décodeurs, backends
torrent) — sonder l'exécution réelle, pas seulement la présence déclarée.

## 105.7 Repli en cascade, pas en un seul saut

Un échec d'exécution (transcodage, résolution de lecture) doit dégrader progressivement
(qualité inférieure, source alternative) avant de tomber en erreur fatale ou de basculer
sur un fallback lourd. Movviz a laissé cette cascade à moitié câblée (`recordFallbackAttempt()`
jamais appelé) — MUZZIK doit la construire dès le `Playback Resolver` (§12), pas l'ajouter
après coup.

## 105.8 Sécurité fichier : leçons directement applicables

- Toute suppression récursive doit vérifier la profondeur du chemin ET un
  `startsWith(dossierAttendu)` avant `rm`/`rmSync` récursif.
- Tout nom issu d'un torrent/indexer doit être assaini (`/`, `\`, `:`, `..` retirés)
  avant usage comme segment de chemin — cf. INTERDIT existant sur le path traversal
  (§58), confirmé par un vrai correctif Movviz (`finishTorrent` côté engine).
- SSRF : toute URL fournie par la configuration (serveur Plex, indexer) doit être
  validée (schéma http/https uniquement, IP privées/locales bloquées) avant `fetch`.
- Seuls les admins peuvent déclencher une suppression ; toute action destructive
  affiche une confirmation.

## 105.9 Réutilisation directe actée

Conformément à §1.1 (réutilisation Movviz autorisée et souhaitée), les modules suivants
sont portés tel quel ou quasi tel quel dès le bootstrap MUZZIK plutôt que réécrits :

- `fsJsonCache.ts` (store JSON cache/atomique) — porté directement, renommé
  `__muzzik*` au lieu de `__movviz*`.
- Pattern `readJsonCached`/`writeJsonCached` pour **tout** nouveau store MUZZIK
  (`library_items`, `provider_mappings`, `acquisition_jobs`...) — jamais d'accès fichier
  brut, dès le premier store écrit.
- Pattern journal borné + flush disque debouncé (`searchLog`/`statusTransitions` côté
  Movviz) pour tout futur journal MUZZIK (historique d'état d'acquisition, décisions de
  scoring).

Auth, scheduler, jobs et event bus Movviz sont candidats à un portage ultérieur une fois
la Phase A entamée — non faits dans ce premier bootstrap pour rester sur un vertical
slice court (§95), mais explicitement le prochain chantier de réutilisation.

---

