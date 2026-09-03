package com.muzziq.mobile.domain

import com.muzziq.mobile.data.model.Track

/**
 * Seule implémentation réelle de [LyricsProvider] à ce jour — retourne honnêtement
 * `Result.success(null)` (aucune erreur, aucune donnée) pour tout morceau, jamais un
 * texte fabriqué. Le contrat n'a ni route serveur (`/api/lyrics`, plan §38) ni fournisseur
 * tiers branché ; brancher un vrai fournisseur plus tard consistera à ajouter une nouvelle
 * implémentation de [LyricsProvider] et à la substituer ici, sans toucher à `LyricsPanel`
 * (ui/player/LyricsPanel.kt) qui consomme déjà le contrat, pas cette classe directement.
 */
class NullLyricsProvider : LyricsProvider {
    override suspend fun lyricsFor(track: Track): Result<String?> = Result.success(null)
}
