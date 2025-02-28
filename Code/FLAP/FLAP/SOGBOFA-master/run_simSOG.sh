#!/bin/bash
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
#random
name=$RANDOM
mkdir run-data/$name

for i in {1..2000000} 
do 
java -Xms100M -Xmx2000M -classpath $CP rddl.competition.SimClientSOG $1 $2 $name $3 $4 $5 $6 $7 $8 $9 ${10} ${11} ${12}
done
