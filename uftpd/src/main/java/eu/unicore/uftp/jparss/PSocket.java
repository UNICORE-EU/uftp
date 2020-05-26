/**************************************************************************
 * Copyright (c)   2001    Southeastern Universities Research Association,
 *                         Thomas Jefferson National Accelerator Facility
 *
 * This software was developed under a United States Government license
 * described in the NOTICE file included as part of this distribution.
 *
 * Jefferson Lab HPC Group, 12000 Jefferson Ave., Newport News, VA 23606
 **************************************************************************
 *
 * Description:
 *      This is the socket a server socket returns
 *
 * Author:  
 *      Jie Chen
 *      Jefferson Lab HPC Group
 *
 * Revision History:
 *   $Log: PSocket.java,v $
 *   Revision 1.1  2001/06/14 15:51:42  chen
 *   Initial import of jparss
 *
 *
 *
 */
package eu.unicore.uftp.jparss;

import java.io.IOException;
import java.net.Socket;

public class PSocket extends PBaseSocket {

	/**
	 * Constructor. Creates an unconnected socket.
	 * @param key - encoded key for encryption/decryption. Set no <code>null</code> for no encryption
	 */
	public PSocket(byte[]key, boolean compress) {
		super(key,compress);
	}

	/**
	 * Set up this socket.
	 */
	public void init(int id, int numstreams) throws IOException {
		id_ = id;
		numStreams_ = numstreams;
		sockets_ = new Socket[numStreams_];
		// initialize all sockets to null
		for (int i = 0; i < numStreams_; i++)
			sockets_[i] = null;
	}

	/**
	 * return id number of this parallel stream socket
	 */
	public int getId() {
		return id_;
	}

	/**
	 * Add a socket to this parallel stream
	 */
	public synchronized void addSocketStream(Socket sock) {
		for (int i = 0; i < numStreams_; i++) {
			if (sockets_[i] == null) {
				sockets_[i] = sock;
				break;
			}
		}
	}

	/**
	 * Check whether client finished connection.
	 */
	public synchronized boolean clientConnectionDone() {
		for (int i = 0; i < numStreams_; i++) {
			if (sockets_[i] == null)
				return false;
		}
		return true;
	}

	/**
	 * Close socket.
	 */
	public void close() throws IOException {
		int i;

		try {
			if (sockets_ != null) {
				for (i = 0; i < numStreams_; i++)
					sockets_[i].close();
			}
		} catch (IOException e) {
			throw e;
		}
	}
}
