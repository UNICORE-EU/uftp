import Connector
from UFTPD import MY_VERSION

_REQUEST_PASSWORD = "331 Please specify the password"
_SYSTEM_REPLY = "215 Unix Type: L8"
_PROTOCOL_ERROR = "500 Protocol error"

def login(cmd: str, connector: Connector):
    secret = None
    if "USER anonymous"==cmd:
        connector.write_message(_REQUEST_PASSWORD)
        pwd_line = connector.read_line()
        if pwd_line.startswith("USER ") or pwd_line.startswith("PASS "):
            password = pwd_line.split(" ", 1)[1]
            if password != "anonymous":
                secret = password
    if not secret:
        connector.write_message(_PROTOCOL_ERROR)
        connector.close()
    return secret

def establish_connection(connector: Connector, config: dict):
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
            return job_map.get(secret, None)
        elif chk.startswith("SYST"):
            cmds.remove("SYST")
            connector.write_message(_SYSTEM_REPLY)
        else:
            connector.write_message(_PROTOCOL_ERROR)
            return False

