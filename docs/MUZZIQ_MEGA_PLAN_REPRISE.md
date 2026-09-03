# MuzziQ — Mega-plan de reprise et de complétion

## 0. Objectif

Ce document reprend le plan média unifié existant et le transforme en plan d’exécution
centré sur l’état réel du dépôt MuzziQ.

Objectif produit :

```text
installer MuzziQ
  ↓
utiliser l'application sans serveur si souhaité
  ↓
rechercher et écouter YouTube Music
  ↓
lire les fichiers locaux
  ↓
ajouter des morceaux/albums à la bibliothèque
  ↓
obtenir une copie locale de meilleure qualité si nécessaire
  ↓
importer proprement cette copie
  ↓
la lire automatiquement en priorité
  ↓
apprendre les habitudes d'écoute
  ↓
proposer des recommandations et des radios
```

Le serveur reste une capacité optionnelle pour le mode standalone, mais devient le
centre de catalogue, de bibliothèque et d'acquisition lorsqu'il est utilisé.

---

## 1. État réel au 03/09/2026

### 1.1 Déjà présent

- serveur Next.js, Docker et routes API principales ;
- authentification locale, session, rôles et réglages ;
- bibliothèque locale : scan, fichiers, artistes, albums, playlists ;
- recherche YouTube Music côté serveur ;
- résolution de lecture serveur avec fallback externe ;
- indexers Torznab, recherche de releases, parser et scorer ;
- téléchargement torrent, staging et import musical partiel ;
- profils de qualité et logique d'upgrade partielle ;
- recommandations déterministes de base ;
- providers IA, configuration, fallback et actions initiales ;
- intégration Plex : OAuth, serveurs, sections, synchronisation et historique ;
- app Android avec Media3, MediaSession, queue, favoris, playlists et historique ;
- mode standalone local MediaStore ;
- mise à jour Android déclenchée par tag Git ;
- première liaison Android vers recherche et résolution YouTube Music directe.

### 1.2 Partiellement présent

- le provider YouTube Music ne couvre pas encore toutes les opérations browse ;
- le déchiffrement des URLs YouTube n'est pas uniformément fiable ;
- le streaming Android direct est branché mais non validé sur appareil réel ;
- la source standalone mélange désormais local et YouTube, mais ne possède pas encore
  un cache, une gestion complète des URLs expirantes ni un health-check dédié ;
- le registre de providers existe mais reste principalement utilisé en mode exclusif ;
- Room est posé pour plusieurs fonctions, alors que certaines données utilisent encore
  SQLiteOpenHelper ou des stores JSON ;
- l'import musical existe mais n'est pas encore un pipeline entièrement transactionnel ;
- Plex fonctionne comme intégration, mais la fusion profil/média n'est pas complète ;
- l'interface Android est avancée mais plusieurs parcours restent à vérifier sur appareil.

### 1.3 Non terminé

- lecture YouTube Music standalone réellement validée ;
- catalogue YouTube Music complet : artiste, album, playlist, browse et détails ;
- cache et stratégie de santé provider ;
- identité canonique et dédoublonnage cross-provider dans tous les parcours ;
- téléchargement offline Android réel ;
- synchronisation standalone vers serveur ;
- paroles ;
- radio et recommandations avancées ;
- Event Bus et Scheduler robustes ;
- Duplicate Engine et Upgrade Engine complets ;
- contrats partagés TypeScript/Kotlin/OpenAPI ;
- tests E2E et fixtures API ;
- diagnostic bundles et observabilité complète.

---

## 2. Règles absolues

### 2.1 Autonomie

- Le mode standalone ne doit jamais exiger un serveur MuzziQ.
- Une panne YouTube ne doit pas casser la bibliothèque locale.
- Une panne Plex ne doit pas casser MuzziQ.
- Une panne d'un provider ne doit pas empêcher les autres providers de fonctionner.

### 2.2 Identité

- Un ID YouTube, Plex ou Spotify n'est jamais un ID canonique MuzziQ.
- Toute fusion passe par `provider_mappings` et `IdentityResolver`.
- Un résultat incertain reste `UNRESOLVED` ; aucune fusion silencieuse.
- Un titre local et un titre distant peuvent représenter le même Recording sans être
  le même Track source.

### 2.3 Lecture

Ordre cible :

