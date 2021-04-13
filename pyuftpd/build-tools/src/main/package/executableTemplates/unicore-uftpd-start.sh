#!/bin/bash
#
# Startup script for the UNICORE UFTPD server
#

#
# Source the config file
#
@cdInstall@

. @etc@/uftpd.conf

#
# check whether a server is already running
#
if [ -e $UFTPD_PID ] 
 then 
  if [ -d /proc/$(cat $UFTPD_PID) ]
   then
     echo "A UFTPD instance may be already running with process id "$(cat $UFTPD_PID)
     echo "If this is not the case, delete the file $PID and re-run this script"
     exit 1
   fi
fi

#
# Python interpreter
#
PYTHON=python3

export PYTHONPATH=$UFTPD_LIB

#
# go
#
$PYTHON $UFTPD_LIB/UFTPD.py & echo $! > ${UFTPD_PID}
