import io

class MockSocket(object):

    def __init__(self, msg="", reader=None, writer=None):
        if reader is not None:
            self.reader = reader
        else:
            self.reader = io.TextIOWrapper(io.BufferedReader(io.BytesIO(msg.encode("UTF-8"))))
        if writer is not None:
            self.writer = writer
        else:
            self.writer = io.StringIO()

    def get_reply(self):
        return self.writer.getvalue()


    def makefile(self, type):
        if(str(type).startswith("r")):
            return self.reader
        else:
            return self.writer

