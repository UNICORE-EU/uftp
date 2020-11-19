#!/bin/bash

#
# This testing utility sends a job to the UFTPD server,
# i.e. announces that a client will connect.
# Please refer to the UFTPD manual!
#

CONF=/etc/unicore/uftpd/

if [ -f ${CONF}/uftpd.conf] ; then
   : # deb/rpm install
else
    # tgz install
    dir=$(dirname $0)
    CONF=$(dirname $dir)/conf
fi


#
# source config file
#
. $CONF/uftpd.conf


#
# netcat executable
#
NC=nc

#
# parse options
#
while getopts ":c:f:x:u:g:n:s:" opt; do
  case $opt in
    c)
      CLIENT=$OPTARG
      ;;
    f)
      FILE=$OPTARG
      ;;
    s)
      SEND=$OPTARG
      ;;
    x)
      SECRET=$OPTARG
      ;;
    u)
      USER=$OPTARG
      ;;
    g)
      GROUP=$OPTARG
      ;;
    x)
      SECRET=$OPTARG
      ;;
    n)
      STREAMS=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done

#
# go
#

${NC} $CMD_HOST $CMD_PORT <<EOF
request-type=uftp-transfer-request
client-ip=$CLIENT
send=$SEND
file=$FILE
streams=$STREAMS
secret=$SECRET
user=$USER
group=$GROUP

EOF
