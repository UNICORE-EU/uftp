# UFTP Authserver

This repository contains the source code for the
UFTP authentication service ("Auth server").

The Auth server is a RESTful service for authenticating users
and initiating UFTP transfers. It is indended to be used with
the 'uftp' client and provides access to one or more UFTPD servers.

Besides the authentication feature for UFTP data transfer,
the Auth server also provides REST APIs for data sharing
and accessing shared data sets.

The Auth server is based on the UNICORE Services Environment, and all
of UNICORE's flexible authentication features and security
configuration options are available as well. For example, the Auth
server can be deployed behind a UNICORE Gateway, or it can be
configured to use Unity for authenticating users.

## Download and installation

Working binaries can be found in the [GitHub Releases](https://github.com/UNICORE-EU/uftp/releases) section
or [downloaded from SourceForge](https://sourceforge.net/projects/unicore/files/Servers/UFTP-AuthServer)

To run, you'll also need a Java runtime (version 11 or later).

## Documentation

See the [Auth server manual](https://uftp-docs.readthedocs.io/en/latest/admin-docs/authserver/index.html)

## Building from source

To build from source you need Java (11+) and Apache Maven. 
Check the versions given in the pom.xml file. 

The following commands create distribution packages
in zip, tgz, deb and rpm formats.

 * zip
```
mvn install -DskipTests
```

 * tar.gz
 
```
mvn package -DskipTests -Ppackman -Dpackage.type=bin.tar.gz
```
 
 * Debian

```
mvn package -DskipTests -Ppackman -Dpackage.type=deb -Ddistribution=Debian
```

 * RedHat
 
```
mvn package -DskipTests -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat
```



