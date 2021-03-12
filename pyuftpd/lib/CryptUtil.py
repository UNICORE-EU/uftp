"""
Classes that encrypt/decrypt streaming data, 
similar to the CipherInput/OutputStream classes in Java. The whole stream is treated 
as a "single block", applying padding only to the last chunk
"""

from Crypto.Cipher import Blowfish
from struct import pack

class CryptedWriter(object):
    
    def __init__(self, target, key):
        self.target = target
        self.cipher = Blowfish.new(key, mode = Blowfish.MODE_ECB)
        self.stored = b""

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
    
    def close(self):
        length = 8-len(self.stored)
        padding = [length]*length
        self.target.write(self.cipher.encrypt(self.stored + pack('b'*length, *padding)))
    
class Decrypt(object):
    
    def __init__(self, source, key):
        self.cipher = Blowfish.new(key, mode = Blowfish.MODE_ECB)
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
    
    