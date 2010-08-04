package org.sqs4j;

import org.simpleframework.xml.*;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.util.Dictionary;
import org.simpleframework.xml.util.Entry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.*;

/**
 * Sqs4J�����ļ�
 * User: wstone
 * Date: 2010-7-30
 * Time: 13:08:46
 */
@Root(name = "Sqs4j")
public class Sqs4jConf {
  @Element
  public String bindAddress = "*";  //������ַ,*��������

  @Element
  public int bindPort = 1218;  //�����˿�

  @Element
  public int backlog = 200;  //���� backlog ����

  @Element
  public int corePoolSize = 10;  //��С�����߳�

  @Element
  public int maximumPoolSize = 5000;  //������߳�

  @Element
  public String defaultCharset = "UTF-8";  //ȱʡ�ַ���

  @Element
  public int syncinterval = 10;  //ͬ���������ݵ����̵ļ��ʱ��

  public static Sqs4jConf load(String path) throws Exception {
    Persister serializer = new Persister();

    InputStreamReader reader = null;
    try {
      reader = new InputStreamReader(new FileInputStream(path), "UTF-8");
      Sqs4jConf conf = serializer.read(Sqs4jConf.class, reader);
      return conf;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ex) {
        }
      }
    }
  }

  public void store(String path) throws Exception {
    Persister serializer = new Persister(new Format(2, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    OutputStreamWriter writer = null;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(path), "UTF-8");
      serializer.write(this, writer);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ex) {
        }
      }
    }
  }

  @Override
  public String toString() {
    return "Sqs4jConf{" +
            "bindAddress='" + bindAddress + '\'' +
            ", bindPort=" + bindPort +
            ", backlog=" + backlog +
            ", corePoolSize=" + corePoolSize +
            ", maximumPoolSize=" + maximumPoolSize +
            ", defaultCharset='" + defaultCharset + '\'' +
            ", syncinterval=" + syncinterval +
            '}';
  }
}
