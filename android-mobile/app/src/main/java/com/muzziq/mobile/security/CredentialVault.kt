package com.muzziq.mobile.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.credentialVaultDataStore by preferencesDataStore(name = "muzziq_credential_vault")

/**
 * Coffre de credentials chiffrés (plan §67, prérequis des priorités 4/5 —
 * comptes liés YouTube Music/Spotify). Règle absolue : `LinkedMusicAccountEntity`
 * (data/room/Entities.kt) ne contient AUCUN token/cookie brut — tout secret réel
 * (refresh token Spotify, cookies de session YTM) passe par ici, jamais par Room
 * ni par AppPrefs/DataStore en clair.
 *
 * Interface + implémentation séparées : `AndroidKeystoreCredentialVault` ne peut
 * s'exécuter que sur un vrai appareil/émulateur Android (le provider
 * "AndroidKeyStore" n'existe pas en JVM pure, donc pas testable par un test
 * unitaire classique dans cet environnement sans SDK) — l'interface permet une
 * `FakeCredentialVault` en mémoire pour les tests de ce qui consomme le coffre
 * (voir androidTest/test à venir) sans dépendre du Keystore réel.
 */
interface CredentialVault {
    suspend fun store(key: String, plaintext: String)
    suspend fun retrieve(key: String): String?
    suspend fun remove(key: String)
}

/**
 * AES-256-GCM, clé générée et gardée dans l'Android Keystore (jamais exportable
 * en clair, même par ce process). Choix délibéré face à
 * `androidx.security:security-crypto` (EncryptedSharedPreferences) : cette
 * librairie n'a toujours pas de version stable (1.1.0 reste en alpha au moment
 * d'écrire ceci) — l'Android Keystore directement est l'API stable sous-jacente
 * qu'elle ne fait qu'envelopper, donc pas une réinvention d'une librairie mature
 * (règle §87.4 du plan) mais l'inverse : éviter une dépendance non stable pour
 * un besoin que la plateforme couvre déjà nativement.
 *
 * Persistance : DataStore Preferences (déjà une dépendance du projet, même
 * pattern que data/AppPrefs.kt) — seul IV + texte chiffré (Base64) y est écrit,
 * jamais un secret en clair, jamais dans les logs (aucun `Log`/`println` du
 * texte en clair nulle part dans ce fichier).
 */
class AndroidKeystoreCredentialVault(context: Context) : CredentialVault {
    private val appContext = context.applicationContext

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Pas d'authentification utilisateur requise (biométrie/PIN) pour cette V1 :
            // un token de session doit rester utilisable pour un refresh en arrière-plan
            // (PlaybackService, sync) sans interaction. Le coffre protège contre
            // l'extraction du fichier/de la base par une autre app ou un accès physique
            // au stockage, pas contre un appareil déjà déverrouillé par l'utilisateur.
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override suspend fun store(key: String, plaintext: String) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH_BYTES) { "IV GCM de taille inattendue" }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        appContext.credentialVaultDataStore.edit { it[stringPreferencesKey(key)] = payload }
    }

    override suspend fun retrieve(key: String): String? {
        val payload = appContext.credentialVaultDataStore.data.first()[stringPreferencesKey(key)] ?: return null
        val raw = runCatching { Base64.decode(payload, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (raw.size <= GCM_IV_LENGTH_BYTES) return null
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = raw.copyOfRange(GCM_IV_LENGTH_BYTES, raw.size)
        val secretKey = getOrCreateKey()
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun remove(key: String) {
        appContext.credentialVaultDataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "muzziq_credential_vault_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_IV_LENGTH_BYTES = 12
    }
}
