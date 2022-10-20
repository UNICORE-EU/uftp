#!/bin/bash

#
# Startup script for UFTP AuthServer
#

@cdInstall@

#
# Read basic settings
#
. @etc@/startup.properties

#
# check whether the server might be already running
#
if [ -e $PID ] 
 then 
  if [ -d /proc/$(cat $PID) ]
   then
     echo "An UFTP AuthServer instance may be already running with process id "$(cat $PID)
     echo "If this is not the case, delete the file $INST/$PID and re-run this script"
     exit 1
   fi
fi

#
# setup classpath
#
CP=.$(@cdRoot@find "$LIB" -name "*.jar" -exec printf ":{}" \;)

echo $CP | grep jar > /dev/null
if [ $? != 0 ] 
then
  echo "ERROR: empty classpath, please check that the LIB variable is properly defined."
  exit 1
fi

MAIN_CONFIG=${MAIN_CONFIG:-"${CONF}/container.properties"}

#
# go
#

CLASSPATH=$CP; export CLASSPATH

SERVERNAME=${SERVERNAME:-"AUTHSERVER"}

nohup $JAVA ${MEM} ${OPTS} ${DEFS} eu.unicore.services.USEContainer ${MAIN_CONFIG} ${SERVERNAME} > ${STARTLOG} 2>&1  & echo $! > ${PID}

echo "UFTP ${SERVERNAME} starting"
