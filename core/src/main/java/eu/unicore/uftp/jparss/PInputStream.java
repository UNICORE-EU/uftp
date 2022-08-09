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
 *      Parallel Data Input Stream
 *
 * Author:  
 *      Jie Chen
 *      Jefferson Lab HPC Group
 *
 * Revision History:
 *   $Log: PInputStream.java,v $
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

public class PInputStream extends InputStream {
	/**
	 * internal list of streams
	 */
	private InputStream[] inputs_ = null;

	/**
	 * internal expected sequence number.
	 */
	private int seq_ = -1;

	/**
	 * All readers of this parallel stream.
	 */
	private PReader[] readers_ = null;

	/**
	 * 
	 * Flag to signal whether we are finished.
	 */
	private boolean done_ = false;

	/**
	 * Finished read count
	 */
	private int readCount_ = 0;

	/**
	 * Array of booleans holding internal read status for all readers.
	 */
	private boolean[] status_ = null;

	/**
	 * Array of integers holding number of bytes read for all readers.
	 */
	private int[] readLens_ = null;

	private static final AtomicInteger threadCount = new AtomicInteger(0);
	
	/**
	 * Default constructor Construct an empty parallel output stream.
	 */
	public PInputStream(InputStream[] streams) {
		super();

		int i;
		inputs_ = new InputStream[streams.length];
		status_ = new boolean[streams.length];
		readLens_ = new int[streams.length];

		for (i = 0; i < streams.length; i++) {
			inputs_[i] = streams[i];
			status_[i] = true;
			readLens_[i] = 0;
		}

		if (PConfig.usethreads == true) {
			Thread worker = null;

			readers_ = new PReader[inputs_.length];
			for (i = 0; i < inputs_.length; i++) {
				readers_[i] = new PReader(this, inputs_[i], i, inputs_.length);
				// fire this thread
				worker = new Thread(readers_[i]);
				worker.setName("ParallelReaderThread-"+threadCount.incrementAndGet());
				worker.start();
			}
		}
	}

