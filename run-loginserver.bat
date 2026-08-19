@echo off
rem Launch the login server.
rem
rem Usage: run-loginserver.bat [conf\ls\Conf.xml]
rem
rem The login server is the only process that talks to the database. Every
rem world needs one running; start it before run.bat. It is also what
rem aggregates per-world player counts, so a multi-world operator runs one of
rem these beside all the game servers.
rem
rem Unlike the game server's config, conf\ls\Conf.xml is REQUIRED: it holds
rem the database credentials, which deliberately have no compiled-in fallback.
rem This script checks it exists and says how to make one.
rem
rem Same shape as run.bat: relative paths, no --add-opens, any Java 8 or later.

setlocal
cd /d "%~dp0"

if defined JDK (
   set "JAVA=%JDK%\bin\java.exe"
) else if defined JAVA_HOME (
   set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
   set "JAVA=java"
)

"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
   echo.
   echo The login server needs Java, and java was not found on this machine. 1>&2
   echo.
   echo   Download a JDK from https://adoptium.net, 1>&2
   echo   or in a terminal:  winget install EclipseAdoptium.Temurin.21.JDK 1>&2
   echo.
   echo If Java is already installed, set JAVA_HOME to its folder. 1>&2
   exit /b 1
)

if not exist rscd.jar (
   echo rscd.jar is not built yet -- building it now ^(first run only^)...
   call build.bat
   if errorlevel 1 exit /b 1
)

if not exist conf\ls\Conf.xml if "%~1"=="" (
   echo.
   echo There is no conf\ls\Conf.xml yet. The login server cannot start without 1>&2
   echo one -- it holds the database credentials, and those have no default. 1>&2
   echo.
   echo   copy conf\ls\Conf.xml.example conf\ls\Conf.xml 1>&2
   echo   then edit mysql_user / mysql_pass in it. 1>&2
   echo.
   echo The README's "Set up the database" section covers creating the 1>&2
   echo database itself and importing rscd.sql. 1>&2
   exit /b 1
)

if exist conf\ls\Conf.xml (
   findstr /r /c:"^ *mysql_pass *= *CHANGEME *$" conf\ls\Conf.xml >nul 2>&1
   if not errorlevel 1 (
      echo.
      echo conf\ls\Conf.xml still has the template password ^(mysql_pass = CHANGEME^). 1>&2
      echo Edit it to match your real database user -- the README's database 1>&2
      echo section walks through the whole setup. 1>&2
      exit /b 1
   )
)

if not exist logs mkdir logs

if not defined JAVA_OPTS set "JAVA_OPTS=-Xmx256m"

"%JAVA%" %JAVA_OPTS% -cp rscd.jar org.rscdaemon.ls.Server %*
if errorlevel 1 (
   echo.
   echo The login server exited with an error -- the messages above say why.
   echo The usual first-run causes: MySQL is not running, or the credentials
   echo in conf\ls\Conf.xml do not match a real database user.
   exit /b 1
)
