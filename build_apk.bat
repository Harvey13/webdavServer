@echo off
setlocal
cd /d "%~dp0"

echo === Compilation de l'APK (debug) ===
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [ERREUR] La compilation a echoue.
    pause
    exit /b 1
)

set "APK_SRC=app\build\outputs\apk\debug\app-debug.apk"
set "APK_DST=%~dp0webdavServer-debug.apk"

if exist "%APK_SRC%" (
    copy /Y "%APK_SRC%" "%APK_DST%" >nul
    echo.
    echo [OK] APK genere : %APK_DST%
    explorer /select,"%APK_DST%"
) else (
    echo [ERREUR] APK introuvable : %APK_SRC%
    pause
    exit /b 1
)

pause