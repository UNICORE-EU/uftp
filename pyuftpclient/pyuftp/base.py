""" Base command class and a few general commands """

from pyuftp.authenticate import create_credential
from pyuftp.authenticate import get_json, authenticate
from urllib.parse import urlparse


import argparse, json, os.path, sys

class Base:
    """ Base command class with support for common commandline args """

    def __init__(self):
        self.parser = argparse.ArgumentParser(prog="pyuftp",
                                              description="A commandline client for UFTP (UNICORE FTP)")
        self.args = None
        self.verbose = False
        self.endpoint = None
        self.credential = None
        self.add_base_args()
        self.add_command_args()#

    def add_base_args(self):
        self.parser.add_argument("-v", "--verbose",
                            required=False,
                            action="store_true",
                            help="be verbose")
        self.parser.add_argument("-t", "--token", help="authentication: token")
        self.parser.add_argument("-u", "--user", help="authentication: username[:password]")
        self.parser.add_argument("-P", "--password", action="store_true",
                            help="interactively query for password")
        self.parser.add_argument("-i", "--identity", help="authentication: private key file")

    def add_command_args(self):
        pass

    def run(self):
        self.args = self.parser.parse_args(sys.argv[2:])
        self.verbose = self.args.verbose
        
    def get_synopsis(self):
        return "N/A"

    def create_credential(self):
        username = None
        password = None
        identity = self.args.identity
        token  = self.args.token
        if self.args.user:
            if ":" in self.args.user:
                username, password = self.args.user.split(":",1)
            else:
                username = self.args.user
        if self.args.password and password is None:
            import getpass
            if self.args.identity is not None:
                prompt = "Enter passphrase for key: "
            else:
                prompt = "Enter password: "
            password = getpass.getpass(prompt)
        self.credential = create_credential(username, password, token, identity)
        
    def parse_url(self, url):
        """ 
        parses the given URL and returns a tuple consisting of
         - auth endpoint URL (or None if URL is not a http(s) URL)
         - base directory
         - file name
        as appropriate
        """
        parsed = urlparse(url)
        service_path = parsed.path
        endpoint = None
        basedir = ""
        filename = "."
        if ":" in service_path:
            service_path, file_path = service_path.split(":",1)
            if len(file_path)>0:
                basedir = os.path.dirname(file_path)
                filename = os.path.basename(file_path)
        if service_path.endswith("/"):
                service_path = service_path[:-1]
        if parsed.scheme.lower().startswith("http"):
            endpoint = f"{parsed.scheme}://{parsed.netloc}{service_path}"
        return endpoint, basedir, filename

class Info(Base):

    def add_command_args(self):
        self.parser.prog = "pyuftp info"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("authURL", help="Auth server URL")
        self.parser.add_argument("-R", "--raw", help="print the JSON response from the server")

    def get_synopsis(self):
        return """Gets info about the remote server"""

    def run(self):
        super().run()
        self.endpoint, _, _ = self.parse_url(self.args.authURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        auth_url = self.endpoint.split("/rest/auth")[0]+"/rest/auth"
        not self.verbose or print(f"Connecting to {auth_url}")
        reply = get_json(auth_url, self.credential)
        if self.args.raw:
            print(json.dumps(reply, indent=2))
        else:
            self.show_info(reply, auth_url)
    
    def show_info(self, reply, auth_url):
        print(f"Client identity:    {reply['client']['dn']}")
        print(f"Client auth method: {self.credential}")
        print(f"Auth server type:   AuthServer v{reply['server'].get('version', 'n/a')}")
        for key in reply:
            if key in ['client', 'server']:
                continue
            server = reply[key]
            print(f"Server: {key}")
            print(f"  URL base:         {auth_url}/{key}")
            print(f"  Description:      {server.get('description', 'N/A')}")
            print(f"  Remote user info: uid={server.get('uid', 'N/A')};gid={server.get('gid', 'N/A')}")
            if str(server["dataSharing"]["enabled"]).lower() == 'true':
                sharing = "enabled"
            else:
                sharing = "disabled"
            print(f"  Sharing support:  {sharing}")
            print(f"  Server status:    {server.get('status', 'N/A')}")

class Auth(Base):

    def add_command_args(self):
        self.parser.prog = "pyuftp auth"
        self.parser.description = self.get_synopsis()
        self.parser.add_argument("authURL", help="Auth URL")

    def get_synopsis(self):
        return """Authenticate only, returning UFTP address and one-time password"""

    def run(self):
        super().run()
        self.endpoint, base_dir, _ = self.parse_url(self.args.authURL)
        if self.endpoint is None:
            raise ValueError(f"Does not seem to be a valid URL: {self.args.authURL}")
        self.create_credential()
        not self.verbose or print(f"Authenticating at {self.endpoint}, base dir: '{base_dir}'")
        host, port, onetime_pwd = authenticate(self.endpoint, self.credential, base_dir)
        print(f"Connect to {host}:{port} password: {onetime_pwd}")
