package eu.unicore.uftp.rsync;

import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class TestRollingChecksum {
	
	@Test
	public void testRolling()throws Exception{
		RollingChecksum r=new RollingChecksum();
		byte[]full=new byte[2048];
		new Random().nextBytes(full);
		ByteArrayInputStream is=new ByteArrayInputStream(full);
		byte[]block=new byte[512];
		is.read(block);
		r.init(block);

		for(int i=1; i<500;i++){
			is.reset();
			is.skip(i);
			is.read(block);
			long c1=r.update(i, 511+i, full[i], full[511+i]);
			//compute directly
			long c2=r.reset(block, i, 511+i);
			Assert.assertEquals(c1,c2);
		}
	}
	
	@Test
	public void testFunctions() throws Exception {
		var block = new byte[] { 1,2,3,4,5,6,7,8,9,10 };
		Assert.assertEquals(55,Checksum.a(block));
		block = new byte[258];
		for (int i=0; i<258; i++) {
			block[i] = (byte)255;
		}
		block[257] = 2;
		Assert.assertEquals(1,Checksum.a(block));
		
		block = new byte[] { 1,2,3,4,5,6,7,8,9,10 };
		Assert.assertEquals(275,Checksum.b(block,0, 10));
		
		Assert.assertEquals( 10 + (32 * 65536), Checksum.sum(10, 32));
		
	}

	@Test
	public void testComputeChecksums() throws Exception {
		var s = new Slave(new RandomAccessFile("src/test/resources/rsync-test-1.txt", "r"),
				null, "test", 512);
		s.computeChecksums();
		var result = new ArrayList<Long>();
		result.add(3670588062l);
		result.add(1824262269l);
		Assert.assertEquals(s.getWeakChecksums(), result);
	}

}
