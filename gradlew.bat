@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.13"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_DIR=%GRADLE_USER_HOME%\neon-wrapper\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%DIST_DIR%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  if not exist "%GRADLE_USER_HOME%\neon-wrapper" mkdir "%GRADLE_USER_HOME%\neon-wrapper"
  set "ZIP=%GRADLE_USER_HOME%\neon-wrapper\gradle-%GRADLE_VERSION%-bin.zip"
  if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_USER_HOME%\neon-wrapper\gradle-%GRADLE_VERSION%-bin.zip'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%GRADLE_USER_HOME%\neon-wrapper\gradle-%GRADLE_VERSION%-bin.zip' '%GRADLE_USER_HOME%\neon-wrapper'"
)
call "%GRADLE_BIN%" -p "%APP_HOME%" %*
endlocal
