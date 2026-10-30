' Windowless launcher: javaw has no console, so double-clicking shows only the UI.
' Use legado-tidy.bat instead when you need to see errors.
Dim sh, fso, base, javaPath, appPath, quote, cmd
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
javaPath = base & "\runtime\bin\javaw.exe"
appPath = base & "\app"
quote = Chr(34)

' Quote the executable, Skiko native-library path and wildcard classpath
' independently so folders containing spaces or parentheses remain one argument.
cmd = quote & javaPath & quote _
    & " -Dfile.encoding=UTF-8 -Xmx2g" _
    & " -Dskiko.library.path=" & quote & appPath & quote _
    & " -cp " & quote & appPath & "\*" & quote _
    & " io.github.theeht.legadotidy.desktop.MainKt"
sh.Run cmd, 0, False
