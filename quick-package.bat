@echo off
echo Building PDF Viewer...

REM Clean and package the application
call mvn clean package

REM Create the native installer
jpackage --input target/ ^
  --dest target/installer ^
  --name "PDF Viewer" ^
  --main-jar pdf-viewer-1.0-SNAPSHOT.jar ^
  --main-class com.pdfviewer.PDFViewer ^
  --type msi ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut

echo Installation package created in target/installer
pause 