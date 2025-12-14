@echo off
setlocal
cd /d "%~dp0"

echo === HikeTrack: Traduction strings Android via LibreTranslate ===

REM Verification rapide que l'API repond
curl.exe -s http://127.0.0.1:5000/languages >nul 2>nul
if errorlevel 1 (
  echo.
  echo ERREUR: LibreTranslate ne repond pas sur http://127.0.0.1:5000
  echo Lance d'abord 01_start_libretranslate.bat et attends que ca soit OK.
  pause
  exit /b 1
)

REM Lance la traduction: source FR, cibles EN/ES/DE, base = FR
py "%~dp0tools_translate\translate_android_strings_libretranslate.py" ^
  --res "%~dp0app\src\main\res" ^
  --source-dir "%~dp0app\src\main\res\values" ^
  --targets en,es,de ^
  --endpoint http://127.0.0.1:5000 ^
  --clean-target-dirs ^
  --write-base fr

echo.
echo Terminé.
pause
endlocal
