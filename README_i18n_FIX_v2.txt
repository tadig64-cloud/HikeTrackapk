HikeTrack — i18n fix tools (v2)
================================

Objectif :
- Corriger les erreurs AAPT2 "Invalid unicode escape sequence" (ex: \u{str}) dans les strings traduites.
- Nettoyer les dossiers values-xx (en/es/de/...) : on garde uniquement les fichiers de strings/arrays/plurals.
- Déplacer en backup les fichiers non-localisables copiés par erreur (attrs/styles/dimens/colors…).

⚠️ IMPORTANT
Ne faites plus de robocopy "values -> values-en/..." sur tous les XML.
L’outil de traduction ne recrée que les ressources localisables.

Mode “je veux compiler maintenant” (recommandé)
-----------------------------------------------
1) Double-cliquez : tools_translate\00_FIX_BUILD_RESOURCES.bat
2) Relancez le build Android Studio (Build > Rebuild Project)

Mode “je régénère proprement les traductions”
---------------------------------------------
1) Lancez LibreTranslate : tools_translate\01_START_LIBRETRANSLATE.bat
2) Lancez : tools_translate\02_TRANSLATE_FR_TO_EN_ES_DE_AND_SANITIZE.bat
3) Rebuild.

Notes
-----
- Un backup est créé dans app\src\main\res\_i18n_backup\...
- Les fichiers déplacés ne sont PAS supprimés, juste mis de côté.
