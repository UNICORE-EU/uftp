from struct import pack, unpack

import Connector, Log

class PConnector(object):
    """
        Multi-stream connector that writes/reads multiple TCP (data)streams
    """

    def __init__(self, connectors: Connector, LOG: Log, key=None):
        self._connectors = connectors
        self._inputs = []
        self._outputs = []
        self.LOG = LOG
        self.encrypt = key is not None
        if self.encrypt:
            import CryptUtil
        for conn in connectors:
            if self.encrypt:
                self._outputs.append(CryptUtil.CryptedWriter(conn, key))
                self._inputs.append(CryptUtil.Decrypt(conn, key))
            else:
                self._outputs.append(conn.client.makefile("wb"))
                self._inputs.append(conn.client.makefile("rb"))
        self.seq = 0
        self.num_streams = len(self._connectors)

    def write(self, data):
        """ Write all the data to remote channel """
        
        _magic = 0xcebf
        size = len(data)
        chunk = int(len(data) / self.num_streams)
        i = 0
        for out in self._outputs:
            offset = i * chunk
            if i == self.num_streams - 1:
                chunk_len = size - i * chunk;
            else:
                chunk_len = chunk;
            self.write_block(pack(">HHIII", _magic, i, self.seq, size, chunk_len)
                             +data[offset:offset+chunk_len], out)
            i += 1
        self.seq += 1

    def write_block(self, data, _out):
        """ Write all the data to out """
        to_write = len(data)
        write_offset = 0
        while to_write > 0:
            written = _out.write(data[write_offset:])
            if written is None:
                written = 0
            write_offset += written
            to_write -= written
        _out.flush()

    def read(self, length):
        """ Read data from remote channel """
        buffer = bytearray(length)
        i = 0
        _magic = 0xcebf
        for src in self._inputs:
            header = src.read(16)
            if len(header)==0:
                break
            (magic, pos, seq, size, chunk_len) = unpack(">HHIII", header)
            if magic!=_magic:
                raise Exception("I/O error (magic number)")
            if seq!=self.seq:
                raise Exception("I/O error (sequence number)")
            if size != len(buffer):
                raise Exception("I/O error (total size)")
            chunk = int(size / self.num_streams)
            offset = pos * chunk
            buffer[offset:offset+chunk_len] = src.read(chunk_len)
            i += 1
        self.seq += 1
        return buffer

    def close(self):
        for i in range(0, len(self._connectors)):
            try:
                self._outputs[i].close()
                self._inputs[i].close()
            except Exception as e:
                self.LOG.error(e)
            try:
                self._connectors[i].close()
            except:
                pass
