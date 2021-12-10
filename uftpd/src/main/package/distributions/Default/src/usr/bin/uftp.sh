#!/bin/bash
#
# Client script for executing a UNICORE UFTP data transfer
# (used by UNICORE/X for server-to-server transfers)
#
#  usage: uftp.sh [OPTIONS]
#                  
#   -l,--listen-host <Server host>    Hostname of the server socket
#   -L,--listen-port <Server port>    Port of the server socket
#   -a,--advertise-host <Server host> Hostname of public server in place of listen-host
#   -f,--file <File name>             Local file name
#   -n,--streams <Streams>            Number of streams
#   -r,--receive                      Receive data
#   -s,--send                         Send data
#   -x,--secret <Secret>              Authorisation secret
#   -E,--encryption-key <Key>         (Optional) Encryption key (12 characters)
#   -b,--buffersize <size>            (Optional) File read/write buffer size(in kbytes)

LIB=/usr/share/unicore/uftpd/lib

if ls ${LIB}/uftp-core-* > /dev/null 2>&1 ; then
   : # deb/rpm install
else
    # tgz install
    dir=$(dirname $0)
    LIB=$(dirname $dir)/lib
fi

#
# Java command 
#
JAVA=java

# helper function to set an option if it is not already set
Options=( )
set_option(){
	if [[ "$UFTP_OPTS" != *$1* ]]
	then
		N=${#Options[*]}
		Options[$N]="-$1$2"
	fi
}

#
# Memory for the Java VM
#
set_option "Xmx" "128m"

#
# setup classpath
#
CP=$(find "$LIB" -name "*.jar" -exec printf ":{}" \; )
CP=."$CP"

CLASSPATH=$CP; export CLASSPATH

#
# go
#
$JAVA "${Options[@]}" eu.unicore.uftp.client.ClientFactory ${1+"$@"}
