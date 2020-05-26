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
 *      Parallel Output Stream
 *
 * Author:  
 *      Jie Chen
 *      Jefferson Lab HPC Group
 *
 * Revision History:
 *   $Log: POutputStream.java,v $
 *   Revision 1.1  2001/06/14 15:51:42  chen
 *   Initial import of jparss
 *
 *
 *
 */
package eu.unicore.uftp.jparss;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public class POutputStream extends OutputStream {
	/**
	 * internal list of streams
	 */
	private OutputStream[] outputs_ = null;

	/**
	 * internal sequence number.
	 */
	private int seq_ = 0;

	/**
	 * internal flag of write status for each writer
	 */
	private boolean[] status_ = null;

	/**
	 * all writers of this parallel stream.
	 */
	private PWriter[] writers_ = null;

	/**
	 * Flag to signal whether we are finished.
	 */
	private boolean done_ = false;

	/**
	 * Finished write count
	 */
	private int writeCount_ = 0;

	private static final AtomicInteger threadCount = new AtomicInteger(0);
	
	/**
	 * Default constructor Construct an empty parallel output stream.
	 */
	public POutputStream(OutputStream[] streams) {
		super();

		int i;
		outputs_ = new OutputStream[streams.length];
		status_ = new boolean[streams.length];
		for (i = 0; i < streams.length; i++) {
			outputs_[i] = streams[i];
			status_[i] = true;
		}

		if (PConfig.usethreads == true) {
			Thread worker = null;
			writers_ = new PWriter[outputs_.length];
			for (i = 0; i < outputs_.length; i++) {
				writers_[i] = new PWriter(this, outputs_[i], i, outputs_.length);
				// fire this thread
				worker = new Thread(writers_[i]);
				worker.setName("ParallelWriterThread-"+threadCount.incrementAndGet());
				worker.start();
			}
		}

	}

	/**
	 * Closes this output stream and releases any system resources associated
	 * with this stream. The general contract of close is that it closes the
	 * output stream. A closed stream cannot perform output operations and
	 * cannot be reopened.
	 */
	public void close() throws IOException {
		if (outputs_ == null) {
			throw new IOException("No internal output streams.");
		}

		byte[] tbuf = new byte[4];
		if (PConfig.usethreads == true) {
			int i;
			// wake up all writers
			done_ = true;
			for (i = 0; i < writers_.length; i++)
				writers_[i].set(tbuf, 0, 0);
		}

		try {
			for (int i = 0; i < outputs_.length; i++)
				outputs_[i].close();
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * Check whether we are finished or not.
	 */
	public synchronized boolean finished() {
		return done_;
	}

	/**
	 * Update writer status by individual writer.
	 */
	public synchronized void writerStatus(int pos, boolean stat) {
		if (pos >= 0 && pos < status_.length) {
			status_[pos] = stat;
			writeCount_++;
		}
		// if all writers finished writing, wake this stream
		if (writeCount_ == status_.length)
			notify();

	}

	/**
	 * Flushes this output stream and forces any buffered output bytes to be
	 * written out. The general contract of flush is that calling it is an
	 * indication that, if any bytes previously written have been buffered by
	 * the implementation of the output stream, such bytes should immediately be
	 * written to their intended destination.
	 */
	public void flush() throws IOException {
		if (outputs_ == null) {
			throw new IOException("No internal output streams.");
		}
		try {
			for (int i = 0; i < outputs_.length; i++)
				outputs_[i].flush();
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * Writes the specified byte to this output stream. The general contract for
	 * write is that one byte is written to the output stream. The byte to be
	 * written is the eight low-order bits of the argument b. The 24 high-order
	 * bits of b are ignored.
	 * 
	 * This single byte will travel on command stream
	 */
	public void write(int b) throws IOException {
		DataOutputStream ostream = null;

		for (int i = 0; i < outputs_.length; i++) {
			ostream = new DataOutputStream(outputs_[0]);

			// write magic number
			ostream.writeShort(PConfig.magic);
			// write individual stream number
			ostream.writeShort((short) i);
			// write sequence number
			ostream.writeInt(seq_);
			// write total bytes for all streams
			ostream.writeInt(1);

			if (i == 0) {
				// write number bytes
				ostream.writeInt(1);
				ostream.writeByte(b);
			} else
				ostream.writeInt(0);
		}
		// increase sequence number
		seq_++;
	}

	/**
	 * Writes b.length bytes from the specified byte array to this output
	 * stream. The general contract for write(b) is that it should have exactly
	 * the same effect as the call write(b, 0, b.length).
	 * 
	 * This is a parallel write.
	 */
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	/**
	 * Writes len bytes from the specified byte array starting at offset off to
	 * this output stream. The general contract for write(b, off, len) is that
	 * some of the bytes in the array b are written to the output stream in
	 * order; element b[off] is the first byte written and b[off+len-1] is the
	 * last byte written by this operation.
	 * 
	 * If b is null, a NullPointerException is thrown. If off is negative, or
	 * len is negative, or off+len is greater than the length of the array b,
	 * then an IndexOutOfBoundsException is thrown.
	 * 
	 * This is a parallel write.
	 * 
	 * @param b
	 *            - the data.
	 * @param off
	 *            - the start offset in the data.
	 * @param len
	 *            - the number of bytes to write.
	 */
	public void write(byte[] b, int off, int len) throws IOException {
		if (PConfig.usethreads == true) {
			writeParallel(b, off, len);
		} else { // single thread
			writeSingleThreaded(b, off, len);
		}
		// increase sequence number
		seq_++;
	}

	protected void writeParallel(byte[]b,int off, int len)throws IOException{
		// reset some variables
		resetVariables();
		int i=0;
		// wake up all writer threads
		for (i = 0; i < writers_.length; i++)
			writers_[i].set(b, off, len);

		if (PConfig.debug == true)
			System.out.println("POutputStream is waking up all writers.");
		// this thread is going to sleep
		if (PConfig.debug == true)
			System.out
			.println("POutputStream is waiting for writers to finish.");
		// this thread is going to sleep
		waitWriters();

		if (PConfig.debug == true)
			System.out.println("POutputStream is finished write\n");

		for (i = 0; i < status_.length; i++) {
			if (status_[i] != true)
				throw new IOException("Internal stream write error");
		}
	}

	protected void writeSingleThreaded(byte[]b, int off, int len)throws IOException{
		DataOutputStream ostream = null;
		int toffset, tlen, chunk;
		int i=0;
		chunk = len / outputs_.length;
		for (i = 0; i < outputs_.length; i++) {
			ostream = new DataOutputStream(outputs_[i]);

			// offset for this stream
			toffset = off + i * chunk;

			// how many bytes to write for this writer
			if (i == outputs_.length - 1)
				tlen = len - i * chunk;
			else
				tlen = chunk;

			if (PConfig.debug == true)
			System.out.println("Writer " + String.valueOf(i)
					+ " writes from " + String.valueOf(toffset)
					+ " with " + String.valueOf(tlen) + " bytes");

			// write magic number
			ostream.writeShort(PConfig.magic);
			// write individual stream number
			ostream.writeShort((short) i);

			// write out seq number
			ostream.writeInt(seq_);

			// write out total number of bytes for all streams
			ostream.writeInt(len);

			// write out number bytes coming
			ostream.writeInt(tlen);

			// write real bytes
			ostream.write(b, toffset, tlen);
		}

	}
	/**
	 * Wait for writers to finish.
	 */
	private synchronized void waitWriters() {
		while (writeCount_ < outputs_.length) {
			try {
				wait();
			} catch (InterruptedException e) {
				;
			}
		}
	}

	/**
	 * Return sequence number.
	 */
	public synchronized int getSeq() {
		return seq_;
	}

	/**
	 * Reset variables before write.
	 */
	private void resetVariables() {
		int i;
		for (i = 0; i < status_.length; i++)
			status_[i] = true;
		writeCount_ = 0;
	}

}
