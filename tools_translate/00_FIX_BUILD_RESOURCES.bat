\
@echo off
setlocal
set ROOT=%~dp0..
pushd "%ROOT%" >nul

echo ===============================
echo HikeTrack - i18n quick FIX
echo - Fix invalid \u escapes in strings
echo - Move non-localizable files out of values-xx
echo ===============================
echo.

py tools_translate\sanitize_android_resources.py --res "app\src\main\res"
if errorlevel 1 (
  echo.
  echo [ERREUR] Le script de nettoyage a echoue.
  popd >nul
  pause
  exit /b 1
)

echo.
echo OK - Ressources nettoyees. Vous pouvez rebuild.
popd >nul
pause
