// File: DnsClientQueryMakerTests.java
// Compile:
//   javac -cp .:junit-platform-console-standalone-1.10.2.jar DnsClient.java DnsClientQueryMakerTests.java
// Run:
//   java -jar junit-platform-console-standalone-1.10.2.jar -cp . --scan-classpath

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class DnsClientQueryMakerTests {

  // u16 view (big-endian)
  private static int u16(byte[] b, int off) {
    return ((b[off] & 0xFF) << 8) | (b[off+1] & 0xFF);
  }
  private static byte[] qname(String name) {
    ByteBuffer bb = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
    for (String label : name.split("\\.")) {
      byte[] lb = label.getBytes(StandardCharsets.US_ASCII);
      bb.put((byte) lb.length);
      bb.put(lb);
    }
    bb.put((byte) 0x00);
    byte[] out = new byte[bb.position()];
    System.arraycopy(bb.array(), 0, out, 0, out.length);
    return out;
  }
  private static DnsClient.Config cfg(String name, int qtype) {
    DnsClient.Config c = new DnsClient.Config();
    c.name = name; c.qtype = qtype; return c;
  }

  @Test
  public void header_uses_given_short_id_and_RD_set() {
    short id = (short)0x1234;
    byte[] q = DnsClient.queryMaker(cfg("www.mcgill.ca", 1), id);
    assertNotNull(q);
    assertTrue(q.length > 12);
    assertEquals(0x1234, u16(q, 0), "ID must match provided short (unsigned compare)");
    int flags = u16(q, 2);
    assertEquals(1, (flags >> 8) & 1, "RD must be set");
    assertEquals(1, u16(q, 4), "QDCOUNT=1");
    assertEquals(0, u16(q, 6), "ANCOUNT=0");
    assertEquals(0, u16(q, 8), "NSCOUNT=0");
    assertEquals(0, u16(q,10), "ARCOUNT=0");
  }

  @Test
  public void qname_is_length_prefixed_and_terminated() {
    String name = "www.mcgill.ca";
    short id = (short)0xAAAA;
    byte[] q = DnsClient.queryMaker(cfg(name, 1), id);
    int pos = 12;
    while (q[pos] != 0) pos += 1 + (q[pos] & 0xFF);
    int qnameLen = (pos - 12) + 1;
    byte[] actual = new byte[qnameLen];
    System.arraycopy(q, 12, actual, 0, qnameLen);
    assertArrayEquals(qname(name), actual);
  }

  @Test
  public void qtype_qclass_are_correct_for_A_NS_MX() {
    // A
    byte[] qa = DnsClient.queryMaker(cfg("mcgill.ca", 1), (short)0x0102);
    int pos = 12; while (qa[pos] != 0) pos += 1 + (qa[pos] & 0xFF); pos += 1;
    assertEquals(1, u16(qa, pos)); pos += 2;
    assertEquals(1, u16(qa, pos));

    // NS
    byte[] qns = DnsClient.queryMaker(cfg("mcgill.ca", 2), (short)0x0203);
    pos = 12; while (qns[pos] != 0) pos += 1 + (qns[pos] & 0xFF); pos += 1;
    assertEquals(2, u16(qns, pos));
    assertEquals(1, u16(qns, pos+2));

    // MX
    byte[] qmx = DnsClient.queryMaker(cfg("mcgill.ca", 15), (short)0x0304);
    pos = 12; while (qmx[pos] != 0) pos += 1 + (qmx[pos] & 0xFF); pos += 1;
    assertEquals(15, u16(qmx, pos));
    assertEquals(1, u16(qmx, pos+2));
  }

  @Test
  public void rejects_label_over_63_bytes() {
    String tooLong = "a".repeat(64) + ".mcgill.ca";
    try {
      byte[] q = DnsClient.queryMaker(cfg(tooLong, 1), (short)0x1111);
      assertNull(q, "Expect null on invalid label length (>63)");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().toLowerCase().contains("label"));
    }
  }

  @Test
  public void rejects_name_over_255_on_wire() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 50; i++) { if (i>0) sb.append('.'); sb.append("abcde"); }
    String tooBig = sb.toString();
    try {
      byte[] q = DnsClient.queryMaker(cfg(tooBig, 1), (short)0x2222);
      assertNull(q, "Expect null when on-wire name > 255 bytes");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().contains("255"));
    }
  }

  @Test
  public void structure_identical_for_same_inputs() {
    String name = "www.mcgill.ca";
    short id = (short)0xBEEF;
    byte[] q1 = DnsClient.queryMaker(cfg(name, 1), id);
    byte[] q2 = DnsClient.queryMaker(cfg(name, 1), id);
    assertArrayEquals(q1, q2);
  }
}
