package com.muzziq.mobile.standalone

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/**
 * Scan de la bibliothèque locale (§34, adapté §56.4) via l'API MediaStore
 * standard — jamais un accès fichier direct au stockage, conformément à
 * l'esprit de la règle serveur "jamais d'accès fichier direct sur un store"
 * (règle absolue du dépôt serveur, §1) transposée au device : ici aussi on passe par
 * l'API système plutôt que de parcourir /storage à la main.
 */
class LocalLibraryScanner(private val context: Context) {

    fun scan(): List<LocalTrackRow> {
        val out = mutableListOf<LocalTrackRow>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.ARTIST} ASC, ${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                out += LocalTrackRow(
                    contentUri = uri.toString(),
                    title = cursor.getString(titleCol) ?: "Titre inconnu",
                    artist = cursor.getString(artistCol) ?: "Artiste inconnu",
                    album = cursor.getString(albumCol),
                    durationSeconds = cursor.getLong(durationCol) / 1000.0,
                    albumId = cursor.getLong(albumIdCol),
                )
            }
        }
        return out
    }

    fun albumArtUri(albumId: Long) =
        ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), albumId)
}
