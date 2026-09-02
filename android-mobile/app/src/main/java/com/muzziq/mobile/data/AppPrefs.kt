package com.muzziq.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

    val mode: Flow<AppMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.MODE]) {
            "STANDALONE" -> AppMode.STANDALONE
            "LINKED" -> AppMode.LINKED
            else -> AppMode.UNSET
        }
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val sessionCookie: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_COOKIE] }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setStandalone() {
        context.dataStore.edit {
            it[Keys.MODE] = "STANDALONE"
            it[Keys.ONBOARDED] = true
        }
    }

    suspend fun setLinked(serverUrl: String) {
        context.dataStore.edit {
            it[Keys.MODE] = "LINKED"
            it[Keys.SERVER_URL] = serverUrl
            it[Keys.ONBOARDED] = true
        }
    }

    suspend fun setSessionCookie(cookie: String?) {
        context.dataStore.edit {
            if (cookie == null) it.remove(Keys.SESSION_COOKIE) else it[Keys.SESSION_COOKIE] = cookie
        }
    }

    /** Retour au choix du mode — ne supprime pas la bibliothèque locale déjà scannée. */
    suspend fun resetMode() {
        context.dataStore.edit {
            it.remove(Keys.MODE)
            it.remove(Keys.SERVER_URL)
            it.remove(Keys.SESSION_COOKIE)
            it[Keys.ONBOARDED] = false
        }
    }

    suspend fun currentServerUrlOrNull(): String? = serverUrl.first()
}
