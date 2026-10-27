# Configuration YouTube et premier test

## 1. Test immédiat sans compte Google Cloud

Installe l'APK debug construit par GitHub Actions, ouvre DjipieLive et appuie sur « Écouter un message d'essai ».
La synthèse vocale Android doit lire une question fictive. Ce test ne lit aucun commentaire réel.

## 2. Activer la lecture réelle (configuration par le propriétaire)

1. Ouvrir [Google Cloud Console](https://console.cloud.google.com/).
2. Créer un projet `DjipieLive` et activer **YouTube Data API v3**.
3. Configurer l'écran de consentement OAuth, ajouter son adresse Google comme utilisateur de test si nécessaire.
4. Créer un **identifiant client OAuth Android**, package `fr.djipie.live`.
5. Ajouter l'empreinte SHA-1 **du certificat ayant signé précisément l'APK installé**.
   Sur GitHub Actions la tâche « SHA-1 du certificat debug » l'affiche dans les logs.
6. Attention : les APK debug générés par des exécutions GitHub Actions distinctes peuvent avoir des empreintes différentes, car leur clé de debug est régénérée sur chaque machine. Pour une vraie distribution, prévoir un certificat de signature stable conservé dans les secrets GitHub (jamais publié dans le dépôt).
7. Installer cet APK, lancer un vrai direct YouTube avec le **chat activé**, sur le compte Google autorisé.
8. Sur le second appareil, ouvrir DjipieLive → « Autoriser l'accès YouTube » → choisir le compte qui diffuse → « Démarrer la lecture du direct ».
9. Envoyer un **nouveau** message dans le chat à partir d'un autre compte. Les messages anciens sont volontairement ignorés.
10. Essayer « Lire uniquement les questions », Pause/Reprise et la vitesse vocale.

Les autorisations OAuth exigées : `youtube.readonly` seulement (lecture seule).
Les jetons restent dans la mémoire vive et ne sont ni écrits dans un fichier ni envoyés à un serveur tiers.

## Conditions et limitations

- YouTube doit avoir activé les directs sur la chaîne.
- En mode test OAuth Google, seuls les utilisateurs de test inscrits peuvent autoriser l'application. La publication grand public nécessitera un examen des règles de validation OAuth/YouTube.
- Si la session expire, réautoriser le compte ; le jeton ne peut pas être renouvelé silencieusement sans nouvelle requête d'autorisation.
- Durant la v0.1, garder DjipieLive **au premier plan sur le deuxième téléphone**. La fenêtre reste allumée pendant la lecture.
- La recherche automatique ne récupère que les directs du compte autorisé, actifs et avec chat.
- Ne partage jamais de jeton OAuth, fichier de signature privé ni secret dans une issue GitHub.

Références : [Google Authorization](https://developer.android.com/identity/authorization), [YouTube LiveBroadcasts](https://developers.google.com/youtube/data/live/reference/rest/v3/liveBroadcasts/list), [YouTube chat](https://developers.google.com/youtube/data/live/reference/rest/v3/liveChatMessages/list).
