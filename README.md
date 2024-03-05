# UNICORE File transfer (UFTP)

This repository contains the source code for various server components
for the UNICORE FTP high-performance file transfer toolkit.

WORKING BINARIES can be downloaded from
 * GitHub [Releases](https://github.com/UNICORE-EU/uftp/releases)
 * or from [SourceForge](https://sourceforge.net/projects/unicore/files/Servers)

DOCUMENTATION for users and administrators can be found at
[UFTP-Docs](https://uftp-docs.readthedocs.io)

 * pyuftpd - contains the sources for the 'uftpd' server,
   written in Python

 * authserver -  a set of services providing authentication for UFTP
   as well as data sharing features

 * core - contains the core Java library, as well as a
   reference implementation of the 'uftpd' server, which
   can be used in tests of other Java components.

 * datashare - library containing support code for the data sharing feature


Client components can be found in their own repositoris:
 * [PyUFTP commandline client](https://github.com/UNICORE-EU/pyuftp)
 * ['uftp' Java commandline client](https://github.com/UNICORE-EU/uftp-javaclient)

