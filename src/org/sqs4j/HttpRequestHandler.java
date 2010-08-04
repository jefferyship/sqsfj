package org.sqs4j;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public class HttpRequestHandler extends SimpleChannelUpstreamHandler {
  private HttpRequest _request;
  private boolean _readingChunks;
  private StringBuilder _buf = new StringBuilder();  //Buffer that stores the response content
  private Charset _charsetObj;

  private Sqs4jApp _app;

  public HttpRequestHandler(Sqs4jApp app) {
    _app = app;
    _charsetObj = _app._defaultCharset;
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

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
          throws Exception {
    //e.getCause().printStackTrace();
    e.getChannel().close();
  }

  /**
   * ����ģ��
   *
   * @param ctx
   * @param e
   * @throws Exception
   */
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    if (!_readingChunks) {
      _request = (HttpRequest) e.getMessage();
      _buf.setLength(0);

      if (_request.isChunked()) {
        _readingChunks = true;
      } else {
        writeResponse(e);
      }
    } else {
      HttpChunk chunk = (HttpChunk) e.getMessage();

      if (chunk.isLast()) {
        _readingChunks = false;
        writeResponse(e);
      }
    }

  }

  private void writeResponse(MessageEvent e) throws UnsupportedEncodingException {
    //����URL����
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(_request.getUri());  //ȱʡUTF-8����
    Map<String, List<String>> requestParameters = queryStringDecoder.getParameters();

    String charset = requestParameters.get("charset") != null ? requestParameters.get("charset").get(0) : null;  //�ȴ�query�����charset
    if (charset == null) {
      if (_request.getHeader("Content-Type") != null) {
        charset = getCharsetFromContentType(_request.getHeader("Content-Type"));
        if (charset == null) {
          charset = _app._conf.defaultCharset;
        }
      } else {
        charset = _app._conf.defaultCharset;
      }
    } else {  //˵����ѯ������ָ�����ַ���
      _charsetObj = Charset.forName(charset);
      queryStringDecoder = new QueryStringDecoder(_request.getUri(), _charsetObj);
      requestParameters = queryStringDecoder.getParameters();
    }

    //����GET������
    String httpsqs_input_name = requestParameters.get("name") != null ? requestParameters.get("name").get(0) : null; /* �������� */
    String httpsqs_input_opt = requestParameters.get("opt") != null ? requestParameters.get("opt").get(0) : null; //�������
    String httpsqs_input_data = requestParameters.get("data") != null ? requestParameters.get("data").get(0) : null; //��������
    String httpsqs_input_pos_tmp = requestParameters.get("pos") != null ? requestParameters.get("pos").get(0) : null; //����λ�õ�
    String httpsqs_input_num_tmp = requestParameters.get("num") != null ? requestParameters.get("num").get(0) : null; //�����ܳ���
    long httpsqs_input_pos = 0;
    long httpsqs_input_num = 0;
    if (httpsqs_input_pos_tmp != null) {
      httpsqs_input_pos = Long.parseLong(httpsqs_input_pos_tmp);
    }
    if (httpsqs_input_num_tmp != null) {
      httpsqs_input_num = Long.parseLong(httpsqs_input_num_tmp);
    }

    //���ظ��û���Headerͷ��Ϣ
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setHeader("Content-Type", "text/plain;charset=" + charset);
    response.setHeader("Connection", "keep-alive");
    response.setHeader("Cache-Control", "no-cache");

    Sqs4jApp._lock.lock();
    try {
      /*�����Ƿ�����ж� */
      if (httpsqs_input_name != null && httpsqs_input_opt != null && httpsqs_input_name.length() <= 256) {
        /* ����� */
        if (httpsqs_input_opt.equals("put")) {
          /* ���Ƚ���POST������Ϣ */
          if (_request.getMethod().getName().equalsIgnoreCase("POST")) {
            long queue_put_value = _app.httpsqs_now_putpos(httpsqs_input_name);
            if (queue_put_value > 0) {
              String queue_name = String.format("%s:%d", httpsqs_input_name, queue_put_value);

              String httpsqs_input_postbuffer = URLDecoder.decode(_request.getContent().toString(_charsetObj), charset);
              DatabaseEntry key = new DatabaseEntry(queue_name.getBytes(Sqs4jApp.DB_CHARSET));
              DatabaseEntry value = new DatabaseEntry(httpsqs_input_postbuffer.getBytes(Sqs4jApp.DB_CHARSET));
              OperationStatus status = _app._db.put(null, key, value);
              if (status == OperationStatus.SUCCESS) {
                response.setHeader("Pos", queue_name);
                _buf.append("HTTPSQS_PUT_OK");
              } else {
                _buf.append("HTTPSQS_PUT_ERROR");
              }
            } else {
              _buf.append("HTTPSQS_PUT_END");
            }
          } else if (httpsqs_input_data != null) {  //���POST���������ݣ���ȡURL��data������ֵ
            long queue_put_value = _app.httpsqs_now_putpos(httpsqs_input_name);
            if (queue_put_value > 0) {
              String queue_name = String.format("%s:%d", httpsqs_input_name, queue_put_value);

              DatabaseEntry key = new DatabaseEntry(queue_name.getBytes(Sqs4jApp.DB_CHARSET));
              DatabaseEntry value = new DatabaseEntry(httpsqs_input_data.getBytes(Sqs4jApp.DB_CHARSET));
              OperationStatus status = _app._db.put(null, key, value);
              if (status == OperationStatus.SUCCESS) {
                response.setHeader("Pos", queue_name);
                _buf.append("HTTPSQS_PUT_OK");
              } else {
                _buf.append("HTTPSQS_PUT_ERROR");
              }
            } else {
              _buf.append("HTTPSQS_PUT_END");
            }
          } else {
            _buf.append("HTTPSQS_PUT_ERROR");
          }
        } else if (httpsqs_input_opt.equals("get")) {  //������
          long queue_get_value = _app.httpsqs_now_getpos(httpsqs_input_name);
          if (queue_get_value == 0) {
            _buf.append("HTTPSQS_GET_END");
          } else {
            String queue_name = String.format("%s:%d", httpsqs_input_name, queue_get_value);

            DatabaseEntry key = new DatabaseEntry(queue_name.getBytes(Sqs4jApp.DB_CHARSET));
            DatabaseEntry value = new DatabaseEntry();
            OperationStatus status = _app._db.get(null, key, value, LockMode.DEFAULT);
            if (status == OperationStatus.SUCCESS && value.getSize() > 0) {
              response.setHeader("Pos", queue_name);
              _buf.append(new String(value.getData(), Sqs4jApp.DB_CHARSET));
            } else {
              _buf.append("HTTPSQS_GET_END");
            }
          }
          /* �鿴����״̬����ͨ�����ʽ�� */
        } else if (httpsqs_input_opt.equals("status")) {
          String put_times = "1st lap";
          String get_times = "1st lap";

          long maxqueue = _app.httpsqs_read_maxqueue(httpsqs_input_name); /* ���������� */
          long putpos = _app.httpsqs_read_putpos(httpsqs_input_name);     /* �����д��λ�� */
          long getpos = _app.httpsqs_read_getpos(httpsqs_input_name);     /* �����ж�ȡλ�� */
          long ungetnum = 0;
          if (putpos >= getpos) {
            ungetnum = Math.abs(putpos - getpos); //��δ����������
          } else if (putpos < getpos) {
            ungetnum = Math.abs(maxqueue - getpos + putpos); /* ��δ���������� */
            put_times = "2nd lap";
          }
          _buf.append(String.format("HTTP Simple Queue Service (Sqs4J)v%s\n", Sqs4jApp.VERSION));
          _buf.append("------------------------------\n");
          _buf.append(String.format("Queue Name: %s\n", httpsqs_input_name));
          _buf.append(String.format("Maximum number of queues: %d\n", maxqueue));
          _buf.append(String.format("Put position of queue (%s): %d\n", put_times, putpos));
          _buf.append(String.format("Get position of queue (%s): %d\n", get_times, getpos));
          _buf.append(String.format("Number of unread queue: %d\n", ungetnum));
          /* �鿴����״̬��JSON��ʽ������ͷ��˳����� */
        } else if (httpsqs_input_opt.equals("status_json")) {
          String put_times = "1st lap";
          String get_times = "1st lap";

          long maxqueue = _app.httpsqs_read_maxqueue(httpsqs_input_name); /* ���������� */
          long putpos = _app.httpsqs_read_putpos(httpsqs_input_name);     /* �����д��λ�� */
          long getpos = _app.httpsqs_read_getpos(httpsqs_input_name);     /* �����ж�ȡλ�� */
          long ungetnum = 0;
          if (putpos >= getpos) {
            ungetnum = Math.abs(putpos - getpos); //��δ����������
          } else if (putpos < getpos) {
            ungetnum = Math.abs(maxqueue - getpos + putpos); /* ��δ���������� */
            put_times = "2nd lap";
          }

          _buf.append(String.format("{\"name\":\"%s\",\"maxqueue\":%d,\"putpos\":%d,\"putlap\":%s,\"getpos\":%d,\"getlap\":%s,\"unread\":%d}\n", httpsqs_input_name, maxqueue, putpos, put_times, getpos, get_times, ungetnum));
          /* �鿴������������ */
        } else if (httpsqs_input_opt.equals("view") && httpsqs_input_pos >= 1 && httpsqs_input_pos <= 1000000000) {
          String httpsqs_output_value = _app.httpsqs_view(httpsqs_input_name, httpsqs_input_pos);
          if (httpsqs_output_value == null) {
            _buf.append(String.format("%s", "HTTPSQS_ERROR_NOFOUND"));
          } else {
            _buf.append(String.format("%s", httpsqs_output_value));
          }
          /* ���ö��� */
        } else if (httpsqs_input_opt.equals("reset")) {
          boolean reset = _app.httpsqs_reset(httpsqs_input_name);
          if (reset) {
            _buf.append(String.format("%s", "HTTPSQS_RESET_OK"));
          } else {
            _buf.append(String.format("%s", "HTTPSQS_RESET_ERROR"));
          }
          /* �������Ķ�����������СֵΪ10�������ֵΪ10���� */
        } else if (httpsqs_input_opt.equals("maxqueue") && httpsqs_input_num >= 10 && httpsqs_input_num <= 1000000000) {
          if (_app.httpsqs_maxqueue(httpsqs_input_name, httpsqs_input_num) != 0) {
            _buf.append(String.format("%s", "HTTPSQS_MAXQUEUE_OK"));  //���óɹ�
          } else {
            _buf.append(String.format("%s", "HTTPSQS_MAXQUEUE_CANCEL"));  //����ȡ��
          }
          /* ���ö�ʱ�����ڴ����ݵ����̵ļ��ʱ�䣬��СֵΪ1�룬���ֵΪ10���� */
        } else if (httpsqs_input_opt.equals("synctime") && httpsqs_input_num >= 1 && httpsqs_input_num <= 1000000000) {
          if (_app.httpsqs_synctime((int) httpsqs_input_num) >= 1) {
            _buf.append(String.format("%s", "HTTPSQS_SYNCTIME_OK"));
          } else {
            _buf.append(String.format("%s", "HTTPSQS_SYNCTIME_CANCEL"));
          }
        } else {  /* ������� */
          _buf.append(String.format("%s", "HTTPSQS_ERROR"));
        }
      } else {
        _buf.append(String.format("%s", "HTTPSQS_ERROR"));
      }
    } finally {
      Sqs4jApp._lock.unlock();
    }

    response.setContent(ChannelBuffers.copiedBuffer(_buf.toString(), _charsetObj));
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent().readableBytes());
    // Write the response.
    ChannelFuture future = e.getChannel().write(response);

    // Close the non-keep-alive connection after the write operation is done.
    boolean keepAlive = HttpHeaders.isKeepAlive(_request);
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE);
    }

  }
}
