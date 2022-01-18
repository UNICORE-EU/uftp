Building packages 
-----------------

You need Java 8 and Apache Maven.

The java code is then built and tested using

 `mvn install`


### Creating distribution packages

The following commands create the distribution packages
in tgz, deb and rpm formats

#### tgz
 `mvn package -DskipTests -Ppackman -Dpackage.type=bin.tar.gz`

#### deb
 `mvn package -DskipTests -Ppackman -Dpackage.type=deb -Ddistribution=Debian`

#### rpm
 `mvn package -DskipTests -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat`


