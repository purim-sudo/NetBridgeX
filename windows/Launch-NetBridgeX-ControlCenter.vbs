Set shell = CreateObject("WScript.Shell")
root = "C:\Users\Administrator\source\repos\NetBridgeX"
script = root & "\windows\NetBridgeX-ControlCenter.ps1"
cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File " & Chr(34) & script & Chr(34)
shell.CurrentDirectory = root
shell.Run cmd, 0, False
