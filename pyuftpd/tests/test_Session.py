import os, unittest
import MockSocket
from lib import Connector, Log, Session

class TestSession(unittest.TestCase):

    def setUp(self):
        self.LOG = Log.Logger(verbose=False, use_syslog=False)
        os.makedirs("./target/testdata", exist_ok=True)

    def test_session_1(self):
        print("\n*** test_session_1")
   
        data = MockConnector()
        ms = MockSocket.MockSocket()
        control = Connector.Connector(ms, self.LOG)
        cwd = os.getcwd()
        job = {'file': '%s/target/testdata' % cwd,
               'access-permissions': "WRITE"
               }
        session = Session.Session(control, job, self.LOG)
        self.assertTrue(session.access_level==3) # Session.MODE_WRITE
        session.data = data
        session.syst("")
        self.assertTrue("215 " in ms.get_reply())
        
        ms.reset()
        session.feat("")
        self.assertTrue("211-Features" in ms.get_reply())
        self.assertTrue("211 END" in ms.get_reply())
        
        ms.reset()
        session.cwd("testdata")
        self.assertTrue("200 OK" in ms.get_reply())
        
        ms.reset()
        session.pwd("")
        self.assertTrue("257 " in ms.get_reply())
        self.assertTrue("testdata" in ms.get_reply())
            
        ms.reset()
        session.cdup("")
        self.assertTrue("200 OK" in ms.get_reply())
        os.chdir(cwd)

    def test_session_2(self):
        print("\n*** test_session_2")
        data = MockConnector()
        ms = MockSocket.MockSocket()
        control = Connector.Connector(ms, self.LOG)
        cwd = os.getcwd()
        job = {'file': '%s/target/testdata' % cwd,
               #'access-permissions': "FULL"
               }
        session = Session.Session(control, job, self.LOG)
        self.assertTrue(session.access_level==4) # Session.MODE_FULL
        session.data = data
        ms.reset()
        session.mkdir("test123")
        self.assertTrue("257 " in ms.get_reply())
        self.assertTrue("directory created" in ms.get_reply())
        
        ms.reset()
        session.rmdir("test123")
        self.assertTrue("200 OK" in ms.get_reply())

    def test_stat(self):
        print("\n*** test_stat")
        data = MockConnector()
        ms = MockSocket.MockSocket()
        control = Connector.Connector(ms, self.LOG)
        cwd = os.getcwd()
        job = {'file': '%s/target/testdata/' % cwd }
        session = Session.Session(control, job, self.LOG)
        session.data = data
        session.stat("F .")
        self.assertTrue("testdata" in ms.get_reply())



class MockConnector():
    def __init__(self):
        self.data = bytearray()
        self.pos = 0

    def write(self, data, do_flush=True):
        self.data.extend(data)

    def flush(self):
        pass

    def read(self, length):
        length = min(length, len(self.data)-self.pos)
        b = bytes(self.data[self.pos:self.pos+length])
        self.pos += length
        if self.pos >= len(self.data):
            self.data = bytearray()
            self.pos = 0
        return b

if __name__ == '__main__':
    unittest.main()
