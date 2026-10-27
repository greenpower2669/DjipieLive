# 🎙️ DjipieLive

Compagnon vocal Android libre pour les créateurs de directs, pensé en priorité pour les personnes malvoyantes.

## Objectif v0.1 — YouTube
- Lire à haute voix les commentaires d'un direct YouTube dont on est propriétaire.
- Donner priorité aux questions, avec option « questions uniquement ».
- Régler la vitesse de parole, mettre en pause et reprendre.
- Tester la synthèse vocale hors connexion YouTube.
- Utiliser **un second téléphone** au départ, pour laisser le jeu et YouTube sur le premier.

Ce dépôt est public sous MIT. Aucune clé, aucun jeton OAuth ni donnée privée ne doit y être publiée.

## Démarrage
Voir [docs/CONFIGURATION-YOUTUBE.md](docs/CONFIGURATION-YOUTUBE.md) pour les étapes Google Cloud et le test réel.
La branche de développement `feature/youtube-voice-mvp-v01` génère un APK debug via GitHub Actions.
**Le mode de démonstration vocal fonctionne sans Google Cloud** ; la récupération réelle du chat nécessite OAuth configuré.

## Confidentialité
Les messages arrivent directement depuis l'API YouTube vers l'appareil. Ils sont conservés seulement en mémoire vive le temps de la lecture. Aucun serveur DjipieLive, traqueur ou publicité. Le jeton OAuth n'est pas sauvegardé sur disque.

## Limite v0.1
L'écran doit rester affiché sur le second appareil durant l'essai. L'exécution fiable en arrière-plan sur un seul téléphone demandera un service Android dédié. La lecture peut avoir quelques secondes de retard (API `liveChatMessages.list` et intervalle imposé par YouTube).

### Références officielles
- [YouTube — liveChatMessages.list](https://developers.google.com/youtube/data/live/reference/rest/v3/liveChatMessages/list)
- [YouTube — liveBroadcasts.list](https://developers.google.com/youtube/data/live/reference/rest/v3/liveBroadcasts/list)
- [Google Authorization Android](https://developer.android.com/identity/authorization)

## Licence
MIT. Voir `LICENSE` et `THIRD-PARTY-NOTICES.md`.
