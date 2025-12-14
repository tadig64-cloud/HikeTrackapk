# HikeTrack – i18n quick FIX v3 (Windows)

## Pourquoi tu avais l'erreur ?
Ton `sanitize_android_resources.py` contenait dans son commentaire (docstring) un `\uXXXX` "factice".
Python essaye d'interpréter `\u` comme une vraie séquence Unicode, et plante si ce n'est pas 4 chiffres hexadécimaux.

## Ce pack fait quoi ?
1) Nettoie les dossiers `values-en`, `values-es`, `values-de`, etc. en retirant les fichiers **non localisables**
   (attrs, colors, dimens, styles, themes, ids, secrets…).
2) Corrige les chaînes qui contiennent des `\u` invalides (ex: `\u{str}`) en les transformant en `\\u{str}`
   pour que **AAPT** (le compilateur Android) n’explose plus.

## Installation (simple)
1) Ferme Android Studio (recommandé, pour éviter les fichiers verrouillés).
2) Dézippe ce pack **à la racine du projet** (là où il y a `app/` et `tools_translate/`).
   → Il va remplacer `tools_translate/sanitize_android_resources.py`.
3) Double-clique `03_sanitize_resources.bat` (ou lance-le dans un terminal).

## Après
Relance Android Studio → Build / Run.

Si tu as encore un message `Invalid unicode escape sequence`, copie/colle la ligne exacte (fichier + numéro de ligne).
