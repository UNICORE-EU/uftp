"""
Main UFTPD module

Starts the FTP listener thread, and containing the main loop that listens to
the control socket, and processes control requests
"""

import os
import sys
import threading
import time
import BecomeUser, FTPHandler, Log, Server, UserInfoHandler, Utils

#
# the UFTPD version
#
MY_VERSION = "DEV"

# supported Python version
REQUIRED_VERSION = (3, 4, 0)

# how long to wait for transfer client to connect
_REQUEST_LIFETIME = 300

def assert_version():
    """
    Checks that the Python version is correct
    """
    return sys.version_info >= REQUIRED_VERSION

def setup_config(config):
    config['CMD_HOST'] = os.getenv("CMD_HOST", "localhost")
    config['CMD_PORT'] = int(os.getenv("CMD_PORT", "64435"))
    config['SERVER_HOST'] = os.getenv("SERVER_HOST", "localhost")
    config['SERVER_PORT'] = int(os.getenv("SERVER_PORT", "64434"))
    config['ADVERTISE_HOST'] = os.getenv("ADVERTISE_HOST", None)
    config['SSL_CONF'] = os.getenv("SSL_CONF", None)
    config['ACL'] = os.getenv("ACL", "conf/uftpd.acl")
    config['uftpd.acl'] = []
    config['MAX_STREAMS'] = int(os.getenv("MAX_STREAMS", "4"))
    config['MAX_CONNECTIONS'] = int(os.getenv("MAX_CONNECTIONS", "16"))
    config['UFTP_KEYFILES'] = os.getenv("UFTP_KEYFILES", ".ssh/authorized_keys:.uftp/authorized_keys").split(":")
    config['UFTP_NOWRITE'] = os.getenv("UFTP_NOWRITE", ".ssh/authorized_keys").split(":")
    config['uftpd.enforce_os_gids'] =  os.getenv("UFTP_ENFORCE_OS_GIDS", "true").lower() in [ "true", "yes", "1" ]
    config['uftpd.use_id_to_resolve_gids'] = True
    config['LOG_VERBOSE'] = os.getenv("LOG_VERBOSE", "false").lower() in [ "true", "yes", "1" ]
    config['LOG_SYSLOG'] = os.getenv("LOG_SYSLOG", "true").lower() in [ "true", "yes", "1" ]
    config['DISABLE_IP_CHECK'] = os.getenv("DISABLE_IP_CHECK", "false").lower() in [ "true", "yes", "1" ]
    config['PORTRANGE'] = configure_portrange()

def configure_portrange():
    rangespec = os.getenv("PORT_RANGE", None)
    first = 0
    lower = -1
    upper = -1
    if rangespec is not None:
        try:
            lower,upper = rangespec.strip().split(":")
            lower = int(lower)
            upper = int(upper)
            if upper<=lower:
                raise Exception()
            first = lower
        except:
            raise Exception("Invalid PORT_RANGE specified, must be 'lower:upper'")
    return (first, lower, upper)

def parse_request(message):
    request = {}
    for line in message:
        if line.startswith("END"):
            break
        try:
            k, v = line.strip().split("=",1)
            request[k]=v
        except:
            pass
    return request

def init_functions():
    """
    Creates the function lookup table used to map request types
    to the appropriate function
    """
    return {
        "uftp-ping-request": ping,
        "uftp-get-user-info-request": get_user_info, 
        "uftp-transfer-request": add_job,
    }

def ping(request, connector, config, LOG):
    response = """Version: %s
ListenPort: %s
ListenAddress: %s
""" % (
        MY_VERSION,
        config['SERVER_PORT'],
        config['SERVER_HOST']
    )
    if config['ADVERTISE_HOST'] is not None:
        response += "AdvertiseAddress: %s" % config['ADVERTISE_HOST']
    connector.write_message(response)
    connector.close()

def get_user_info(request, connector, config, LOG):
    user = request['user'].strip()
    if user=="root":
        connector.write_message("500 Not allowed")
        connector.close()
        return
    user_cache = config['uftpd.user_cache']
    home = user_cache.get_home_4user(user)
    if home:
        UserInfoHandler.get_user_info(user, home, connector, config, LOG)
        return
    else:
        connector.write_message("500 No such user or no home directory found")
        connector.close()
   
