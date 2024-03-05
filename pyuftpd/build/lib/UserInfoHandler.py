import os

import BecomeUser, Connector, Log, UFTPD

def get_user_info(user, home, connector: Connector, config, LOG: Log):
    pid = os.fork()
    if pid:
        # parent
        config['user_info_process_pids'].append(pid)
        connector.cleanup()
        return
    #
    # child - drop privileges and read key file(s)
    #
    user_switch_status = BecomeUser.become_user(user, ["NONE"], config, LOG)
    if user_switch_status is not True:
        connector.write_message("530 Not logged in: %s" % user_switch_status)
        connector.close()
        os._exit(1)
        
    response = """Version: %s
User: %s
""" % (
        UFTPD.MY_VERSION,
        user
    )
    status = ""
    response += "Home: %s\n" % home
    i = 0   
    for keyfile in config['UFTP_KEYFILES']:
        _file = os.path.join(home, keyfile)
        try:
            with open(_file, "r") as f:
                status += " keyfile %s : OK" % _file
                for line in f.readlines():
                    if line.startswith("#"):
                        continue
                    response+="Accepted key %d: %s\n" % (i, line.strip())
                    i+=1
        except Exception as e:
            status += " keyfile %s : %s" % (_file, str(e))
    response += "Status:%s\n" % status
    connector.write_message(response)
    connector.close()
    os._exit(0)
