@echo off
rem Compile the server and pack rscd.jar.
rem
rem Usage: build.bat
rem
rem The Windows twin of build.sh. Replaces "ant compile" for anyone who does
rem not have Ant; build.xml still works and still does the same thing.
rem
rem Everything is relative to this script. Java comes from JDK, then JAVA_HOME,
rem then PATH.
rem
rem build.xml compiles with target="1.5", which no modern javac accepts (8 is
rem the floor). Java 8 is targeted instead (--release 8 on a modern JDK,
rem -source/-target on JDK 8 itself, which predates the --release flag); set
rem RELEASE to change it.

setlocal enabledelayedexpansion
cd /d "%~dp0"

if defined JDK (
   set "JBIN=%JDK%\bin\"
) else if defined JAVA_HOME (
   set "JBIN=%JAVA_HOME%\bin\"
) else (
   set "JBIN="
)

set "JAVAC=%JBIN%javac"
set "JAR=%JBIN%jar"
if not defined RELEASE set "RELEASE=8"

"%JAVAC%" -version >nul 2>&1
if errorlevel 1 (
   echo.
   echo Building needs a JDK ^(the Java compiler^), and javac was not found. 1>&2
   echo.
   echo   Download one from https://adoptium.net ^(pick the JDK, not the JRE^), 1>&2
   echo   or in a terminal:  winget install EclipseAdoptium.Temurin.21.JDK 1>&2
   echo.
   echo If a JDK is already installed, set JAVA_HOME to its folder, e.g. 1>&2
   echo   set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21" 1>&2
   exit /b 1
)

rem JDK 8's javac has no --release flag (it arrived in JDK 9), so pick the
rem spelling this javac understands. javac -version prints "javac 1.8.0_431"
rem on 8 and "javac 17.0.2" on later ones.
set "JAVAC_VER="
for /f "tokens=2" %%V in ('"%JAVAC%" -version 2^>^&1') do if not defined JAVAC_VER set "JAVAC_VER=%%V"
if "%JAVAC_VER:~0,2%"=="1." (
   set "TARGET_FLAGS=-source %RELEASE% -target %RELEASE%"
) else (
   set "TARGET_FLAGS=--release %RELEASE%"
)

if exist build rmdir /s /q build
mkdir build

rem Paths are quoted AND slashes flipped. javac splits argument files on
rem whitespace (so a checkout under a path with spaces needs the quotes) and
rem then treats '\' inside those quotes as an escape (so the quotes alone
rem would eat every separator). Windows javac accepts '/' fine.
for /f "delims=" %%F in ('dir /s /b src\*.java') do (
   set "SRC=%%F"
   echo "!SRC:\=/!">> build\sources.txt
)
"%JAVAC%" -nowarn %TARGET_FLAGS% -d build "@build\sources.txt"
if errorlevel 1 (
   echo.
   echo The compile failed -- the errors above name the file and line. 1>&2
   exit /b 1
)
del build\sources.txt

if exist rscd.jar del rscd.jar
"%JAR%" cf rscd.jar -C build .
if errorlevel 1 exit /b 1

rem Counted through a temp file rather than a pipe inside for /f: every form of
rem for /f ('...') mangles the quotes around a %JAR% in "C:\Program Files", and
rem the failure is silent -- it just reports 0 classes.
"%JAR%" tf rscd.jar > "%TEMP%\rscd-server-jarlist.txt"
for /f %%N in ('find /c ".class" ^< "%TEMP%\rscd-server-jarlist.txt"') do set "COUNT=%%N"
del "%TEMP%\rscd-server-jarlist.txt"
echo rscd.jar packed: %COUNT% classes
echo.
echo First time here? The README's "Set up the database" section walks you
echo through MySQL and the config ^(the guided install.sh is for Linux/macOS^).
echo Already set up?  run-loginserver.bat first, then run.bat in another window.
