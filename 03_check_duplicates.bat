@echo off
setlocal
cd /d "%~dp0"
echo.
echo === HikeTrack: Recherche de duplicates resources (par dossier values-xx) ===
echo.
python tools\check_duplicates.py --res "app\src\main\res"
echo.
pause
