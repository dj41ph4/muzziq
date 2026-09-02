package com.muzziq.mobile.domain

import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource

/**
 * Fixtures de test pour `ProviderRegistry` — JVM pur, aucun réseau, aucune classe
 * Android. C'est délibéré : `SpotifyAuthManager`/`AndroidKeystoreCredentialVault`
 * (providers/spotify/, security/) dépendent de `android.util.Base64`/l'Android
 * Keystore réel et ne sont testables que sur un vrai appareil/émulateur — absent
 * de cet environnement de développement (voir docs/online-streaming-status.md).
 * Ces fakes couvrent ce qui EST vérifiable ici : le contrat `MusicProvider`/
 * `ProviderRegistry` lui-même, indépendamment de toute implémentation réseau.
 */

fun fakeTrack(
    id: String,
    title: String = "Track $id",
    source: TrackSource = TrackSource.Local("content://$id"),
) = Track(id = id, title = title, artist = "Artiste $id", source = source)

class FakeCatalogueProvider(
    private val results: List<Track> = emptyList(),
    private val shouldFail: Boolean = false,
) : CatalogueProvider {
    var lastQuery: String? = null
        private set

    override suspend fun search(query: String): Result<List<Track>> {
        lastQuery = query
        return if (shouldFail) {
            Result.failure(IllegalStateException("recherche indisponible (fake)"))
        } else {
            Result.success(results)
        }
    }
}

class FakeLibraryRepository(
    private val tracks: List<Track> = emptyList(),
    private val shouldFail: Boolean = false,
) : LibraryRepository {
    override suspend fun library(): Result<List<Track>> =
        if (shouldFail) Result.failure(IllegalStateException("bibliothèque indisponible (fake)")) else Result.success(tracks)
}

class FakeStreamResolver(private val playableUri: String?) : StreamResolver {
    override suspend fun resolvePlayableUri(track: Track): Result<String> =
        playableUri?.let { Result.success(it) }
            ?: Result.failure(UnsupportedOperationException("non résolvable (fake)"))
}

/** Reflète StandaloneMusicSource : toujours un flux jouable (fichier local). */
fun fakeLocalProvider(tracks: List<Track> = emptyList()) = MusicProvider(
    id = MusicProviderId.LOCAL,
    label = "Local (fake)",
    catalogue = FakeCatalogueProvider(tracks),
    library = FakeLibraryRepository(tracks),
    streamResolver = FakeStreamResolver("content://fake-local"),
)

/** Reflète ServerMusicSource : flux jouable résolu côté serveur. */
fun fakeServerProvider(tracks: List<Track> = emptyList()) = MusicProvider(
    id = MusicProviderId.SERVER,
    label = "Serveur (fake)",
    catalogue = FakeCatalogueProvider(tracks),
    library = FakeLibraryRepository(tracks),
    streamResolver = FakeStreamResolver("https://fake-server/stream"),
)

/** Reflète le vrai SpotifyProvider (providers/spotify/SpotifyProvider.kt) : catalogue/
 * bibliothèque réels, mais JAMAIS de flux jouable — la Web API Spotify n'en fournit
 * aucun à un tiers. `playableUri = null` fait échouer `resolvePlayableUri` comme le
 * vrai provider, pour que ce comportement soit couvert par un test plutôt que
 * seulement documenté en commentaire. */
fun fakeSpotifyProvider(tracks: List<Track> = emptyList()) = MusicProvider(
    id = MusicProviderId.SPOTIFY,
    label = "Spotify (fake)",
    catalogue = FakeCatalogueProvider(tracks),
    library = FakeLibraryRepository(tracks),
    streamResolver = FakeStreamResolver(playableUri = null),
)
