@echo off
setlocal enabledelayedexpansion

echo ===============================
echo HikeTrack - i18n quick FIX (v9)
echo - Fix invalid backslash/unicode escapes in strings
echo - Remove/move non-localizable files out of values-xx
echo ===============================
echo.

set "SCRIPT_DIR=%~dp0"
set "SCRIPT=%SCRIPT_DIR%sanitize_android_resources.py"

REM Prefer your local Python 3.14 path, fallback to "python"
set "PY=%LocalAppData%\Programs\Python\Python314\python.exe"
if not exist "%PY%" set "PY=python"

echo [INFO] Script: %SCRIPT%

REM Move to project root (tools_translate is inside project root)
pushd "%SCRIPT_DIR%.."
echo [INFO] Project root: %cd%
echo.

"%PY%" "%SCRIPT%"
set "EXITCODE=%ERRORLEVEL%"

echo.
if not "%EXITCODE%"=="0" (
  echo [ERREUR] Le script de nettoyage a echoue. Code: %EXITCODE%
) else (
  echo [OK] Nettoyage termine.
)
popd
echo.
pause
exit /b %EXITCODE%
