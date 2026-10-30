@echo off
rem Console launcher: use this one when something breaks, it shows the stack trace.
rem
rem Two encoding settings, both needed:
rem   chcp 65001            console code page -> UTF-8
rem   -Dstdout.encoding     JVM stdio -> UTF-8
rem Without them Chinese source names and failure reasons come out as mojibake.
rem Do NOT put non-ASCII text in this file: cmd reads it byte-by-byte in the
rem old code page before chcp takes effect, and the line gets chopped up.
setlocal
chcp 65001 >nul
set "APP_DIR=%~dp0"
"%APP_DIR%runtime\bin\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Xmx2g "-Dskiko.library.path=%APP_DIR%app" -cp "%APP_DIR%app\*" io.github.theeht.legadotidy.desktop.MainKt %*
if errorlevel 1 pause
