import os, unittest
import Connector, RSync
from hashlib import md5
from time import sleep
from random import randint

class TestRSync(unittest.TestCase):

    def setUp(self):
        os.makedirs("./target/testdata", exist_ok=True)

    def test_func_a(self):
        block = [1,2,3,4,5,6,7,8,9,10]
        self.assertEqual(55, RSync.a(block))
        block = []
        for x in range(0, 257):
            block.append(255)
        block.append(2)
        self.assertEqual(1, RSync.a(block))

    def test_func_b(self):
        block = [1,2,3,4,5,6,7,8,9,10]
        self.assertEqual(275, RSync.b(block, 0, 10))
        
    def test_sum_b(self):
        a =  10
        b =  32
        self.assertEqual(10+(32 * 65536), RSync.r_sum(a,b))

    def test_compute_checksums(self):
        f1 = "tests/rsync-test-1.txt"
        connector = MockConnector()
        follower = RSync.Follower(connector, f1)
        follower.compute_checksums()
        self.assertEqual(follower.weak_checksums, [3670588062, 1824262269])
     
    def test_identical_files(self):
        print("\n*** test_identical_files")
        f1 = "tests/rsync-test-1.txt"
        file_to_update = "target/testdata/rsync-test-to-be-updated.txt"
        with open(f1, "rb") as f_1:
            with open(file_to_update, "wb") as f_2: 
                f_2.write(f_1.read())
        d1 = self.digest(f1)
        d2 = self.digest(file_to_update)
        print("Original:      ", d1)
        print("To be sync'ed: ", d2)
        connector = MockConnector()
        leader = RSync.Leader(connector, f1)
        follower = RSync.Follower(connector, file_to_update)
        follower.compute_checksums()
        follower.send_checksums()
        leader.receive_checksums()
        leader.find_matches()
        follower.reconstruct_file()
        d1 = self.digest(f1)
        d2 = self.digest(file_to_update)
        print("Original: ", d1)
        print("Sync'ed:  ", d2)
        
    def test_almost_identical_files(self):
        print("\n*** test_almost_identical_files")
        f1 = "tests/rsync-test-1.txt"
        file_to_update = "target/rsync-test-to-be-updated.txt"
        with open(f1, "rb") as f_1:
            with open(file_to_update, "wb") as f_2: 
                d = bytearray(f_1.read())
                x = 383 #randint(0,len(d))
                d[x] = 155
                f_2.write(d)
        d1 = self.digest(f1)
        d2 = self.digest(file_to_update)
        print("Original:      ", d1)
        print("To be sync'ed: ", d2)
        connector = MockConnector()
        leader = RSync.Leader(connector, f1)
        follower = RSync.Follower(connector, file_to_update, blocksize=64)
        follower.compute_checksums()
        follower.send_checksums()
        leader.receive_checksums()
        leader.find_matches()
        follower.reconstruct_file()
        d1 = self.digest(f1)
        d2 = self.digest(file_to_update)
        print("Original: ", d1)
        print("Sync'ed:  ", d2)

    def test_follower_leader_1(self):
        print("\n*** test_follower_leader_1")
        f1 = "target/rsync-test-original.dat"
        file_to_update = "target/rsync-test-to-be-updated.dat"
        with open(f1, "wb") as f_1:
            with open(file_to_update, "wb") as f_2: 
                for i in range(0,32):
                    data = bytearray(os.urandom(1024))
                    f_1.write(data)
                    x = randint(0,1023)
                    data[x]=0
                    f_2.write(data)
        d1 = self.digest(f1)
        d2 = self.digest(file_to_update)
        print("Original:      ", d1)
        print("To be sync'ed: ", d2)

        connector = MockConnector()
        leader = RSync.Leader(connector, f1)
        follower = RSync.Follower(connector, file_to_update)
        follower.compute_checksums()
        follower.send_checksums()
        leader.receive_checksums()
        self.assertEqual(follower.blocksize, leader.blocksize)
        for cs in follower.weak_checksums:
            block_refs = leader.checksums.get(cs)
            self.assertIsNotNone(block_refs)
            for block_ref in block_refs:
                strong_cs = block_ref[0];
                index = block_ref[1]
                self.assertEqual(strong_cs, follower.strong_checksums[index])
        leader.find_matches()
        follower.reconstruct_file()
        d1 = self.digest(f1)
        d2 = self.digest(file_to_update)
        print("Original: ", d1)
        print("Sync'ed:  ", d2)

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
