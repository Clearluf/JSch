import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

public class LinuxConnetionHelper {
 
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LinuxConnetionHelper.class);
    private static final int TIME_OUT = 5*60*1000; //���ó�ʱΪ5����
    private static final Map<String,Session> cache = new HashMap<>(); //session����
 
    public static SessionMonitor sessionMonitor;
    
    public static void main(String[] args) {
		try {
			Session ss=connect("47.94.171.179", "root", "L1442826l", 22);
			Vector files=listFiles(ss, "/var");
			for(int i=0;i<files.size();i++) {
				System.out.println(files.get(i));
				
			}
			
		} catch (JSchException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    
    
    
    
    
    /**
     * ���� ���ӣ����ֶ��ر�
     * @param host
     * @param userName
     * @param password
     * @param port
     * @throws JSchException
     */
    public static Session connect(String host,String userName,String password,int port) throws JSchException {
        //��������
        JSch jsch = new JSch();
        //�����Ự
        Session session = jsch.getSession(userName,host, port);
        //��������
        session.setPassword(password);
        //������Ϣ
        Properties config = new Properties();
        //���ò��ü��hostKey
        //������óɡ�yes����ssh�Ͳ����Զ��Ѽ�������ܳ׼��롰$HOME/.ssh/known_hosts���ļ���
        //����һ����������ܳ׷����˱仯���;ܾ����ӡ�
        config.setProperty("StrictHostKeyChecking", "no");
//        //Ĭ��ֵ�� ��yes�� �˴�����������SFTP��������DNS���������⣬���UseDNS����Ϊ��no��
//        config.put("UseDNS", "no");
        session.setConfig(config);
        //����ʱ��
        session.setTimeout(TIME_OUT);
        //��������
        session.connect();
 
        return session;
    }
 
    /**
     * ����  ���ӣ������ֶ��ر�
     * @param host
     * @param userName
     * @param password
     * @param port
     * @throws JSchException
     */
    public static Session longConnect(String host,String userName,String password,int port) throws JSchException {
        String key = host + userName + password + port;
        Session session = cache.get(key);
        if (session == null){
            //��������
            JSch jsch = new JSch();
            //�����Ự
            session = jsch.getSession(userName,host, port);
            //��������
            session.setPassword(password);
            //������Ϣ
            Properties config = new Properties();
            //���ò��ü��hostKey
            //������óɡ�yes����ssh�Ͳ����Զ��Ѽ�������ܳ׼��롰$HOME/.ssh/known_hosts���ļ���
            //����һ����������ܳ׷����˱仯���;ܾ����ӡ�
            config.setProperty("StrictHostKeyChecking", "no");
//        //Ĭ��ֵ�� ��yes�� �˴�����������SFTP��������DNS���������⣬���UseDNS����Ϊ��no��
//        config.put("UseDNS", "no");
            session.setConfig(config);
            //����ʱ��
            //session.setTimeout(TIME_OUT);
            //�������ӣ���ʱ����linux���½�һ�����̣�timeout ������������̣�ֻ�е���disconnect()�Ż�����˽���
            session.connect();
            cache.put(key,session);
        }else{
            //�ж�session�Ƿ�ʧЧ
            if (testSessionIsDown(key)){
                //session is down
                //session ʧȥ���������
                closeLongSessionByKey(key);
                //��������session
                session = longConnect(host, userName, password, port);
            }
        }
        //������ʱ��
        createSessionMonitor();
        return session;
    }
 
    /**
     * ���� session
     * @param session
     */
    public static void close(Session session){
        if (session != null){
            session.disconnect();
        }
    }
 
    /**
     * ����session�Ƿ�ʧЧ
     * @param key
     * @return
     */
    public static boolean testSessionIsDown(String key){
        Session session = cache.get(key);
        if (session == null){
            return true;
        }
        ChannelExec channelExec = null;
        try {
            channelExec = openChannelExec(session);
            channelExec.setCommand("true");
            channelExec.connect();
            return false;
        }catch (Throwable e){
            //session is down
            return true;
        }finally {
            if (channelExec != null){
                channelExec.disconnect();
            }
        }
    }
    /**
     * ���� session
     * @param key
     */
    public static synchronized void closeLongSessionByKey(String key){
        Session session = cache.get(key);
        if (session != null){
            session.disconnect();
            cache.remove(key);
        }
    }
 
    /**
     * ���� session
     * @param session
     */
    public static void closeLongSessionBySession(Session session){
        Iterator iterator = cache.keySet().iterator();
        while (iterator.hasNext()){
            String key = (String)iterator.next();
            Session oldSession = cache.get(key);
            if (session == oldSession){
                session.disconnect();
                cache.remove(key);
                return;
            }
        }
    }
    /**
     * ����һ�� sftp ͨ������������
     * @param session
     * @return
     * @throws Exception
     */
    public static ChannelSftp openChannelSftp(Session session) throws Exception
    {
        ChannelSftp channelSftp = (ChannelSftp)session.openChannel("sftp");
        channelSftp.connect();
        return channelSftp;
    }
 
    /**
     * �ر� sftp ͨ��
     * @param channelSftp
     * @throws Exception
     */
    public static void closeChannelSftp(ChannelSftp channelSftp)
    {
        if (channelSftp != null){
            channelSftp.disconnect();
        }
    }
 
    /**
     * �����ļ�
     * @param remoteFile
     *          Զ�̷��������ļ�·��
     * @param localPath
     *          ��Ҫ�����ļ��ı���·��
     * @throws IOException
     * @throws SftpException
     */
    public static void downloadFile(Session session , String remoteFile, String localPath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            String remoteFilePath = remoteFile.substring(0, remoteFile.lastIndexOf("/"));
            String remoteFileName = remoteFile.substring(remoteFile.lastIndexOf("/") + 1,remoteFile.length());
            if(localPath.charAt(localPath.length() - 1) != '/'){
                localPath += '/';
            }
            File file = new File(localPath + remoteFileName);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            OutputStream output = new FileOutputStream(file);
            try {
                channelSftp.cd(remoteFilePath);
                log.info("Զ�̷�����·����" + remoteFilePath);
                log.info("��������·����" + localPath + remoteFileName);
                SftpATTRS attrs = channelSftp.lstat(remoteFile);
                channelSftp.get(remoteFile, output, new FileSftpProgressMonitor(attrs.getSize()));
            }catch (Exception e){
                throw e;
            }finally {
                output.flush();
                output.close();
            }
        }catch (Exception e){
            throw e;
        }finally {
            closeChannelSftp(channelSftp);
        }
    }
 
    /**
     * �ϴ��ļ�
     * @param localFile
     *          �����ļ�·��
     * @param remotePath
     *          Զ�̷�����·��
     * @throws IOException
     * @throws SftpException
     */
    public static void uploadFile(Session session,String localFile,String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        String remoteFileName = localFile.substring(localFile.lastIndexOf("/") + 1, localFile.length());
        File file = new File(localFile);
        final InputStream input = new FileInputStream(file);
        try {
            channelSftp.cd(remotePath);
        }catch (SftpException e){
            String tempPath = null;
            try {
                tempPath = remotePath.substring(0, remotePath.lastIndexOf("/"));
                channelSftp.cd(tempPath);
            }catch (SftpException e1){
                channelSftp.mkdir(tempPath);
            }
            channelSftp.mkdir(remotePath);
            channelSftp.cd(remotePath);
        }
        log.info("Զ�̷�����·����" + remotePath);
        log.info("�����ϴ�·����" + localFile);
        try {
            channelSftp.put(input, remoteFileName, new FileSftpProgressMonitor(file.length()));
        }catch (Exception e){
            throw e;
        }finally {
            input.close();
            closeChannelSftp(channelSftp);
        }
    }
 
    /**
     * �ļ��ϴ�
     * @param session
     * @param inputStream
     * @param fileName
     * @param remotePath
     * @throws Exception
     */
    public static void uploadFile(Session session,InputStream inputStream,String fileName,String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        channelSftp.cd(remotePath);
        log.info("Զ�̷�����·����" + remotePath);
        try {
            channelSftp.put(inputStream, fileName,new FileSftpProgressMonitor(inputStream.available()));
        }catch (Exception e){
            throw e;
        }finally {
            inputStream.close();
            closeChannelSftp(channelSftp);
        }
    }
 
    /**
     * ��ȡԶ�̷������ļ��б�
     * @param session
     * @param remotePath
     *          Զ�̷�����·��
     * @return
     * @throws SftpException
     */
    public static Vector listFiles(Session session,String remotePath) throws Exception {
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            Vector vector = channelSftp.ls(remotePath);
            return vector;
        }catch (Exception e){
            throw e;
        }finally {
            closeChannelSftp(channelSftp);
        }
    }
 
    /**
     * ɾ���ļ�
     * @param session
     * @param remotePath
     * @param fileName
     * @throws Exception
     */
    public static void removeFile(Session session,String remotePath,String fileName) throws Exception{
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            channelSftp.cd(remotePath);
            channelSftp.rm(fileName);
        }catch (Exception e){
            throw e;
        }finally {
            closeChannelSftp(channelSftp);
        }
    }
 
    /**
     * ɾ���ļ���
     * @param session
     * @param remotePath
     * @throws Exception
     */
    public static void removeDir(Session session,String remotePath) throws Exception{
        ChannelSftp channelSftp = openChannelSftp(session);
        try {
            if (remotePath.lastIndexOf("/") == remotePath.length() - 1){
                remotePath = remotePath.substring(0,remotePath.length() - 1);
            }
            String parentDir = remotePath.substring(0,remotePath.lastIndexOf("/") + 1);
            String rmDir = remotePath.substring(remotePath.lastIndexOf("/") + 1,remotePath.length());
            channelSftp.cd(parentDir);
            channelSftp.rmdir(rmDir);
        }catch (Exception e){
            throw e;
        }finally {
            closeChannelSftp(channelSftp);
        }
    }
 
    /**
     * �½�һ�� exec ͨ��
     * @param session
     * @return
     * @throws JSchException
     */
    public static ChannelExec openChannelExec(Session session) throws JSchException {
        ChannelExec channelExec = (ChannelExec)session.openChannel("exec");
        return channelExec;
    }
 
    /**
     * �ر� exec ͨ��
     * @param channelExec
     */
    public static void closeChannelExec(ChannelExec channelExec){
        if (channelExec != null){
            channelExec.disconnect();
        }
    }
 
    /**
     * ִ�� �ű�
     * @param session
     * @param cmd
     *          ִ�� .sh �ű�
     * @param charset
     *          �ַ���ʽ
     * @return
     * @throws IOException
     * @throws JSchException
     */
    public static String[] execCmd(Session session,String cmd,String charset) throws Exception{
        //��ͨ��
        ChannelExec channelExec = openChannelExec(session);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        channelExec.setCommand(cmd);
        channelExec.setOutputStream(out);
        channelExec.setErrStream(error);
        channelExec.connect();
        //ȷ���ܹ�ִ����ɼ���Ӧ��������
        Thread.sleep(10000);
        String[] msg = new String[2];
        msg[0] = new String(out.toByteArray(),charset);
        msg[1] = new String(error.toByteArray(),charset);
        out.close();
        error.close();
        //�ر�ͨ��
        closeChannelExec(channelExec);
        return msg;
    }
 
    /**
     * ����һ������ʽ�� shell ͨ��
     * @param session
     * @return
     * @throws JSchException
     */
    public static ChannelShell openChannelShell(Session session) throws JSchException{
        ChannelShell channelShell = (ChannelShell)session.openChannel("shell");
        return channelShell;
    }
 
    /**
     * �ر� shell ͨ��
     * @param channelShell
     */
    public static void closeChannelShell(ChannelShell channelShell){
        if (channelShell != null){
            channelShell.disconnect();
        }
    }
 
    /**
     * ִ������
     * @param cmds
     *          �������
     * @param session
     * @param timeout
     *          ���ӳ�ʱʱ��
     * @param sleepTimeout
     *          �̵߳ȴ�ʱ��
     * @return
     * @throws Exception
     */
    public static String execShellCmd(String[] cmds,Session session,int timeout,int sleepTimeout) throws Exception {
        //��ͨ��
        ChannelShell channelShell = openChannelShell(session);
        //�������������
        PipedOutputStream pipedOut = new PipedOutputStream();
        ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
        channelShell.setInputStream(new PipedInputStream(pipedOut));
        channelShell.setOutputStream(errorOut);
        channelShell.connect(timeout);
        for (String cmd : cmds){
            pipedOut.write(cmd.getBytes("UTF-8"));
            //�߳����ߣ���ִ֤��������ܹ���ʱ������Ӧ����
            Thread.sleep(sleepTimeout);
 
        }
        String msg = new String(errorOut.toByteArray(),"UTF-8");
        log.info(msg);
        pipedOut.close();
        errorOut.close();
        //�ر�ͨ��
        closeChannelShell(channelShell);
        return msg;
    }
 
    /**
     * ������ʱ�������timeout �� session ����
     */
    public static void createSessionMonitor(){
        if (sessionMonitor == null){
            synchronized (SessionMonitor.class){
                if (sessionMonitor == null){
                    //��ʱ������ʱ���ʧЧ��session
                    sessionMonitor = new SessionMonitor();
                    sessionMonitor.start();
                }
            }
        }
    }
    /**
     * ����ļ�����
     */
   static class FileSftpProgressMonitor extends TimerTask implements SftpProgressMonitor{
 
        private long progressInterval = 5 * 1000; // Ĭ�ϼ��ʱ��Ϊ5��
 
        private boolean isEnd = false; // ��¼�����Ƿ�ֹͣ
 
        private long transfered; // ��¼�Ѵ���������ܴ�С
 
        private long fileSize; // ��¼�ļ��ܴ�С
 
        private Timer timer; // ��ʱ������
 
        private boolean isScheduled = false; // ��¼�Ƿ�������timer��ʱ��
 
       private NumberFormat df = NumberFormat.getInstance(); //��ʽ��
 
        public FileSftpProgressMonitor(long fileSize){
            this.fileSize = fileSize;
        }
 
        @Override
        public void run() {
            if (!isEnd()){
                long transfered = getTransfered();
                if (transfered != fileSize){
                    log.info("Current transfered: " + transfered + " bytes");
                    sendProgressMessage(transfered);
                }else{
                    log.info("transfered end.");
                    setIsEnd(true);
                }
            }else {
                log.info("Transfering done. Cancel timer.");
                stop(); // ���Ǵ���ֹͣ��ֹͣtimer��ʱ��
            }
        }
 
        /**
         * ��ʱ���ر�
         */
        public void stop() {
            log.info("Try to stop progress monitor.");
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
                isScheduled = false;
            }
            log.info("Progress monitor stoped.");
        }
 
        /**
         * ��ʱ������
         */
        public void start() {
            log.info("Try to start progress monitor.");
            if (timer == null) {
                timer = new Timer();
            }
            timer.schedule(this, 1000, progressInterval);
            isScheduled = true;
            log.info("Progress monitor started.");
        }
 
        /**
         * �������
         * @param transfered
         */
        private void sendProgressMessage(long transfered) {
            if (fileSize != 0) {
                double d = ((double)transfered * 100)/(double)fileSize;
                log.info("Sending progress message: " + df.format(d) + "%");
            } else {
                log.info("Sending progress message: " + transfered);
            }
        }
 
        @Override
        public void init(int i, String s, String s1, long l) {
            log.info("transfering start.");
        }
 
        @Override
        public boolean count(long l) {
            if (isEnd()){
                return false;
            }
            if (!getIsScheduled()){
                start();
            }
            add(l);
            return true;
        }
        @Override
        public void end() {
            setIsEnd(false);
            log.info("transfering end.");
        }
 
 
        private synchronized void add(long count) {
            transfered = transfered + count;
        }
 
        public synchronized  boolean isEnd() {
            return isEnd;
        }
 
        public synchronized  void setIsEnd(boolean isEnd) {
            this.isEnd = isEnd;
        }
 
        public synchronized  long getTransfered() {
            return transfered;
        }
 
        public synchronized  void setTransfered(long transfered) {
            this.transfered = transfered;
        }
 
 
        public synchronized  boolean getIsScheduled() {
            return isScheduled;
        }
 
        public synchronized  void setIsScheduled(boolean isScheduled) {
            this.isScheduled = isScheduled;
        }
 
 
    }
 
    /**
     * ��ʱ������ʱ���ʧЧ��session
     */
    static class SessionMonitor extends TimerTask{
 
        private Timer timer; // ��ʱ������
        private long progressInterval = 30 * 1000; // Ĭ�ϼ��ʱ��Ϊ30��
 
        @Override
        public void run() {
            if (!cache.isEmpty()){
                Iterator iterator = cache.keySet().iterator();
                while (iterator.hasNext()){
                    String key = (String)iterator.next();
                    //���ʧЧsession
                    if (testSessionIsDown(key)){
                        closeLongSessionByKey(key);
                    }
                }
            }
        }
 
        public void start(){
            if (timer == null){
                timer = new Timer();
            }
            timer.schedule(this,1000,progressInterval);
        }
 
        public void stop(){
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
        }
    }
}