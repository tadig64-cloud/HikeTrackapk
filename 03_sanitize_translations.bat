@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

echo === HikeTrack: Nettoyage des traductions (AAPT2 fixes) ===
echo.

echo [INFO] 1) Ferme Android Studio (sinon certains fichiers restent verrouillés).
echo [INFO] 2) Assure-toi d'etre a la racine du projet (le dossier qui contient "app").
echo.

REM Va à la racine du projet (le dossier où se trouvent "app" et "tools_translate")
cd /d "%~dp0"

set RES=.\app\src\main\res

if not exist "%RES%" (
  echo [ERREUR] Dossier res introuvable: %RES%
  echo Lance ce .bat depuis la racine du projet HikeTrackapk.
  pause
  exit /b 2
)

echo [INFO] res = %RES%
echo.

py .\tools_translate\sanitize_android_resources.py --res "%RES%"

echo.
echo [INFO] Ensuite dans Android Studio:
echo   Build ^> Clean Project
echo   puis Run
echo.
pause
