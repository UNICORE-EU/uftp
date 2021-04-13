#
# simplistic logger - write everything to console
# and let systemd worry about it
#
from sys import stdout

class Logger(object):

    def __init__(self, verbose=False):
        self.verbose = verbose

    def error(self, message):
        self.info("ERROR %s" % str(message))

    def info(self, message):
        print(message)
        stdout.flush()

    def debug(self, message):
        if self.verbose:
            self.info("[DEBUG] %s" % str(message))

    
