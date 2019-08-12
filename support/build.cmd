@echo off

cd ..
setlocal enableextensions

set NEXIAL_DIST_HOME=projects\nexial-core\build\install\nexial-core
set NEXIAL_LIB=%NEXIAL_DIST_HOME%\lib
set NEXIAL_JAR=%NEXIAL_LIB%\*.jar

set NEXIALSERVICE_HOME=%~dp0..
set NEXIALSERVICE_LIB=%NEXIALSERVICE_HOME%\lib-nexial

if EXIST "%NEXIALSERVICE_LIB%\*.jar" (
del %NEXIALSERVICE_LIB%\*.jar
)

REM add all the nexial jars and its 3rd-party libs to /lib-nexial
xcopy  %NEXIAL_JAR% %NEXIALSERVICE_LIB%

cd %NEXIALSERVICE_HOME%

REM run nexialservice build

gradle clean installDist

if %ERRORLEVEL% EQU 0 (
   echo Build Success
) else (
   echo Failure Reason Given is %errorlevel%
   exit /b %errorlevel%
)


