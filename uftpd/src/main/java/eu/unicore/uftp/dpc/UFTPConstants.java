package eu.unicore.uftp.dpc;

public interface UFTPConstants {

	/**
	 * tag used as "filename" to indicate session mode, i.e. multifile and byte
	 * range support
	 */
	public static final String sessionModeTag = "___UFTP___MULTI___FILE___SESSION___MODE___";

	
	/**
	 * environment setting for a ":" separated list of patters UFTPD should NOT write to
	 * (used by UFTPD admins to protect certain files)
	 */
	public static final String ENV_UFTP_NO_WRITE = "UFTP_NO_WRITE";

	/**
	 * environment setting for a ":" separated list of files (relative to $HOME) that 
	 * UFTPD should read user public keys from 
	 */
	public static final String ENV_UFTP_KEYFILES = "UFTP_KEYFILES";
	
	
}
