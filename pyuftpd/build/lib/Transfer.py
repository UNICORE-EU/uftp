from contextlib import contextmanager
from ftplib import FTP
import os, socket, sys, time

from Connector import Connector
import GzipConnector
from Log import Logger
from PConnector import PConnector


class Transfer:
    """
    Send/receive file from a remote UFTPD server.
    Authentication has to be done out-of-band: this class requires the
    remote UFTPD's host/port and the one-time password to open a session.
    Uses ftplib to open the session and interact with the UFTPD server
    """

    def __init__(self, host:str, port: int, password: str, LOG: Logger, username: str):
        self.host = host
        self.port= port
        self.password = password
        self.BUFFER_SIZE = 65536
        self.LOG = LOG
        self.username = username
        self.rate_limit = -1
        self.key = None
        self.algo = None
        self.compress = False
        self.number_of_streams = 1
        self.sleep_time = 0

    def connect(self):
       """open an FTP session at the given UFTP server"""
       self.ftp = FTP()
       self.ftp.connect(self.host, self.port)
       self.ftp.login("anonymous", self.password)
       self.LOG.info("Connected to %s:%s" % (self.host, self.port))

    def send_file(self, local_file, remote_file, offset, num_bytes):
        """ send a local file to the remote server """
        with open(local_file, "rb") as f:
            with self.get_writer(remote_file, offset, num_bytes) as target:
                if offset>0:
                    f.seek(offset)
                total, duration = self._copy_data(f, target, num_bytes)
                self.log_usage(True, total, duration)

    def receive_file(self, remote_file, local_file, offset, num_bytes):
        """ gets a remote file and stores to the given local file"""
        with open(local_file, "wb") as f:
            with self.get_reader(remote_file, offset, num_bytes) as source:
                if offset:
                    f.seek(offset)
                total, duration = self._copy_data(source, f, num_bytes)
                self.log_usage(False, total, duration)

    def _copy_data(self, source, target, num_bytes):
        if self.number_of_streams>1:
            # parallel connector expects this
            self.buffer_size = 16384
        total = 0
        limit_rate = self.rate_limit > 0
        start_time = int(time.time())
        if num_bytes<0:
            num_bytes = sys.maxsize
        while total<num_bytes:
            length = min(self.BUFFER_SIZE, num_bytes-total)
            data = source.read(length)
            if len(data)==0:
                break
            to_write = len(data)
            write_offset = 0
            while(to_write>0):
                written = target.write(data[write_offset:])
                if written is None:
                    written = 0
                write_offset += written
                to_write -= written
            total = total + len(data)
            if limit_rate:
                self._control_rate(total, start_time)
        return total, int(time.time()) - start_time
    
    def log_usage(self, send, size, duration):
        if send:
            operation = "Sent"
        else:
            operation = "Received"
        rate = 0.001*float(size)/(float(duration)+1)
        if rate<1000:
            unit = "kB/sec"
            rate = int(rate)
        else:
            unit = "MB/sec"
            rate = int(rate / 1000)
        msg = "USAGE [%s] [%s bytes] [%s %s] [%s]" % (operation, size, rate, unit, self.username)
        self.LOG.info(msg)

    def _get_write_socket(self, path, offset, num_bytes):
        self._set_range(offset, num_bytes)
        return self.ftp.transfercmd("STOR %s" % path)

    def _get_read_socket(self, path, offset, num_bytes):
        self._set_range(offset, num_bytes)
        return self.ftp.transfercmd("RETR %s" % path)

    @contextmanager
    def get_reader(self, path, offset, length):
        connectors = []
        try:
            if offset>0 or length>-1:
                self._set_range(offset, length)
            connectors = self._open_data_connections()
            if self.number_of_streams>1:
                s = PConnector(connectors, LOG=self.LOG, key=self.key, algo=self.algo, compress=self.compress)
            else:
                s = self._wrap_connector(connectors[0], isRead=True)
            reply = self.ftp.sendcmd("RETR %s" % path)
            if not reply.startswith("150"):
                raise OSError("ERROR "+reply)
            #len = int(reply.split(" ")[2])
            yield s #, len
        finally:
            for c in connectors:
                try:
                    c.close()
                except:
                    pass

    @contextmanager
    def get_writer(self, path, offset, length):
        connectors = []
        try:
            self._set_range(offset, length)
            connectors = self._open_data_connections()
            if self.number_of_streams>1:
                s = PConnector(connectors, LOG=self.LOG, key=self.key, algo=self.algo, compress=self.compress)
            else:
                s = self._wrap_connector(connectors[0], isRead=False)
            self.ftp.sendcmd("STOR %s" % path)
            yield s
        finally:
            for c in connectors:
                try:
                    c.close()
                except:
                    pass

    def _wrap_connector(self, conn: Connector, isRead: bool):
        if self.key is not None:
                import CryptUtil
                cipher = CryptUtil.create_cipher(self.key, self.algo)
                if isRead:
                    conn = CryptUtil.DecryptReader(conn, cipher)
                else:
                    conn = CryptUtil.CryptWriter(conn, cipher)
        if self.compress:
            conn = GzipConnector.GzipConnector(conn)
        self.LOG.debug(f"Transfer encrypted={self.key is not None} compress={self.compress}")
        return conn

    def _set_range(self, offset, num_bytes):
        if offset>0 or num_bytes>-1:
            range_cmd = f"RANG {offset}"
            if num_bytes > 0:
                end = offset+num_bytes-1
                range_cmd = f"RANG {offset} {end}"
            reply = self.ftp.sendcmd(range_cmd)
            if not reply.startswith("350"):
                raise OSError("Error setting RANG: %s" % reply)

    def _control_rate(self, total, start_time):
        interval = int(time.time() - start_time) + 1
        current_rate = total / interval
        if current_rate < self.rate_limit:
            self.sleep_time = int(0.5 * self.sleep_time)
        else:
            self.sleep_time = self.sleep_time + 5
            time.sleep(0.001*self.sleep_time)

    def _negotiate_streams(self):
        if self.number_of_streams>1:
            resp = self.ftp.sendcmd(f"NOOP {self.number_of_streams}")
            if resp.startswith("223"):
                # adjust number of connections in case server has limited them
                self.number_of_streams = int(resp.split(" ")[2])

    def _open_data_connections(self) -> list[Connector]:
        self._negotiate_streams()
        connectors = []
        for _ in range(0, self.number_of_streams):
            host, port = self.ftp.makepasv()
            sock = socket.create_connection((host, port), self.ftp.timeout,
                                            source_address=self.ftp.source_address)
            connectors.append(Connector(sock, self.LOG, conntype="DATA", binary_mode=True))
        return connectors

def launch_transfer(remote_file_spec, local_file: str, mode: str, LOG: Logger, username: str, 
                    offset: int=0, length: int=-1,
                    rate_limit: int=-1, number_of_streams: int=1, key: str=None, algo: str=None, compress=False):
    """ helper method to launch a transfer """
    remote_file, server_spec, passwd = remote_file_spec
    host, port = server_spec.split(":")
    pid = os.fork()
    if pid:
        # parent
        return pid
    # child - launch transfer
    LOG.reinit("UFTPD-transfer")
    transfer = Transfer(host, int(port), passwd, LOG, username)
    transfer.rate_limit = rate_limit
    transfer.key = key
    transfer.algo = algo
    transfer.compress = compress
    transfer.number_of_streams = number_of_streams
    transfer.connect()
    try:
        if mode=="send":
            transfer.send_file(local_file, remote_file, offset, length)
        else:
            transfer.receive_file(remote_file, local_file, offset, length)
    except:
        LOG.log_exception()
    os._exit(0)