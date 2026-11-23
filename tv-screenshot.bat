@echo off
rem Saves what the TV is showing right now to tv-screen.png next to this file.
setlocal
set "TV=%~1"
if "%TV%"=="" set "TV=192.168.1.103"
set "SDK="
if exist "%~dp0local.properties" for /f "tokens=1,* delims==" %%a in ('findstr /b "sdk.dir" "%~dp0local.properties"') do set "SDK=%%b"
if defined SDK set "SDK=%SDK:\:=:%"
if defined SDK set "SDK=%SDK:\\=\%"
set "ADB=%SDK%\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"
"%ADB%" connect %TV%:5555 > "%~dp0tv-screenshot-log.txt" 2>&1
"%ADB%" -s %TV%:5555 exec-out screencap -p > "%~dp0tv-screen.png" 2>> "%~dp0tv-screenshot-log.txt"
"%ADB%" -s %TV%:5555 shell wm size >> "%~dp0tv-screenshot-log.txt" 2>&1
"%ADB%" -s %TV%:5555 shell wm density >> "%~dp0tv-screenshot-log.txt" 2>&1
