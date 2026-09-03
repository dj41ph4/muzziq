package com.muzziq.mobile.standalone

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Moteur de préférences on-device (§56.4) : équivalent allégé et embarqué du
 * Context Engine serveur (plan §43/§45) — même logique d'affinité incrémentale
 * (evidence_count, jamais de fusion aveugle), portée plutôt que réinventée
 * pour le sous-ensemble utile en standalone : deux tables, pas d'ORM externe
 * (SQLiteOpenHelper natif — zéro dépendance supplémentaire, comportement
 * vérifiable simplement).
 *
 * Tient aussi le cache de la bibliothèque locale scannée (table local_tracks)
 * pour que la recherche/liste ne dépendent pas de rescanner MediaStore à
 * chaque écran.
 */
class LocalTasteDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "muzziq_standalone.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE local_tracks (
                content_uri TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                duration_seconds REAL,
                album_id INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE play_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content_uri TEXT NOT NULL,
                played_at INTEGER NOT NULL,
                position_ms INTEGER NOT NULL,
                completed INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE artist_affinity (
                artist TEXT PRIMARY KEY,
                score REAL NOT NULL DEFAULT 0,
                evidence_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS local_tracks")
        db.execSQL("DROP TABLE IF EXISTS play_events")
        db.execSQL("DROP TABLE IF EXISTS artist_affinity")
        onCreate(db)
    }

    fun replaceLibrary(tracks: List<LocalTrackRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("local_tracks", null, null)
            for (t in tracks) {
                val cv = ContentValues().apply {
                    put("content_uri", t.contentUri)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("duration_seconds", t.durationSeconds)
                    put("album_id", t.albumId)
                }
                db.insertWithOnConflict("local_tracks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun listTracks(): List<LocalTrackRow> {
        val db = readableDatabase
        val out = mutableListOf<LocalTrackRow>()
        db.rawQuery("SELECT content_uri, title, artist, album, duration_seconds, album_id FROM local_tracks ORDER BY artist, album, title", null).use { c ->
            while (c.moveToNext()) {
                out += LocalTrackRow(
                    contentUri = c.getString(0),
                    title = c.getString(1),
                    artist = c.getString(2),
                    album = c.getString(3),
                    durationSeconds = if (c.isNull(4)) null else c.getDouble(4),
                    albumId = if (c.isNull(5)) null else c.getLong(5),
                )
            }
        }
        return out
    }

    fun searchTracks(query: String): List<LocalTrackRow> {
        val db = readableDatabase
        val like = "%${query.trim()}%"
        val out = mutableListOf<LocalTrackRow>()
        db.rawQuery(
            "SELECT content_uri, title, artist, album, duration_seconds, album_id FROM local_tracks WHERE title LIKE ? OR artist LIKE ? OR album LIKE ? ORDER BY artist, title LIMIT 200",
            arrayOf(like, like, like)
        ).use { c ->
            while (c.moveToNext()) {
                out += LocalTrackRow(
                    contentUri = c.getString(0),
                    title = c.getString(1),
                    artist = c.getString(2),
                    album = c.getString(3),
                    durationSeconds = if (c.isNull(4)) null else c.getDouble(4),
                    albumId = if (c.isNull(5)) null else c.getLong(5),
                )
            }
        }
        return out
    }

    /** Règles statistiques du plan (§42), portées telles quelles côté device. */
    fun recordPlayEvent(contentUri: String, artist: String, positionMs: Long, durationMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ratio = if (durationMs > 0) positionMs.toDouble() / durationMs.toDouble() else 0.0
            val completed = ratio > 0.85
            db.insert(
                "play_events",
                null,
                ContentValues().apply {
                    put("content_uri", contentUri)
                    put("played_at", System.currentTimeMillis())
                    put("position_ms", positionMs)
                    put("completed", if (completed) 1 else 0)
                },
            )
            if (ratio > 0.5) {
                // Evidence-based increment — jamais une écrasement, toujours une accumulation
                // pondérée (même logique de confiance que l'IdentityResolver serveur, §45).
                db.execSQL(
                    """
                    INSERT INTO artist_affinity (artist, score, evidence_count) VALUES (?, ?, 1)
                    ON CONFLICT(artist) DO UPDATE SET
                        score = score + excluded.score,
                        evidence_count = evidence_count + 1
                    """.trimIndent(),
                    arrayOf<Any>(artist, if (completed) 2.0 else 1.0),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun topAffinityArtists(limit: Int = 20): List<String> {
        val db = readableDatabase
        val out = mutableListOf<String>()
        db.rawQuery(
            "SELECT artist FROM artist_affinity WHERE evidence_count >= 2 ORDER BY score DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    /** Historique d'écoute (plan §41) — jointure play_events/local_tracks : un morceau
     * dont le fichier a disparu depuis (rescan, suppression) sort naturellement de la
     * liste plutôt que d'afficher une entrée cassée. [rawLimit] borne la requête SQL
     * avant dédoublonnage Kotlin (plusieurs écoutes du même morceau → une seule entrée,
     * la plus récente), donc peut renvoyer moins de [displayLimit] entrées distinctes. */
    fun recentPlayEvents(displayLimit: Int = 50, rawLimit: Int = 300): List<HistoryEntryRow> {
        val db = readableDatabase
        val out = mutableListOf<HistoryEntryRow>()
        val seen = mutableSetOf<String>()
        db.rawQuery(
            """
            SELECT lt.content_uri, lt.title, lt.artist, lt.album, lt.duration_seconds, lt.album_id, pe.played_at
            FROM play_events pe
            JOIN local_tracks lt ON pe.content_uri = lt.content_uri
            ORDER BY pe.played_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(rawLimit.toString())
        ).use { c ->
            while (c.moveToNext() && out.size < displayLimit) {
                val contentUri = c.getString(0)
                if (!seen.add(contentUri)) continue
                out += HistoryEntryRow(
                    contentUri = contentUri,
                    title = c.getString(1),
                    artist = c.getString(2),
                    album = c.getString(3),
                    durationSeconds = if (c.isNull(4)) null else c.getDouble(4),
                    albumId = if (c.isNull(5)) null else c.getLong(5),
                    playedAt = c.getLong(6),
                )
            }
        }
        return out
    }
}

data class HistoryEntryRow(
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Double?,
    val albumId: Long?,
    val playedAt: Long,
)

data class LocalTrackRow(
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Double?,
    val albumId: Long?,
)
