""" 
  Utility commands (ls, mkdir, ...) and helpers
"""

from pyuftp.authenticate import authenticate

import pyuftp.base, pyuftp.uftp

import fnmatch, stat

class Ls(pyuftp.base.Base):
    
    def add_command_args(self):
        self.parser.prog = "pyuftp ls"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("remoteURL", help="Remote UFTP URL")

    def get_synopsis(self):
        return """List a remote directory"""

    def run(self):
        super().run()
        self.endpoint, base_dir, file_name = self.parse_url(self.args.remoteURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        not self.verbose or print(f"Authenticating at {self.endpoint}, base dir: '{base_dir}'")
        host, port, onetime_pwd = authenticate(self.endpoint, self.credential, base_dir)
        not self.verbose or print(f"Connecting to UFTPD {host}:{port}")
        uftp = pyuftp.uftp.UFTP()
        uftp.open_session(host, port, onetime_pwd)
        for entry in uftp.listdir(file_name):
            print(entry)


class Mkdir(pyuftp.base.Base):
    
    def add_command_args(self):
        self.parser.prog = "pyuftp mkdir"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("remoteURL", help="Remote UFTP URL")

    def get_synopsis(self):
        return """Create a remote directory"""

    def run(self):
        super().run()
        self.endpoint, base_dir, file_name = self.parse_url(self.args.remoteURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        not self.verbose or print(f"Authenticating at {self.endpoint}, base dir: '{base_dir}'")
        host, port, onetime_pwd = authenticate(self.endpoint, self.credential, base_dir)
        not self.verbose or print(f"Connecting to UFTPD {host}:{port}")
        uftp = pyuftp.uftp.UFTP()
        uftp.open_session(host, port, onetime_pwd)
        uftp.mkdir(file_name)


class Rm(pyuftp.base.Base):
    
    def add_command_args(self):
        self.parser.prog = "pyuftp rm"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("remoteURL", help="Remote UFTP URL")

    def get_synopsis(self):
        return """Remove a remote file/directory"""

    def run(self):
        super().run()
        self.endpoint, base_dir, file_name = self.parse_url(self.args.remoteURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        not self.verbose or print(f"Authenticating at {self.endpoint}, base dir: '{base_dir}'")
        host, port, onetime_pwd = authenticate(self.endpoint, self.credential, base_dir)
        not self.verbose or print(f"Connecting to UFTPD {host}:{port}")
        uftp = pyuftp.uftp.UFTP()
        uftp.open_session(host, port, onetime_pwd)
        st = uftp.stat(file_name)
        if st['st_mode']&stat.S_IFDIR:
            uftp.rmdir(file_name)
        else:
            uftp.rm(file_name)


class Checksum(pyuftp.base.Base):
    
    def add_command_args(self):
        self.parser.prog = "pyuftp checksum"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("remoteURL", help="Remote UFTP URL")
        self.parser.add_argument("-a", "--algorithm", help="hash algorithm (MD5, SHA-1, SHA-256, SHA-512")
    def get_synopsis(self):
        return """Remove a remote file/directory"""

    def run(self):
        super().run()
        self.endpoint, base_dir, file_name = self.parse_url(self.args.remoteURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        not self.verbose or print(f"Authenticating at {self.endpoint}, base dir: '{base_dir}'")
        host, port, onetime_pwd = authenticate(self.endpoint, self.credential, base_dir)
        not self.verbose or print(f"Connecting to UFTPD {host}:{port}")
        uftp = pyuftp.uftp.UFTP()
        uftp.open_session(host, port, onetime_pwd)
        st = uftp.stat(file_name)
        if st['st_mode']&stat.S_IFREG:
            _hash, _f = uftp.checksum(file_name, self.args.algorithm)
            print(_hash, _f)
        else:
            raise ValueError(f"Not a regular file: {file_name}")

class Find(pyuftp.base.Base):
    
    def add_command_args(self):
        self.parser.prog = "pyuftp find"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("remoteURL", help="Remote UFTP URL")

    def get_synopsis(self):
        return """List all files in a remote directory"""

    def run(self):
        super().run()
        self.endpoint, base_dir, file_name = self.parse_url(self.args.remoteURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        not self.verbose or print(f"Authenticating at {self.endpoint}, base dir: '{base_dir}'")
        host, port, onetime_pwd = authenticate(self.endpoint, self.credential, base_dir)
        not self.verbose or print(f"Connecting to UFTPD {host}:{port}")
        uftp = pyuftp.uftp.UFTP()
        uftp.open_session(host, port, onetime_pwd)
        for entry in crawl_remote(uftp, ".", file_name, all=True):
            print(entry)


def crawl_remote(uftp, base_dir, file_pattern = None, recurse=False, all=False):
    for x in uftp.listdir("."):
        if x.is_dir and all:
            uftp.cwd(x.path)
            for y in crawl_remote(uftp, base_dir+"/"+x.path, file_pattern, recurse, all):
                yield y
            uftp.cdup()
        else:
            if file_pattern and not fnmatch.fnmatch(x.path, file_pattern):
                continue
            yield base_dir+"/"+x.path


