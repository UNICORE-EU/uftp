#
# Makefile for 
#  - running unit tests
#  - building RPM and other packages
#

VERSION=3.1.3
RELEASE=1
MVN=mvn

VERSION ?= ${DEFAULT_VERSION}
RELEASE ?= ${DEFAULT_RELEASE}

TESTS = $(wildcard tests/test_*.py)

export PYTHONPATH := lib:.:tests

PYTHON=python3

# ports for testing
export SERVER_PORT := 54434
export CMD_PORT := 54435

# by default, test and build everything
default: test all

test: init runtest

init:
	mkdir -p build
	mkdir -p target

.PHONY: runtest $(TESTS)

runtest: $(TESTS)

$(TESTS):
	@echo "\n** Running test $@"
	@${PYTHON} $@

#
# packaging
#
prepare:
	mkdir -p target
	rm -rf build/*
	@find -name "*~" -delete
	@find -name "*.pyc" -delete
	@find -name "__pycache__" -delete
	mkdir -p build/lib
	cp -R docs build-tools/* build/
	cp lib/* build/lib
	cp CHANGES.txt LICENSE build/docs/
	sed -i "s/VERSION/${VERSION}/" build/pom.xml
	sed -i "s/MY_VERSION = \"DEV\"/MY_VERSION = \"${VERSION}\"/" build/lib/UFTPD.py

deb: prepare
	cd build && ${MVN} package -Ppackman -Dpackage.type=deb -Ddistribution=Debian -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/*.deb target/

rpm: prepare
	cd build && ${MVN} package -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/*.rpm target/

tgz: prepare
	cd build && ${MVN} package -Ppackman -Dpackage.type=bin.tar.gz -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/*.tar.gz target/

all: tgz deb rpm
	echo "Done."

clean:
	@find -name "*~" -delete
	@find -name "*.pyc" -delete
	@find -name "__pycache__" -delete
	@rm build -rf
	@rm target -rf

run-test-server:
	python3 lib/UFTPD.py

run-test-ssl-server: export SSL_CONF = tests/uftp-ssl.conf
run-test-ssl-server: export ACL = tests/uftpd.acl
run-test-ssl-server: export VERBOSE = true

run-test-ssl-server:
	@python3 lib/UFTPD.py
