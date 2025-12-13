@echo off
setlocal
set PS_EXE=powershell.exe
set SCRIPT=%~dp0scan_strings_duplicates.ps1

set VALDIR=%1
if "%VALDIR%"=="" set VALDIR=.\app\src\main\res\values

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -ValuesDir "%VALDIR%"
if %ERRORLEVEL% NEQ 0 (
  echo Scan failed.
  exit /b %ERRORLEVEL%
)
echo Done scanning for duplicate string keys.
