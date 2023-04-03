import os, unittest
import Connector, Log, RSync
from hashlib import md5
from time import sleep
from sys import exit
from random import randint
import cProfile
from pstats import Stats

class TestRSync(unittest.TestCase):

    def setUp(self):
        self.pr = cProfile.Profile()
        self.pr.enable()
        os.makedirs("./target/testdata", exist_ok=True)

    def tearDown(self):
        p = Stats (self.pr)
        p.strip_dirs()
        p.print_stats ()

    def test_1(self):
        print("*** profiled run of rsync checksumming")
        f1 = "target/rsync-perftest1.dat"
        f2 = "target/rsync-perftest2.dat"
        try:
            os.remove(f2)
        except:
            pass
        with open(f1, "wb") as f_1:
           for i in range(0,10):
               data = bytearray(os.urandom(1024*1024))
               f_1.write(data)
        with open(f2, "wb") as f_2:
           for i in range(0,2):
               data = bytearray(os.urandom(1024*1024))
               f_2.write(data)
        d1 = self.digest(f1)
        d2 = self.digest(f2)
        print("Original:      ", d1)
        print("To be sync'ed: ", d2)
        connector = MockConnector()
        leader = RSync.Leader(connector, f1)
        follower = RSync.Follower(connector, f2)
        follower.compute_checksums()
        follower.send_checksums()
        leader.receive_checksums()
        leader.find_matches()
        follower.reconstruct_file()
        d1 = self.digest(f1)
        d2 = self.digest(f2)
        print("Original: ", d1)
        print("Sync'ed:  ", d2)
        print("Stats: %s\n" % leader.stats)

    def digest(self, f):
        h = md5()
        with open(f, 'rb') as file:
            while True:
                chunk = file.read(h.block_size)
                if not chunk:
                    break
                h.update(chunk)
            return h.hexdigest()


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
