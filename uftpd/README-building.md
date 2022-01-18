Building
--------

You need Java 8 and Apache Maven.

The java code is then built and tested using

 `mvn install`


### Updating the native library


The UFTPD server uses a native library, which is expected in the
`src/main/package/distributions/Default/src/usr/share/unicore/uftpd/lib`
directory. A version compiled on a recent Linux is already provided.
If the existing version needs to be rebuilt for some reason,
the Makefile and C code is provided in `src/main/native` folder.
To use the Makefile, it is required to run `mvn install` first.
Then the Makefile needs to be edited and the correct paths 
need to be provided. Finally a `make install` will build the new
version of the native lib. It should be placed into the directory 
above and committed to SVN.


### Creating distribution packages

The following commands create the distribution packages
in tgz, deb and rpm formats

#### tgz
 `mvn package -DskipTests -Ppackman -Dpackage.type=bin.tar.gz`

#### deb
 `mvn package -DskipTests -Ppackman -Dpackage.type=deb -Ddistribution=Debian`

#### rpm
 `mvn package -DskipTests -Ppackman -Dpackage.type=rpm -Ddistribution=RedHat`



