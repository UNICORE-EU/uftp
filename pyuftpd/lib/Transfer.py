
from ftplib import FTP
from time import time
import sys

class Transfer:
    """
    Send/receive file from a remote UFTPD server.
    Authentication has to be done out-of-band: this class requires the
    remote UFTPD's host/port and the one-time password to open a session.
    Uses ftplib to open the session and interact with the UFTPD server
    """

    def __init__(self, host, port, password):
        self.host = host
        self.port= port
        self.password = password
        self.BUFFER_SIZE = 65536
        self.rate_limit = -1

    def connect(self):
       """open an FTP session at the given UFTP server"""
       self.ftp = FTP()
       self.ftp.connect(self.host, self.port)
       self.ftp.login("anonymous", self.password)

    def send_file(self, local_file, remote_file, offset=None, length=-1):
        """ send a local file to the remote server """
        with open(local_file, "rb") as f:
            target = self._get_write_socket(remote_file, offset).makefile("wb")
            if offset:
                f.seek(offset)
            self._copy_data(f, target, length)
    
    def receive_file(self, remote_file, local_file, offset=None, length=-1):
        """ gets a remote file and stores to the given local file"""
        with open(local_file, "wb") as f:
            source = self._get_read_socket(remote_file, offset).makefile("rb")
            if offset:
                f.seek(offset)
            self._copy_data(source, f, length)

    def _copy_data(self, source, target, num_bytes=-1):
        total = 0
        limit_rate = self.rate_limit > 0
        start_time = int(time()*1000)
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
                self.control_rate(total, start_time)
        return total
    
    def _get_write_socket(self, path, offset=None):
        self.connect()
        if offset:
            reply = self.ftp.sendcmd(f"RANG {offset}")
            if not reply.startswith("350"):
                raise OSError("Error setting RANG: %s" % reply)
        return self.ftp.transfercmd("STOR %s" % path)

    def _get_read_socket(self, path, offset):
        return self.ftp.transfercmd("RETR %s" % path, rest=offset)

    def _control_rate(self, total, start_time):
        interval = int(time()*1000 - start_time) + 1
        current_rate = 1000 * total / interval
        if current_rate < self.rate_limit:
            self.sleep_time = int(0.5 * self.sleep_time)
        else:
            self.sleep_time = self.sleep_time + 5
            sleep(0.001*self.sleep_time)
            
def launch_transfer(remote_file_spec, local_file, mode="send"):
    """ helper method to launch a transfer """
    remote_file, server_spec, passwd = remote_file_spec
    host, port = server_spec.split(":")
    transfer = Transfer(host, int(port), passwd)
    transfer.connect()
    if mode=="send":
        transfer.send_file(local_file, remote_file)
    else:
        transfer.receive_file(remote_file, local_file)
