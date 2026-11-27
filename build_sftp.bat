```bat
@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo Mise a jour et compilation SftpServer
echo ==========================================

echo.
echo [1/2] Recuperation des modifications Git...
git pull origin feature/sftp-server
if errorlevel 1 (
    echo ERREUR : impossible de mettre a jour le depot.
    pause
    exit /b 1
)

echo.
echo [2/2] Compilation de l'APK...
call gradlew.bat -p SftpServer assembleDebug
if errorlevel 1 (
    echo ERREUR : compilation echouee.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo COMPILATION REUSSIE
echo ==========================================
echo APK :
echo %~dp0SftpServer\app\build\outputs\apk\debug\app-debug.apk
echo.

pause