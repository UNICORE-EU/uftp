"""
Classes that encrypt/decrypt streaming data, 
similar to the CipherInput/OutputStream classes in Java. The whole stream is treated 
as a "single block", applying padding only to the last chunk
"""

from Crypto.Cipher import AES, Blowfish
from struct import pack


def create_cipher(key, algo):
    if "BLOWFISH".upper()==algo:
        if len(key)>56:
            raise ValueError("Key length must <=56 for encryption algorithm: %s" % algo)
        return Blowfish.new(key, mode = Blowfish.MODE_ECB)
    elif "AES".upper()==algo:
        if len(key)<32:
            raise ValueError("Key length must be >32 for encryption algorithm: %s" % algo)
        key_length = len(key) - 16;
        if not key_length in [16,24,32]:
            raise ValueError("Illegal key length for encryption algorithm: %s" % algo)
        iv = key[:16]
        key = key[16:]
        return AES.new(key, mode = AES.MODE_CBC, iv = iv)
    else:
        raise ValueError("Unknown encryption algorithm: %s" % algo)

class CryptedConnector(object):

    def __init__(self, data_connector, key, algo):
        self.write_mode = True
        self.writer = CryptedWriter(data_connector, create_cipher(key, algo))
        self.reader = Decrypt(data_connector, create_cipher(key, algo))

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

    def __init__(self, target, cipher):
        self.target = target
        self.cipher = cipher
        self.stored = b""
        self.block_length = self.cipher.block_size
        self._closed = False

    def write(self, data):
        if len(self.stored)>0:
            data = self.stored + data
        extra = divmod(len(data), self.block_length)[1];
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
        length = self.block_length - len(self.stored)
        padding = [length]*length
        self.target.write(self.cipher.encrypt(self.stored + pack('b'*length, *padding)))
        self.target.close()
    
class Decrypt(object):
    
    def __init__(self, source, cipher):
        self.cipher = cipher
        self.source = source
        self.stored = b""
        self.block_length = self.cipher.block_size

    def read(self, length):
        data = self.source.read(length)
        finish = len(data)<length
        if len(self.stored)>0:
            data = self.stored + data
        extra = divmod(len(data), self.block_length)[1];
        if extra>0:
            decrypted = self.cipher.decrypt(data[:-extra])
            self.stored = data[-extra:]
            finish = False
        else:
            decrypted = self.cipher.decrypt(data)
            self.stored = b""
        if finish and len(decrypted)>0:
            padlength = decrypted[-1]
            return decrypted[:-padlength]
        else:
            return decrypted
    
    def close(self):
        self.source.close()

    