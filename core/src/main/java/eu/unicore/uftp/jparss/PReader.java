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
 *      Parallel Stream Individual Reader
 *
 * Author:  
 *      Jie Chen
 *      Jefferson Lab HPC Group
 *
 * Revision History:
 *   $Log: PReader.java,v $
 *   Revision 1.1  2001/06/14 15:51:42  chen
 *   Initial import of jparss
 *
 *
 *
 */
package eu.unicore.uftp.jparss;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

public class PReader implements Runnable {
	
	// counts active readers - for testing that we do not leak resources
	public static final AtomicInteger activeReaders = new AtomicInteger(0);
	
	/**
	 * Internal buffer to allow multiple threads to access
	 */
	private byte[] buffer_ = null;

	/**
	 * offset and length information for a parallel read
	 */
	private int offset_ = 0;
	private int len_ = 0;

	/**
	 * number of parallel streams.
	 */
	public int num_ = 0;

	/**
	 * Internal real output stream
	 */
	private InputStream input_ = null;
	private DataInputStream istream_ = null;

	/**
	 * Position of this reader in the parallel stream.
	 */
	private int pos_ = 0;

	/**
	 * Parallel stream that contains this reader
	 */
	private PInputStream parent_ = null;

	/**
	 * Header buffer to hold initial header
	 */
	byte[] header_ = null;

	/**
	 * Data input stream to parse header
	 */
	ByteArrayInputStream headerArrayStream_ = null;
	DataInputStream headerStream_ = null;

	/**
	 * Constructor
	 * 
	 * @param parent
	 *            paralle stream containing this readr.
	 * @param stream
	 *            real inputstream associated with this readr.
	 * @param pos
	 *            position of this readr inside the parallel stream.
	 * @param numreadrs
	 *            total number of readrs of this parallel stream.
	 */
	public PReader(PInputStream parent, InputStream stream, int pos,
			int numreadrs) {
		parent_ = parent;
		input_ = stream;
		pos_ = pos;
		num_ = numreadrs;
		istream_ = new DataInputStream(input_);

		header_ = new byte[PConfig.pheaderlen];
		headerArrayStream_ = new ByteArrayInputStream(header_);
		headerStream_ = new DataInputStream(headerArrayStream_);
	}

	/**
	 * Set up total buffer to read for this parallel stream.
	 */
	public synchronized void set(byte[] buf, int off, int length) {
		buffer_ = buf;
		offset_ = off;
		len_ = length;
		if (buffer_ != null)
			notify(); // wake up a readr that is waiting to read
	}

	/**
	 * Reset buffer
	 */
	private void reset() {
		buffer_ = null;
		offset_ = 0;
		len_ = 0;
	}

	/**
	 * Readr reads to an input stream
	 */
	public synchronized int readData() throws IOException {
		boolean doit = true;
		int bytes = 0;

		if (PConfig.debug == true)
			System.out
					.println("Readr[" + String.valueOf(pos_) + "] is waiting");
		while (buffer_ == null) {
			try {
				wait();
			} catch (InterruptedException e) {
				doit = false;
			}
		}

		if (PConfig.debug == true)
			System.out.println("Readr[" + String.valueOf(pos_) + "] is awaken");

		if (parent_.finished() == false && doit == true && len_ > 0) {
			// now all readrs should compete for resource fairly.
			// we cannot use nofifyAll to wake up all readrs since
			// once all readrs are awaken there is only one readr
			// gets monitor.
			try {
				bytes = doRead();
			} catch (EOFException ee) {
				eof=true;
				reset();
				return 0;
			}
			reset();
		}
		return bytes;
	}

	/**
	 * Real work is carried out here.
	 */
	private int doRead() throws IOException, EOFException {
		short magic, pos;
		int seq, totalnum, numtoread, expseq;
		int toffset, tlen, chunk;

		magic = pos = (short) 0;
		seq = numtoread = expseq = 0;
		chunk = 0;

		// rewind the array stream to the beginning
		headerArrayStream_.reset();

		// read header
		try {
			istream_.readFully(header_);
		} catch (EOFException ee) {
			throw ee;
		} catch (IOException e) {
			System.err.println(e);
			throw e;
		}

		try {
			magic = headerStream_.readShort();
			pos = headerStream_.readShort();
			seq = headerStream_.readInt();
			totalnum = headerStream_.readInt();
			numtoread = headerStream_.readInt();
		} catch (IOException e) {
			throw e;
		}

		if (PConfig.debug == true)
			System.out
					.println("Reader[" + String.valueOf(pos_)
							+ "] Received header: magic: "
							+ Integer.toHexString(magic) + " stream position: "
							+ String.valueOf(pos) + " seq: "
							+ String.valueOf(seq) + " number to read: "
							+ String.valueOf(numtoread)
							+ " with total number of bytes "
							+ String.valueOf(totalnum));

		if (magic != PConfig.magic)
			throw new IOException("Bad magic number");
		if (pos < 0 || pos >= num_)
			throw new IOException("Parallel stream position error");
		if (seq < 0)
			throw new IOException("Negative sequence number");
		if (totalnum < 0 || totalnum > len_)
			throw new IOException("Total number of bytes error");
		if (numtoread < 0)
			throw new IOException("NUmber to read is negative");
		// check sequence number
		if ((expseq = parent_.getSeq()) == -1)
			parent_.setSeq(seq);
		else if (expseq != seq)
			throw new IOException("Sequence number mismatch");

		// now calculate chunk which is number of buffer size for each
		// stream
		chunk = totalnum / num_;

		// offset for this reader
		toffset = offset_ + pos * chunk;

		// how many bytes of buffer space for this reader
		if (pos == num_ - 1)
			tlen = len_ - pos * chunk;
		else
			tlen = chunk;

		if (PConfig.debug == true)
			System.out.println("Reader[" + String.valueOf(pos_)
					+ "] reads from " + String.valueOf(toffset)
					+ " with buffer size " + String.valueOf(tlen));

		if (numtoread > tlen)
			throw new IOException("Read buffer overflow");
		try {
			istream_.readFully(buffer_, toffset, numtoread);
		} catch (EOFException ee) {
			throw ee;
		} catch (IOException e) {
			System.err.println(e);
			throw e;
		}
		return numtoread;
	}

	public void run() {
		activeReaders.incrementAndGet();
		boolean status = true;
		int bytes = 0;

		while (parent_.finished() != true) {
			status = true;
			try {
				bytes = readData();
			} catch (IOException e) {
				status = false;
				bytes = 0;
			}
			parent_.readerStatus(pos_, status, bytes);
		}
		
		activeReaders.decrementAndGet();
	}
	
	private boolean eof=false;
	public boolean isEOF(){
		return eof;
	}
	
}
