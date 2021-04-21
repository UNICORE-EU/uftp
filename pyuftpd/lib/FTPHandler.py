import os
import threading

import BecomeUser, Connector, Log, Protocol, Server, Session

def create_session(connector: Connector, config, LOG: Log, ftp_server, cmd_server):
    try:
        LOG.debug("Processing %s" % connector.info())
        job = Protocol.establish_connection(connector, config)
        if job is None:
            connector.write_message("530 Not logged in: no matching transfer request found")
            connector.close()
            return
        LOG.info("Established %s for '%s'" % (connector.info(), job['user']))
    except Exception as e:
        LOG.error(e)
        connector.close()
        return
    limit = config['MAX_CONNECTIONS']
    if len(job['_PIDS'])==limit:
        connector.write_message("500 Too many open UFTP sessions (limit: %s)!" % limit)
        connector.close()
        LOG.debug("Rejected: too many open sessions for this transfer")
        return
    
    pid = os.fork()
    if pid:
        # parent
        connector.cleanup()
        LOG.debug("Created new UFTP session, child process <%s>" % pid)
        with job['_LOCK']:
            job['_PIDS'].append(pid)
        return

    #
    # child - cleanup, drop privileges and launch session processing
    #
    try:
        LOG.reinit()
        ftp_server.close()
        cmd_server.close()
        user = job['user']
        groups = job.get('group')
        user_switch_status = BecomeUser.become_user(user, groups, config, LOG)
        if user_switch_status is not True:
            connector.write_message("530 Not logged in: %s" % user_switch_status)
            raise Exception("Cannot switch UID/GID: %s" % user_switch_status)
        connector.write_message("230 Login successful")
        job['UFTP_NOWRITE'] = config["UFTP_NOWRITE"]
        job['MAX_STREAMS'] = config['MAX_STREAMS']
        job['compress'] = job.get("compress", "false").lower()=="true"
        session = Session.Session(connector, job, LOG)
        session.run()
        connector.close()
    except Exception as e:
        LOG.error(e)
    os._exit(0)

def ftp_listener(ftp_server, config, LOG: Log, cmd_server):
    LOG.info("Started FTP listener thread.")
    while True:
        try:
            connector = Server.accept_ftp(ftp_server, LOG)
            worker_thread = threading.Thread(target=create_session,
                                  args=(connector, config, LOG, ftp_server, cmd_server))
            worker_thread.start()
        except Exception as e:
            LOG.error(e)
            try:
                connector.close()
            except:
                pass
