package eu.unicore.uftp.rsync;

import java.io.ByteArrayInputStream;
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
}
