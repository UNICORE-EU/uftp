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

    def flush(self, finish = False):
        if self._closed:
            return
        if finish:
            compressed = self.compressor.flush()
        else:
            compressed = self.compressor.flush(zlib.Z_SYNC_FLUSH)
        self.target.write(compressed)
        self.target.flush()

    def close(self):
        if self._closed:
            return
        self.flush(finish=True)
        self.target.close()
        self._closed = True
    
class GzipReader(object):
    
    def __init__(self, source):
        self.source = source
        self.decompressor = zlib.decompressobj(wbits=31)
        self.stored = b""

    def read(self, length):
        buf = bytearray(self.stored)
        have = len(buf)
        finish = False
        while have<length and not finish:
            data = self.source.read(length-have)
            if len(data)==0:
                finish = True
                decompressed = self.decompressor.flush()
            else:
                decompressed = self.decompressor.decompress(data)
            buf+=decompressed
            have = len(buf)
        if have>length:
            result = buf[0:length]
            self.stored = buf[length:]
        else:
            result = buf
            self.stored = b""
        return result
    
    def close(self):
        self.source.close()