def add_job(request, connector, config, LOG):
    user = request['user']
    limit = config['MAX_CONNECTIONS']
    
    user_session_counts = config['_JOB_COUNTER']
    counter = user_session_counts.get(user, Utils.Counter())
    user_session_counts[user]=counter
    if counter.increment()==limit:
        counter.decrement()
        raise Exception("Too many active sessions for '%s' - server limit is %s" % (user, limit))
    secret = request['secret']
    job_map = config['job_map']
    if job_map.get(secret, None) is not None:
        raise Exception("Duplicate secret - this is not allowed.")
    group = request.get('group', "NONE")
    groups = group.split(":")
    if len(groups)==1:
        # only primary  group requested - make sure
        # we add all the user's supplementary groups
        groups.append("DEFAULT_GID")
    request['group'] = groups
    request['_LOCK'] = threading.Lock()
    request['_EXPIRES'] = int(time.time())+_REQUEST_LIFETIME
    request['_PIDS'] = []
    job_map[secret] = request
    LOG.info("New transfer request for '%s' groups: %s" % (user, str(group)))
    connector.write_message("OK::%s" % config['SERVER_PORT'])
    connector.close()

def cleanup(config, LOG):
    LOG.debug("Request cleanup thread started.")
    job_map = config['job_map']
    while True:
        for key in set(job_map.keys()):
            try:
                job = job_map[key]
                lock = job['_LOCK']
                user = job['user']
                with lock:
                    pids = job['_PIDS']
                    if len(pids)==0:
                        expires = job['_EXPIRES']
                        if time.time() > expires:
                            LOG.info("Removing expired job from '%s'" % user)
                            del job_map[key]
                            config['_JOB_COUNTER'][user].decrement()
                    else:
                        for pid in pids[:]:
                            (_pid, _status) = os.waitpid(pid, os.WNOHANG)
                            if _pid!=0:
                                pids.remove(pid)
                                config['_JOB_COUNTER'][user].decrement()
                        if len(pids)==0:
                            LOG.info("Processing request for '%s' finished." % user)
                            del job_map[key]
            except Exception as e:
                LOG.error(e)
        try:
            children = config.get('user_info_process_pids')
            for pid in children:
                (_pid, _status) = os.waitpid(pid, os.WNOHANG)
                if _pid!=0:
                    children.remove(pid)
        except Exception as e:
            LOG.error(e)
        time.sleep(5)
                             
def process(cmd_server, config, LOG):
    """
    Command processing loop. Reads commands from cmd socket and invokes the
    appropriate command.
    """
    functions = init_functions()

    while True:
        try:
            connector = Server.accept_command(cmd_server, config, LOG)
            msg = connector.read_request()
            request = parse_request(msg)
            request_type = request.get('request-type', "n/a")
            func = functions.get(request_type, None)
            if not func:
                raise Exception("Unsupported request type '%s'" % request_type)
            func(request, connector, config, LOG)
        except Exception as e:
            LOG.error("Error in command loop: %s" % e)
            connector.close()

def main():
    """
    Start UFTPD
    """
    if not assert_version():
        raise RuntimeError("Unsupported version of Python! "
                           "Must be %s or later." % str(REQUIRED_VERSION))

    config = {}
    setup_config(config)
    verbose = config["LOG_VERBOSE"]
    use_syslog = config['LOG_SYSLOG']
    LOG = Log.Logger(verbose=verbose, use_syslog=use_syslog)

    LOG.info("**** UFTPD Version %s starting" % MY_VERSION)

    BecomeUser.initialize(config, LOG)

    config['job_map'] = {}

    config['_JOB_COUNTER'] = {}
    config['_JOB_COUNTER_LOCK'] = threading.Lock()

    config['user_info_process_pids'] = []

    cmd_server = Server.setup_cmd_server_socket(config, LOG)

    housekeeping_thread = threading.Thread(target=cleanup,
                                  name="Cleanup",
                                  args=(config, LOG))
    housekeeping_thread.start()

    ftp_server = Server.setup_ftp_server_socket(config, LOG)
    ftp_thread = threading.Thread(target=FTPHandler.ftp_listener,
                                  name="FTPListener",
                                  args=(ftp_server, config, LOG, cmd_server))
    ftp_thread.start()

    LOG.debug("Reading user keys from     : %s" % config['UFTP_KEYFILES'])
    LOG.debug("Write protected files      : %s" % config['UFTP_NOWRITE'])
    LOG.debug("Max. FTP sessions per user : %s" % config['MAX_CONNECTIONS'])
    LOG.debug("Max. parallel streams      : %s" % config['MAX_STREAMS'])
    pr = config['PORTRANGE']
    if pr[0]>0:
        LOG.debug("Data port range            : %s:%s" % (pr[1], pr[2]))
    LOG.debug("Validating client IPs      : %s" % str(not config['DISABLE_IP_CHECK']))
    process(cmd_server, config, LOG)
    return 0

# application entry point
if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)