	/**
	 * Closes this input stream and releases any system resources associated
	 * with the stream.
	 * 
	 */
	public void close() throws IOException {
		if (inputs_ == null) {
			throw new IOException("No internal input streams.");
		}

		byte[] tbuf = new byte[4];
		if (PConfig.usethreads == true) {
			int i;
			// wake up all readers
			done_ = true;
			for (i = 0; i < readers_.length; i++)
				readers_[i].set(tbuf, 0, 0);
		}

		try {
			for (int i = 0; i < inputs_.length; i++)
				inputs_[i].close();
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * set sequence number.
	 */
	public synchronized void setSeq(int seq) {
		seq_ = seq;
	}

	/**
	 * get sequence number.
	 */
	public synchronized int getSeq() {
		return seq_;
	}

	/**
	 * Reads the next byte of data from the input stream. The value byte is
	 * returned as an int in the range 0 to 255. If no byte is available because
	 * the end of the stream has been reached, the value -1 is returned. This
	 * method blocks until input data is available, the end of thestream is
	 * detected, or an exception is thrown.
	 * 
	 * This read only works if the input stream on the other end sends out one
	 * byte of data, otherwise IOException will be thrown.
	 * 
	 * Returns: the next byte of data, or -1 if the end of the stream is
	 * reached.
	 */
	public int read() throws IOException {
		int i;
		short magic, pos;
		int seq, totalnum, numtoread, expseq;
		int value;
		byte[] header = new byte[PConfig.pheaderlen];
		DataInputStream headerStream = new DataInputStream(
				new ByteArrayInputStream(header));
		DataInputStream istream = null;

		value = 0;
		for (i = 0; i < inputs_.length; i++) {
			istream = new DataInputStream(inputs_[i]);
			try {
				istream.readFully(header);
			} catch (EOFException ee) {
				System.err
				.println("PInput read: Output stream closed connection");
				throw new IOException("Connection closed");
			} catch (IOException e) {
				throw e;
			}

			try {
				magic = headerStream.readShort();
				pos = headerStream.readShort();
				seq = headerStream.readInt();
				totalnum = headerStream.readInt();
				numtoread = headerStream.readInt();
			} catch (IOException e) {
				throw e;
			}

			if (PConfig.debug == true)
				System.out.println("PInput read: Received header: magic: "
						+ Integer.toHexString(magic) + " stream position: "
						+ String.valueOf(pos) + " seq: " + String.valueOf(seq)
						+ " number to read: " + String.valueOf(numtoread));

			if (magic != PConfig.magic)
				throw new IOException("magic number mismatch");
			if (pos < 0 || pos >= inputs_.length)
				throw new IOException("Stream position mismatch");
			if (seq < 0)
				throw new IOException("Sequence number error");
			if (totalnum != 1)
				throw new IOException("Total byte number error");
			if (numtoread != 0 && numtoread != 1)
				throw new IOException("Number to read receiving error");
			// check sequence number
			if ((expseq = seq_) == -1)
				seq_ = seq;
			else if (expseq != seq)
				throw new IOException("Sequence number mismatch");

			if (numtoread == 1)
				value = inputs_[i].read();
		}
		seq_++;
		return value;
	}

	/**
	 * Reads some number of bytes from the input stream and stores them into the
	 * buffer array b. The number of bytes actually read is returned as an
	 * integer. This method blocks until input data is available, end of file is
	 * detected, or an exception is thrown.
	 * 
	 * If size of b is less than data size from input stream, an IOException
	 * will be thrown.
	 */
	public int read(byte[] b) throws IOException {
		int bytes = 0;
		try {
			bytes = read(b, 0, b.length);
		} catch (IOException e) {
			throw e;
		}
		return bytes;
	}

	/**
	 * Reads up to len bytes of data from the input stream into an array of
	 * bytes. An attempt is made to read as many as len bytes, but a smaller
	 * number may be read, possibly zero. The number of bytes actually read is
	 * returned as an integer.
	 * 
	 * This method blocks until input data is available, end of file is
	 * detected, or an exception is thrown.
	 * 
	 * The parameter len must be larger than the size of data being sent over,
	 * or an IOException is thrown.
	 * 
	 * @param b
	 *            - the data.
	 * @param off
	 *            - the start offset in the data.
	 * @param length
	 *            - the maximum number of bytes to read.
	 */
	public int read(byte[] b, int off, int length) throws IOException {
		if(PConfig.usethreads)return readMultiThreaded(b, off, length);

		int i, chunk;
		int toffset, tlen;
		short magic, pos;
		int seq, totalnum, numtoread, expseq;
		int value;
		byte[] header = new byte[PConfig.pheaderlen];
		DataInputStream istream = null;
		ByteArrayInputStream thstream = new ByteArrayInputStream(header);
		DataInputStream headerStream = new DataInputStream(thstream);

		value = 0;
		for (i = 0; i < inputs_.length; i++) {
			istream = new DataInputStream(inputs_[i]);

			try {
				istream.readFully(header);
			} catch (EOFException ee) {
				return -1;
			} catch (IOException e) {
				throw e;
			}
			// rewind the header stream
			thstream.reset();
			try {
				magic = headerStream.readShort();
				pos = headerStream.readShort();
				seq = headerStream.readInt();
				totalnum = headerStream.readInt();
				numtoread = headerStream.readInt();
			} catch (IOException e) {
				throw e;
			}

			if (PConfig.debug == true)
				System.out.println("PInput read: Reader["
						+ String.valueOf(i) + "] Received header: magic: "
						+ Integer.toHexString(magic) + " stream position: "
						+ String.valueOf(pos) + " seq: "
						+ String.valueOf(seq) + " number to read: "
						+ String.valueOf(numtoread));

			if (magic != PConfig.magic)
				throw new IOException("magic number mismatch");
			if (pos < 0 || pos >= inputs_.length)
				throw new IOException("Stream position mismatch");
			if (seq < 0)
				throw new IOException("Sequence number error");
			if (totalnum < 0 || totalnum > length)
				throw new IOException("Total byte number error: got "+totalnum);
			if (numtoread < 0)
				throw new IOException("Number to read receiving error");
			// check sequence number
			if ((expseq = getSeq()) == -1)
				setSeq(seq);
			else if (expseq != seq)
				throw new IOException("Sequence number mismatch");

			chunk = totalnum / inputs_.length;

			// calculate where to put input bytes inside user provided
			// buffer
			// offset for this stream
			toffset = off + pos * chunk;
			// how much space do we have
			if (i == inputs_.length - 1)
				tlen = length - i * chunk;
			else
				tlen = chunk;

			if (numtoread > tlen)
				throw new IOException("Not enough buffer size");

			if (PConfig.debug == true)
				System.out.println("Reader[" + String.valueOf(i)
						+ "] reads from " + String.valueOf(toffset)
						+ " with buffer size " + String.valueOf(tlen)
						+ " and tries to read " + String.valueOf(numtoread)
						+ " bytes");
			try {
				istream.readFully(b, toffset, numtoread);
			} catch (EOFException ee) {
				System.err.println(ee);
				throw new IOException("Unexpected EOF");
			} catch (IOException e) {
				throw e;
			}

			value += numtoread;
		}
		seq_++;
		return value;
	}

	protected int readMultiThreaded(byte[] b, int off, int length) throws IOException {
		int i;
		int value = 0;
		// reset all values
		resetVariables();

		// wake up all readers
		for (i = 0; i < readers_.length; i++)
			readers_[i].set(b, off, length);

		if (PConfig.debug == true)
			System.out.println("PInputStream is waking up all readers.");

		// this thread is going to sleep
		if (PConfig.debug == true)
			System.out
			.println("PInputStream is waiting for readers to finish.");

		waitReaders();

		if (PConfig.debug == true)
			System.out.println("PInputStream is finished reading\n");

		//check for reader's EOF
		boolean eof=true;
		for (i = 0; i < readers_.length; i++)
			eof&=readers_[i].isEOF();
		if(eof)return -1;
		
		for (i = 0; i < status_.length; i++) {
			if (status_[i] != true) {
				throw new IOException("Internal input stream error (Non-matching number of connections?)");
			}
			value += readLens_[i];
		}
		// increase sequence number
		seq_++;
		return value;
	}
	/**
	 * Reset variables before read.
	 */
	private void resetVariables() {
		int i;
		for (i = 0; i < status_.length; i++) {
			status_[i] = true;
			readLens_[i] = 0;
		}
		readCount_ = 0;
	}

	/**
	 * Wait for all readers to finish reading.
	 */
	private synchronized void waitReaders() {
		while (readCount_ < inputs_.length) {
			try {
				wait();
			} catch (InterruptedException e) {
				;
			}
		}
	}

	/**
	 * Update reader status by individual reader.
	 */
	public synchronized void readerStatus(int pos, boolean stat, int len) {
		if (pos >= 0 && pos < status_.length) {
			status_[pos] = stat;
			readLens_[pos] = len;
			readCount_++;
		}
		// if all writers finished writing, wake this stream
		if (readCount_ == status_.length)
			notify();
	}

	/**
	 * Check whether we are finished or not.
	 */
	public synchronized boolean finished() {
		return done_;
	}

}
