@echo off
rem Wrapper so the script runs without changing the PowerShell execution policy.
rem Usage: scripts\dev-local.cmd [start|stop|restart|status]
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev-local.ps1" %*
