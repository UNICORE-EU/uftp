import unittest
import os
import pwd
import time
import secrets
from lib import Connector, CryptUtil, Log
import MockConnector


class TestCrypto(unittest.TestCase):
    def setUp(self):
        self.LOG = Log.Logger(verbose=True, use_syslog=False)

    def test_crypto_blowfish(self):
        print("*** test_crypto_blowfish")
        msg = bytes("test123", "UTF-8")
        key = secrets.token_bytes(56)
        ms = MockConnector.MockConnector(None)
        cipher = CryptUtil.create_cipher(key, "BLOWFISH")
        print("BLOWFISH %s" % cipher.block_size)
        crypted_writer = CryptUtil.CryptWriter(ms, cipher)
        crypted_writer.write(msg)
        crypted_writer.close()
        crypted_msg = ms.getvalue()
        ms = MockConnector.MockConnector(crypted_msg)
        cipher = CryptUtil.create_cipher(key, "BLOWFISH")
        crypted_reader = CryptUtil.DecryptReader(ms, cipher)
        decrypted_msg = crypted_reader.read(8192)
        crypted_reader.close()
        x = str(decrypted_msg, "UTF-8")
        self.assertTrue("test123"==x)

    def test_crypto_aes(self):
        print("*** test_crypto_aes")
        msg = bytes("test123", "UTF-8")
        for kl in [16,24,32]:
            key = secrets.token_bytes(16+kl)
            ms = MockConnector.MockConnector(None)
            cipher = CryptUtil.create_cipher(key, "AES")
            crypted_writer = CryptUtil.CryptWriter(ms, cipher)
            crypted_writer.write(msg)
            crypted_writer.close()
            crypted_msg = ms.getvalue()
            ms = MockConnector.MockConnector(crypted_msg)
            cipher = CryptUtil.create_cipher(key, "AES")
            crypted_reader = CryptUtil.DecryptReader(ms, cipher)
            decrypted_msg = crypted_reader.read(8192)
            crypted_reader.close()
            x = str(decrypted_msg, "UTF-8")
            self.assertTrue("test123"==x)
if __name__ == '__main__':
    unittest.main()
