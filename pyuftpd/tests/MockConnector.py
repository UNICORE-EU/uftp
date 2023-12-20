import io

class MockConnector(object):

    def __init__(self, content):
        self.reader = io.BufferedReader(io.BytesIO(content))
        self.writer = io.BytesIO()

    def write(self, msg):
        self.writer.write(msg)

    def getvalue(self):
        return self.writer.getvalue()
    
    def read(self, length):
        return self.reader.read(length)
    
    def close(self):
        pass