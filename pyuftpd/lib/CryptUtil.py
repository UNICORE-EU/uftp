"""
Classes that encrypt/decrypt streaming data, 
similar to the CipherInput/OutputStream classes in Java. The whole stream is treated 
as a "single block", applying padding only to the last chunk
"""

from Crypto.Cipher import Blowfish
from struct import pack

class CryptedConnector(object):

    def __init__(self, data_connector, key):
        self.write_mode = True
        self.cipher = Blowfish.new(key, mode = Blowfish.MODE_ECB)
        self.writer = CryptedWriter(data_connector, key, self.cipher)
        self.reader = Decrypt(data_connector, key, self.cipher)

    def write(self, data):
        return self.writer.write(data)

    def flush(self):
        pass

    def read(self, length):
        self.write_mode = False
        return self.reader.read(length)

    def close(self):
        if self.write_mode:
            self.writer.close()
        else:
            self.reader.close()

class CryptedWriter(object):

    def __init__(self, target, key, cipher=None):
        self.target = target
        if cipher is None:
            self.cipher = Blowfish.new(key, mode = Blowfish.MODE_ECB)
        else:
            self.cipher = cipher
        self.stored = b""
        self._closed = False

    def write(self, data):
        if len(self.stored)>0:
            data = self.stored + data
        extra = divmod(len(data),8)[1];
        if extra>0:
            crypted = self.cipher.encrypt(data[:-extra])
            self.stored = data[-extra:]
        else:
            crypted = self.cipher.encrypt(data)
            self.stored = b""
        self.target.write(crypted)
        return len(data)

    def flush(self):
        pass
    
    def close(self):
        if self._closed:
            return
        self._closed = True
        length = 8-len(self.stored)
        padding = [length]*length
        self.target.write(self.cipher.encrypt(self.stored + pack('b'*length, *padding)))
        self.target.close()
    
class Decrypt(object):
    
    def __init__(self, source, key, cipher=None):
        if cipher is None:
            self.cipher = Blowfish.new(key, mode = Blowfish.MODE_ECB)
        else:
            self.cipher = cipher
        self.source = source
        self.stored = b""

    def read(self, length):
        data = self.source.read(length)
        finish = len(data)<length
        if len(self.stored)>0:
            data = self.stored + data
        extra = divmod(len(data),8)[1];
        if extra>0:
            decrypted = self.cipher.decrypt(data[:-extra])
            self.stored = data[-extra:]
            finish = False
        else:
            decrypted = self.cipher.decrypt(data)
            self.stored = b""
        if finish:
            padlength = decrypted[-1]
            return decrypted[:-padlength]
        else:
            return decrypted
    
    def close(self):
        self.source.close()

    