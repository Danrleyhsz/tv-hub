@echo off
chcp 65001 >nul
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "TV=192.168.1.147:5555"
set "PKG=com.petrobot.hume"
rem Instala o TV Hub na TV. Nao mexe na tela inicial: para isso, definir-como-padrao.bat.
if not exist "%~dp0build\tvos-home.apk" (
  echo APK nao encontrado. Compile antes:  bash build.sh
  call :fim & exit /b 1
)
"%ADB%" connect %TV%
"%ADB%" -s %TV% install -r "%~dp0build\tvos-home.apk" || (echo Falhou a instalacao. & call :fim & exit /b 1)
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity
echo.
echo Instalado. A TV continua abrindo a tela inicial de fabrica no botao Home.
call :fim
exit /b 0

:fim
if not "%TVHUB_AUTO%"=="1" pause
goto :eof
