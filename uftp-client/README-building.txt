#
# Building packages for the 'uftp' client
#

You need Java and Apache Maven. 
Check the versions given in the pom.xml file. 

#
# Creating distribution packages
#

The following commands create the distribution packages
in tgz, deb and rpm formats. The versions are taken from the pom.xml

#tgz
 mvn package -DskipTests -Ppackman -Dpackage.type=bin.tar.gz
 
#deb
 mvn package -DskipTests -Ppackman -Dpackage.type=deb -Ddistribution=Debian

#rpm redhat
 mvn package -DskipTests -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat



