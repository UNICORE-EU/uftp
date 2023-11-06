# UNICORE File transfer (UFTP)

This repository contains the source code for various server components
for the UNICORE FTP high-performance file transfer toolkit.

WORKING BINARIES can be
[downloaded from SourceForge](https://sourceforge.net/projects/unicore/files)

DOCUMENTATION for users and administrators can be found at
[UFTP-Docs](https://uftp-docs.readthedocs.io)

 * core - contains the core Java library, as well as a
   reference implementation of the 'uftpd' server, which
   can be used in tests of other Java components.

 * pyuftpd - contains the production version of the uftpd server,
   written in Python

 * authserver -  a set of services providing authentication for UFTP as well
   as data sharing features

 * datashare - library containing support code for the data sharing feature

Client components can be found in their own repositoris:
 * [PyUFTP commandline client](https://github.com/UNICORE-EU/pyuftp)
 * ['uftp' Java commandline client](https://github.com/UNICORE-EU/uftp-javaclient)

