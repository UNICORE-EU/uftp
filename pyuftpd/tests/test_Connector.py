import io, unittest

from lib import Connector, Log
import MockSocket

class TestConnector(unittest.TestCase):

    def setUp(self):
        self.LOG = Log.Logger(verbose=True, use_syslog=False)
    
    def test_connector_read(self):
        msg = "test123\n"
        ms = MockSocket.MockSocket(msg)
        connector = Connector.Connector(ms, self.LOG)
        x = connector.read_line()
        self.assertTrue("test123"==x)

    def test_connector_write(self):
        ms = MockSocket.MockSocket()
        connector = Connector.Connector(ms, self.LOG)
        x = connector.write_message("test123")
        self.assertTrue("test123\n"==ms.get_reply())

if __name__ == '__main__':
    unittest.main()