```text
fichier local conforme
  ↓
fichier local inférieur
  ↓
cache audio local
  ↓
YouTube Music direct
  ↓
serveur MuzziQ si mode lié
  ↓
provider de secours autorisé
```

Les URLs de flux sont temporaires. Elles ne doivent jamais être considérées comme des
fichiers permanents ni persistées sans expiration.

### 2.4 Acquisition

- `torrent completed` ne signifie jamais `album imported` ;
- aucun téléchargement incomplet ne va directement dans le dossier musique ;
- toute importation passe par staging, vérification, matching et commit atomique ;
- un fichier n'est déclaré local qu'après validation complète ;
- aucune suppression automatique sans politique explicite.

### 2.5 Synchronisation

- LWW se fait par champ, jamais sur une ligne entière.
- L'historique est append-only et n'est jamais écrasé par une synchronisation.
- Une absence côté provider ne signifie pas automatiquement suppression.
- Chaque mutation synchronisée possède un identifiant stable et une provenance.
- Aucun replay aveugle d'une mutation déjà appliquée.

---

## 3. Architecture cible

```text
Web / Android standalone / Android lié
                 ↓
         contrats communs
                 ↓
   catalogue · bibliothèque · lecture
                 ↓
 Identity · Availability · Policy
                 ↓
   providers · acquisition · imports
                 ↓
          stockage MuzziQ
```

### 3.1 Domaines

```text
src/lib/contracts
src/lib/identity
src/lib/library
src/lib/recommendations
src/lib/acquisition
src/lib/ai
src/lib/integrations
src/providers
android-mobile/app/src/main/java/com/muzziq/mobile/domain
android-mobile/app/src/main/java/com/muzziq/mobile/providers
```

Les providers exposent des contrats. Ils ne modifient pas directement les tables d'un
autre domaine.

### 3.2 Entités canoniques

```text
Artist
AlbumGroup
AlbumEdition
Track
Recording
MediaFile
LibraryItem
Playlist
PlaybackEvent
ProviderMapping
Availability
AcquisitionJob
ImportJob
UserTaste
ProviderHealth
```

---

## 4. Priorité P0 — rendre Android standalone réellement fiable

### 4.1 Source Android

Fichiers concernés :

```text
android-mobile/.../standalone/StandaloneMusicSource.kt
android-mobile/.../providers/youtube/
android-mobile/.../data/model/Models.kt
android-mobile/.../playback/PlaybackService.kt
android-mobile/.../playback/MusicSourceLocator.kt
```

À terminer :

- client InnerTube local avec contexte client stable ;
- recherche YouTube Music et parsing robuste des résultats ;
- résolution player audio ;
- déchiffrement de signature avec fallback contrôlé ;
- gestion PoToken lorsque requis ;
- sélection de format audio ;
- validation stricte de domaine et d'expiration ;
- nouvelle résolution automatique après expiration ;
- headers nécessaires transmis à Media3 ;
- gestion d'erreur lisible pour l'utilisateur ;
- cache mémoire court des résolutions ;
- nettoyage des clients HTTP et WebView au cycle de vie approprié.

### 4.2 Intégration fonctionnelle

Le parcours obligatoire est :

```text
standalone
  → saisir une recherche
  → obtenir des titres YouTube
  → sélectionner un titre
  → résoudre le flux sur Android
  → jouer avec Media3
  → pause / reprise / seek
  → suivant / précédent
  → verrouiller l'écran
  → reprendre depuis notification
```

### 4.3 Protection contre les régressions

- le provider YouTube doit être séparé de la source locale ;
- la source locale reste jouable sans réseau ;
- aucun bouton ne doit apparaître comme disponible si la résolution est connue comme
  indisponible ;
- tous les résultats de recherche portent une source explicite ;
- les pistes YouTube doivent survivre à la persistance de queue et playlist.

### 4.4 Tests Android obligatoires

- test unitaire du parsing de recherche ;
- test unitaire du choix de format ;
- test des URLs expirées ;
- test de fallback local ;
- test de queue avec source locale et YouTube ;
- test sur appareil réel : écran éteint, arrière-plan, Bluetooth et Android Auto ;
- test réseau lent, hors ligne et changement Wi-Fi/mobile.

---

## 5. Priorité P0 — stabiliser le provider YouTube Music serveur

