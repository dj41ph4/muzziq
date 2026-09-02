package com.muzziq.mobile.domain

import android.content.Context
import com.muzziq.mobile.data.room.FavoriteEntity
import com.muzziq.mobile.data.room.MuzziQDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Premier consommateur réel du schéma Room (data/room/) — jusqu'ici défini
 * mais jamais instancié (voir le commentaire en tête d'Entities.kt). Les
 * favoris n'ont aucune dépendance sur le catalogue en ligne ni sur le cipher
 * YouTube (contrairement à la recherche/lecture streaming) : fonctionnent à
 * l'identique en standalone et en mode Lié, sur n'importe quel Track déjà
 * affiché à l'écran. Pas encore synchronisé avec le serveur (le contrat
 * server existant — `/api/library/items` avec `addPolicy` — n'est pas un
 * concept de favori au sens strict) : les favoris Android sont pour l'instant
 * strictement locaux à l'appareil, quel que soit le mode.
 */
class RoomFavoriteRepository(context: Context) : FavoriteRepository {
    private val dao = MuzziQDatabase.get(context).favoriteDao()

    override suspend fun isFavorite(trackId: String): Boolean = dao.isFavorite(trackId)

    override suspend fun setFavorite(trackId: String, favorite: Boolean) {
        if (favorite) {
            dao.add(FavoriteEntity(trackId = trackId, addedAt = System.currentTimeMillis()))
        } else {
            dao.remove(trackId)
        }
    }

    override fun observeFavorites(): Flow<List<String>> =
        dao.observeAll().map { list -> list.map { it.trackId } }
}
