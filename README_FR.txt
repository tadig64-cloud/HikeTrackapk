HikeTrack — Traduction SANS carte bancaire (LibreTranslate local)

Objectif
- Générer automatiquement toutes les traductions Android (strings/arrays/plurals) à partir de la base FR (values/)
- Pour toutes les langues listées dans app/src/main/res/xml/locales_config.xml
- Sans DeepL, sans carte bancaire : on lance un serveur LibreTranslate en local (Docker)

────────────────────────────────────────────────────────────
0) Sauvegarde (simple)
- Copie le dossier du projet :
  C:\Users\Tadig64\AndroidStudioProjects\HikeTrackapk
  → copie-colle et renomme : HikeTrackapk_BACKUP

────────────────────────────────────────────────────────────
1) Installer Docker Desktop (1 fois)
- Télécharge + installe Docker Desktop (Windows)
- Redémarre si demandé
- Lance Docker Desktop (doit être “Running”)

────────────────────────────────────────────────────────────
2) Mettre ce pack à la racine du projet
- Dézippe ce fichier ZIP DANS :
  C:\Users\Tadig64\AndroidStudioProjects\HikeTrackapk
- À la fin tu dois voir :
  - 01_start_libretranslate.bat
  - 02_translate_all.bat
  - tools\translate_resources_local.py
  - ton dossier app\

────────────────────────────────────────────────────────────
3) Démarrer le traducteur local
- Double-clique : 01_start_libretranslate.bat
- Une fenêtre s’ouvre avec des logs.
- Vérifie dans ton navigateur :
  http://localhost:5000
  (si tu vois une page / ou un message, c’est OK)

⚠️ IMPORTANT
- Laisse cette fenêtre ouverte pendant la traduction.

────────────────────────────────────────────────────────────
4) Lancer la traduction
- Double-clique : 02_translate_all.bat
- Le script :
  - lit app/src/main/res/values/ (FR)
  - lit app/src/main/res/xml/locales_config.xml
  - crée/maj app/src/main/res/values-en, values-es, values-de, values-it, values-pt, values-pt-rBR, values-nl, values-pl, values-ru, values-ar, values-zh-rCN, values-ja
  - fait un backup automatique avant d’écrire : translations_backup_YYYYMMDD_HHMMSS.zip

Si ça dit “python n’est pas reconnu” :
- Installe Python (3.10+) en cochant “Add Python to PATH”
- Puis relance 02_translate_all.bat

────────────────────────────────────────────────────────────
5) Rebuild dans Android Studio
- Build > Clean Project
- Build > Rebuild Project
- Run

────────────────────────────────────────────────────────────
Dépannage rapide
A) Erreur duplicate resources
- Ça veut dire : même clé déclarée 2 fois dans la même langue.
- Lance : 03_check_duplicates.bat pour obtenir la liste.

B) Langue pas supportée par LibreTranslate
- Le script affiche les langues disponibles (endpoint /languages).
- Certaines instances LibreTranslate n’ont pas toutes les langues par défaut.
- Solution : installer les modèles/langues côté LibreTranslate (ou on fait un pack “langues minimales” en attendant).

────────────────────────────────────────────────────────────
Notes
- Le script protège automatiquement les placeholders Android (%1$s, %d, %% …) et les balises <b>…</b> etc.
- Les fichiers non textuels (colors/dimens/etc.) ne sont PAS copiés dans values-xx : ils restent communs.
