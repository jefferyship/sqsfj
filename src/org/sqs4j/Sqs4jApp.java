package org.sqs4j;

import com.sleepycat.je.*;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.DOMConfigurator;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * ����HTTPЭ�����������Դ�򵥶��з���.
 * User: wstone
 * Date: 2010-7-30
 * Time: 11:44:52
 */
public class Sqs4jApp implements Runnable {
  static final String VERSION = "1.3.1";
  static final String DB_CHARSET = "UTF-8";
  private final String CONF_NAME;  //�����ļ�
  private final String DB_PATH;  //���ݿ�Ŀ¼

  private org.slf4j.Logger _log = org.slf4j.LoggerFactory.getLogger(this.getClass());
  Sqs4jConf _conf;
  Charset _defaultCharset;

  static Lock _lock = new ReentrantLock();
  private Environment _env;
  Database _db;

  //ͬ�����̵�Scheduled
  private ScheduledExecutorService _scheduleSync = Executors.newSingleThreadScheduledExecutor();

  private Channel _channel;

  //��ʼ��Ŀ¼��Log4j
  static {
    try {
      File file = new File(System.getProperty("user.dir", ".") + "/conf/");
      if (!file.exists() && !file.mkdirs()) {
        throw new IOException("Can not create:" + System.getProperty("user.dir", ".") + "/conf/");
      }

      file = new File(System.getProperty("user.dir", ".") + "/db/");
      if (!file.exists() && !file.mkdirs()) {
        throw new IOException("Can not create:" + System.getProperty("user.dir", ".") + "/db/");
      }

      String logPath = System.getProperty("user.dir", ".") + "/conf/log4j.xml";
      if (logPath.toLowerCase().endsWith(".xml")) {
        DOMConfigurator.configure(logPath);
      } else {
        PropertyConfigurator.configure(logPath);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public static void main(String args[]) {
    @SuppressWarnings("unused")
    Sqs4jApp app = new Sqs4jApp(args);
  }

  @Override
  //��ʱ���ڴ��е�����д�����
  public void run() {
    try {
      _db.sync();
    } catch (Throwable thex) {
      //
    }
  }

  /**
   * ���캯��
   *
   * @param args
   */
  public Sqs4jApp(String args[]) {
    java.lang.Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        doStop2();
      }
    }));

    DB_PATH = System.getProperty("user.dir", ".") + "/db";
    CONF_NAME = System.getProperty("user.dir", ".") + "/conf/Sqs4jConf.xml";

    if (!this.doStart2()) {
      System.exit(-1);
    }

//    for (; ;) {
//      try {
//        java.util.concurrent.TimeUnit.SECONDS.sleep(10);
//      } catch (InterruptedException ex) {
//        Thread.currentThread().interrupt();
//        System.exit(0);
//      }
//    }

  }

  /**
   * ��HTTP Header���ҵ��ַ�������,û�з��ַ���null
   *
   * @param contentType
   * @return
   */
  private String getCharsetFromContentType(String contentType) {
    if (contentType == null) {
      return null;
    }
    int start = contentType.indexOf("charset=");
    if (start < 0) {
      return null;
    }
    String encoding = contentType.substring(start + 8);
    int end = encoding.indexOf(';');
    if (end >= 0) {
      encoding = encoding.substring(0, end);
    }
    encoding = encoding.trim();
    if ((encoding.length() > 2) && (encoding.startsWith("\"")) && (encoding.endsWith("\""))) {
      encoding = encoding.substring(1, encoding.length() - 1);
    }
    return (encoding.trim());
  }

  /**
   * ��HTTP��URL������������ҵ��ַ�������,û�з��ַ���null
   *
   * @param query
   * @return
   */
  private String getCharsetFromQuery(String query) {
    if (query == null) {
      return null;
    }
    int start = query.indexOf("charset=");
    if (start < 0) {
      return null;
    }
    String encoding = query.substring(start + 8);
    int end = encoding.indexOf('&');
    if (end >= 0) {
      encoding = encoding.substring(0, end);
    }
    encoding = encoding.trim();
    return encoding;
  }

  /**
   * ��HTTP��URL���������������Map
   *
   * @param query
   * @param charset
   * @return
   */
  private Map<String, String> makeParameters(String query, String charset) {
    Map<String, String> map = new HashMap<String, String>();
    if (query == null || charset == null) {
      return map;
    }

    String[] keyValues;
    keyValues = query.split("&");
    for (String keyValue : keyValues) {
      String[] kv = keyValue.split("=");
      if (kv.length == 2) {
        try {
          map.put(kv[0], URLDecoder.decode(kv[1], charset));
        } catch (UnsupportedEncodingException e) {
          //
        }
      }
    }

    return map;
  }

