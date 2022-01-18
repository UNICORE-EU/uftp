Update procedure
----------------

As a first step and precaution, you should make backups of your 
existing config files and put them in a safe place.

In the following, LIB refers to the directory containing the jar files for the component, and CONF to the config directory of the existing installation.

It is assumed that you have unpacked the tar.gz file somewhere, 
e.g. to `/tmp/`

In the following, this location will be denoted as "`$NEW`":

`export NEW=/tmp/unicore-authserver-2.5.0`

 - Stop the server. If not yet done, make a backup of the config files.

 - Update the jar files:
 
   `rm -rf LIB/*`
   `cp $NEW/lib/*.jar LIB/`

 - Start the server.

 - Check the logs for any ERROR or WARN messages and if necessary correct them.