***
***  README for upgrades to this server version
***

### UPDATING from 2.0.0 to 2.1.0

In case you use the data sharing feature in production you will need
to migrate your data since the database table names needed to be
changed! Please contact us for assistance with this process!

### UPGRADING from 1.x to 2.x

The remainder of this file describes how to upgrade to the current
release, which is based on UNICORE 8.0

For a list of new features, fixes etc, see the CHANGES.txt file.

For the 2.x release, updating from 1.x is more involved than usual
because the wsrflite.xml config file is no longer
used. It is replaced by a simpler and shorter properties
file.

*** helper tool: extract-properties.py

For simplifying the update we provide a helper tool that can extract
the settings from the wsrflite.xml file into the new properties
format.

***
***  Update procedure
***

As a first step and precaution, you should make backups of your 
existing config files and put them in a safe place.

In the following, LIB refers to the directory containing the jar files
for the component, and CONF to the config directory of the existing
installation.

It is assumed that you have unpacked the tar.gz file somewhere, 
e.g. to /tmp/

In the following, this location will be denoted as "$NEW":

$> export NEW=/tmp/unicore-authserver-2.1.0

 - stop the server. If not yet done, make a backup of the config files.

 - update the jar files:
 
   $> rm -rf LIB/*
   $> cp $NEW/lib/*.jar LIB/

 - combine the properties from wsrflite.xml into a new container.properties

   $> $NEW/extract-properties.py conf/wsrflite.xml >> conf/container.properties

 - remove conf/wsrflite.xml

 - update the start script $NEW/bin/start.sh to use the new container.properties

   $> sed -i "s/wsrflite.xml/container.properties/g" bin/start.sh 
   $> sed -i "s/Kernel/USEContainer/g" bin/start.sh 
 
 - start the server

 - check the logs for any ERROR or WARN messages and if necessary correct them
