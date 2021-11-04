package eu.unicore.uftp.server;

/**
 *
 * @author jj
 */
public final class UFTPCommands {

    public static final String AUTHFAIL = "Authorization FAILED";
    public static final String AUTHOK = "Authorization OK";
    
    public static final String END = "END";
    public static final String ENDCODE = "211 END";
    public static final String OK = "200 OK";
    public static final String RETR_OK = "150 OK";
    public static final String FEATURES_REQUEST = "FEAT";
    public static final String FEATURES_REPLY_LONG = "211-Features:";
    
    public static final String MLST = "MLST";
    public static final String MLSD = "MLSD";
    
    public static final String EPSV = "EPSV";
    public static final String PASV = "PASV";
    public static final String SYST = "SYST";
    public static final String APPE = "APPE";
    
    // keep-alive: leaves data connections open in a session
    public static final String KEEP_ALIVE = "KEEP-ALIVE";
    
    // server can unpack archive data from incoming data stream
    public static final String ARCHIVE = "ARCHIVE";
    
    // server supports limited session
    public static final String RESTRICTED_SESSION = "RESTRICTED_SESSION";

    
    // pseudo "feature" denoting that server supports protocol version 2 
    // and has accepted the login using the client secret
    public static final String PROTOCOL_VER_2_LOGIN_OK = "DPC2_LOGIN_OK";
    
    // pseudo feature denoting that server expects RFC compliant
    // "RANG startbyte endbyte"
    public static final String FEATURE_RFC_RANG = "RFC_RANG";
    
    
    public static final String USER_ANON = "USER anonymous";
    public static final String REQUEST_PASSWORD = "331 Please specify the password";
    public static final String FILE_ACTION_OK = "350 File action OK";
    public static final String LOGIN_SUCCESS = "230 Login successful";
    public static final String SYSTEM_REPLY= "215 Unix Type: L8";
    
    
    public static final String ERROR = "500";
    public static final String RANGSTREAM = "RANG STREAM";
    public static final String RESTSTREAM = "REST STREAM";
    public static final String MFMT = "MFMT"; // "modify fact modification time"
    
    public static final String CDUP= "CDUP"; 
    
    public static final String NEWLINE = "\r\n";    
    public static String SIZE_REPLY = "213";
    
}
