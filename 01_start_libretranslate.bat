@echo off
setlocal
cd /d "%~dp0"

echo === HikeTrack: Demarrage LibreTranslate local (Docker) ===

REM Supprime un ancien container si existe (pas grave si absent)
docker rm -f libretranslate >nul 2>nul

REM Dossier de modeles sur E:
if not exist "E:\libretranslate_models" (
  mkdir "E:\libretranslate_models"
)

echo Lancement du container "libretranslate" en arriere-plan...
docker run -d --name libretranslate -p 5000:5000 ^
  -e LT_LOAD_ONLY=en,fr,de,es ^
  -e LT_UPDATE_MODELS=true ^
  -v "E:\libretranslate_models:/home/libretranslate/.local/share/argos-translate/packages" ^
  libretranslate/libretranslate

if errorlevel 1 (
  echo.
  echo ERREUR: docker run a echoue. Regarde Docker Desktop (partage du disque E: ?).
  pause
  exit /b 1
)

echo.
echo Attente que l'API soit prete (ca telecharge des modeles la 1ere fois)...
echo Logs recents:
docker logs --tail 60 libretranslate

echo.
echo Test /languages (on essaie plusieurs fois)...
set READY=0
for /l %%i in (1,1,30) do (
  curl.exe -s http://127.0.0.1:5000/languages >nul 2>nul
  if not errorlevel 1 (
    set READY=1
    goto :ready
  )
  timeout /t 2 >nul
)

:ready
echo.
if "%READY%"=="1" (
  echo OK: LibreTranslate repond !
  curl.exe http://127.0.0.1:5000/languages
) else (
  echo Toujours pas pret. Ouvre les logs complets avec:
  echo   docker logs -f libretranslate
)

echo.
pause
endlocal
