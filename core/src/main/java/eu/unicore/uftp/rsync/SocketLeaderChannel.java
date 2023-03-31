package eu.unicore.uftp.rsync;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;

import eu.unicore.uftp.rsync.Checksum.ChecksumHolder;

public class SocketLeaderChannel implements LeaderChannel{

	private final Socket socket;
	
	public SocketLeaderChannel(Socket socket){
		this.socket=socket;
	}

	@Override
	public ChecksumHolder receiveChecksums() throws IOException {
		DataInputStream dis=new DataInputStream(socket.getInputStream());
		ChecksumHolder res=new ChecksumHolder();
		res.blocksize=dis.readInt();
		int numBlocks=dis.readInt();
		res.weakChecksums=new ArrayList<>();
		res.strongChecksums=new ArrayList<>();
		for(int i=0; i<numBlocks; i++){
			res.weakChecksums.add(dis.readLong());
			byte[]cs=new byte[16];
			dis.readFully(cs);
			res.strongChecksums.add(cs);
		}
		return res;
	}

	@Override
	public void sendData(long bytes, ByteChannel source, int index)
			throws IOException {
		DataOutputStream dos=new DataOutputStream(socket.getOutputStream());
		// send index first
		dos.writeInt(index);
		dos.writeLong(bytes);
		dos.flush();
		if(source==null)return;
		
		// send bytes
		long remaining=bytes;
		ByteBuffer buf=ByteBuffer.allocate(2048);
		int len=0;
		while(len>-1 && remaining>0){
			if(remaining<buf.limit()){
				buf.limit((int)remaining);
			}
			len=source.read(buf);
			if(len>0){
				buf.flip();
				socket.getOutputStream().write(buf.array(),0,len);
				buf.clear();
				remaining-=len;
			}
		}
		socket.getOutputStream().flush();
	}

	@Override
	public void shutdown() throws IOException {
		// of course we do NOT close any streams here
		sendData(-1, null, -1);
	}
	
}
