package org.httpsqs.client;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public class HttpsqsClient {

  private String server;
  private int port;
  private String charset;
  private int connectTimeout = 0;
  private int readTimeout = 0;

  public HttpsqsClient(String server, int port, String charset, int connectTimeout, int readTimeout) {
    this.server = server;
    this.port = port;
    this.charset = charset;
    this.connectTimeout = connectTimeout;
    this.readTimeout = connectTimeout;
  }

  private String doprocess(String urlstr) {
    URL url = null;
    try {
      url = new URL(urlstr);
    } catch (MalformedURLException e) {
      return "The httpsqs server must be error";
    }

    BufferedReader reader = null;
    try {
      URLConnection conn = url.openConnection();
      conn.setConnectTimeout(connectTimeout);
      conn.setReadTimeout(readTimeout);
      conn.setUseCaches(false);
      conn.setDoOutput(false);
      conn.setDoInput(true);
      conn.setRequestProperty("Content-Type", "text/plain;charset=" + charset);

      conn.connect();

      reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset));
      String line = null;
      StringBuilder result = new StringBuilder();

      while ((line = reader.readLine()) != null) {
        result.append(line).append("\n");
      }
      return result.toString();
    } catch (IOException e) {
      return "Get data error";
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ex) {
        }
      }
    }
  }

  public String maxqueue(String queue_name, String num) {
    String result = null;
    try {
      String urlstr = "http://" + this.server + ":" + this.port + "/?name=" + URLEncoder.encode(queue_name, charset) + "&opt=maxqueue&num=" + num;
      result = this.doprocess(urlstr);
    } catch (UnsupportedEncodingException ex) {
    }
    return result;
  }

  public String reset(String queue_name) {
    String result = null;
    try {
      String urlstr = "http://" + this.server + ":" + this.port + "/?name=" + URLEncoder.encode(queue_name, charset) + "&opt=reset";

      result = this.doprocess(urlstr);
    } catch (UnsupportedEncodingException ex) {
    }
    return result;
  }

  public String view(String queue_name, String pos) {
    String result = null;
    try {
      String urlstr = "http://" + this.server + ":" + this.port + "/?charset=" + this.charset + "&name=" + URLEncoder.encode(queue_name, charset) + "&opt=view&pos=" + pos;

      result = this.doprocess(urlstr);
    } catch (UnsupportedEncodingException ex) {
    }
    return result;
  }

  public String status(String queue_name) {
    String result = null;
    try {
      String urlstr = "http://" + this.server + ":" + this.port + "/?name=" + URLEncoder.encode(queue_name, charset) + "&opt=status";

      result = this.doprocess(urlstr);
    } catch (UnsupportedEncodingException ex) {
    }
    return result;
  }

  public String statusJson(String queue_name) {
    String result = null;
    try {
      String urlstr = "http://" + this.server + ":" + this.port + "/?name=" + URLEncoder.encode(queue_name, charset) + "&opt=status_json";

      result = this.doprocess(urlstr);
    } catch (UnsupportedEncodingException ex) {
    }
    return result;
  }

  public String get(String queue_name) {
    String result = null;
    try {
      String urlstr = "http://" + this.server + ":" + this.port + "/?charset=" + this.charset + "&name=" + URLEncoder.encode(queue_name, charset) + "&opt=get";

      result = this.doprocess(urlstr);
    } catch (UnsupportedEncodingException ex) {
    }
    return result;
  }

  public String put(String queue_name, String data) {
    String urlstr;
    URL url;
    try {
      urlstr = "http://" + this.server + ":" + this.port + "/?name=" + URLEncoder.encode(queue_name, charset) + "&opt=put";
      url = new URL(urlstr);
    } catch (UnsupportedEncodingException ex) {
      return "URLEncoder.encode() error";
    } catch (MalformedURLException e) {
      return "The httpsqs server must be error";
    }
    URLConnection conn;

    OutputStreamWriter writer = null;
    try {
      conn = url.openConnection();
      conn.setConnectTimeout(connectTimeout);
      conn.setReadTimeout(readTimeout);
      conn.setUseCaches(false);
      conn.setDoOutput(true);
      conn.setDoInput(true);
      conn.setRequestProperty("Content-Type", "text/plain;charset=" + charset);

      conn.connect();

      writer = new OutputStreamWriter(conn.getOutputStream(), charset);
      writer.write(URLEncoder.encode(data, charset));
      writer.flush();
    } catch (IOException e) {
      return "Put data error";
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ex) {
        }
      }
    }

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset));
      return reader.readLine() + "\n";
    } catch (IOException e) {
      return "Get return data error";
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ex) {
        }
      }
    }
  }
}