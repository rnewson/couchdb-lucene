@ECHO OFF
SETLOCAL EnableDelayedExpansion
SET JAVA_OPTS=-server -Xmx1g
SET CLASS=com.github.rnewson.couchdb.lucene.Main
SET CLASSPATH=conf

FOR %%G IN (lib/*.jar) DO (
	CALL :addclasspath lib/%%G
)
GOTO :done

:addclasspath
SET CLASSPATH=%CLASSPATH%;%1
GOTO :eof

:done

java %JAVA_OPTS% -cp %CLASSPATH% %CLASS%

ENDLOCAL
