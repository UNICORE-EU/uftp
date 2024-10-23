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

import eu.unicore.uftp.dpc.Utils;

public class PConfig {
	/**
	 * Magic number
	 */
	public static final short magic = (short) 0xcebf;

	/**
	 * Connection magic number.
	 */
	public static final int cmagic = (int) 0xcebfc011;

	/**
	 * Are we using threads or not.
	 */
	public static boolean usethreads = true;

	public static String USE_THREADS = "UFTP.multistream.usethreads";

	static {
		try{
			usethreads = Boolean.parseBoolean(Utils.getProperty(USE_THREADS, "true"));
		}catch(Exception e) {}
	}

	/**
	 * Default server socket read timeout during handshakes.
	 */
	public static int handShakeTimeOut = 5000;

	/**
	 * Parallel stream header length.
	 */
	public static final int pheaderlen = 16;

};
