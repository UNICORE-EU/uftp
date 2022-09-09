***
***  README for upgrades to this server version
***

*** H2 DATABASE NOTE (if you use the data share feature and the H2 database)

   This release includes the new H2 v2 engine, which is unfortunately
   not directly backwards compatible to the one used in previous versions.
   If you want to keep existing data during the update, you'll
   need to convert the databases, or use the old H2 v1 version


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

$> export NEW=/tmp/unicore-authserver-2.7.0

 - stop the server. If not yet done, make a backup of the config files.

 - update the jar files:
 
   $> rm -rf LIB/*
   $> cp $NEW/lib/*.jar LIB/

 - start the server

 - check the logs for any ERROR or WARN messages and if necessary correct them
