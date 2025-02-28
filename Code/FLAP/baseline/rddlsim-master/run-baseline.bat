::run baseline 2011-2014
::注意修改了localhost、seed、clientname、
rem Note: Need to run from JavaRelated directory.
set LOCAL_HOME=.
set LIB_HOME=%LOCAL_HOME%\lib
set LIBJARS=%LOCAL_HOME%\bin
set seed=32561
set port=2356
set num=1

for %%i in (%LIB_HOME%\*.jar) do call %LOCAL_HOME%\cpappend.bat %%i

for /f "delims=" %%a in (files/Domains/aabaseline-inst.txt) do (
	java -Xms200M -Xmx1024M -classpath %LIBJARS% rddl.competition.Client files\Domains localhost original-%%a rddl.policy.RandomConcurrentPolicy1 2301 %1 %%a
)

