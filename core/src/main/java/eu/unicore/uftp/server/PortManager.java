package eu.unicore.uftp.server;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * provide server sockets within a given port range
 *
 * @author schuller
 */
public class PortManager {

	private final int lowerBound;
	private final int upperBound;
	private boolean[] map;
	private int offset = 0;

	public PortManager() {
		this(0, 0);
	}
	
	public PortManager(int lowerBound, int upperBound) {
		if(lowerBound<0 || upperBound<0 || lowerBound>upperBound){
			throw new IllegalArgumentException("Invalid port range "+lowerBound+":"+upperBound);
		}
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	public ServerSocket getServerSocket() throws IOException {
		int port = 0;
		if(upperBound>lowerBound){
			port = getPort();
		}
		return new ServerSocket(port);
	}
	
	public void free(ServerSocket s) throws IOException {
		int port = s.getLocalPort();
		s.close();
		if(upperBound>lowerBound){
			freePort(port);
		}
	}

	synchronized void freePort(int port){
		int pos = port-lowerBound;
		map[pos]=false;
	}

	synchronized int getPort() throws IOException {
		int count = upperBound - lowerBound + 1;
		if(map == null){
			map = new boolean[count];
		}
		for(int i = 0; i<count; i++){
			int thisOffset = offset;
			boolean taken = map[thisOffset];
			offset++;
			if(lowerBound+offset>upperBound)offset=0;
			if(!taken){
				map[thisOffset]=true;
				return lowerBound+thisOffset;
			}
			if(offset>upperBound)offset=lowerBound;
		}
		throw new IOException("Too many connections, try again later!");
	}

	public int getLowerBound() {
		return lowerBound;
	}

	public int getUpperBound() {
		return upperBound;
	}

	public synchronized int getFreePorts(){
		int count = upperBound - lowerBound + 1;
		if(count>1){
			if(map == null){
				map = new boolean[count];
			}
			int free = 0;
			for(int i = 0; i<count; i++){
				if(!map[i])free++;
			}
			return free;
		}
		else return Integer.MAX_VALUE;
	}
	
}
