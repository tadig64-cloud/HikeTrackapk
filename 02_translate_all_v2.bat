@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

echo === HikeTrack: Traduire strings via LibreTranslate (V2 safe) ===
echo.

cd /d "%~dp0"

set RES=.\app\src\main\res
if not exist "%RES%" (
  echo [ERREUR] Dossier res introuvable: %RES%
  echo Lance ce .bat depuis la racine du projet HikeTrackapk.
  pause
  exit /b 2
)

REM Source: priorite a values-fr si present, sinon values
set SRC=
if exist "%RES%\values-fr" (
  set SRC=%RES%\values-fr
) else (
  set SRC=%RES%\values
)

echo [INFO] Source: %SRC%
echo [INFO] Cibles: en,es,de
echo.

py .\tools_translate\translate_android_strings_libretranslate_v2.py --res "%RES%" --source-dir "%SRC%" --targets en,es,de --endpoint http://127.0.0.1:5000 --source-lang fr --clean-target-dirs --write-base fr

echo.
echo [INFO] Si Android Studio plante ensuite: lance 03_sanitize_translations.bat
pause
