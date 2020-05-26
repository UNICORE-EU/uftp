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
 *      Parallel Stream Individual Writer
 *
 * Author:  
 *      Jie Chen
 *      Jefferson Lab HPC Group
 *
 * Revision History:
 *   $Log: PWriter.java,v $
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

public class PWriter implements Runnable {
	
	// counts active writers - for testing that we do not leak resources
	public static final AtomicInteger activeWriters = new AtomicInteger(0);
		
	/**
	 * Internal buffer to allow multiple threads to access
	 */
	private byte[] buffer_ = null;

	/**
	 * offset and length information for a parallel write
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
	private OutputStream output_ = null;
	private DataOutputStream ostream_ = null;

	/**
	 * Position of this writer in the parallel stream.
	 */
	private int pos_ = 0;

	/**
	 * Parallel stream that contains this writer.
	 */
	private POutputStream parent_ = null;

	/**
	 * Constructor
	 * 
	 * @param parent
	 *            paralle stream containing this writer.
	 * @param stream
	 *            real outputstream associated with this writer.
	 * @param pos
	 *            position of this writer inside the parallel stream.
	 * @param numwriters
	 *            total number of writers of this parallel stream.
	 */
	public PWriter(POutputStream parent, OutputStream stream, int pos,
			int numwriters) {
		parent_ = parent;
		output_ = stream;
		pos_ = pos;
		num_ = numwriters;
		ostream_ = new DataOutputStream(output_);
	}

	/**
	 * Set up total buffer to write for this parallel stream.
	 */
	public synchronized void set(byte[] buf, int off, int length) {
		buffer_ = buf;
		offset_ = off;
		len_ = length;
		if (buffer_ != null)
			notify(); // wake up a writer that is waiting to write
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
	 * Writer writes to an output stream
	 */
	public synchronized void writeData() throws IOException {
		boolean doit = true;

		if (PConfig.debug == true)
			System.out.println("Writer[" + String.valueOf(pos_)
					+ "] is waiting");
		while (buffer_ == null) {
			try {
				wait();
			} catch (InterruptedException e) {
				doit = false;
			}
		}

		if (PConfig.debug == true)
			System.out
					.println("Writer[" + String.valueOf(pos_) + "] is awaken");
		if (parent_.finished() == false && doit == true && len_ > 0) {
			// now all writers should compete for resource fairly.
			// we cannot use nofifyAll to wake up all writers since
			// once all writers are awaken there is only one writer
			// gets monitor.
			try {
				doWrite();
			} catch (IOException e) {
				throw e;
			}
		}
	}

	/**
	 * Real work is carried out here.
	 */
	private void doWrite() throws IOException {
		int toffset, tlen, chunk;

		chunk = len_ / num_;

		// offset for this writer
		toffset = offset_ + pos_ * chunk;

		// how many bytes to write for this writer
		if (pos_ == num_ - 1)
			tlen = len_ - pos_ * chunk;
		else
			tlen = chunk;

		// write header information
		try {
			// write magic number
			ostream_.writeShort(PConfig.magic);
			// write individual stream number
			ostream_.writeShort((short) pos_);
			// write out seq number
			ostream_.writeInt(parent_.getSeq());
			// write out total number of bytes for all streams
			ostream_.writeInt(len_);
			// write number bytes to come
			ostream_.writeInt(tlen);

			// write data
			ostream_.write(buffer_, toffset, tlen);
			if (PConfig.debug == true)
				System.out.println("Writer " + String.valueOf(pos_)
						+ " writes from " + String.valueOf(toffset) + " with "
						+ String.valueOf(tlen) + " bytes and total bytes "
						+ String.valueOf(len_));
		} catch (IOException e) {
			if (PConfig.debug == true) {
				System.out.println("Writer " + String.valueOf(pos_)
						+ " got IOException");
				System.out.flush();
			}
			reset();
			throw e;
		}
		reset();
	}

	public void run() {
		activeWriters.incrementAndGet();
		boolean status = true;
		while (parent_.finished() != true) {
			status = true;
			try {
				writeData();
			} catch (IOException e) {
				status = false;
			}
			parent_.writerStatus(pos_, status);
		}
		activeWriters.decrementAndGet();
	}
}
