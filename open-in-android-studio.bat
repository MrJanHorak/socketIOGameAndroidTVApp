@echo off
rem Opens this project in Android Studio (first open downloads Gradle and SDK pieces).
setlocal
set "STUDIO=%ProgramFiles%\Android\Android Studio\bin\studio64.exe"
if not exist "%STUDIO%" set "STUDIO=%LOCALAPPDATA%\Programs\Android Studio\bin\studio64.exe"
if not exist "%STUDIO%" (
  echo Android Studio was not found in the usual places. Open it and use File ^> Open on:
  echo   %~dp0
  pause
  exit /b 1
)
start "" "%STUDIO%" "%~dp0."
