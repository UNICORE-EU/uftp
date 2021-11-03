import Connector
from UFTPD import MY_VERSION

_REQUEST_PASSWORD = "331 Please specify the password"
_FILE_ACTION_OK = "350 File action OK"
_LOGIN_SUCCESS = "230 Login successful"
_SYSTEM_REPLY = "215 Unix Type: L8"

def login(cmd: str, connector: Connector):
    secret = None
    if "USER anonymous"==cmd:
        connector.write_message(_REQUEST_PASSWORD)
        pwd_line = connector.read_line()
        if pwd_line.startswith("USER ") or pwd_line.startswith("PASS "):
            password = pwd_line.split(" ", 1)[1]
            if password is not "anonymous":
                secret = password
    if not secret:
        connector.write_message("500 Client login does not comply with protocol.")
        connector.close()
    return secret

def establish_connection(connector: Connector, config):
    cmds = ["USER","SYST"]
    connector.write_message("220 UFTPD %s, https://www.unicore.eu" % MY_VERSION)
    while len(cmds)>0:
        try:
            cmd = connector.read_line()
        except:
            return False
        chk = cmd.upper()
        if chk.startswith("USER"):
            cmds.remove("USER")
            secret = login(cmd, connector)
            if not secret:
                return None
            job_map = config['job_map']
            job = job_map.get(secret, None)
            if not job:
                connector.write_message("530 Not logged in")
                connector.close()
            return job
        elif chk.startswith("SYST"):
            cmds.remove("SYST")
            connector.write_message("215 Unix Type: L8")
        else:
            connector.write_message("500 Protocol error")
            return False

