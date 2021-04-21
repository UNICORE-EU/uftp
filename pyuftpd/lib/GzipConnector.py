"""
handles gzip compression of in/out streamlike data
"""

import zlib

class GzipConnector(object):

    def __init__(self, data_connector):
        self.write_mode = True
        self.writer = GzipWriter(data_connector)
        self.reader = GzipReader(data_connector)
        
    def write(self, data):
        return self.writer.write(data)
    
    def flush(self):
        if self.write_mode:
            self.writer.flush()
    
    def read(self, length):
        self.write_mode = False
        return self.reader.read(length)
    
    def close(self):
        if self.write_mode:
            self.writer.close()
        else:
            self.reader.close()
        
class GzipWriter(object):
    
    def __init__(self, target):
        self.target = target
        self.compressor = zlib.compressobj(wbits=31)
        self._closed = False

    def write(self, data):
        compressed = self.compressor.compress(data)
        self.target.write(compressed)
        return len(data)

    def flush(self):
        compressed = self.compressor.flush()
        self.target.write(compressed)
        self.target.flush()

    def close(self):
        if self._closed:
            return
        self._closed = True
        self.flush()
        self.target.close()
    
class GzipReader(object):
    
    def __init__(self, source):
        self.source = source
        self.decompressor = zlib.decompressobj(wbits=31)

    def read(self, length):
        data = self.source.read(length)
        decompressed = self.decompressor.decompress(data)
        return decompressed
    
    def close(self):
        self.source.close()

    