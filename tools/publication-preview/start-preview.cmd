@echo off
setlocal
cd /d "%~dp0\..\.."
start "" "http://127.0.0.1:51347/tools/publication-preview/"
py -m http.server 51347 --bind 127.0.0.1
endlocal
