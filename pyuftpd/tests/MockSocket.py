import io

class MockSocket(object):

    def __init__(self, msg=""):
        self.msg = msg
        self.reader = io.TextIOWrapper(io.BufferedReader(io.BytesIO(self.msg.encode("UTF-8"))))
        self.writer = io.StringIO()
    
    def reset(self):
        self.writer.truncate(0)
        
    def get_reply(self):
        return self.writer.getvalue()

    def makefile(self, type):
        if(str(type).startswith("r")):
            return self.reader
        else:
            return self.writer

