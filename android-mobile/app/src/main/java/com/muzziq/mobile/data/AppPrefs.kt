package com.muzziq.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muzziq.mobile.core.capabilities.ServerConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "muzziq_prefs")

/** Les deux modes du premier lancement (§56.4) — aucun n'est "par défaut" dans le code,
 * seul l'écran d'onboarding décide, mais standalone reste utilisable sans jamais
 * passer par ici (aucune valeur stockée requise pour fonctionner). */
enum class AppMode { STANDALONE, LINKED, UNSET }

class AppPrefs(private val context: Context) {
    private object Keys {
        val MODE = stringPreferencesKey("app_mode")
        val SERVER_URL = stringPreferencesKey("server_url")
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val LAST_SCAN_AT = intPreferencesKey("last_local_scan_at")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val SERVER_CONNECTION_STATE = stringPreferencesKey("server_connection_state")
        val SAVED_SERVER_URLS = stringSetPreferencesKey("saved_server_urls")
    }

    val mode: Flow<AppMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.MODE]) {
            "STANDALONE" -> AppMode.STANDALONE
            "LINKED" -> AppMode.LINKED
            else -> AppMode.UNSET
        }
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    /** Serveurs connus par l'utilisateur, conservés indépendamment du mode actif. */
    val savedServerUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SAVED_SERVER_URLS].orEmpty().toList().sorted()
    }
    val serverConnectionState: Flow<ServerConnectionState> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.SERVER_CONNECTION_STATE]) {
            "CONNECTED" -> ServerConnectionState.CONNECTED
            "CONNECTING" -> ServerConnectionState.CONNECTING
            "DEGRADED" -> ServerConnectionState.DEGRADED
            "ERROR" -> ServerConnectionState.ERROR
            else -> ServerConnectionState.DISCONNECTED
        }
    }
    val sessionCookie: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_COOKIE] }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setStandalone() {
        context.dataStore.edit {
            it[Keys.MODE] = "STANDALONE"
            it[Keys.SERVER_CONNECTION_STATE] = "DISCONNECTED"
            it[Keys.ONBOARDED] = true
        }
    }

    suspend fun setLinked(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
        context.dataStore.edit {
            it[Keys.MODE] = "LINKED"
            it[Keys.SERVER_URL] = normalized
            it[Keys.SERVER_CONNECTION_STATE] = "CONNECTED"
            it[Keys.ONBOARDED] = true
            it[Keys.SAVED_SERVER_URLS] = it[Keys.SAVED_SERVER_URLS].orEmpty() + normalized
        }
    }

    suspend fun rememberServer(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isBlank()) return
        context.dataStore.edit {
            it[Keys.SAVED_SERVER_URLS] = it[Keys.SAVED_SERVER_URLS].orEmpty() + normalized
        }
    }

    suspend fun forgetServer(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
        context.dataStore.edit {
            it[Keys.SAVED_SERVER_URLS] = it[Keys.SAVED_SERVER_URLS].orEmpty() - normalized
        }
    }

    suspend fun setSessionCookie(cookie: String?) {
        context.dataStore.edit {
            if (cookie == null) it.remove(Keys.SESSION_COOKIE) else it[Keys.SESSION_COOKIE] = cookie
        }
    }

    suspend fun setServerConnectionState(state: ServerConnectionState) {
        context.dataStore.edit { it[Keys.SERVER_CONNECTION_STATE] = state.name }
    }

    /** Retour au choix du mode — ne supprime pas la bibliothèque locale déjà scannée. */
    suspend fun resetMode() {
        context.dataStore.edit {
            it.remove(Keys.MODE)
            it.remove(Keys.SESSION_COOKIE)
            it.remove(Keys.SERVER_CONNECTION_STATE)
            it[Keys.ONBOARDED] = false
        }
    }

    suspend fun currentServerUrlOrNull(): String? = serverUrl.first()

    companion object {
        fun normalizeServerUrl(value: String): String = value.trim().trimEnd('/')
    }
}
