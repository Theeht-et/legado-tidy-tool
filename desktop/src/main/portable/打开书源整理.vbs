' Windowless launcher: javaw has no console, so double-clicking shows only the UI.
' Use legado-tidy.bat instead when you need to see errors.
Dim sh, fso, env, base, javaPath, appPath, quote, cmd
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
Set env = sh.Environment("PROCESS")
base = fso.GetParentFolderName(WScript.ScriptFullName)
javaPath = base & "\runtime\bin\javaw.exe"
appPath = base & "\app"
quote = Chr(34)

' The app ships a private JRE. Do not inherit machine-wide JVM flags or classpaths.
env("_JAVA_OPTIONS") = ""
env("JAVA_TOOL_OPTIONS") = ""
env("JDK_JAVA_OPTIONS") = ""
env("CLASSPATH") = ""

' Quote the executable, Skiko native-library path and wildcard classpath
' independently so folders containing spaces or parentheses remain one argument.
cmd = quote & javaPath & quote _
    & " -Dfile.encoding=UTF-8 -Xmx2g" _
    & " -Dskiko.library.path=" & quote & appPath & quote _
    & " -cp " & quote & appPath & "\*" & quote _
    & " io.github.theeht.legadotidy.desktop.MainKt"
sh.CurrentDirectory = base
sh.Run cmd, 0, False
