@echo off
chcp 65001 >nul
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "TV=192.168.1.147:5555"
set "PKG=com.petrobot.hume"
rem Faz o botao Home abrir o TV Hub, desativando (sem apagar) as outras telas iniciais.
rem Tudo volta com restaurar.bat, que reativa exatamente o que este script desativou.
set "LISTA=%~dp0launchers-desativados.txt"
"%ADB%" connect %TV%

rem Trava: sem o TV Hub instalado, desativar os outros deixaria a TV sem tela inicial.
"%ADB%" -s %TV% shell pm path %PKG% | "%SystemRoot%\System32\find.exe" "package:" >nul
if errorlevel 1 (
  echo O TV Hub nao esta instalado. Rode instalar.bat primeiro. Nada foi alterado.
  call :fim & exit /b 1
)

"%ADB%" -s %TV% shell cmd package set-home-activity %PKG%/.MainActivity | "%SystemRoot%\System32\find.exe" "Success" >nul
if errorlevel 1 (
  echo Nao consegui definir o TV Hub como tela inicial. Nada foi desativado.
  call :fim & exit /b 1
)

rem So desativa o que esta ativo agora, e anota para o restaurar.bat.
rem O setupwraith registra uma tela inicial de recuperacao com prioridade maior que a nossa:
rem sem desativar ele, o botao Home cai nela e nao abre o launcher.
for %%P in (com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith com.tcl.tv.tclhome_master com.tcl.tv.tclhome_passive com.gathin.tvhome) do (
  "%ADB%" -s %TV% shell pm list packages -e %%P | "%SystemRoot%\System32\find.exe" "%%P" >nul && (
    "%ADB%" -s %TV% shell pm disable-user --user 0 %%P >nul && echo %%P>>"%LISTA%"
  )
)
"%ADB%" -s %TV% shell input keyevent KEYCODE_HOME
echo.
echo Pronto. Aperte Home no controle: deve abrir o TV Hub.
call :fim
exit /b 0

:fim
if not "%TVHUB_AUTO%"=="1" pause
goto :eof
