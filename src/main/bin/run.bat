@echo off
setlocal EnableDelayedExpansion 
SET OLDCD=%CD%
cd %~dp0..
SET JAVA_OPTS= -Xmx1g
SET CLASS=com.github.rnewson.couchdb.lucene.Main
SET CLASSPATH="conf"
for %%i in ("lib\*.jar") do @SET CLASSPATH=!CLASSPATH!;"%%~dpfi"
"D:\AppData\Local\Temp\couch-bootstrapper-6345514392837730829991\bin\java\bin\java.exe" %JAVA_OPTS% -cp %CLASSPATH% %CLASS% > nul
cd %OLDCD%