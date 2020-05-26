package eu.unicore.uftp.dpc;

import org.junit.BeforeClass;

import eu.unicore.uftp.jparss.PConfig;

public class TestFiletransferClientServerMultiStreamNoThreads extends TestFiletransferClientServerMultiStream {

	@BeforeClass
	public static void setUp()throws Exception{
		PConfig.usethreads=false;
	}

}