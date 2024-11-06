@echo off
setlocal enabledelayedexpansion

REM Set paths
set "CURRENT_DIR=%CD%"
set "JAVAFX_SDK=C:\Users\cayde\openjfx-23.0.1_windows-x64_bin-sdk\javafx-sdk-23.0.1"
set "INSTALL_DIR=%CURRENT_DIR%\target\installer\PDF Viewer"

echo Building PDF Viewer...

REM Build with Maven
call mvn clean package

REM Create directories
mkdir "%INSTALL_DIR%" 2>nul
mkdir "%INSTALL_DIR%\lib" 2>nul
mkdir "%INSTALL_DIR%\runtime" 2>nul

REM Copy files
echo Copying files...
copy "target\PDF Viewer.exe" "%INSTALL_DIR%" /Y
xcopy /E /I /Y "%JAVAFX_SDK%\lib\*" "%INSTALL_DIR%\lib"
xcopy /E /I /Y "%JAVA_HOME%\*" "%INSTALL_DIR%\runtime"
xcopy /E /I /Y "%JAVAFX_SDK%\bin\*" "%INSTALL_DIR%\runtime\bin"

REM Create shortcuts
echo Creating shortcuts...
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut([Environment]::GetFolderPath('Desktop') + '\PDF Viewer.lnk'); $s.TargetPath = '%INSTALL_DIR%\PDF Viewer.exe'; $s.WorkingDirectory = '%INSTALL_DIR%'; $s.Save()"
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut($ws.SpecialFolders('Programs') + '\PDF Viewer.lnk'); $s.TargetPath = '%INSTALL_DIR%\PDF Viewer.exe'; $s.WorkingDirectory = '%INSTALL_DIR%'; $s.Save()"

echo Installation complete. The application has been installed to:
echo %INSTALL_DIR%
echo.
echo Shortcuts have been created on your desktop and in the Start Menu.
pause