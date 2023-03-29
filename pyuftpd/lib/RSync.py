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
    sum=0
    for b in block:
        sum = sum + (b & 0xFF)
    return sum & 0xFFFF


def b(block, k, l):
    """
    the function "b" from the rsync rolling checksum algorithm
    Args: 
        block - byte array
        k - integer
        l - integer
    """
    sum=0
    i=k
    for b in block:
        sum = sum + (l-i+1)*(b & 0xFF)
        i = i +1
    return sum & 0xFFFF

def sum(a, b):
    return a + (b << 16)

def checksum(block, start, finish):
    """
    Computes the weak checksum of the given block of data
    
    Args:
    
      block - byte array containing a block of data
      start - start position of the block in the complete data
      finish - end position of the block in the complete data
    """
    return sum( a(block), b(block, start, finish) ) 


class RollingChecksum():
    
    def __init__(self):
        # last data value
        self.lastX=0
        # last function values
        self.lastA=0
        self.lastB=0
        # last index values for consistency checking
        self.lastK=0
        self.lastL=0
    
    def init(self, data):
        """
        initialise the checksum
        Args:
           data - initial data block
        Returns the checksum of the given initial block
        """
        return self.reset(data, 0, len(data)-1);
    
    def reset(self, data, k, l):
        """
        reset the checksum using the given block data
        Args:     
            Data - data block
            k - start position
            l - end position
        Returns the checksum of the given block
        """
        self.lastX=data[0] & 0xFF
        self.lastK=k
        self.lastL=l
        self.lastA=a(data)
        self.lastB=b(data, k, l)
        return sum(self.lastA,self.lastB);
    
    def update(self, k, l, X_k, X_l):
        """
        Compute the rolling checksum for the given parameters
        Args:
            k - start index
            l - end index
            X_k - the data value at position 'k'
            X_l - the data value at position 'l'
        Returns the checksum value for the block from 'k' to 'l'
        """
        if k==0 or k!=self.lastK+1 or l!=self.lastL+1:
            raise ValueError()
        
        # recurrence relations
        self.lastA = ( self.lastA - self.lastX + (X_l & 0xFF) ) & 0xFFFF
        self.lastB = ( self.lastB - (l-k+1)*self.lastX + self.lastA ) & 0xFFFF
        self.lastK += 1
        self.lastL += 1
        self.lastX =  X_k & 0xFF
        return sum(self.lastA,self.lastB)

_END_MARKER = [255,255,255,255]
_END_MARKER_bytes = bytes(_END_MARKER)

class _Sync():
    """ common base class for both sync sides """
    def __init__(self, data_connector, file_path, blocksize = 512, read_buffer_size = 65536):
        self.data_connector = data_connector
        self.file_path = file_path
        self.weak_checksums = []   # array of 'longs', i.e. 4-byte-sized values
        self.strong_checksums = [] # array of 16-byte-long byte arrays
        self.blocksize = blocksize
        self._READ_BUFSIZE = read_buffer_size

    def _read_int(self):
        b = self.data_connector.read(4)
        if b==_END_MARKER_bytes:
            return -1
        else:
            return int.from_bytes(b, 'big')
    
    def _read_long(self):
        b = self.data_connector.read(8)
        return int.from_bytes(b, 'big')

    def _write_int(self, data):
        self.data_connector.write(data.to_bytes(4, 'big'), do_flush=False)
    
    def _write_long(self, data):
        self.data_connector.write(data.to_bytes(8, 'big'), do_flush=False)


class Leader(_Sync):
    """ The Leader side has the up-to-date copy of a file. 
        It receives checksums from the Follower side, and then sends updates as required, so
        the Follower side can update its copy.
    """
    def __init__(self, data_connector, file_path):
        super().__init__(data_connector, file_path)

    def run(self):
        self.receive_checksums()
        self.find_matches()
    
    def receive_checksums(self):
        self.blocksize = self._read_int()
        num_blocks = self._read_int()
        for i in range(0, num_blocks):
            self.weak_checksums.append(self._read_long())
            self.strong_checksums.append(self.data_connector.read(16))

    def shutdown(self):
        # writes a "-1" index as end marker
        self.send_data(0, None, -1)

    def send_data(self, num_bytes, file_object, match_index):
        if match_index>=0:
            self._write_int(match_index)
        else:
            self.data_connector.write(_END_MARKER)
        self._write_long(num_bytes)
        remaining = num_bytes
        while remaining>0:
            chunksize = min(remaining, self._READ_BUFSIZE)
            data = file_object.read(chunksize)
            self.data_connector.write(data)
            remaining = remaining - len(data)

    def find_weak_match(self, checksum):
        for i in range(0, len(self.weak_checksums)):
            if checksum == self.weak_checksums[i]:
                return i
        return -1
    
    def find_matches(self):
        index_of_last_match = 0
        total = os.stat(self.file_path).st_size
        k = 0
        l = self.blocksize - 1
        rolling_checksum = RollingChecksum()
        
        with open(self.file_path, "rb") as f:
            data = f.read(self.blocksize)
            # special case of very short file
            if total<self.blocksize or len(data)<self.blocksize:
                self.send_data(len(data), data, -1)
                self.shutdown()
                return
            
            checksum = rolling_checksum.init(data)
            while l <= total:
                index = self.find_weak_match(checksum)
                if index>=0 and self.strong_checksums[index]==hashlib.md5(data).digest():
                    num_bytes = k - index_of_last_match
                    f.seek(index_of_last_match)
                    # send literal data and index of matching block
                    self.send_data(num_bytes, f, index)
                    # reset search to the end of the match
                    k += self.blocksize
                    l += self.blocksize
                    index_of_last_match = k
                    f.seek(k);
                    if l <= total:
                        data = f.read(self.blocksize);
                        checksum = rolling_checksum.reset(data, k, l);
                else:
                    # set read position to next block start
                    k += 1
                    f.seek(k)
                    l += 1
                    # and compute the next checksum
                    data = f.read(self.blocksize)
                    checksum = rolling_checksum.update(k, l, data[0], data[-1])

            # finally, we need to send any remaining data
            if index_of_last_match < total:
                num_bytes = total - index_of_last_match
                f.seek(index_of_last_match)
                self.send_data(num_bytes, f, -1);
            self.shutdown()

class Follower(_Sync):
    """ The Follower side has the out-of-date copy of a file. 
        It sends checksums to the Leader side, and then receives updates as required,
        using them to write an up-to-date version of the file
    """
    def __init__(self, data_connector, file_path, blocksize = 512):
        super().__init__(data_connector, file_path, blocksize)

    def run(self):
        self.compute_checksums()
        self.send_checksums()
        self.reconstruct_file()

    def compute_checksums(self):
        size = os.stat(self.file_path).st_size
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
        
    def reconstruct_file(self):
        temp_file_name = self.file_path+"_tmp_"+str(int(time.time()))
        with open(self.file_path, "rb") as my_copy:
            with open(temp_file_name, "wb") as target:
                target.truncate()
                while True:
                    index = self._read_int()
                    length = self._read_long()
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
