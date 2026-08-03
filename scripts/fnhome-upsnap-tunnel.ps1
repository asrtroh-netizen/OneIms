# 本机一键连到 FNHOME Upsnap（远程唤醒面板）
# 用法：在 PowerShell 执行
#   powershell -ExecutionPolicy Bypass -File .\scripts\fnhome-upsnap-tunnel.ps1
# 然后浏览器打开 http://127.0.0.1:18090/

param(
  [string]$RemoteHost = "hfs.itt.fan",
  [int]$SshPort = 1818,
  [string]$User = "Halo",
  [int]$LocalPort = 18090,
  [int]$RemotePort = 8090
)

Write-Host "Opening SSH tunnel: localhost:$LocalPort -> ${RemoteHost}:$RemotePort (via SSH $SshPort)"
Write-Host "Browser: http://127.0.0.1:$LocalPort/"
Write-Host "Admin (PocketBase): http://127.0.0.1:$LocalPort/_/"
Write-Host "Login email on FNHOME Upsnap: mo@itb.one (password: your existing one)"
Write-Host "Ctrl+C to close tunnel."

# Password auth: prefer key; if none, ssh will prompt.
ssh -N -L "${LocalPort}:127.0.0.1:${RemotePort}" -p $SshPort -o StrictHostKeyChecking=accept-new "${User}@${RemoteHost}"
