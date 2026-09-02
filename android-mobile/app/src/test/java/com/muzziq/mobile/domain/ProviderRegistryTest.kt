package com.muzziq.mobile.domain

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Couvre `InMemoryProviderRegistry` en JVM pur — la seule vraie vérification
 * disponible pour cette partie dans cet environnement (pas de SDK/émulateur
 * Android, voir CLAUDE.md règle 4/5 : "le typecheck ne prouve rien"). Le
 * singleton `object` est partagé entre tests, d'où `clear()` avant/après
 * chaque test pour l'isolation.
 */
class ProviderRegistryTest {
    private val registry = InMemoryProviderRegistry

    @Before
    fun setUp() {
        registry.clear()
    }

    @After
    fun tearDown() {
        registry.clear()
    }

    @Test
    fun `registre vide par defaut`() {
        assertTrue(registry.observeActive().value.isEmpty())
        assertNull(registry.activeOrNull(MusicProviderId.LOCAL))
    }

    @Test
    fun `register rend le provider actif immediatement`() {
        val local = fakeLocalProvider()
        registry.register(local)
        assertEquals(listOf(local), registry.observeActive().value)
        assertEquals(local, registry.activeOrNull(MusicProviderId.LOCAL))
    }

    @Test
    fun `register remplace un provider deja enregistre avec le meme id`() {
        registry.register(fakeLocalProvider(listOf(fakeTrack("1"))))
        val replacement = fakeLocalProvider(listOf(fakeTrack("2")))
        registry.register(replacement)

        assertEquals(1, registry.observeActive().value.size)
        assertEquals(replacement, registry.activeOrNull(MusicProviderId.LOCAL))
    }

    @Test
    fun `deux providers de types differents coexistent (pivot multi-provider, §67)`() {
        registry.register(fakeLocalProvider())
        registry.register(fakeServerProvider())

        assertEquals(2, registry.observeActive().value.size)
        assertEquals(
            setOf(MusicProviderId.LOCAL, MusicProviderId.SERVER),
            registry.observeActive().value.map { it.id }.toSet(),
        )
    }

    @Test
    fun `unregister retire seulement le provider vise`() {
        registry.register(fakeLocalProvider())
        registry.register(fakeServerProvider())

        registry.unregister(MusicProviderId.LOCAL)

        assertEquals(listOf(MusicProviderId.SERVER), registry.observeActive().value.map { it.id })
    }

    @Test
    fun `clear vide tout le registre`() {
        registry.register(fakeLocalProvider())
        registry.register(fakeServerProvider())

        registry.clear()

        assertTrue(registry.observeActive().value.isEmpty())
    }

    // Comportement réel reproduit par AppViewModel.refreshLibrary()/search() (voir
    // ui/AppViewModel.kt) : union des providers actifs. Avec un seul actif, résultat
    // identique à l'ancien `source.library()`/`source.search()` — c'est justement ce
    // qui garantit qu'aucun comportement visible n'a changé lors de la migration.
    @Test
    fun `bibliotheque agregee avec un seul provider actif est identique a ce provider`() = runTest {
        val trackA = fakeTrack("a")
        registry.register(fakeLocalProvider(listOf(trackA)))

        val results = registry.observeActive().value.map { it.library.library() }
        val merged = results.mapNotNull { it.getOrNull() }.flatten()

        assertEquals(listOf(trackA), merged)
    }

    @Test
    fun `bibliotheque agregee avec deux providers actifs cumule (mecanisme §67)`() = runTest {
        val trackA = fakeTrack("a")
        val trackB = fakeTrack("b")
        registry.register(fakeLocalProvider(listOf(trackA)))
        registry.register(fakeServerProvider(listOf(trackB)))

        val results = registry.observeActive().value.map { it.library.library() }
        val merged = results.mapNotNull { it.getOrNull() }.flatten()

        assertEquals(setOf(trackA, trackB), merged.toSet())
    }

    // Couvre ce que SpotifyProvider réel documente (providers/spotify/SpotifyProvider.kt) :
    // pas de flux jouable via la Web API Spotify — vérifié ici par un test plutôt que
    // seulement affirmé en commentaire, sans dépendre du vrai SpotifyProvider (qui a
    // besoin d'Android/réseau réels, non testables ici).
    @Test
    fun `spotify echoue explicitement sur resolvePlayableUri, jamais un faux succes`() = runTest {
        val spotify = fakeSpotifyProvider()

        val result = spotify.streamResolver.resolvePlayableUri(fakeTrack("x"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `local et serveur resolvent bien un flux jouable`() = runTest {
        val local = fakeLocalProvider()
        val server = fakeServerProvider()

        assertTrue(local.streamResolver.resolvePlayableUri(fakeTrack("x")).isSuccess)
        assertTrue(server.streamResolver.resolvePlayableUri(fakeTrack("x")).isSuccess)
    }
}
