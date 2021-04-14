#
# simplistic logger writing to syslog (or console)
#
from sys import stdout
from syslog import openlog, syslog

class Logger(object):

    def __init__(self, verbose=False, use_syslog=True):
        self.verbose = verbose
        self.use_syslog = use_syslog
        if use_syslog:
            openlog("UFTPD")

    def error(self, message):
        self.info("[ERROR] %s" % str(message))

    def info(self, message):
        if self.use_syslog:
            syslog(message)
        else:
            print(message)
            stdout.flush()

    def debug(self, message):
        if self.verbose:
            self.info("[DEBUG] %s" % str(message))
