import unittest
import os
import pwd
import time
from lib import Connector, CryptUtil, Log
import MockConnector


class TestCrypto(unittest.TestCase):
    def setUp(self):
        self.LOG = Log.Logger(verbose=True, use_syslog=False)

    def test_crypto1(self):
        msg = bytes("test123", "UTF-8")
        key = bytes("1234567890123456789012345","UTF-8")
        ms = MockConnector.MockConnector(None)
        crypted_writer = CryptUtil.CryptedWriter(ms, key)
        crypted_writer.write(msg)
        crypted_writer.close()
        crypted_msg = ms.getvalue()
        
        ms = MockConnector.MockConnector(crypted_msg)
        crypted_reader = CryptUtil.Decrypt(ms, key)
        decrypted_msg = crypted_reader.read(8192)
        crypted_reader.close()
        x = str(decrypted_msg, "UTF-8")
        self.assertTrue("test123"==x)

if __name__ == '__main__':
    unittest.main()
