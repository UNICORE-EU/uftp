#!/bin/bash

#
# Shutdown script for UNICORE UFTPD
#

@cdInstall@

. @etc@/uftpd.conf


if [ ! -e $UFTPD_PID ]
then
 echo "No PID file found, server probably already stopped."
 exit 0
fi


cat $UFTPD_PID | xargs kill -SIGTERM

#
# wait for shutdown
# 
P=$(cat $UFTPD_PID)
echo "Waiting for server to stop..."
stopped="no"
until [ "$stopped" = "" ]; do
  stopped=$(ps -p $P | grep $P)
  if [ $? != 0 ] 
  then
    stopped=""
  fi
  sleep 2
done

echo "Server stopped."

rm -f $UFTPD_PID
