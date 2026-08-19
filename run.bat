@echo off
rem Launch the game server.
rem
rem Usage: run.bat [conf\server\Conf.xml]
rem
rem The game server needs the login server running first (run-loginserver.bat
rem in another window) -- that is the only process that talks to the database,
rem and this one connects to it on startup.
rem
rem Everything is relative to this script, and the server resolves its config
rem and its definition files against the working directory, so the cd is
rem load-bearing -- not tidiness.
rem
rem conf\server\Conf.xml is OPTIONAL: without it every setting falls back to a
rem sensible local default (loopback addresses, authentic 1x rates). Copy
rem conf\server\Conf.xml.example to conf\server\Conf.xml to change something.
rem
rem No --add-opens. The server used to need one because XStream allocated the
rem model classes through sun.reflect.ReflectionFactory; it reads its
rem definitions with server\util\XmlObjects now, which touches no JDK internal,
rem so any Java 8 or later runs it as-is.

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
   echo The server needs Java, and java was not found on this machine. 1>&2
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

if not exist conf\server\Conf.xml (
   echo Note: no conf\server\Conf.xml -- running on the built-in defaults
   echo ^(local addresses, authentic rates^). To change the world's name, rates
   echo or ports:  copy conf\server\Conf.xml.example conf\server\Conf.xml
)

if not exist logs mkdir logs

if not defined JAVA_OPTS set "JAVA_OPTS=-Xmx512m"

"%JAVA%" %JAVA_OPTS% -cp rscd.jar org.rscdaemon.server.Server %*
if errorlevel 1 (
   echo.
   echo The server exited with an error -- the messages above say why. The
   echo usual first-run cause: the login server is not running yet. Start
   echo run-loginserver.bat in another window, then run this again.
   exit /b 1
)