/* ��ȡ����д����ֵ */

  long httpsqs_read_putpos(String httpsqs_input_name) throws UnsupportedEncodingException {
    DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "putpos").getBytes(DB_CHARSET));
    DatabaseEntry value = new DatabaseEntry();

    OperationStatus status = _db.get(null, key, value, LockMode.DEFAULT);
    if (status == OperationStatus.SUCCESS) {
      return Long.parseLong(new String(value.getData(), DB_CHARSET));
    } else {
      return 0;
    }
  }

/* ��ȡ���ж�ȡ���ֵ */

  long httpsqs_read_getpos(String httpsqs_input_name) throws UnsupportedEncodingException {
    DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "getpos").getBytes(DB_CHARSET));
    DatabaseEntry value = new DatabaseEntry();

    OperationStatus status = _db.get(null, key, value, LockMode.DEFAULT);
    if (status == OperationStatus.SUCCESS) {
      return Long.parseLong(new String(value.getData(), DB_CHARSET));
    } else {
      return 0;
    }
  }

/* ��ȡ�������õ��������� */

  long httpsqs_read_maxqueue(String httpsqs_input_name) throws UnsupportedEncodingException {
    DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "maxqueue").getBytes(DB_CHARSET));
    DatabaseEntry value = new DatabaseEntry();

    OperationStatus status = _db.get(null, key, value, LockMode.DEFAULT);
    if (status == OperationStatus.SUCCESS) {
      return Long.parseLong(new String(value.getData(), DB_CHARSET));
    } else if (status == OperationStatus.NOTFOUND) {
      return 1000000;
    } else {
      return 0;
    }
  }


  /**
   * �������Ķ�������������ֵΪ���õĶ����������������ֵΪ0�����ʾ����ȡ����ȡ��ԭ��Ϊ��
   * ���õ����Ķ�������С�ڡ���ǰ����д��λ�õ㡰�͡���ǰ���ж�ȡλ�õ㡰�����ߡ���ǰ����д��λ�õ㡰С�ڡ���ǰ���еĶ�ȡλ�õ㣩
   *
   * @param httpsqs_input_name
   * @param httpsqs_input_num
   * @return
   */
  long httpsqs_maxqueue(String httpsqs_input_name, long httpsqs_input_num) throws UnsupportedEncodingException {
    long queue_put_value = httpsqs_read_putpos(httpsqs_input_name);
    long queue_get_value = httpsqs_read_getpos(httpsqs_input_name);

    /* ���õ����Ķ�������������ڵ��ڡ���ǰ����д��λ�õ㡰�͡���ǰ���ж�ȡλ�õ㡰�����ҡ���ǰ����д��λ�õ㡰������ڵ��ڡ���ǰ���ж�ȡλ�õ㡰 */
    if (httpsqs_input_num >= queue_put_value && httpsqs_input_num >= queue_get_value && queue_put_value >= queue_get_value) {
      DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "maxqueue").getBytes(DB_CHARSET));
      DatabaseEntry value = new DatabaseEntry(String.valueOf(httpsqs_input_num).getBytes(DB_CHARSET));

      OperationStatus status = _db.put(null, key, value);
      if (status == OperationStatus.SUCCESS) {
        _db.sync();  //ʵʱˢ�µ�����
        _log.info(String.format("�������ñ��޸�:(%s:maxqueue)=%d", httpsqs_input_name, httpsqs_input_num));

        return httpsqs_input_num;
      } else {
        return 0;
      }
    } else {
      return 0L;
    }
  }

  /**
   * ���ö��У�true��ʾ���óɹ�
   *
   * @param httpsqs_input_name
   * @return
   */
  boolean httpsqs_reset(String httpsqs_input_name) throws UnsupportedEncodingException {
    DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "putpos").getBytes(DB_CHARSET));
    _db.delete(null, key);

    key.setData(String.format("%s:%s", httpsqs_input_name, "getpos").getBytes(DB_CHARSET));
    _db.delete(null, key);

    key.setData(String.format("%s:%s", httpsqs_input_name, "maxqueue").getBytes(DB_CHARSET));
    _db.delete(null, key);

    _db.sync();  //ʵʱˢ�µ�����

    return true;
  }

  /**
   * �鿴������������
   *
   * @param httpsqs_input_name
   * @param pos
   * @return
   */
  String httpsqs_view(String httpsqs_input_name, long pos) throws UnsupportedEncodingException {
    DatabaseEntry key = new DatabaseEntry(String.format("%s:%d", httpsqs_input_name, pos).getBytes(DB_CHARSET));
    DatabaseEntry value = new DatabaseEntry();

    OperationStatus status = _db.get(null, key, value, LockMode.DEFAULT);
    if (status == OperationStatus.SUCCESS) {
      return new String(value.getData(), DB_CHARSET);
    } else {
      return null;
    }
  }

  /**
   * �޸Ķ�ʱ�����ڴ����ݵ����̵ļ��ʱ�䣬���ؼ��ʱ�䣨�룩
   *
   * @param httpsqs_input_num
   * @return
   */
  int httpsqs_synctime(int httpsqs_input_num) {
    if (httpsqs_input_num >= 1) {
      _conf.syncinterval = httpsqs_input_num;
      try {
        _conf.store(CONF_NAME);
        _scheduleSync.shutdown();

        _scheduleSync = Executors.newSingleThreadScheduledExecutor();
        _scheduleSync.scheduleWithFixedDelay(this, 1, _conf.syncinterval, TimeUnit.SECONDS);
        _log.info("�����ļ����޸�:" + _conf.toString());
      } catch (Exception ex) {
        _log.error(ex.getMessage(), ex);
      }
    }

    return _conf.syncinterval;
  }

  /**
   * ��ȡ���Ρ�����С������Ķ���д���
   *
   * @param httpsqs_input_name
   * @return
   */
  long httpsqs_now_putpos(String httpsqs_input_name) throws UnsupportedEncodingException {
    long maxqueue_num = httpsqs_read_maxqueue(httpsqs_input_name);
    long queue_put_value = httpsqs_read_putpos(httpsqs_input_name);
    long queue_get_value = httpsqs_read_getpos(httpsqs_input_name);

    DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "putpos").getBytes(DB_CHARSET));
    /* ����д��λ�õ��1 */
    queue_put_value = queue_put_value + 1;
    if(queue_put_value > maxqueue_num && queue_get_value == 0) {  /* �������д��ID+1֮��׷�϶��ж�ȡID����˵����������������0���ܾ�����д�� */
      queue_put_value = 0;
    } if (queue_put_value == queue_get_value) { /* �������д��ID+1֮��׷�϶��ж�ȡID����˵����������������0���ܾ�����д�� */
      queue_put_value = 0;
    } else if (queue_put_value > maxqueue_num) { /* �������д��ID���������������������ö���д��λ�õ��ֵΪ1 */
      DatabaseEntry value = new DatabaseEntry("1".getBytes(DB_CHARSET));
      OperationStatus status = _db.put(null, key, value);
      if (status == OperationStatus.SUCCESS) {
        queue_put_value = 1;
      } else {
        throw new RuntimeException(status.toString());
      }
    } else { /* ����д��λ�õ��1���ֵ����д�����ݿ� */
      DatabaseEntry value = new DatabaseEntry(String.valueOf(queue_put_value).getBytes(DB_CHARSET));
      OperationStatus status = _db.put(null, key, value);
      if (status != OperationStatus.SUCCESS) {
        throw new RuntimeException(status.toString());
      }
    }

    return queue_put_value;
  }

  /**
   * ��ȡ���Ρ������С������Ķ��ж�ȡ�㣬����ֵΪ0ʱ����ȫ����ȡ���
   *
   * @param httpsqs_input_name
   * @return
   */
  long httpsqs_now_getpos(String httpsqs_input_name) throws UnsupportedEncodingException {
    long maxqueue_num = httpsqs_read_maxqueue(httpsqs_input_name);
    long queue_put_value = httpsqs_read_putpos(httpsqs_input_name);
    long queue_get_value = httpsqs_read_getpos(httpsqs_input_name);

    DatabaseEntry key = new DatabaseEntry(String.format("%s:%s", httpsqs_input_name, "getpos").getBytes(DB_CHARSET));
    /* ���queue_get_value��ֵ�����ڣ�����Ϊ1 */
    if (queue_get_value == 0 && queue_put_value > 0) {
      queue_get_value = 1;
      DatabaseEntry value = new DatabaseEntry("1".getBytes(DB_CHARSET));
      OperationStatus status = _db.put(null, key, value);
      if (status != OperationStatus.SUCCESS) {
        throw new RuntimeException(status.toString());
      }

      /* ������еĶ�ȡֵ�������У�С�ڶ��е�д��ֵ������У� */
    } else if (queue_get_value < queue_put_value) {
      queue_get_value = queue_get_value + 1;
      DatabaseEntry value = new DatabaseEntry(String.valueOf(queue_get_value).getBytes(DB_CHARSET));
      OperationStatus status = _db.put(null, key, value);
      if (status != OperationStatus.SUCCESS) {
        throw new RuntimeException(status.toString());
      }

      /* ������еĶ�ȡֵ�������У����ڶ��е�д��ֵ������У������Ҷ��еĶ�ȡֵ�������У�С������������ */
    } else if (queue_get_value > queue_put_value && queue_get_value < maxqueue_num) {
      queue_get_value = queue_get_value + 1;
      DatabaseEntry value = new DatabaseEntry(String.valueOf(queue_get_value).getBytes(DB_CHARSET));
      OperationStatus status = _db.put(null, key, value);
      if (status != OperationStatus.SUCCESS) {
        throw new RuntimeException(status.toString());
      }

      /* ������еĶ�ȡֵ�������У����ڶ��е�д��ֵ������У������Ҷ��еĶ�ȡֵ�������У��������������� */
    } else if (queue_get_value > queue_put_value && queue_get_value == maxqueue_num) {
      queue_get_value = 1;
      DatabaseEntry value = new DatabaseEntry("1".getBytes(DB_CHARSET));
      OperationStatus status = _db.put(null, key, value);
      if (status != OperationStatus.SUCCESS) {
        throw new RuntimeException(status.toString());
      }

      /* ���еĶ�ȡֵ�������У����ڶ��е�д��ֵ������У����������е�������ȫ������ */
    } else {
      queue_get_value = 0;
    }

    return queue_get_value;
  }

  public boolean doStart2() {
    try {
      try {
        _conf = Sqs4jConf.load(CONF_NAME);
      } catch (Exception ex) {
        _conf = new Sqs4jConf();
      }
      _conf.store(CONF_NAME);
      _defaultCharset = Charset.forName(_conf.defaultCharset);

      if (_env == null) {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setLocking(false);
        envConfig.setTransactional(false);
        envConfig.setCachePercent(70);
        _env = new Environment(new File(DB_PATH), envConfig);
      }

      if (_db == null) {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        dbConfig.setSortedDuplicates(false);
        dbConfig.setTransactional(false);
        _db = _env.openDatabase(null, "Sqs4j", dbConfig);
      }

      _scheduleSync.scheduleWithFixedDelay(this, 1, _conf.syncinterval, TimeUnit.SECONDS);

      if (_channel == null) {
        InetSocketAddress addr;
        if (_conf.bindAddress.equals("*")) {
          addr = new InetSocketAddress(_conf.bindPort);
        } else {
          addr = new InetSocketAddress(_conf.bindAddress, _conf.bindPort);
        }

        ThreadPoolExecutor workerExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        workerExecutor.setCorePoolSize(_conf.corePoolSize);
        workerExecutor.setMaximumPoolSize(_conf.maximumPoolSize);
        workerExecutor.setKeepAliveTime(60, TimeUnit.SECONDS);
        workerExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        ServerBootstrap _server = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        workerExecutor,
                        _conf.maximumPoolSize)
        );
        _server.setOption("tcpNoDelay", true);
        _server.setOption("reuseAddress", true);
        _server.setOption("soTimeout", 60000);
        _server.setOption("backlog", _conf.backlog);

        _server.setPipelineFactory(new HttpServerPipelineFactory(this));
        _channel = _server.bind(addr);

        _log.info(String.format("Sqs4J Server is listening on Address:%s Port:%d\n%s", _conf.bindAddress, _conf.bindPort, _conf.toString()));
      }
      return true;
    } catch (Throwable ex) {
      _log.error(ex.getMessage(), ex);
      return false;
    }
  }

  public boolean doStop2() {
    _scheduleSync.shutdown();

    if (_channel != null) {
      try {
        _log.info("Now stoping Sqs4J Server ......");
        ChannelFuture channelFuture = _channel.close();
        channelFuture.awaitUninterruptibly();
      } catch (Throwable ex) {
        _log.error(ex.getMessage(), ex);
      } finally {
        _channel = null;
        _log.info("Sqs4J Server is stoped!");
      }

    }

    if (_db != null) {
      try {
        _db.sync();
        _db.close();
      } catch (Throwable ex) {
      } finally {
        _db = null;
      }
    }

    if (_env != null) {
      try {
        _env.cleanLog();
        _env.close();
      } catch (Throwable ex) {
      } finally {
        _env = null;
      }
    }

    return true;
  }

}
