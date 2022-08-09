#
# Makefile for creating the native library uftp-unix.so
#

#
# Configuration
#
SHELL=/bin/sh
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
UFTP_VERSION=2.9.0

# directory containing uftp-<version>.jar
LIB=../lib

# temporary build directory
TARGET=target

# JDK subdirectory containing platform-specific 
# include files (see INCLUDE definition below)
PLATFORM=linux

INCLUDE=-I/usr/include -I${JAVA_HOME}/include -I${JAVA_HOME}/include/${PLATFORM} -I./include

# tools
CC=cc
LD=ld
JAVAC=javac

# for header generation
JAVASRC=.
CLASSES=${JAVASRC}/eu/unicore/uftp/server/unix/UnixUser.java ${JAVASRC}/eu/unicore/uftp/server/unix/UnixGroup.java

check:
	@echo "JAVA_HOME = ${JAVA_HOME}"
	@echo "LIB = ${LIB}"
	@echo "TARGET = ${TARGET}"
	@echo "Source files (if you need new header files) = ${JAVASRC}"

init: check
	mkdir -p ${TARGET}/include

create-headers: init
	@echo "Updating header files"
	${JAVAC} -h ./include -sourcepath ${JAVASRC} ${CLASSES}

compile: init
	${CC} -fPIC -D_GNU_SOURCE -c ${INCLUDE} -o ${TARGET}/UnixGroup.o src/UnixGroup.c
	${CC} -fPIC -D_GNU_SOURCE -c ${INCLUDE} -o ${TARGET}/UnixUser.o src/UnixUser.c

build: compile
	${LD} -G -o ${TARGET}/libuftp-unix.so ${TARGET}/UnixUser.o ${TARGET}/UnixGroup.o

install: build
ifneq "${TARGET}" "${LIB}"
	cp ${TARGET}/libuftp-unix.so ${LIB}
endif

clean: 
	rm -f ${TARGET}/*.o ${TARGET}/*.so

