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
 *      Global configuration parameters for parallel stream
 *
 * Author:  
 *      Jie Chen
 *      Jefferson Lab HPC Group
 *
 * Revision History:
 *   $Log: PConfig.java,v $
 *   Revision 1.7  2001/10/01 13:48:12  chen
 *   fix multiple get/put
 *
 *   Revision 1.6  2001/09/26 18:55:36  chen
 *   3rd party transfer
 *
 *   Revision 1.5  2001/09/10 18:15:27  chen
 *   Add transfer daemon port
 *
 *   Revision 1.4  2001/09/04 14:23:29  chen
 *   Add secure data transfer option
 *
 *   Revision 1.3  2001/08/15 18:40:36  chen
 *   add fileserver start up script and change location of cacert.pem
 *
 *   Revision 1.2  2001/06/22 23:28:28  chen
 *   Test on Windows
 *
 *   Revision 1.1  2001/06/14 15:51:42  chen
 *   Initial import of jparss
 *
 *
 *
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
	 * Debug flag
	 */
	public static boolean debug = false;

	/**
	 * Are we using threads or not.
	 */
	public static boolean usethreads = true;

	static {
		try{
			usethreads = Boolean.parseBoolean(Utils.getProperty("UFTP_jparss_usethreads", "true"));
		}catch(Exception e) {}
		try{
			debug = Boolean.parseBoolean(Utils.getProperty("UFTP_jparss_debug", "false"));
		}catch(Exception e) {}
	}

	/**
	 * Default server socket read timeout during handshakes.
	 */
	public static int handShakeTimeOut = 5000;

	/**
	 * Check whether root started this Java virtual machine.
	 */
	public static boolean privilegedJavaVM() {
		String user = System.getProperty("user.name");
		String filesep = System.getProperty("file.separator");
		if (filesep.equals("/") == true) {// Unix
			if (user.equals("root") == true)
				return true;
		} else { // windows cannot change uid so let java do it
			return false;
		}
		return false;
	}

	/**
	 * Parallel stream header length.
	 */
	public static final int pheaderlen = 16;

};
