#!/bin/bash 
# #############################################################################
# Function: start and stop UNICORE UFTPD
#
#
# Usage:   unicore-uftpd  { start | stop | status | restart }
#
# #############################################################################
#
### BEGIN INIT INFO
# Provides: unicore-uftpd
# Required-Start: $network $remote_fs
# X-UnitedLinux-Should-Start: 
# Required-Stop: $network
# X-UnitedLinux-Should-Stop: 
# Default-Start: 3 5
# Default-Stop: 0 1 2 6
# Description: Starts the UNICORE UFTPD server
#              
### END INIT INFO


#
# chkconfig: 235 96 04
# description: UNICORE UFTPD server
#

#
# Configuration
#
BIN_DIR="/usr/sbin"
START="${BIN_DIR}/unicore-uftpd-start.sh"
STOP="${BIN_DIR}/unicore-uftpd-stop.sh"
STATUS="${BIN_DIR}/unicore-uftpd-status.sh"

# for testing use non-root user
USER="root"

case "$1" in

start )
         echo "Starting UNICORE UFPD"
         sudo -u $USER $START > /dev/null 2>&1 &            
        ;;
stop  )
         echo "Stopping UNICORE UFTPD"
         sudo -u $USER $STOP
        ;;
status  )
         sudo -u $USER $STATUS
        ;;
restart|force-reload )
	$0 stop
	$0 start
	;;
*)
        echo "Usage: $0  { start | stop | restart }"
        ;;
esac  
