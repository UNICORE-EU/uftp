#!/bin/bash

#
# Check status of UNICORE UFTPD
#
# before use, make sure that the "service name" used in 
# this file is the same as in the corresponding start.sh file

@cdInstall@

. @etc@/uftpd.conf

# service name
SERVICE=uftp

if [ ! -e $UFTPD_PID ]
then
 echo "UNICORE UFTPD not running (no PID file)"
 exit 7
fi

PID=$(cat $UFTPD_PID)

if ps axww | grep -v grep | grep $PID > /dev/null 2>&1 ; then
 echo "UNICORE UFTPD running with PID ${PID}"
 exit 0
fi

#else not running, but PID found
echo "warn: UNICORE UFTPD not running, but PID file $UFTPD_PID found"
exit 3

