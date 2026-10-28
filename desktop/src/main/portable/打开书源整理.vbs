' Windowless launcher: javaw has no console, so double-clicking shows only the UI.
' Use legado-tidy.bat instead when you need to see errors.
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
cmd = """" & base & "\runtime\bin\javaw.exe"" -Dfile.encoding=UTF-8 -Xmx2g -Dskiko.library.path=""" & base & "\app"" -cp """ & base & "\app\*"" io.github.theeht.legadotidy.desktop.MainKt"
sh.Run cmd, 0, False
