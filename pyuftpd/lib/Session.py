import os.path
from sys import maxsize
from time import sleep, time

from Connector import Connector
from FileInfo import FileInfo
from Log import Logger
import Protocol, Server


class Session(object):
    """ UFTP session """
    
    ACTION_CONTINUE = 0
    ACTION_RETRIEVE = 1
    ACTION_STORE = 2
    ACTION_CLOSE_DATA = 7
    ACTION_END = 99

    MODE_NONE = 0
    MODE_INFO = 1
    MODE_READ = 2
    MODE_WRITE = 3
    MODE_FULL = 4
    
    def __init__(self, connector: Connector, job, LOG: Logger):
        self.job = job
        self.connector = connector
        self.LOG = LOG
        _dirname = os.path.dirname(job['file'])
        if _dirname=='':
            # no base dir set - default to HOME and
            # allow client to use absolute paths
            self.allow_absolute_paths = True
            _dirname = os.environ['HOME']
        else:
            # explicit base directory is set - make sure
            # session does not allow to "escape" it
            self.allow_absolute_paths = False
            if not os.path.isabs(_dirname):
                _dirname = os.environ['HOME']+"/"+_dirname
        if not os.path.isdir(_dirname):
            raise IOError("No such directory: %s" % _dirname)
        self.basedir = os.path.normpath(_dirname)
        self.current_dir = self.basedir
        os.chdir(self.basedir)
        self.access_level = self.MODE_FULL
        self.data = None
        self.portrange = job.get("_PORTRANGE", (0, -1, -1))
        self.reset_range()
        self.BUFFER_SIZE = 65536
        self.KEEP_ALIVE = False
        self.archive_mode = False
        self.excludes = []
        for f in job['UFTP_NOWRITE']:
            if len(f)>0:
                self.excludes.append(os.environ['HOME'] + "/" + f)
        for f in job.get("excludes", "").split(":"):
            if len(f)>0:
                self.excludes.append(f)
        self.includes= []
        for f in job.get("includes", "").split(":"):
            if len(f)>0:
                self.includes.append(f)
        self.key = job.get("key", None)
        if(self.key is not None):
            from base64 import b64decode
            self.key = b64decode(self.key)
            self.LOG.info("Data encryption enabled.")
        try:
            self.rate_limit = 0
            self.sleep_time = 0
            self.rate_limit = int(job.get("rateLimit", "0"))
            if self.rate_limit>0:
                self.LOG.info("Rate limit: %s MB/second." % int(self.rate_limit/(1024*1024)))
        except:
            pass

    def init_functions(self):
        self.functions = {
            "BYE": self.shutdown,
            "QUIT": self.shutdown,
            "SYST": self.syst,
            "FEAT": self.feat,
            "NOOP": self.noop,
            "PWD": self.pwd,
            "CWD": self.cwd,
            "CDUP": self.cdup,
            "MKD": self.mkdir,
            "DELE": self.rm,
            "RMD": self.rmdir,
            "PASV": self.pasv,
            "EPSV": self.epsv,
            "LIST": self.do_list,
            "STAT": self.stat,
            "MLST": self.mlst,
            "SIZE": self.size,
            "RANG": self.rang,
            "REST": self.rest,
            "RETR": self.retr,
            "ALLO": self.allo,
            "STOR": self.stor,
            "TYPE": self.switch_type,
            "KEEP-ALIVE": self.set_keep_alive,
        }

    def assert_permission(self, requested):
        if self.access_level < requested:
            raise Exception("Access denied")

    def assert_access(self, path):
        for excl in self.excludes:
            if excl==path:
                raise Exception("Forbidden")

    def makeabs(self, path):
        if self.allow_absolute_paths and os.path.isabs(path):
            return os.path.normpath(path)
        else:
            return os.path.normpath(self.current_dir+"/"+path)

    def shutdown(self, params):
        if self.data:
            self.data.close()
        return Session.ACTION_END

    def syst(self, params):
        self.connector.write_message(Protocol._SYSTEM_REPLY)
        return Session.ACTION_CONTINUE

    def feat(self, params):
        Protocol.send_features(self.connector)
        return Session.ACTION_CONTINUE

    def noop(self, params):
        try:
            # be compatible with the Java client that may want
            # multiple parallel TCP streams which we don't support
            num_streams = int(params)
            if num_streams>1:
                self.connector.write_message("223 Opening 1 data connections")
            else:
                self.connector.write_message("222 Opening 1 data connections")
        except:
            self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE

    def cwd(self, params):
        self.assert_permission(Session.MODE_INFO)
        path = self.makeabs(params.strip())
        if len(path)<len(self.current_dir) and not self.allow_absolute_paths:
            self.connector.write_message("500 Not allowed")
        else:
            os.chdir(path)
            self.current_dir = path
            self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE

    def cdup(self, params):
        if self.current_dir==self.basedir:
            self.connector.write_message("500 Can't cd up, already at base directory")
        else:
            os.chdir("..")
            self.current_dir = os.getcwd()
            self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE

    def pwd(self, params):
        self.assert_permission(Session.MODE_INFO)
        self.connector.write_message("257 \""+os.getcwd()+"\"")
        return Session.ACTION_CONTINUE

    def mkdir(self, params):
        self.assert_permission(Session.MODE_FULL)
        path = self.makeabs(params.strip())
        try:
            os.mkdir(path)
            self.connector.write_message("257 \"%s\" directory created" % path)
        except Exception as e:
            self.connector.write_message("500 Can't create directory: %s" % str(e))
        return Session.ACTION_CONTINUE

    def rm(self, params):
        self.assert_permission(Session.MODE_FULL)
        _path = self.makeabs(params.strip())
        try:
            self.assert_access(_path)
            os.unlink(_path)
            self.connector.write_message("200 OK")
        except Exception as e:
            self.connector.write_message("500 Can't remove path: %s" % str(e))
        return Session.ACTION_CONTINUE

    def rmdir(self, params):
        self.assert_permission(Session.MODE_FULL)
        _path = self.makeabs(params.strip())
        try:
            self.assert_access(_path)
            os.rmdir(_path)
            self.connector.write_message("200 OK")
        except Exception as e:
            self.connector.write_message("500 Can't remove path: %s" % str(e))
        return Session.ACTION_CONTINUE

    def pasv(self, params):
        my_host = self.connector.my_ip()
        server_socket = Server.setup_data_server_socket(my_host, self.portrange)
        my_port = server_socket.getsockname()[1]
        msg = "227 Entering Passive Mode (%s,%d,%d)" % ( my_host.replace(".",","), (my_port / 256), (my_port % 256))
        self.connector.write_message(msg)
        self.data = Server.accept_data(server_socket, self.LOG)
        self.LOG.info("Accepted data connection from %s" % self.data.client_ip())
        return Session.ACTION_CONTINUE

    def epsv(self, params):
        my_host = self.connector.my_ip()
        server_socket = Server.setup_data_server_socket(my_host, self.portrange)
        my_port = server_socket.getsockname()[1]
        msg = "229 Entering Extended Passive Mode (|||%s|)" % my_port
        self.connector.write_message(msg)
        self.data = Server.accept_data(server_socket, self.LOG)
        self.LOG.info("Accepted data connection from %s" % self.data.client_ip())
        return Session.ACTION_CONTINUE

    def reset(self):
        self.reset_range()        
        self.connector.write_message("226 File transfer successful")

    def do_list(self, params):
        self.assert_permission(Session.MODE_INFO)
        path = "."
        if params:
            path = params
        linux_mode = False
        if path=="-a":
            path = "."
            linux_mode = True
        self.LOG.debug("Listing %s" % path)
        path = self.makeabs(path)
        self.connector.write_message("150 OK")
        for f in os.listdir(path):
            try:
                fi = FileInfo(os.path.join(path,f))
                if linux_mode:
                    self.data.write_message(fi.list())
                else:
                    self.data.write_message(fi.simple_list())
            except Exception as e:
                self.LOG.debug("Error listing %s : %s" % (f, str(e)) )
        self.reset()
        return Session.ACTION_CLOSE_DATA

    def stat(self, params):
        self.assert_permission(Session.MODE_INFO)
        tokens = params.split(" ", 1)
        asFile = tokens[0]!="N"
        if len(tokens)>1:
            path = tokens[1]
        else:
            path = "."
        path = self.makeabs(path)
        fi = FileInfo(path)
        if not fi.exists():
            self.connector.write_message("500 Directory/file does not exist or cannot be accessed!")
            return Session.ACTION_CONTINUE
        if asFile or not fi.is_dir():
            file_list = [ fi ]
        else:
            file_list = [ FileInfo(os.path.normpath(os.path.join(path,p))) for p in os.listdir(path)]
        self.connector.write_message("211- Sending file list")
        for f in file_list:
            try:
                self.connector.write_message(" %s" % f.simple_list())
            except:
                # don't want to fail here
                pass
        self.connector.write_message("211 End of file list")
        return Session.ACTION_CONTINUE

    def mlst(self,params):
        self.assert_permission(Session.MODE_INFO)
        path = self.makeabs(params)
        fi = FileInfo(path)
        if not fi.exists():
            self.connector.write_message("500 Directory/file does not exist or cannot be accessed!")
            return Session.ACTION_CONTINUE
        self.connector.write_message("250- Listing %s" % path)
        self.connector.write_message(" %s" % fi.as_mlist())
        self.connector.write_message("250 End")
        return Session.ACTION_CONTINUE

    def size(self, params):
        self.assert_permission(Session.MODE_INFO)
        path = self.makeabs(params)
        fi = FileInfo(path)
        if not fi.exists():
            msg = "500 Directory/file does not exist or cannot be accessed!"
        else:
            msg = "213 %s" % fi.size()
        self.connector.write_message(msg)
        return Session.ACTION_CONTINUE
        
    def set_range(self, offset, number_of_bytes):
        self.offset = offset
        self.number_of_bytes = number_of_bytes
        self.have_range = True

    def reset_range(self):
        self.set_range(0,0)
        self.have_range = False
        self.number_of_bytes = None

    def rang(self, params):
        tokens = params.split(" ")
        try:
            local_offset = int(tokens[0])
            last_byte = int(tokens[1])
        except:
            self.connector.write_message("500 RANG argument syntax error")
            return Session.ACTION_CONTINUE
        if local_offset==1 and last_byte==0:
            response = "350 Resetting range"
            self.reset_range()
        else:
            response = "350 Restarting at %s. End byte range at %s" % (local_offset, last_byte)
            self.set_range(local_offset, last_byte - local_offset)
        self.connector.write_message(response)
        return Session.ACTION_CONTINUE

    def rest(self, params):
        try:
            local_offset = int(params)
        except:
            self.connector.write_message("500 REST argument syntax error")
            return Session.ACTION_CONTINUE
        self.have_range = False
        self.offset = local_offset
        self.connector.write_message("350 Restarting at %s." % local_offset)
        return Session.ACTION_CONTINUE

    def retr(self, params):
        self.assert_permission(Session.MODE_READ)
        path = self.makeabs(params)
        fi = FileInfo(path)
        if not fi.exists():
            self.connector.write_message("500 Directory/file does not exist or cannot be accessed!")
            return Session.ACTION_CONTINUE
        if self.have_range:
            size = self.number_of_bytes
        else:
            size = fi.size()
            self.number_of_bytes = size - self.offset
        self.file_path = path
        self.connector.write_message("150 OK %s bytes available for reading." % size)
        return Session.ACTION_RETRIEVE

    def allo(self, params):
        self.assert_permission(Session.MODE_WRITE)
        self.number_of_bytes = int(params)
        self.connector.write_message("200 OK Will read up to %s bytes from data connection." % self.number_of_bytes)
        return Session.ACTION_CONTINUE

    def stor(self, params):
        self.assert_permission(Session.MODE_WRITE)
        path = self.makeabs(params)
        self.assert_access(path)
        
        if self.number_of_bytes is None:
            self.number_of_bytes = maxsize
        self.file_path = path
        self.connector.write_message("150 OK")
        return Session.ACTION_STORE
    
    def switch_type(self, params):
        if "ARCHIVE"==params.strip():
            self.archive_mode = True
        elif "NORMAL"==params.strip():
            self.archive_mode = False
        self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE        

    def set_keep_alive(self, params):
        self.KEEP_ALIVE = params.lower() in [ "true", "yes", "1" ]
        return Session.ACTION_CONTINUE

    def send_data(self):
        with open(self.file_path, "rb") as f:
            limit_rate = self.rate_limit > 0
            f.seek(self.offset)
            to_send = self.number_of_bytes
            total = 0
            start_time = int(time())
            encrypt = self.key is not None
            if encrypt:
                import CryptUtil
                writer = CryptUtil.CryptedWriter(self.data, self.key)
            else:
                writer = self.data
            while total<to_send:
                length = min(self.BUFFER_SIZE, to_send-total)
                data = f.read(length)
                if len(data)==0:
                    break
                total = total + len(data)
                writer.write(data)
                if limit_rate:
                    self.control_rate(total, start_time*1000)
            if encrypt:
                writer.close()
            # post send
            self.reset()
            if not self.KEEP_ALIVE:
                self.data.close()
            duration = int(time()) - start_time
            self.log_usage(True, total, duration)

    def recv_data(self):
        if self.archive_mode:
            self.recv_archive_data()
        else:
            self.recv_normal_data()

    def recv_normal_data(self):
        with open(self.file_path, "wb") as f:
            f.seek(self.offset)
            reader = self.get_reader()
            start_time = int(time())
            total = self.copy_data(reader, f, self.number_of_bytes)
            # post send
            self.reset()
            if not self.KEEP_ALIVE:
                self.data.close()
            duration = int(time()) - start_time
            self.log_usage(False, total, duration)


    def recv_archive_data(self):
        import tarfile
        reader = self.get_reader()
        start_time = int(time())
        tar = tarfile.TarFile.open(mode="r|", fileobj=reader)
        counter = 0
        total = 0
        while True:
            entry = tar.next()
            if entry is None:
                break
            self.LOG.debug("Processing tar entry: %s length=%s" % (entry.name, entry.size))
            pathname = self.makeabs(os.path.join(self.file_path , entry.name))
            _d = os.path.dirname(pathname)
            try:
                if not os.path.exists(_d):
                    os.mkdir(_d)
            except Exception as e:
                self.LOG.debug("Error creating directory %s: %s"%(_d, str(e)))
            with open(pathname, "wb") as f:
                entry_reader = tar.extractfile(entry)
                if entry_reader is None:
                    # TBD handle links and such?
                    self.LOG.debug("No file returned for %s" % entry.name)
                else:
                    total += self.copy_data(entry_reader, f, maxsize)
            counter+=1
        # post send
        self.reset()
        if not self.KEEP_ALIVE:
            self.data.close()
        duration = int(time()) - start_time
        self.log_usage(False, total, duration, num_files=counter)

    def get_reader(self):
        crypted = self.key is not None
        if crypted:
            import CryptUtil
            reader = CryptUtil.Decrypt(self.data, self.key)
            # crypted data is longer that net data size
            self.number_of_bytes = maxsize
        else:
            reader = self.data
        return reader

    def copy_data(self, reader, target, num_bytes):
        total = 0
        limit_rate = self.rate_limit > 0
        start_time = int(time()*1000)
        
        while total<num_bytes:
            length = min(self.BUFFER_SIZE, num_bytes-total)
            data = reader.read(length)
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

    def control_rate(self, total, start_time):
        interval = int(time()*1000 - start_time) + 1
        current_rate = 1000 * total / interval
        if current_rate < self.rate_limit:
            self.sleep_time = int(0.5 * self.sleep_time)
        else:
            self.sleep_time = self.sleep_time + 5
            sleep(0.001*self.sleep_time)

    def log_usage(self, send, size, duration, num_files = 1):
        if send:
            what = "Sent %d file(s)" % num_files
        else:
            what = "Received %d file(s)" % num_files
        rate = 0.001*float(size)/(float(duration)+1)
        if rate<1000:
            unit = "kB/sec"
            rate = int(rate)
        else:
            unit = "MB/sec"
            rate = int(rate / 1000)

        msg = "USAGE [%s] [%s bytes] [%s %s] [%s]" % (what, size, rate, unit, self.job['user'])
        self.LOG.info(msg)

    def run(self):
        self.init_functions()
        self.LOG.info("Processing UFTP session for <%s : %s : %s>, basedir=%s, allow_absolute_paths=%s" % (
            self.job['user'], self.job['group'],
            self.connector.client_ip(),
            self.basedir, self.allow_absolute_paths
        ))
        while True:
            msg = self.connector.read_line()
            if msg.startswith("BYE") or msg.startswith("QUIT"):
                break
            params = None
            tokens = msg.split(" ", 1)
            cmd = tokens[0]
            if len(tokens)>1:
                params = tokens[1]
            func = self.functions.get(cmd, None)
            if func:
                try:
                    mode = func(params)
                    if mode==Session.ACTION_RETRIEVE:
                        self.send_data()
                    elif mode==Session.ACTION_STORE:
                        self.recv_data()
                    elif mode==Session.ACTION_CLOSE_DATA:
                        self.data.close()
                        self.data = None
                    elif mode==Session.ACTION_END:
                        break
                except Exception as e:
                    self.connector.write_message("500 Error processing command: %s" % str(e))
            else:
                self.connector.write_message("500 Command not implemented.")
