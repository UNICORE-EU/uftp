#
# Makefile for 
#  - running unit tests
#  - building RPM and other packages
#  - creating and deploying documentation  
#

VERSION=3.0.0
RELEASE=1
DOCVERSION=3.0.0
MVN=mvn

VERSION ?= ${DEFAULT_VERSION}
DOCVERSION ?= ${DEFAULT_DOCVERSION}
RELEASE ?= ${DEFAULT_RELEASE}


TESTS = $(wildcard tests/*.py)

export PYTHONPATH := lib:.:tests

PYTHON=python3

# ports for testing
export SERVER_PORT := 54434
export CMD_PORT := 54435

# by default, test and build everything
default: test packages

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
# documentation
#
DOCOPTS=-Ddocman.enabled -Ddoc.relversion=${DOCVERSION} -Ddoc.compversion=${DOCVERSION} -Ddoc.src=docs/manual.txt -Ddoc.target=pyuftpd-manual

doc-generate:
	mkdir -p target
	if [ ! -d target/docman ] ; then svn export https://svn.code.sf.net/p/unicore/svn/tools/docman/trunk target/docman --force ; fi 
	ant -f target/docman/doc-build.xml -lib target/package/tools ${DOCOPTS} doc-all

doc-deploy:
	ssh bschuller@unicore-dev.zam.kfa-juelich.de sudo rm -rf /var/www/documentation/pyuftpd-${DOCVERSION}
	ssh bschuller@unicore-dev.zam.kfa-juelich.de sudo mkdir /var/www/documentation/pyuftpd-${DOCVERSION}
	ssh bschuller@unicore-dev.zam.kfa-juelich.de mkdir -p pyuftpd-${DOCVERSION}
	scp target/site/* bschuller@unicore-dev.zam.kfa-juelich.de:pyuftpd-${DOCVERSION}
	ssh bschuller@unicore-dev.zam.kfa-juelich.de sudo mv pyuftpd-${DOCVERSION}/* /var/www/documentation/pyuftpd-${DOCVERSION}

doc: doc-generate doc-deploy


#
# packaging
#
define prepare-specific
mkdir -p target
rm -rf build/*
mkdir -p build/lib
cp -R docs build-tools/* build/
cp lib/* build/lib
cp CHANGES LICENCE build/docs/
cp build-tools/conf.properties.bssspecific build/src/main/package/conf.properties
#sed -i "s/name=tsi/name=$1/" build/src/main/package/conf.properties
sed -i "s/VERSION/${VERSION}/" build/pom.xml
sed -i "s/__VERSION__/${VERSION}/" build/lib/TSI.py
find build | grep .svn | xargs rm -rf
endef

#
# generic rules for building deb and prm
#

%-deb: %-prepare
	cd build && ${MVN} package -Ppackman -Dpackage.type=deb -Ddistribution=Debian -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/*.deb target/

%-rpm: %-prepare
	cd build && ${MVN} package -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/*.rpm target/

%-tgz: %-prepare
	cd build && ${MVN} package -Ppackman -Dpackage.type=bin.tar.gz -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/*.tar.gz target/


#
# attempts to build all packages (even if they are the "wrong" kind on the current OS)
#
%-all: %-deb %-rpm %-tgz
	echo "Done."

#
# builds the correct package for the current OS
#
%-package: %-prepare
	cd build && ${MVN} package -Ppackman -Dpackage.version=${VERSION} -Dpackage.release=${RELEASE}
	cp build/target/unicore* target/
	echo "Done."

#
# Generic binary tgz containing everything required to install the TSI
# using the Install.sh script
#
tgz:
	@mkdir -p target
	@mkdir -p build
	@rm -rf build/*
	@cp -R build-tools docs lib build/
	@cp README CHANGES LICENCE Install.sh build/
	@sed -i "s/__VERSION__/${VERSION}/" build/lib/UFTPD.py
	@tar czf target/uftpd-${VERSION}.tgz --xform="s%^build/%unicore-tsi-${VERSION}/%" --exclude-vcs build/*

#
# clean
#

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
