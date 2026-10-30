@echo off
rem Console launcher: use this one when something breaks, it shows the stack trace.
rem This app ships its own JRE; ignore JVM options injected by the host machine.
setlocal
chcp 65001 >nul
set "APP_DIR=%~dp0"

rem Prevent global Java settings from injecting incompatible boot-classpath flags.
set "_JAVA_OPTIONS="
set "JAVA_TOOL_OPTIONS="
set "JDK_JAVA_OPTIONS="
set "CLASSPATH="

if not exist "%APP_DIR%runtime\bin\java.exe" (
    echo [ERROR] Bundled Java runtime is missing: "%APP_DIR%runtime\bin\java.exe"
    pause
    exit /b 2
)
if not exist "%APP_DIR%runtime\lib\modules" (
    echo [ERROR] Bundled Java runtime is incomplete: "%APP_DIR%runtime\lib\modules"
    pause
    exit /b 2
)

rem CI uses this switch to verify the bundled JVM without opening the UI.
if /I "%~1"=="--runtime-check" (
    "%APP_DIR%runtime\bin\java.exe" -version
    exit /b %ERRORLEVEL%
)

"%APP_DIR%runtime\bin\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Xmx2g "-Dskiko.library.path=%APP_DIR%app" -cp "%APP_DIR%app\*" io.github.theeht.legadotidy.desktop.MainKt %*
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" pause
exit /b %EXIT_CODE%
