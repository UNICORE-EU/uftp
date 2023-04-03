"""
 Rsync utilities and classes
 
 Based on Andrew Tridgell's 'rsync' as described in
 https://www.andrew.cmu.edu/course/15-749/READINGS/required/cas/tridgell96.pdf

"""

import os
import time
import hashlib

def a(block):
    """
    the function "a" from the rsync rolling checksum algorithm
    Args: byte array
    """
    return sum(block) & 0xFFFF

def b(block, k, l):
    """
    the function "b" from the rsync rolling checksum algorithm
    Args: 
        block - byte array
        k - integer
        l - integer
    """
    const = l - k + 1
    return (const*sum(block) - sum([i*b for i, b in enumerate(block)]))& 0xFFFF

def r_sum(a, b):
    return a + (b << 16)

def checksum(block, start, finish):
    """
    Computes the weak checksum of the given block of data
    
    Args:
    
      block - byte array containing a block of data
      start - start position of the block in the complete data
      finish - end position of the block in the complete data
    """
    return r_sum( a(block), b(block, start, finish) ) 

def update(Ak, Bk, k, l, Xk, X):
    """ update the rolling checksum """
    A = ( Ak - Xk + X ) & 0xFFFF
    B = ( Bk - (l-k+1)*Xk + A ) & 0xFFFF
    return A + (B<<16), A, B

def reset(data, k, l):
    """ reset the rolling checksum """
    A = a(data)
    B = b(data, k, l)
    return A + (B<<16), A, B, data[0], bytearray(data)

class Stats():
    def __init__(self):
        self.sent = 0
        self.received = 0
        self.num_blocks = 0
        self.blocksize = 0
        self.blocks_matched = 0
        self.start = int(time.time())

    def duration(self):
        return int(time.time()) - self.start

    def __repr__(self):
        return "Sent: {}, Received: {}, Time: {}, Blocks: #{} @{} ={}".format(
            self.sent,
            self.received,
            self.duration(),
            self.num_blocks,
            self.blocksize,
            self.blocks_matched
        )

    __str__ = __repr__

_END_MARKER = bytes([255,255,255,255])

class _Sync():
    """ common base class for both sync sides """
    def __init__(self, data_connector, file_path, blocksize = None, read_buffer_size = 65536):
        self.data_connector = data_connector
        self.file_path = file_path
        self.blocksize = blocksize
        self._READ_BUFSIZE = read_buffer_size
        self.stats = Stats()

    def _read_int(self):
        b = self.data_connector.read(4)
        if b==_END_MARKER:
            return -1
        else:
            return int.from_bytes(b, 'big')
    
    def _read_long(self):
        b = self.data_connector.read(8)
        return int.from_bytes(b, 'big')

    def _write_int(self, data):
        if data==-1:
            self.data_connector.write(_END_MARKER, do_flush=False)
        else:
            self.data_connector.write(data.to_bytes(4, 'big'), do_flush=False)
    
    def _write_long(self, data):
        self.data_connector.write(data.to_bytes(8, 'big'), do_flush=False)


