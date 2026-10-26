@echo off
chcp 65001 >nul
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "TV=192.168.1.147:5555"
set "PKG=com.petrobot.hume"
rem Volta a TV para a tela inicial de fabrica e remove o TV Hub.
rem Funciona mesmo que o TV Hub nunca tenha virado padrao.
set "LISTA=%~dp0launchers-desativados.txt"
"%ADB%" connect %TV%

rem Reativa so o que o definir-como-padrao.bat desativou.
if exist "%LISTA%" (
  for /f "usebackq delims=" %%P in ("%LISTA%") do "%ADB%" -s %TV% shell pm enable --user 0 %%P
  del "%LISTA%"
) else (
  echo Nenhuma tela inicial foi desativada por aqui.
)

rem Devolve o botao Home para a tela inicial do Google TV, se ela existir.
"%ADB%" -s %TV% shell pm list packages -e com.google.android.apps.tv.launcherx | "%SystemRoot%\System32\find.exe" "launcherx" >nul
if not errorlevel 1 "%ADB%" -s %TV% shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity

"%ADB%" -s %TV% uninstall %PKG%
echo.
echo Tela inicial de fabrica restaurada. Aperte Home no controle.
call :fim
exit /b 0

:fim
if not "%TVHUB_AUTO%"=="1" pause
goto :eof
