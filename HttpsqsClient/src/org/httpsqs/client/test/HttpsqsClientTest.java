package org.httpsqs.client.test;

import junit.framework.TestCase;
import org.httpsqs.client.HttpsqsClient;

/**
 * @author Administrator
 */
public class HttpsqsClientTest extends TestCase {
  String queue_name = "test_queue";
  HttpsqsClient instance = new HttpsqsClient("127.0.0.1", 1218, "GBK", 60 * 10000, 60 * 10000);

  public HttpsqsClientTest(String testName) {
    super(testName);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  /**
   * Test of put method, of class HttpsqsClient.
   */
  public void testPut() {
    System.out.println("put");
    String data = "test(≤‚ ‘)Httpsqs";
    String expResult = "HTTPSQS_PUT_OK\n";
    String result = instance.put(queue_name, data);
    System.out.println(result);
    assertTrue(expResult.equals(result));
  }

  /**
   * Test of maxqueue method, of class HttpsqsClient.
   */
  public void testMaxqueue() {
    System.out.println("maxqueue");
    String num = "1000000";
    String expResult = "HTTPSQS_MAXQUEUE_OK\n";
    String result = instance.maxqueue(queue_name, num);
    System.out.println(result);
    assertTrue(expResult.equals(result));
  }

  /**
   * Test of reset method, of class HttpsqsClient.
   */
  public void testReset() {
    System.out.println("reset");
    String expResult = "HTTPSQS_RESET_OK\n";
    String result = instance.reset(queue_name);
    System.out.println(result);
    assertTrue(expResult.equals(result));
  }

  /**
   * Test of view method, of class HttpsqsClient.
   */
  public void testView() {
    System.out.println("view");
    String pos = "1";
    String expResult = "HTTPSQS_ERROR_NOFOUND\n";
    String result = instance.view(queue_name, pos);
    System.out.println(result);
    assertTrue(!expResult.equals(result));
  }

  /**
   * Test of status method, of class HttpsqsClient.
   */
  public void testStatus() {
    System.out.println("status");
    String expResult = "HTTPSQS_ERROR\n";
    String result = instance.status(queue_name);
    System.out.println(result);
    assertTrue(!expResult.equals(result));
  }

  /**
   * Test of statusJson method, of class HttpsqsClient.
   */
  public void testStatusJson() {
    System.out.println("statusJson");
    String expResult = "HTTPSQS_ERROR\n";
    String result = instance.statusJson(queue_name);
    System.out.println(result);
    assertTrue(!expResult.equals(result));
  }

  /**
   * Test of get method, of class HttpsqsClient.
   */
  public void testGet() {
    System.out.println("get");
    String expResult = "HTTPSQS_GET_END\n";
    String result = instance.get(queue_name);
    System.out.println(result);
    assertTrue(!expResult.equals(result));
  }

}