class Leader(_Sync):
    """ The Leader side has the up-to-date copy of a file. 
        It receives checksums from the Follower side, and then sends updates as required, so
        the Follower side can update its copy.
    """
    def __init__(self, data_connector, file_path, read_buffer_size=65536):
        super().__init__(data_connector, file_path, read_buffer_size=read_buffer_size)
        # key: weak checksum, value tuple (strong checksum, block index)
        self.checksums = {}

    def run(self):
        self.receive_checksums()
        self.find_matches()
        return self.stats
    
    def receive_checksums(self):
        self.blocksize = self._read_int()
        self.stats.blocksize = self.blocksize
        num_blocks = self._read_int()
        for index in range(0, num_blocks):
            weak_checksum = self._read_long()
            strong_checksum = self.data_connector.read(16)
            block_refs = self.checksums.get(weak_checksum, None)
            if block_refs is None:
                block_refs = []
                self.checksums[weak_checksum] = block_refs
            block_refs.append( (strong_checksum, index) )
        self.stats.num_blocks = num_blocks
        self.stats.received += num_blocks*20
        
    def send_data(self, num_bytes, file_object, match_index):
        if match_index>=0:
            self._write_int(match_index)
        else:
            self._write_int(-1)
        self._write_long(num_bytes)
        remaining = num_bytes
        while remaining>0:
            chunksize = min(remaining, self._READ_BUFSIZE)
            data = file_object.read(chunksize)
            self.data_connector.write(data)
            remaining = remaining - len(data)
        self.stats.sent += num_bytes+4

    def find_matches(self):
        end_of_last_match = 0
        total = os.stat(self.file_path).st_size
        end = total - self.blocksize
        # for the rolling checksum
        A = 0
        B = 0
        k = 0
        l = self.blocksize - 1
        Xk = 0
        X = 0
        _data = None
        _buf = None
        _position=0
        _max_pos=0
        
        with open(self.file_path, "rb", buffering=self._READ_BUFSIZE) as f:
            data = f.read(self.blocksize)
            if total<self.blocksize:
                self.send_data(len(data), data, -1)
                self.shutdown()
                return
            checksum, A, B, Xk, _data = reset(data, k, l)
            _max_pos = len(data) 
            while l < total:
                index = None
                block_refs = self.checksums.get(checksum)
                if block_refs:
                    if data is None:
                        f.seek(k)
                        data = f.read(self.blocksize)
                    strong_checksum = hashlib.md5(data).digest()
                    for block_ref in block_refs:
                        if strong_checksum==block_ref[0]:
                            index = block_ref[1]
                            break
                if index:
                    num_bytes = k - end_of_last_match
                    if num_bytes>0:
                        f.seek(end_of_last_match)
                    self.stats.blocks_matched+=1
                    # send literal data and index of matching block
                    self.send_data(num_bytes, f, index)
                    # reset search to the end of the match
                    end_of_last_match = l+1
                    k += self.blocksize
                    if num_bytes>0:
                        f.seek(k);
                    data = f.read(self.blocksize);
                    _max_pos = len(data)
                    l = k-1+_max_pos
                    if _max_pos >0:
                       checksum, A, B, Xk, _data = reset(data, k, l);
                elif l < end:
                    # search byte-by-byte for a next match
                    if _position==0:
                        data = None
                        _buf = f.read(_max_pos)
                    X = _buf[_position]
                    checksum, A, B = update(A, B, k, l, Xk, X)
                    _data[_position] = X
                    _position += 1
                    if _position==_max_pos:
                        _position=0
                    k, l, Xk = k+1, l+1, _data[_position]
                else:
                    break
            # finally, we need to send any remaining data
            num_bytes = total - end_of_last_match
            if num_bytes>0:
                f.seek(end_of_last_match)
            self.send_data(num_bytes, f, -1);
            self.data_connector.flush()

class Follower(_Sync):
    """ The Follower side has the out-of-date copy of a file. 
        It sends checksums to the Leader side, and then receives updates as required,
        using them to write an up-to-date version of the file
    """
    def __init__(self, data_connector, file_path, blocksize = None, read_buffer_size=65536):
        super().__init__(data_connector, file_path, blocksize, read_buffer_size)
        self.weak_checksums = []   # array of 'longs', i.e. 4-byte-sized values
        self.strong_checksums = [] # array of 16-byte-long byte arrays
        
    def run(self):
        self.compute_checksums()
        self.send_checksums()
        self.reconstruct_file()
        return self.stats

    def compute_checksums(self):
        size = os.stat(self.file_path).st_size
        if not self.blocksize:
            # compute "reasonable" blocksize
            self.blocksize = int(max(size/1000, 512))
        self.stats.blocksize = self.blocksize
        with open(self.file_path, "rb") as f:
            offset=0;
            remaining=size;
            length = self.blocksize;
            while remaining>0:
                if remaining<length:
                    length = remaining
                buf = f.read(length);
                if len(buf)==0:
                    break
                remaining = remaining - len(buf)
                self.weak_checksums.append(checksum(buf, offset, offset+len(buf)-1))
                self.strong_checksums.append(hashlib.md5(buf).digest());
                offset += len(buf)

    def send_checksums(self):
        self._write_int(self.blocksize)
        num_blocks = len(self.weak_checksums)
        self._write_int(num_blocks)
        for i in range(0,num_blocks):
            self._write_long(self.weak_checksums[i])
            self.data_connector.write(self.strong_checksums[i], do_flush=False)
        self.data_connector.flush()
        self.stats.sent += num_blocks * 20
        
    def reconstruct_file(self):
        temp_file_name = self.file_path+"_tmp_"+str(int(time.time()))
        with open(self.file_path, "rb") as my_copy:
            with open(temp_file_name, "wb") as target:
                target.truncate()
                while True:
                    index = self._read_int()
                    length = self._read_long()
                    self.stats.received += 20+length
                    # first read literal data
                    if length>0:
                        to_read = length
                        while to_read>0:
                            chunksize = min(length, self._READ_BUFSIZE)
                            data = self.data_connector.read(chunksize)
                            target.write(data)
                            to_read -= len(data)
                    # then write out referenced block
                    if index>=0:
                        my_copy.seek(index*self.blocksize)
                        data = my_copy.read(self.blocksize)
                        target.write(data)
                    else:
                        break
        os.rename(temp_file_name, self.file_path)