### 5.1 Services à compléter

```text
InnerTubeClient
ClientContextManager
VisitorDataManager
SearchService
BrowseService
ArtistService
AlbumService
PlaylistService
PlayerService
StreamResolver
CipherResolver
PoTokenManager
ProviderCache
ProviderHealth
ResponseMapper
```

### 5.2 Contrats minimaux

```ts
interface MusicProvider {
  capabilities(): ProviderCapabilities;
  search(query: SearchQuery): Promise<SearchResult>;
  getTrack(id: string): Promise<ExternalTrack>;
  getArtist(id: string): Promise<ExternalArtist>;
  getAlbum(id: string): Promise<ExternalAlbum>;
  getPlaylist(id: string): Promise<ExternalPlaylist>;
  resolvePlayback(id: string, context: PlaybackContext): Promise<ExternalPlaybackSource>;
}
```

### 5.3 Santé provider

Probes séparées :

```text
search
browse
player
cipher
PoToken
authenticated session
```

États : `OK`, `DEGRADED`, `BROKEN`, `AUTH_REQUIRED`, `RATE_LIMITED`.

Chaque erreur doit enregistrer le provider, l'opération, la durée, le statut et la
cause normalisée, sans cookie, token ni URL signée.

---

## 6. Priorité P1 — modèle média unifié

### 6.1 Bibliothèque

Compléter :

- ajout et retrait d'artiste, album et morceau ;
- politiques `STREAM_ONLY`, `MONITOR`, `ACQUIRE_IMMEDIATELY` ;
- statut `WANTED`, `PARTIAL`, `AVAILABLE`, `UPGRADABLE`, `FAILED` ;
- provenance et date de dernière vérification ;
- preferred edition et quality profile ;
- suppression logique et restauration.

### 6.2 IdentityResolver

Ajouter des tests et règles pour :

- variantes de titre ;
- featuring ;
- remasters ;
- éditions deluxe ;
- multi-disc ;
- singles et albums ;
- artistes homonymes ;
- durée et numéro de piste ;
- correspondance local ↔ YouTube ;
- correspondance import provider ↔ catalogue.

### 6.3 Dédoublonnage

Créer un `DuplicateEngine` qui distingue :

- doublon exact ;
- qualité différente ;
- édition différente ;
- bonus track légitime ;
- variante live ;
- fichier mal tagué.

Le moteur propose une action ; il ne supprime jamais seul.

---

## 7. Priorité P1 — acquisition et import musical

### 7.1 Pipeline cible

```text
search indexer
  ↓
parse release
  ↓
score quality profile
  ↓
create acquisition job
  ↓
download incomplete
  ↓
verify torrent/files
  ↓
stage import
  ↓
read tags
  ↓
match tracks
  ↓
detect duplicates
  ↓
atomic commit
  ↓
rescan library
```

### 7.2 À terminer

- jobs persistants avec reprise après redémarrage ;
- retry borné et backoff ;
- annulation explicite ;
- progression fiable ;
- validation de taille et codec ;
- matching par album/disc/track ;
- renommage configurable ;
- rollback complet ;
- import partiel clairement signalé ;
- upgrade d'une qualité existante ;
- conservation ou archivage de l'ancien fichier selon politique.

### 7.3 Tests d'acceptation

- album complet ;
- album multi-disc ;
- release mal nommée ;
- track manquante ;
- fichier corrompu ;
- doublon ;
- upgrade FLAC depuis MP3 ;
- redémarrage pendant téléchargement ;
- redémarrage pendant import.

---

## 8. Priorité P1 — offline et synchronisation Android

### 8.1 Offline standalone

Créer un vrai modèle local :

```text
DeviceOfflineItem
├── provider
├── externalId
├── localPath
├── downloadedAt
├── quality
├── size
└── state
```

À implémenter : téléchargement en arrière-plan, reprise, espace disponible, annulation,
suppression utilisateur, lecture locale prioritaire et migration de version.

### 8.2 Synchronisation standalone → serveur

Cycle ponctuel :

```text
connexion serveur
  ↓
prévisualisation des conflits
  ↓
fusion bibliothèque
  ↓
fusion historique
  ↓
fusion affinités
  ↓
rapport visible
  ↓
mode lié
```

