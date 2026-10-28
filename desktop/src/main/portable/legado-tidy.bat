@echo off
rem Console launcher: use this when something fails, it keeps the stack trace visible.
setlocal
set DIR=%~dp0
"%DIR%runtime\bin\java.exe" -Dfile.encoding=UTF-8 -Xmx2g -Dskiko.library.path="%DIR%app" -cp "%DIR%app\*" io.github.theeht.legadotidy.desktop.MainKt %*
if errorlevel 1 pause
