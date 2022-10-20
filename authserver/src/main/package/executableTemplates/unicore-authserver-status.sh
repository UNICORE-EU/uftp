#!/bin/sh

#
# Check status
#

@cdInstall@

#
# Read basic settings
#
. @etc@/startup.properties

if [ ! -e $PID ]
then
 echo "UNICORE/X not running (no PID file)"
 exit 7
fi

PIDV=$(cat $PID)

SERVERNAME=${SERVERNAME:-"AUTHSERVER"}

if ps axww | grep -v grep | grep $PIDV | grep "${SERVERNAME}" > /dev/null 2>&1 ; then
 echo "UNICORE service ${SERVERNAME} running with PID ${PIDV}"
 exit 0
fi

echo "warn: UNICORE service ${SERVERNAME} not running, but PID file $PID found"
exit 3

