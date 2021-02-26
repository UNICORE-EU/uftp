#
# simplistic logger - write everything to console
# and let systemd worry about it
#

class Logger(object):

    def __init__(self, verbose=False):
        self.verbose = verbose

    def error(self, message):
        print(message)

    def info(self, message):
        print(message)

    def debug(self, message):
        if self.verbose:
            print(message)

    
