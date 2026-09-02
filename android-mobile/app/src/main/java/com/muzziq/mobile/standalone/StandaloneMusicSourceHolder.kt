package com.muzziq.mobile.standalone

/** Référence process-wide vers l'instance StandaloneMusicSource active, pour que
 * PlaybackService (créé indépendamment de l'Activity par le système) puisse
 * enregistrer les événements de lecture dans le moteur de goût local même
 * quand l'app tourne en mode Lié avec la bibliothèque locale en arrière-plan
 * (les deux DB — locale et serveur — coexistent, jamais l'une n'écrase l'autre). */
object StandaloneMusicSourceHolder {
    var instance: StandaloneMusicSource? = null
}
