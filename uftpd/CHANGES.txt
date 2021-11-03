Changelog
---------
The UFTP issue tracker is at
https://sourceforge.net/p/unicore/uftp-issues

UFTP 2.12.0
-----------
 - new feature: HASH command (#70)

UFTP 2.11.0
-----------
 - update to log4j/2, logging config update recommended
 - update to securityLibrary 5.3.1
 - new feature: implement restart command ("REST")
 - fix: persistent sessions were removed even
   when still active
 - fix: handling of absolute vs relative directories, allow 
   creating sessions with a fixed base directory
 - fix: wrong format string for MFMT

Starting with UFTPD 3.0, we release the file server uftpd only in the
Python implementation. This project is still used for
providing the Java implementation of the base library used for
clients and in the Java servers.

UFTP 2.9.2 (released Nov 16 2020)
---------------------------------
 - fix: debian package now depends on "default-jre-headless"
 - fix: tgz bundle was was missing uftp.sh script

UFTP 2.9.1 (released Oct 16 2020)
---------------------------------
 - fix: FTP session starting directory was not correct in
   some cases

UFTP 2.9.0 (released Sep 21 2020)
---------------------------------
 - new feature: support for FTP proxies (DeleGate and Frox)
 - new feature: "persistent" sessions to allow mounting via
   'curlftpfs' where the login secret is re-used
   several times
 - simplify login handshake handling to better support
   login from non-UFTP clients
 - fix: stat'ing the "/" directory returned empty filename
   and thus listing "/" did not work
 - fix: client sent extra empty lines over the control
   channel
 - fix: less harsh timeouts for control socket communications
 - fix: use setresuid() for UID switching to fix problems
   with root-squash mouted file systems
 
UFTP 2.8.0 (released Jul 06 2020)
---------------------------------
 - new feature: session features can be limited 
   by authserver (e.g. to readonly access)
 - new feature: read public keys from configurable
   list of files (defaults to ~/.ssh/authorized_keys) (#61)
 - new feature: allow to blacklist writing to certain
   files (#62)
 
UFTP 2.7.1 (released Nov 4 2019)
--------------------------------
 - fix: files were not closed when receiving archive
   stream data, leading to a "too many open files" error

UFTP 2.7.0 (released Sept 25 2019)
----------------------------------
 - new feature: support direct unpacking of tar/zip streams
   (#53)
 - fix: keep data connections open in session mode

UFTP 2.6.2 (released Aug 27 2019)
---------------------------------
 - fix some packaging and documentation issues
 - update libs
 - remove support for the old (pre uftp 2.4.0) connection protocol

UFTP 2.6.1 (released Sep 14 2018)
---------------------------------
 - fix: backwards compatibility with old authentication
   protocol version

UFTP 2.6.0 (released Jul 11 2018)
---------------------------------
 - new feature: use CANL for SSL, i.e. greatly improved
   credential and truststore setup (#39)
 - new feature: allow to disable client-IP checking
 - allow to retrieve user info (incl. ssh public keys)
   via command port (#47)
 - update version of commons-codec and commons-io

UFTP 2.5.2 (released Jan 8 2018)
--------------------------------
 - fix: close parallel reader threads after each data
   transfer (#45)

UFTP 2.5.1 (released Nov 8 2017)
--------------------------------
 - systemd support
 - fix packaged version of 'uftp.sh' (#40)
 - client (uftp.sh): print proper error message to stderr (#41)
 - fix: append flag not honored for single-file upload (#42)
 - implement "APPE" (append) command (#44)
 
UFTP 2.5.0 (released June 12 2017)
----------------------------------
 - new feature: multiple allowed client IPs per transfer (#26)
 - new feature: tunneling / traffic forwarding via UFTPD (#36) 
 - fix: NullPointerException when group is null (#34)
 - fix: return Unix file permissions (rwx for owner) in MLST
   reply (#35)

UFTP 2.4.1 (released Dec 7 2016)
--------------------------------
 - fix: IllegalArgumentException when requested Unix group is null
   (https://sourceforge.net/p/unicore/uftp-issues/34)

UFTP 2.4.0 (released Nov 23 2016)
---------------------------------
 - fix: set effective group correctly to requested one, instead of
   default group (https://sourceforge.net/p/unicore/uftp-issues/32)
 - fix: when using port range for data connections,
   ports were not freed, leading to unusable UFTPD
   (https://sourceforge.net/p/unicore/uftp-issues/17)
 - fix: session mode 'mkdir' reports errors
   (https://sourceforge.net/p/unicore/uftp-issues/27)
 - fix: session mode 'mkdir' creates all required parent dirs
 - new feature: implement MFMT command to set file modification time
   (https://sourceforge.net/p/unicore/uftp-issues/28)
 - fix: more flexible and RFC-compliant implementation of the
   FTP protocol (connection establish, authentication, retrieval, ...)
   Compliance tested with Unix 'ftp' and 'curl' tools
   (https://sourceforge.net/p/unicore/uftp-issues/12)
 - new feature: support EPSV in addition to PASV (and thus support IPv6)
   (https://sourceforge.net/p/unicore/uftp-issues/29)
 - new feature: support MLST and MLSD commands
   (https://sourceforge.net/p/unicore/uftp-issues/30)

UFTP 2.3.2 (released Sep 15 2015)
--------------------------------- 
 - new feature: rename files in session mode
   (https://sourceforge.net/p/unicore/uftp-issues/13)
 - new feature: allow to get single file/directory info in session mode 
 - new feature: UFTPD logs usage (transferred bytes, transfer rate, client IP)
   at info level (https://sourceforge.net/p/unicore/uftp-issues/16)
 - new feature: session mode allows to upload data in streaming mode
   (https://sourceforge.net/p/unicore/uftp-issues/14)
 - fix: typo in unicore-uftp-start.sh scripts
   (https://sourceforge.net/p/unicore/uftp-issues/10)
 - fix: when synching a local master with a remote slave 
   on a filesystem mounted with root squash, the rsync tmp 
   file (remote) was not deleted and the target file was not 
   updated (https://sourceforge.net/p/unicore/uftp-issues/11)
 - improvement: simpler logging config (see manual)
 
UFTP 2.3.1 (released Feb 11 2015)
--------------------------------
 - fix: cannot get file info on file systems mounted with root-squash
   (http://sourceforge.net/p/unicore/uftp-issues/6)
 - fix: allow to "stat" also a single file
 - fix: remove stacktrace printout to stdout in parallel reader

UFTP 2.3.0 (released Jan 26 2015)
--------------------------------

 - new feature: allow to specify a port range for the
   data connections (http://sourceforge.net/p/unicore/uftp-issues/2/)
 - fix: cannot get file info on file systems mounted with root-squash
   (http://sourceforge.net/p/unicore/uftp-issues/6)
 - fix: NPE when include/exclude patterns are used

UFTP 2.2.0 (released Nov 3 2014)
--------------------------------

 - new feature: allow to pass in allowed/forbidden file patterns 
   when creating a new session (SF feature #340)
 - improvement: add possibility to 'ping' UFTPD via the control 
   channel
 - fix: user's home is used as inital session base directory
 - fix: fail startup when running as root without setuid 
 - fix: compress/encrypt in session mode did not work

UFTP 2.1.2 (released Oct 2 2014)
--------------------------------

 - new feature: support NATs by adding an ADVERTISE_HOST variable for 
   setting the public IP address of the server in case this is different 
   from the actual SERVER_HOST 
 - improvement: allow to write part of a file in Session mode. This enables
   both restarting a failed transfer, and parallel writes to a file using
   multiple server instances.
 - fix: better error reporting in case of faulty transfer requests
 - fix: sync of very small files did not work
 
UFTP 2.1.0 (released July 25 2014)
---------------------------------
 - improvement: allow to configure limit on control connections per 
   client IP. NOTE: please read the manual since option names 
   have changed:
   MAX_CONNECTIONS : maximum connections per client IP (default: 32)
   MAX_STREAMS     : maximum number of parallel data streams per 
                     connection (default: 8)
   
 - fix: make sure to close client connection in case of protocol 
   or authorisation errors
 - fix: when UFTPD writes an rsync'ed file, the ownership was 
   changed to root (SF bug #742)
 - fix: access control when listing directory
 - fix: in session mode, client connection counter was not decremented on 
   session close

UFTP 2.0.0 (released 10 Dec 2013)
---------------------------------
 
 - new feature: introduced session mode for reading multiple files over a single
   UFTP connection (SF feature #271)
 - new feature: a transfer rate limit can be specified per transfer request
   (SF improvements #3577575)
 - new feature: optional data compression
 - improvement: encryption scheme changed, removed dependency on 
   BouncyCastle library
 - improvement: improved connection setup (SF feature #282)
 - improvement: new Demo CA certificates, using a p12 for keystore, jks for 
   truststore
 - fix: do not accept transfer request if requested user does not exist, 
   and send back an error message

UFTP 1.2.1 (released May 7 2012)
--------------------------------

 - better error reporting in client if all server addresses are unreachable
 - improvement: special handling of "/dev/.." filenames for 
   performance measurements
 - fix: multiple interface support was broken (SF bug #3500430)
 - improve client side error message if connection was refused by the 
   server
 - client reads feature list from server (prepare future UFTP extensions)
 - fix: add missing init script on Debian

UFTP 1.2.0 (released 15 Dec 2011)
---------------------------------

 - improvement: UFTPD sets also supplementary groups
   (SF feature #3439861)
 - fix: effective group ID was always "0" (SF bug #3439859)
 - fix: LD_LIBRARY_PATH was not setup correctly
 - improvement: attempt to load native lib directly at startup
   instead of waiting for first data transfer
 - improvement: better error reporting to client
 
UFTP 1.1.0 (released 7 Oct 2011)
----------------------------------------

 - improvement: support multiple alternative server IP addresses (SF improvement #3389004)
 - improvement: add support for monitoring filetransfer progress
 - fix: configured value of maximum streams per connection was ignored
 - improvement: allow to configure the internal buffer size for file 
   read/write

UFTP 1.0.0 (released 5 Jul 2011)
----------------------------------------

 - first release