Les fichiers locaux ne sont jamais supprimés par cette synchronisation.

---

## 9. Priorité P1 — profil d'écoute et recommandations

### 9.1 Événements

Ajouter et normaliser :

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
DOWNLOAD
IMPORT
```

Les événements possèdent `sourceEventId`, `deviceId`, `provider`, `recordingId`,
timestamp et position. Ils sont append-only.

### 9.2 UserTaste

Le profil doit intégrer artistes, albums, genres, décennies, énergie, répétition,
skips, complétion, récence et tolérance à la découverte.

### 9.3 Recommendation Engine

Le moteur déterministe doit gérer :

- favoris ;
- historique significatif ;
- disponibilité ;
- diversité ;
- pénalité de répétition ;
- ratio connu/découverte ;
- qualité locale prioritaire.

L'IA ne produit pas directement une queue arbitraire. Elle produit une intention validée
que le moteur exécute.

---

## 10. Priorité P2 — expérience produit

### 10.1 Web

- recherche unifiée avec fusion et provenance ;
- fiche artiste, album et playlist ;
- ajout bibliothèque avec politique claire ;
- player avec source, qualité et expiration ;
- page historique complète ;
- page téléchargements/jobs ;
- diagnostic provider visible pour l'administrateur.

### 10.2 Android

- afficher local, YouTube et serveur comme sources distinctes ;
- rendre les erreurs de résolution compréhensibles ;
- terminer le téléchargement offline ;
- Android Auto cohérent avec le mode actif ;
- notification et MediaSession complètes ;
- reprise après redémarrage ;
- mise à jour APK avec vérification de version et changelog.

### 10.3 Playlists

- playlists locales standalone ;
- playlists serveur liées ;
- playlists YouTube consultables si le catalogue est activé ;
- aucun mélange silencieux entre providers ;
- ordre et source conservés lors de la persistance.

---

## 11. Priorité P2 — IA complète

Actions à finaliser :

```text
search
play
queue
add_to_library
download
upgrade
create_playlist
like
rate
show_artist
show_album
recommend
```

Avant toute réponse négative, l'IA doit interroger le catalogue et la disponibilité.
Toutes les actions passent par des schémas validés et des permissions explicites.

---

## 12. Priorité P2 — contrats, cache et observabilité

### 12.1 Contrats

Créer progressivement :

```text
packages/contracts
OpenAPI ou schémas équivalents
modèles Kotlin générés ou validés par fixture
```

Les modèles Android ne doivent plus diverger silencieusement des réponses serveur.

### 12.2 Cache

Séparer :

```text
metadata cache
provider response cache
artwork cache
stream URL cache
audio cache
offline device cache
```

Chaque entrée possède une expiration ou une règle d'invalidation.

### 12.3 Diagnostic

Prévoir un bundle anonymisé avec version, état providers, migrations, jobs récents,
erreurs normalisées et configuration non sensible.

---

## 13. Migration des données

Avant chaque migration : backup et version de schéma.

À traiter :

- stores JSON vers modèles structurés ;
- SQLiteOpenHelper vers Room lorsque la structure est stabilisée ;
- anciennes clés de queue ;
- anciennes sources `LOCAL`, `SERVER`, `SPOTIFY` et `YOUTUBE` ;
- URLs expirées ;
- bibliothèques sans mapping ;
- playlists sans provider explicite.

Une migration doit être idempotente, testée et réversible autant que possible.

---

## 14. Tests globaux obligatoires

### 14.1 Backend

- parser de release ;
- scoring ;
- identité ;
- qualité ;
- machine d'état ;
- provider health ;
- auth ;
- permissions ;
- DB et migrations ;
- import transactionnel ;
- Plex désactivé ou indisponible ;
- cache et expiration.

### 14.2 Fixtures YouTube

Conserver des réponses anonymisées pour :

```text
search
player
browse
artist
album
playlist
erreurs
```

Chaque changement de parser doit ajouter ou mettre à jour une fixture.

### 14.3 E2E

```text
search → play streaming
search → add library
download → import → play local
upgrade quality
provider failure → fallback
standalone → linked sync
Plex unavailable
AI action → validated domain action
```

### 14.4 Android réel

- standalone sans serveur ;
- lecture locale sans Internet ;
- recherche YouTube avec Internet ;
- écran éteint ;
- casque/Bluetooth ;
- Android Auto ;
- téléchargement offline ;
- reprise après kill process ;
- appareil Android minimum SDK 26 ;
- changement de réseau pendant la lecture.

---

## 15. Ordre d'implémentation imposé

### Phase 1 — validation et compilation

- vérifier CI du tag actuel ;
- corriger les erreurs Gradle/Kotlin ;
- ne pas accepter une capacité uniquement parce qu'elle compile ;
- ajouter fixtures et logs minimaux.

### Phase 2 — standalone YouTube Music

- stabiliser recherche ;
- stabiliser source `TrackSource.YouTube` ;
- terminer extraction et signatures ;
- brancher tokens ;
- tester Media3 sur appareil réel.

### Phase 3 — provider serveur

- compléter browse, artist, album et playlist ;
- centraliser cache, health et erreurs ;
- fiabiliser résolution et expiration.

### Phase 4 — identité et bibliothèque

- mappings ;
- fusion local/provider ;
- dédoublonnage ;
- politiques de bibliothèque ;
- favoris et playlists unifiés.

### Phase 5 — acquisition/import

- jobs persistants ;
- staging atomique ;
- matching ;
- duplicate/upgrade ;
- tests de reprise.

### Phase 6 — offline et synchronisation

- téléchargements Android ;
- offline device ;
- sync standalone → serveur ;
- résolution de conflits.

### Phase 7 — profil et recommandations

- événements ;
- UserTaste ;
- radio ;
- découverte ;
- qualité et disponibilité.

### Phase 8 — IA et expérience complète

- actions validées ;
- recherche unifiée ;
- fiches détaillées ;
- diagnostics ;
- finitions Web et Android.

### Phase 9 — validation de release

- tests backend ;
- tests Android ;
- build release ;
- changelog ;
- tag ;
- artefact APK ;
- vérification d'installation et de mise à jour.

---

## 16. Stratégie de commits

Les commits doivent rester regroupés par capacité :

```text
android: standalone YouTube Music
android: offline downloads
provider: stabilize YouTube playback
library: unify provider mappings
acquisition: complete music import
recommendations: add listening profile
ai: validate domain actions
tests: add end-to-end fixtures
release: bump Android version
```

Un commit ne doit pas déclarer une capacité terminée si elle n'a ni test, ni fallback,
ni état d'erreur documenté.

---

## 17. Definition of Done

Un module est terminé uniquement s'il possède :

```text
code
+ tests
+ health
+ logs sûrs
+ configuration
+ erreurs explicites
+ fallback
+ migration si nécessaire
+ documentation courte
+ validation sur son environnement réel
```

---

## 18. Critères de V1 MuzziQ

La V1 est acceptable lorsque ces scénarios fonctionnent :

```text
✓ installation Docker
✓ login et réglages
✓ mode standalone sans serveur
✓ recherche YouTube Music
✓ lecture YouTube Music Android
✓ lecture fichiers locaux
✓ bibliothèque structurée
✓ identité et mappings
✓ ajout d'un morceau ou album
✓ téléchargement et import musical
✓ qualité et upgrade
✓ playlists
✓ historique
✓ recommandations de base
✓ provider health
✓ fallback provider
✓ mise à jour APK
```

Plex, IA avancée, fingerprinting, casting et social peuvent rester en V1.x, à condition
qu'ils soient isolés et qu'ils ne bloquent pas le cœur autonome.

---

## 19. Scénario d'acceptation final

```text
1. Installer l'APK.
2. Choisir standalone.
3. Refuser toute configuration de serveur.
4. Rechercher un titre YouTube Music.
5. Lancer la lecture.
6. Mettre en pause, reprendre et faire un seek.
7. Ajouter le titre à une playlist locale.
8. Télécharger le titre pour offline.
9. Couper Internet.
10. Relire le fichier offline.
11. Reconnecter un serveur.
12. Afficher la prévisualisation de synchronisation.
13. Fusionner sans perdre l'historique ni les fichiers locaux.
14. Vérifier que la lecture locale reste prioritaire.
15. Construire et installer la release suivante par tag.
```

Ce scénario est le véritable jalon produit. Le reste du plan doit servir ce parcours,
pas le remplacer par des écrans ou des abstractions non vérifiés.
