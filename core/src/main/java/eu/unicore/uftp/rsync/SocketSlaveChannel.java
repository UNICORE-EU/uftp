package eu.unicore.uftp.rsync;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

public class SocketSlaveChannel implements SlaveChannel {

	private final Socket socket;
	
	public SocketSlaveChannel(Socket socket){
		this.socket=socket;
	}

	@Override
	public void sendToMaster(List<Long> weakChecksums,
			List<byte[]> strongChecksums, int blocksize) throws IOException {
		DataOutputStream dos=new DataOutputStream(socket.getOutputStream());
		dos.writeInt(blocksize);
		int numBlocks=weakChecksums.size();
		dos.writeInt(numBlocks);	
		for(int i=0;i<numBlocks;i++){
			dos.writeLong(weakChecksums.get(i));
			byte[]cs=strongChecksums.get(i);
			if(cs.length!=16){
				// be paranoid
				throw new IllegalStateException("Strong checksum at index "+i+" has unexpected size "+cs.length);
			}
			dos.write(cs);
		}
		dos.flush();
	}

	@Override
	public RsyncData receive() throws IOException {
		DataInputStream dis=new DataInputStream(socket.getInputStream());
		final int index=dis.readInt();
		final long length=dis.readLong();
		
		final ReadableByteChannel channel=new ReadableByteChannel() {
			
			@Override
			public boolean isOpen() {
				return true;
			}
			
			@Override
			public void close() throws IOException {
			}
			
			byte[]buf=new byte[1024];
			long read=0;
			@Override
			public int read(ByteBuffer dst) throws IOException {
				int maxlen=Math.min(dst.remaining(),buf.length);
				maxlen=Math.min(maxlen, (int)(length-read));
				int len=socket.getInputStream().read(buf, 0, maxlen);
				if(len>=0){
					read+=len;
					dst.put(buf,0,len);
				}
				return len;
			}
		};
		return new RsyncData(length,channel,index);
	}

}
