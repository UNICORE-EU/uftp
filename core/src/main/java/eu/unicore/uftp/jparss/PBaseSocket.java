/**************************************************************************
 * Copyright (c)   2001    Southeastern Universities Research Association,
 *                         Thomas Jefferson National Accelerator Facility
 *
 * This software was developed under a United States Government license
 * described in the NOTICE file included as part of this distribution.
 *
 * Jefferson Lab HPC Group, 12000 Jefferson Ave., Newport News, VA 23606
 **************************************************************************
 */
package eu.unicore.uftp.jparss;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.dpc.Utils.EncryptionAlgorithm;

public class PBaseSocket extends Socket {
	
	public PBaseSocket(byte[] key, boolean compress, EncryptionAlgorithm algo){
		super();
		this.key = key;
		this.algo = algo;
		this.compress = compress;
	}

	final EncryptionAlgorithm algo;

	final byte[] key;

	final boolean compress;
	
	/**
	 * Internal ID number assigned by server
	 */
	protected int id_ = 0;

	/**
	 * Internal stream sockets.
	 */
	protected Socket[] sockets_ = null;

	/**
	 * Number of streams.
	 */
	protected int numStreams_ = 0;

	/**
	 * Returns an input stream for this socket.
	 */
	public InputStream getInputStream() throws IOException {
		InputStream[] tinputs = new InputStream[numStreams_];
		for (int i = 0; i < numStreams_; i++){
			InputStream source=sockets_[i].getInputStream();
			tinputs[i] = key!=null? Utils.getDecryptStream(source, key, algo) : source;
			if(compress){
				tinputs[i] = Utils.getDecompressStream(tinputs[i]);
			}
		}
		return new PInputStream(tinputs);
	}

	/**
	 * Returns an output stream for this socket.
	 */
	public OutputStream getOutputStream() throws IOException {
		// set up parallel data streams
		OutputStream[] toutputs = new OutputStream[numStreams_];
		for (int i = 0; i < numStreams_; i++){
			OutputStream sink=sockets_[i].getOutputStream();
			toutputs[i] = key!=null ? Utils.getEncryptStream(sink, key, algo) : sink;
			if(compress){
				toutputs[i] = Utils.getCompressStream(toutputs[i]);
			}
		}
		return new POutputStream(toutputs);
	}

	/**
	 * Returns the address to which the socket is connected.
	 */
	public InetAddress getInetAddress() {
		throw new IllegalStateException("This is a parallel socket");
	}

	/**
	 * Gets the local address to which the socket is bound.
	 */
	public InetAddress getLocalAddress() {
		throw new IllegalStateException("This is a parallel socket");
	}

	/**
	 * Returns the remote port to which this socket is connected.
	 */
	public int getPort() {
		throw new IllegalStateException("This is a parallel socket");
	}

	/**
	 * Returns the local port to which this socket is bound.
	 */
	public int getLocalPort() {
		throw new IllegalStateException("This is a parallel socket");
	}

