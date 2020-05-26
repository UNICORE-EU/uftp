@echo off

rem
rem Windows startscript for the UFTP Standalone client
rem

rem Figure out where the uftp client is installed
set UFTP_HOME=%~d0%~p0..

rem Build the Java classpath
set CLASSPATH=.
for %%i in ("%UFTP_HOME%\lib\*.jar") do ( call :cpappend %%i )

set VM_ARGS1="-Xmx128m"
set VM_ARGS2="-Dlog4j.configuration=file:///%UFTP_HOME%\conf\logging.properties"

set CMD_LINE_ARGS=%*

rem
rem Go
rem
java %VM_ARGS1% %VM_ARGS2% de.fzj.unicore.ucc.UCC %CMD_LINE_ARGS%
goto :eof


rem
rem Helper to append stuff to the classpath
rem
:cpappend
if ""%1"" == """" goto done
set CLASSPATH=%CLASSPATH%;%*
shift
goto :cpappend
:done
goto :eof
