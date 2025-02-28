#!/bin/bash
#run simClient
Home=.
libDir=${Home}/lib
CP=${Home}/bin
CYGWIN_SEP=";"
UNIX_SEP=":"

# Choose separator as appropriate for shell system (Cygwin, otherwise UNIX)
SEP=":"
if [[ $OSTYPE == "cygwin" ]] ; then
    SEP=";"
fi

for i in ${libDir}/*.jar ; do
    CP="${CP}${SEP}$i"
done





for i in {1..2000}
do
	java -Xms100M -Xmx2000M -classpath $CP rddl.competition.Simclient $1 $2 $3 $4 rddl.policy.$5
done


