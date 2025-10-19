@echo off
setlocal
set PS_EXE=powershell.exe
set SCRIPT=%~dp0fix_nbsp.ps1

REM Default relative path to project file unless provided as first argument
set FILEPATH=%1
if "%FILEPATH%"=="" set FILEPATH=.\app\src\main\res\values\strings_waypoint_additions.xml

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -FilePath "%FILEPATH%"
if %ERRORLEVEL% NEQ 0 (
  echo Something went wrong.
  exit /b %ERRORLEVEL%
)
echo Done.