	/**
	 * Enable/disable TCP_NODELAY (disable/enable Nagle's algorithm).
	 */
	public void setTcpNoDelay(boolean on) throws SocketException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].setTcpNoDelay(on);
		}
	}

	/**
	 * Tests if TCP_NODELAY is enabled.
	 */
	public boolean getTcpNoDelay() throws SocketException {
		boolean nodelay=true;
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				nodelay=nodelay & sockets_[i].getTcpNoDelay();
		}
		return nodelay;
	}

	/**
	 * Enable/disable SO_LINGER with the specified linger time in seconds. The
	 * maximum timeout value is platform specific. The setting only affects
	 * socket close.
	 */
	public void setSoLinger(boolean on, int linger) throws SocketException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].setSoLinger(on, linger);
		}
	}

	/**
	 * Returns setting for SO_LINGER. -1 returns implies that the option is
	 * disabled. The setting only affects socket close.
	 */
	public int getSoLinger() throws SocketException {
		if (sockets_ != null && sockets_.length>0){
			return sockets_[0].getSoLinger();
		}
		return -1;
	}

	/**
	 * Enable/disable SO_TIMEOUT with the specified timeout, in milliseconds.
	 * With this option set to a non-zero timeout, a read() call on the
	 * InputStream associated with this Socket will block for only this amount
	 * of time. If the timeout expires, a java.io.InterruptedIOException is
	 * raised, though the Socket is still valid. The option must be enabled
	 * prior to entering the blocking operation to have effect. The timeout must
	 * be &gt; 0. A timeout of zero is interpreted as an infinite timeout.
	 */
	public void setSoTimeout(int timeout) throws SocketException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].setSoTimeout(timeout);
		}
	}

	/**
	 * Returns setting for SO_TIMEOUT. 0 returns implies that the option is
	 * disabled (i.e., timeout of infinity).
	 */
	public int getSoTimeout() throws SocketException {
		if (sockets_ != null && sockets_.length>0) {
			return sockets_[0].getSoTimeout();
		}
		return 0;
	}

	/**
	 * Sets the SO_SNDBUF option to the specified value for this Socket. The
	 * SO_SNDBUF option is used by the platform's networking code as a hint for
	 * the size to set the underlying network I/O buffers.
	 * 
	 * Increasing buffer size can increase the performance of network I/O for
	 * high-volume connection, while decreasing it can help reduce the backlog
	 * of incoming data. For UDP, this sets the maximum size of a packet that
	 * may be sent on this Socket.
	 * 
	 * Because SO_SNDBUF is a hint, applications that want to verify what size
	 * the buffers were set to should call getSendBufferSize(). Parameters:
	 */
	public void setSendBufferSize(int size) throws SocketException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].setSendBufferSize(size);
		}
	}

	/**
	 * Get value of the SO_SNDBUF option for this Socket, that is the buffer
	 * size used by the platform for output on this Socket.
	 */
	public int getSendBufferSize() throws SocketException {
		int res = 0;
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				res = Math.min(res, sockets_[i].getSendBufferSize());
		}
		return res;
	}

	/**
	 * Sets the SO_RCVBUF option to the specified value for this Socket. The
	 * SO_RCVBUF option is used by the platform's networking code as a hint for
	 * the size to set the underlying network I/O buffers.
	 * 
	 * Increasing buffer size can increase the performance of network I/O for
	 * high-volume connection, while decreasing it can help reduce the backlog
	 * of incoming data. For UDP, this sets the maximum size of a packet that
	 * may be sent on this Socket.
	 * 
	 * Because SO_RCVBUF is a hint, applications that want to verify what size
	 * the buffers were set to should call getReceiveBufferSize().
	 */
	public void setReceiveBufferSize(int size) throws SocketException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].setReceiveBufferSize(size);
		}
	}

	/**
	 * Gets the value of the SO_RCVBUF option for this Socket, that is the
	 * buffer size used by the platform for input on this Socket.
	 */
	public int getReceiveBufferSize() throws SocketException {
		int res = 0;
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				res = Math.min(res, sockets_[i].getReceiveBufferSize());
		}
		return res;
	}

	/**
	 * Enable/disable SO_KEEPALIVE.
	 */
	public void setKeepAlive(boolean on) throws SocketException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].setKeepAlive(on);
		}
	}

	/**
	 * Tests if SO_KEEPALIVE is enabled.
	 */
	public boolean getKeepAlive() throws SocketException {
		boolean keepalive=true;
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				keepalive=keepalive & sockets_[i].getKeepAlive();
		}
		return keepalive;
	}

	/**
	 * Places the input stream for this socket at "end of stream". Any data sent
	 * to the input stream side of the socket is acknowledged and then silently
	 * discarded. If you read from a socket input stream after invoking
	 * shutdownInput() on the socket, the stream will return EOF.
	 */
	public void shutdownInput() throws IOException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].shutdownInput();
		}
	}

	/**
	 * Disables the output stream for this socket. For a TCP socket, any
	 * previously written data will be sent followed by TCP's normal connection
	 * termination sequence. If you write to a socket output stream after
	 * invoking shutdownOutput() on the socket, the stream will throw an
	 * IOException.
	 */
	public void shutdownOutput() throws IOException {
		if (sockets_ != null) {
			for (int i = 0; i < numStreams_; i++)
				sockets_[i].shutdownOutput();
		}
	}

}
