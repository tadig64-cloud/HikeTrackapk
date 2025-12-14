\
@echo off
setlocal
set ROOT=%~dp0..
pushd "%ROOT%" >nul

echo ===============================
echo LibreTranslate (local) - start
echo ===============================
echo.

REM Si vous avez deja un autre start script, vous pouvez l'utiliser.
REM Ce script demarre un serveur LibreTranslate en local si installe.
REM Sinon, il affiche juste un message.

if exist "tools_translate\start_libretranslate.bat" (
  call "tools_translate\start_libretranslate.bat"
) else (
  echo [INFO] start_libretranslate.bat introuvable dans tools_translate.
  echo        Si vous demarrez LibreTranslate autrement, ignorez ce script.
  pause
)

popd >nul
