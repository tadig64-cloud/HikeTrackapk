@echo off
setlocal
set PS_EXE=powershell.exe
set SCRIPT=%~dp0fix_dup_more_title.ps1

REM Optional: override path as first arg
set FILEPATH=%1
if "%FILEPATH%"=="" set FILEPATH=.\app\src\main\res\values\strings_help.xml

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -HelpFilePath "%FILEPATH%"
if %ERRORLEVEL% NEQ 0 (
  echo Something went wrong.
  exit /b %ERRORLEVEL%
)
echo Done removing duplicate 'more_title' from %FILEPATH%.
