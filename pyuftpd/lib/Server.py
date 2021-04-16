#
# Helpers to create sockets
#

import errno
import socket
import sys

import Connector
import Log
from SSL import setup_ssl, verify_peer, convert_dn


def configure_socket(sock):
    """
    Setup socket options (keepalive).
    """
    after_idle = 5
    interval = 1
    max_fails = 3
    sock.settimeout(None)
    if not sys.platform.startswith("win"):
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
    if sys.platform.startswith("darwin"):
        TCP_KEEPALIVE = 0x10
        sock.setsockopt(socket.IPPROTO_TCP, TCP_KEEPALIVE, interval)
    if sys.platform.startswith("linux"):
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPIDLE, after_idle)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPINTVL, interval)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_KEEPCNT, max_fails)


def close_quietly(closeable):
    try:
        closeable.close()
    except:
        pass

def update_acl(configuration, LOG):
    with open(configuration.get('ACL'), "r") as f:
        lines = f.readlines()
        acl = configuration.get('uftpd.acl')
        for line in lines:
            try:
                line = line.strip()
                if line.startswith("#") or len(line)==0:
                    continue
                dn = convert_dn(line)
                LOG.info("Allowing access for <%s>" % line)
                acl.append(dn)
            except Exception as e:
                LOG.error("ACL entry could not be parsed: %s %s" % (line, e))

def setup_cmd_server_socket(configuration, LOG: Log):
    """
    Return command socket for communicating
    with the authentication server(s)

    Parameters: dictionary of config settings, logger
    """

    host = configuration['CMD_HOST']
    port = configuration['CMD_PORT']
    ssl_mode = configuration.get('SSL_CONF') is not None
    if ssl_mode:
        with open(configuration.get('SSL_CONF'), "r") as f:
            lines = f.readlines()
            for line in lines:
                try:
                    key, value = line.split("=",1)
                    configuration[key.strip()]=value.strip()
                except:
                    pass
        update_acl(configuration, LOG)
    
    LOG.info("UFTPD Command server socket started on %s:%s" % (host, port))
    LOG.info("SSL enabled: %s" % ssl_mode)
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((host, port))
    if ssl_mode:
        server = setup_ssl(configuration, server, LOG, True)
    else:
        LOG.info("*****")
        LOG.info("*****   WARNING:")
        LOG.info("*****   Using a plain-text socket for receiving commands.")
        LOG.info("*****   On production systems you should enable SSL!")
        LOG.info("*****   Consult the UFTPD manual for details.")
        LOG.info("*****")
    return server

def accept_command(server, configuration, LOG: Log):
    """ Waits for a connection from the Auth server. 
    Upon a new connection, it is checked it is from a valid source.
    If yes, the message is read and returned to the caller for processing.
    """
    ssl_mode = configuration.get('SSL_CONF') is not None

    server.listen(2)

    while True:
        try:
            (auth, (auth_host, _x)) = server.accept()
        except EnvironmentError as e:
            if e.errno != errno.EINTR:
                LOG.error("Error waiting for new connection: " + str(e))
            continue

        if ssl_mode:
            try:
                verify_peer(configuration, auth, LOG)
            except EnvironmentError as e:
                LOG.error("Error verifying connection from %s : %s" % (
                    auth_host, str(e)))
                close_quietly(auth)
                continue

        configure_socket(auth)
        connector = Connector.Connector(auth,LOG,conntype="COMMAND")
        LOG.debug("Accepted %s" % connector.info())
        return connector

    
def setup_ftp_server_socket(configuration, LOG: Log):
    """
    Return FTP listener socket

    Parameters: dictionary of config settings, logger
    """

    host = configuration['SERVER_HOST']
    port = configuration['SERVER_PORT']

    LOG.info("UFTPD Listener server socket started on %s:%s" % (host, port))
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((host, port))
    return server


def accept_ftp(server, LOG: Log):
    """ Waits for a connection to the FTP socket
    """
    server.listen(2)

    while True:
        try:
            (client, (_x, _y)) = server.accept()
        except EnvironmentError as e:
            if e.errno != errno.EINTR:
                LOG.error("Error waiting for new connection: " + str(e))
            continue

        configure_socket(client)
        return Connector.Connector(client,LOG)


def setup_data_server_socket(host="0.0.0.0", port_range=(0,-1,-1)):
    """
    Return listener socket for data connections
    """

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    port = port_range[0]
    if port>0:
        raise Exception("Port range not yet implemented")
    server.bind((host, port))
    return server


def accept_data(server, LOG: Log, expected_client=None):
    """ Waits for a data connection
    """
    server.listen(2)
    attempts = 0
    while attempts < 3:
        try:
            (client, (client_host, _x)) = server.accept()
            if expected_client is not None:
                if client_host!=expected_client:
                    raise Exception("Connection from unexpected host %s" % client_host)
            return Connector.Connector(client, LOG, conntype="DATA", binary_mode=True)
        except EnvironmentError as e:
            LOG.error(e)
            attempts+=1
