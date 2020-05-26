#!/bin/bash
#
# Send a job to the UNICORE UFTP server, i.e. announce that 
# a client will connect
#
# this implementation  uses the netcat (nc) utility 
#
 
#
# source config file
#
. /etc/unicore/uftpd/uftpd.conf


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
