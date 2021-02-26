import os.path
from time import time
from sys import maxsize
from FileInfo import FileInfo
import Protocol, Server

class Session(object):
    """ FTP session """
    
    ACTION_CONTINUE = 0
    ACTION_RETRIEVE = 1
    ACTION_STORE = 2
    ACTION_SEND_STREAM_DATA = 6
    ACTION_CLOSE_DATA = 7
    ACTION_END = 99

    MODE_NONE = 0
    MODE_INFO = 1
    MODE_READ = 2
    MODE_WRITE = 3
    MODE_FULL = 4
    
    def __init__(self, connector, job, LOG):
        self.job = job
        self.connector = connector
        self.LOG = LOG
        _dirname = os.path.dirname(job['file'])
        if not os.path.isabs(_dirname):
            _dirname = os.environ['HOME']+"/"+_dirname
        if not os.path.isdir(_dirname):
            raise IOError("No such directory: %s" % _dirname)
        self.basedir = _dirname
        self.access_level = self.MODE_FULL
        self.data = None
        self.portrange = job.get("_PORTRANGE", (0, -1, -1))
        os.chdir(_dirname)
        self.reset_range()
        self.BUFFER_SIZE = 65536
        self.KEEP_ALIVE = False

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
        }

    def assertPermission(self, requested):
        if self.access_level < requested:
            raise Exception("Access denied")

    def assertAccess(self, path):
        pass

    def makeabs(self, path):
        if os.path.isabs(path):
            return path
        else:
            return self.basedir+"/"+path

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
        self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE

    def cwd(self, params):
        _dir = self.makeabs(params.strip())
        os.chdir(_dir)
        self.basedir = _dir
        self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE

    def cdup(self, params):
        os.chdir("..")
        self.basedir = os.getcwd()
        self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE

    def pwd(self, params):
        self.connector.write_message("257 \""+os.getcwd()+"\"")
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
        self.number_of_bytes = int(params)
        self.connector.write_message("200 OK Will read up to %s bytes from data connection." % self.number_of_bytes)
        return Session.ACTION_CONTINUE

    def stor(self, params):
        path = self.makeabs(params)
        if self.number_of_bytes is None:
            self.number_of_bytes = maxsize
        self.file_path = path
        self.connector.write_message("150 OK")
        return Session.ACTION_STORE
    
    # TODO: archive mode
    def switch_type(self, params):
        self.connector.write_message("200 OK")
        return Session.ACTION_CONTINUE        
                    
    def send_data(self):
        with open(self.file_path, "rb") as f:
            f.seek(self.offset)
            to_send = self.number_of_bytes
            total = 0
            start_time = int(time())
            while total<to_send:
                length = min(self.BUFFER_SIZE, to_send-total)
                data = f.read(length)
                if len(data)==0:
                    break
                self.data.write_data(data)
                total = total + len(data)
                # tbd control rate
            # post send
            self.reset()
            if not self.KEEP_ALIVE:
                self.data.close()
            duration = int(time()) - start_time
            self.log_usage(True, total, duration)

    def recv_data(self):
        with open(self.file_path, "wb") as f:
            f.seek(self.offset)
            to_recv = self.number_of_bytes
            total = 0
            start_time = int(time())
            while total<to_recv:
                length = min(self.BUFFER_SIZE, to_recv-total)
                data = self.data.read_data(length)
                if len(data)==0:
                    break
                to_write = len(data)
                write_offset = 0
                while(to_write>0):
                    written = f.write(data[write_offset:])
                    if written is None:
                        written = 0
                    write_offset += written
                    to_write -= written                
                total = total + len(data)
                # tbd control rate
            # post send
            self.reset()
            if not self.KEEP_ALIVE:
                self.data.close()
            duration = int(time()) - start_time
            self.log_usage(True, total, duration)

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
        self.LOG.info("Proccessing UFTP session for <%s : %s : %s>, basedir=%s" % (
            self.job['user'], self.job['group'],
            self.connector.client_ip(),
            self.basedir
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
