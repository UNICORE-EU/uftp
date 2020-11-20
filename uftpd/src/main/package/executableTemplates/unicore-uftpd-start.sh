#!/bin/bash
#
# Startup script for the UNICORE UFTPD server
#

STARTLOG=@log@/startup.log

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
# Java command 
#
JAVA=java

#
# Options to the Java VM
#

#
# helper function to set an option if it is not already set
#
# arg1: option name (without leading "-", e.g "Ducc.extensions")
# arg2: option value (e.g. =conf/extensions)
#
Options=( )
set_option(){
	if [[ "$UCC_OPTS" != *$1* ]]
	then
		N=${#Options[*]}
		Options[$N]="-$1$2"
	fi
}


#
# Memory for the VM
#
set_option "Xmx" "${UFTPD_MEM}m"

#
# log configuration
#
set_option "Dlog4j.configurationFile" "=@filePrefix@@etc@/logging.properties"

#
# SSL config file
#
set_option "Duftpd-ssl.conf" "=${SSL_CONF}"

#
# ACL file
#
set_option "Duftpd.acl" "=${ACL}"


#
# put all jars in lib folder on the classpath
#

CP=$(@cdRoot@find "$UFTPD_LIB" -name "*.jar" -exec printf ":{}" \; )
CP=."$CP"

PARAMS="-c $CMD_HOST -p $CMD_PORT -l $SERVER_HOST -L $SERVER_PORT"
PARAMS=$PARAMS" -m $MAX_CONNECTIONS -b $BUFFER_SIZE"
PARAMS=$PARAMS" -s $MAX_STREAMS"
[ ! -z "$ADVERTISE_HOST" ] && PARAMS=$PARAMS" -a $ADVERTISE_HOST"
[ ! -z "$PORT_RANGE" ] && PARAMS=$PARAMS" -P $PORT_RANGE"
[ ! -z "$DISABLE_IP_CHECK" ] && PARAMS=$PARAMS" -I"

CLASSPATH=$CP; export CLASSPATH

#
# path to native lib required for setuid
#
LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$UFTPD_LIB
export LD_LIBRARY_PATH

#
# go
#
nohup $JAVA "${Options[@]}" ${UFTPD_OPTS} eu.unicore.uftp.server.UFTPServer $PARAMS > $STARTLOG 2>&1 & echo $! > $UFTPD_PID
