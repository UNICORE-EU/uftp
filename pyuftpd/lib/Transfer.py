
from ftplib import FTP
import os, sys, time

class Transfer:
    """
    Send/receive file from a remote UFTPD server.
    Authentication has to be done out-of-band: this class requires the
    remote UFTPD's host/port and the one-time password to open a session.
    Uses ftplib to open the session and interact with the UFTPD server
    """

    def __init__(self, host, port, password, LOG, username, rate_limit):
        self.host = host
        self.port= port
        self.password = password
        self.BUFFER_SIZE = 65536
        self.LOG = LOG
        self.username = username
        self.rate_limit = rate_limit
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
            target = self._get_write_socket(remote_file, offset, num_bytes).makefile("wb")
            if offset>0:
                f.seek(offset)
            total, duration = self._copy_data(f, target, num_bytes)
            self.log_usage(True, total, duration)
    
    def receive_file(self, remote_file, local_file, offset, num_bytes):
        """ gets a remote file and stores to the given local file"""
        with open(local_file, "wb") as f:
            source = self._get_read_socket(remote_file, offset, num_bytes).makefile("rb")
            if offset:
                f.seek(offset)
            total, duration = self._copy_data(source, f, num_bytes)
            self.log_usage(False, total, duration)
    
    def _copy_data(self, source, target, num_bytes):
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
            
def launch_transfer(remote_file_spec, local_file, mode, offset, number_of_bytes,
                    LOG, username,
                    rate_limit):
    """ helper method to launch a transfer """
    remote_file, server_spec, passwd = remote_file_spec
    host, port = server_spec.split(":")
    pid = os.fork()
    if pid:
        # parent
        return pid
    # child - launch transfer
    LOG.reinit("UFTPD-transfer")
    transfer = Transfer(host, int(port), passwd, LOG, username, rate_limit)
    transfer.connect()
    try:
        if number_of_bytes is None:
            number_of_bytes = -1
        if mode=="send":
            transfer.send_file(local_file, remote_file, offset, number_of_bytes)
        else:
            transfer.receive_file(remote_file, local_file, offset, number_of_bytes)
    except:
        LOG.log_exception()
    os._exit(0)

