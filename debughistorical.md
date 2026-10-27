# Debug historical — DjipieLive

2026-10-27 — Première implémentation proposée sur la branche feature/youtube-voice-mvp-v01.
À vérifier via GitHub Actions : compilation Android 35, dépendance Google auth, tests.
À vérifier sur appareil : consentement OAuth avec certificat de l'APK, direct actif, délai de polling, voix, pause, vitesse.
Aucun test sur un direct réel n'est encore attesté.

2026-10-27 — Workflow CI initial : échec `android-actions/setup-android@v3` (package obsolète `tools`). Étape supprimée.
2026-10-27 — Workflow CI : `sdkmanager` absent du PATH ; recours au SDK préinstallé.
2026-10-27 — Première vraie compilation Kotlin : `fillViewport` non reconnu ; correction `isFillViewport`.
2026-10-27 — Build/test Kotlin **réussis** (BUILD SUCCESSFUL, run 40544036010), mais étape SHA-1 échouée à cause du filtre grep ; modification pour utiliser `apksigner verify --print-certs`.
2026-10-27 — APK **non encore livré**, dernière vérification CI et test sur appareil toujours en attente. Pas de fusion `main`.
