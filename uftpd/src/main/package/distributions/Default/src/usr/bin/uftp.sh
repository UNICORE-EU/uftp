#!/bin/bash
#
# Client script for executing a UNICORE UFTP data transfer
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


#
# Directory containing the UFTPD libraries
#
export UFTP_LIB=/usr/share/unicore/uftpd/lib

#
# Java command 
#
JAVA=java

# helper function to set an option if it is not already set
#
# arg1: option name (without leading "-", e.g "Ducc.extensions")
# arg2: option value (e.g. =conf/extensions)
#
Options=( )
set_option(){
	if [[ "$UFTP_OPTS" != *$1* ]]
	then
		N=${#Options[*]}
		Options[$N]="-$1$2"
	fi
}


#
# Options to the Java VM
#

#
# Memory for the VM
#
set_option "Xmx" "128m"


#
# log configuration
#
set_option "Dlog4j.configuration" "=file:///etc/unicore/uftpd/client.logging.properties"

#
# put all jars in lib/ on the classpath
#

CP=$(find "$UFTP_LIB" -name "*.jar" -exec printf ":{}" \; )
CP=."$CP"

CLASSPATH=$CP; export CLASSPATH

#
# go
#
$JAVA "${Options[@]}" eu.unicore.uftp.client.UFTPClient ${1+"$@"}



