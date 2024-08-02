package eu.unicore.uftp.rsync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class TestRollingChecksum {
	
	@Test
	public void testRolling()throws Exception{
		RollingChecksum r=new RollingChecksum();
		byte[]full=new byte[255];
		new Random().nextBytes(full);
		ByteArrayInputStream is=new ByteArrayInputStream(full);
		byte[]block=new byte[32];
		is.read(block);
		r.init(block);
		is.reset();
		is.skip(block.length);
		long c1 = 0;
		for(int i=0; i<61;i++){
			int x = is.read();
			c1 = r.update((byte)x);
		}
		long k = r.getK();
		long l = r.getL();
		is.reset();
		is.skip(k);
		is.read(block);
		long c2=r.reset(block, k, l);
		assertEquals(c1,c2);
	}

	@Test
	public void testRolling2()throws Exception{
		// block 1
		long c1 = Checksum.checksum(new byte[]{12,33,1,84},0,3);
		// block 3
		long c3 = Checksum.checksum(new byte[]{55,8,53,17}, 2*4, 2*4+3);
		
		RollingChecksum r = new RollingChecksum();
		long c_test1 = r.init(new byte[]{12,33,1,84});
		assertEquals(c_test1,c1);
		// block 2 does not match
		r.reset(new byte[]{0,11,13,5}, 4, 7);
		// update with individual 'correct') bytes
		long c_test3 = -1;
		for (byte b: new byte[] {55,8,53,17}) {
			c_test3 = r.update(b);
		}
		// now should be the same checksum as c3:
		assertEquals(c_test3,c3);
	}
	@Test
	public void testRolling3()throws Exception{
		// block 1
		long c1 = Checksum.checksum(new byte[]{-12,-33,-1,-84},0,3);
		// block 3
		long c3 = Checksum.checksum(new byte[]{-55,-8,-53,-17}, 2*4, 2*4+3);
		
		RollingChecksum r = new RollingChecksum();
		long c_test1 = r.init(new byte[]{-12,-33,-1,-84});
		assertEquals(c_test1,c1);
		// block 2 does not match
		r.reset(new byte[]{0,-11,-13,-5}, 4, 7);
		// update with individual 'correct') bytes
		long c_test3 = -1;
		for (byte b: new byte[] {-55,-8,-53,-17}) {
			c_test3 = r.update(b);
		}
		// now should be the same checksum as c3:
		assertEquals(c_test3,c3);
	}

	@Test
	public void testFunctions() throws Exception {
		var block = new byte[] { 1,2,3,4,5,6,7,8,9,10 };
		assertEquals(55,Checksum.a(block));
		block = new byte[258];
		for (int i=0; i<258; i++) {
			block[i] = (byte)255;
		}
		block[257] = 2;
		assertEquals(1,Checksum.a(block));
		
		block = new byte[] { 1,2,3,4,5,6,7,8,9,10 };
		assertEquals(275,Checksum.b(block,0, 10));
		assertEquals( 10 + (32 * 65536), Checksum.sum(10, 32));
	}

	@Test
	public void testComputeChecksums() throws Exception {
		var s = new Follower(new RandomAccessFile("src/test/resources/rsync-test-1.txt", "r"),
				null, "test", 512);
		s.computeChecksums();
		var result = new ArrayList<Long>();
		result.add(3670588062l);
		result.add(1824262269l);
		assertEquals(s.getWeakChecksums(), result);
	}

}
