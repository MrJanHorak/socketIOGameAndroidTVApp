@echo off
rem Installs the built APK on the Android TV over the network and starts it.
rem Usage: install-on-tv.bat [tv-ip]   (default 192.168.1.103)
rem Output is also saved to install-log.txt next to this file.
setlocal
call :main %* > "%~dp0install-log.txt" 2>&1
type "%~dp0install-log.txt"
pause
exit /b

:main
set "TV=%~1"
if "%TV%"=="" set "TV=192.168.1.103"
rem Find adb: the SDK Android Studio wrote to local.properties, then the default SDK, then PATH.
set "SDK="
if exist "%~dp0local.properties" for /f "tokens=1,* delims==" %%a in ('findstr /b "sdk.dir" "%~dp0local.properties"') do set "SDK=%%b"
if defined SDK set "SDK=%SDK:\:=:%"
if defined SDK set "SDK=%SDK:\\=\%"
set "ADB=%SDK%\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"
echo Using adb: %ADB%
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo No APK yet. In Android Studio choose Build ^> Generate App Bundles or APKs ^> Generate APKs first.
  exit /b 1
)

echo Connecting to the TV at %TV% ...
echo If the TV shows "Allow debugging?", tick "Always allow" and press OK on the remote.
set /a TRIES=0
:connect
set /a TRIES+=1
"%ADB%" connect %TV%:5555
"%ADB%" -s %TV%:5555 get-state 2>nul | findstr /x "device" >nul && goto connected
if %TRIES% geq 10 (
  echo Could not connect. Check that USB/Network debugging is on, that you accepted the prompt on the TV, and the IP address.
  "%ADB%" devices -l
  exit /b 1
)
timeout /t 5 /nobreak >nul
goto connect

:connected
echo Connected.
echo Installing ...
"%ADB%" -s %TV%:5555 install -r "%APK%"
if errorlevel 1 (
  echo Install failed.
  exit /b 1
)
echo Starting Science Fair Showdown on the TV ...
"%ADB%" -s %TV%:5555 shell monkey -p com.partygame.sciencefair.tv -c android.intent.category.LEANBACK_LAUNCHER 1
echo Done.
exit /b 0
