#
# Makefile for creating the native library uftp-unix.so
#

#
# Configuration
#
SHELL=/bin/sh
JAVA_HOME=/usr/lib64/jvm/java-6-openjdk
UFTP_VERSION=1.2.1

# directory containing uftp-<version>.jar
LIB=../lib

# Java classpath 
CP=${LIB}/uftp-${UFTP_VERSION}.jar

# temporary build directory
TARGET=target

# JDK subdirectory containing platform-specific 
# include files (see INCLUDE definition below)
PLATFORM=linux

INCLUDE=-I/usr/include -I${JAVA_HOME}/include -I${JAVA_HOME}/include/${PLATFORM} -I${TARGET}/include

# tools
CC=cc
LD=ld
JAVAH=${JAVA_HOME}/bin/javah


CLASSES=eu.unicore.uftp.server.unix.UnixUser eu.unicore.uftp.server.unix.UnixGroup

check:
	@echo "JAVA_HOME = ${JAVA_HOME}"
	@echo "LIB = ${LIB}"
	@echo "TARGET = ${TARGET}"
	@echo "Classpath: ${CP}"

init: check
	mkdir -p ${TARGET}/include

create-headers: init
	@echo "Have libraries: ${CP}"
	${JAVAH} -classpath ${CP} -jni -d ${TARGET}/include ${CLASSES}

clean-headers:
	rm -f ${TARGET}include/*.h

compile: create-headers
	${CC} -fPIC -c ${INCLUDE} -o ${TARGET}/UnixGroup.o src/UnixGroup.c
	${CC} -fPIC -c ${INCLUDE} -o ${TARGET}/UnixUser.o src/UnixUser.c

build: compile
	${LD} -G -o ${TARGET}/libuftp-unix.so ${TARGET}/UnixUser.o ${TARGET}/UnixGroup.o

install: build
ifneq "${TARGET}" "${LIB}"
	cp ${TARGET}/libuftp-unix.so ${LIB}
endif

clean: init
	rm -f ${TARGET}/*.o ${TARGET}/*.so

