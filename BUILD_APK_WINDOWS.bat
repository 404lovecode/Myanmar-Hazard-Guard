@echo off
setlocal
cd /d "%~dp0"
echo.
echo ================================================
echo  MYANMAR HAZARD GUARD - APK BUILDER
 echo ================================================
echo.
echo Prerequisites: Android Studio with SDK Platform 35, Android SDK Build Tools,
echo JDK 17+, and Internet for first-time Gradle / plugin downloads.
echo.
call gradlew.bat --no-daemon assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Check Android Studio SDK / Java installation and network.
  echo You may also open the project in Android Studio then Build ^> Build APK(s).
  pause
  exit /b 1
)
set "RESULT=app\build\outputs\apk\debug\app-debug.apk"
echo.
echo SUCCESS: %CD%\%RESULT%
explorer /select,"%CD%\%RESULT%"
pause
