\
@echo off
setlocal
set ROOT=%~dp0..
pushd "%ROOT%" >nul

echo ===========================================
echo HikeTrack - Translate FR (values) to EN/ES/DE
echo + sanitize resources
echo ===========================================
echo.

REM Source: app\src\main\res\values  (FR)
REM Targets: values-en, values-es, values-de

py tools_translate\translate_android_strings_libretranslate_v2.py ^
  --res "app\src\main\res" ^
  --source-dir "app\src\main\res\values" ^
  --targets "en,es,de" ^
  --source-lang "fr" ^
  --endpoint "http://127.0.0.1:5000" ^
  --clean-target-dirs

if errorlevel 1 (
  echo.
  echo [ERREUR] Traduction echouee. Verifiez que LibreTranslate tourne sur http://127.0.0.1:5000
  popd >nul
  pause
  exit /b 1
)

echo.
echo (2/2) Sanitize resources...
py tools_translate\sanitize_android_resources.py --res "app\src\main\res"

if errorlevel 1 (
  echo.
  echo [ERREUR] Sanitize echoue.
  popd >nul
  pause
  exit /b 1
)

echo.
echo OK - Traductions regenerees + ressources nettoyees.
popd >nul
pause
