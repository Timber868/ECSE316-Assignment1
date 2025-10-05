// File: ParseResponseTests.java
// -----------------------------------------------------------------------------
// Compile:
//   javac -cp .:junit-platform-console-standalone-1.10.2.jar DnsClient.java ParseResponseTests.java
// Run:
//   java -jar junit-platform-console-standalone-1.10.2.jar -cp . --scan-classpath
//
// This suite checks correctness of DnsClient.parseResponse() and its helpers
// (decodeName, parseRR). It builds minimal DNS response byte arrays by hand
// rather than contacting a real server.
// -----------------------------------------------------------------------------

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import org.junit.jupiter.api.Test;

public class ParseResponseTests {

    // Helper: build header (12 bytes)
    private static byte[] header(short id, int flags, int qd, int an, int ns, int ar) {
        ByteBuffer bb = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        bb.putShort(id);
        bb.putShort((short) flags);
        bb.putShort((short) qd);
        bb.putShort((short) an);
        bb.putShort((short) ns);
        bb.putShort((short) ar);
        return bb.array();
    }

    // Helper: encode QNAME (length-prefixed labels + 0)
    private static byte[] qname(String name) {
        ByteBuffer bb = ByteBuffer.allocate(256);
        for (String label : name.split("\\.")) {
            bb.put((byte) label.length());
            for (byte b : label.getBytes()) bb.put(b);
        }
        bb.put((byte) 0);
        byte[] out = new byte[bb.position()];
        System.arraycopy(bb.array(), 0, out, 0, out.length);
        return out;
    }

    // Utility: append arrays
    private static byte[] join(byte[]... parts) {
        int len = 0; for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    // -----------------------------------------------------------
    // 1. Test: valid A-record response (single answer)
    // -----------------------------------------------------------
    @Test
    public void parses_single_A_record() {
        short id = (short)0x1234;
        int flags = 0x8180; // QR=1, RD=1, RA=1, RCODE=0
        byte[] hdr = header(id, flags, 1, 1, 0, 0);

        // Question: example.com, QTYPE=A, QCLASS=IN
        byte[] q = join(qname("example.com"), new byte[]{0,1, 0,1});

        // Answer: NAME=pointer to 12 (0xC00C), TYPE=A, CLASS=IN, TTL=60, RDLEN=4, RDATA=1.2.3.4
        byte[] ans = new byte[] {
                (byte)0xC0, 0x0C,   // pointer to question name
                0x00, 0x01,         // TYPE A
                0x00, 0x01,         // CLASS IN
                0x00, 0x00, 0x00, 0x3C, // TTL 60
                0x00, 0x04,         // RDLEN 4
                0x01, 0x02, 0x03, 0x04  // RDATA IPv4
        };

        byte[] msg = join(hdr, q, ans);

        DnsClient.ParsedResult r = DnsClient.parseResponse(msg, id);
        assertTrue(r.ok, "Parsing should succeed");
        assertEquals(1, r.answerCount);
        assertTrue(r.answers.get(0).startsWith("IP\t1.2.3.4"));
    }

    // -----------------------------------------------------------
    // 2. Test: MX record parsing (pref + host)
    // -----------------------------------------------------------
    @Test
    public void parses_single_MX_record() {
        short id = (short)0x2222;
        int flags = 0x8180;
        byte[] hdr = header(id, flags, 1, 1, 0, 0);
        byte[] q = join(qname("mail.test"), new byte[]{0,15, 0,1}); // QTYPE=MX
        // Answer: name pointer, TYPE=MX, CLASS=IN, TTL=600, RDLEN=9,
        // pref=10, exchange="mx.test"
        byte[] rdata = join(new byte[]{0,10}, qname("mx.test"));
        ByteBuffer ans = ByteBuffer.allocate(2+2+2+4+2+rdata.length);
        ans.put(new byte[]{(byte)0xC0,0x0C}); // name ptr
        ans.putShort((short)15); ans.putShort((short)1);
        ans.putInt(600); ans.putShort((short)rdata.length); ans.put(rdata);

        byte[] msg = join(hdr, q, ans.array());
        DnsClient.ParsedResult r = DnsClient.parseResponse(msg, id);
        assertTrue(r.ok);
        assertEquals(1, r.answerCount);
        String line = r.answers.get(0);
        assertTrue(line.contains("MX\tmx.test\t10"), "Should show MX host and pref");
    }

    // -----------------------------------------------------------
    // 3. Test: NS record parsing with compression
    // -----------------------------------------------------------
    @Test
    public void parses_NS_record_with_pointer() {
        short id = (short)0x3333;
        int flags = 0x8180;
        byte[] hdr = header(id, flags, 1, 1, 0, 0);
        byte[] q = join(qname("zone.example"), new byte[]{0,2, 0,1});
        // NS answer: NAME ptr(0xC00C), TYPE=NS, CLASS=IN, TTL=1200, RDLEN=2,
        // RDATA= pointer to 0xC011 ("example")
        byte[] ans = {
                (byte)0xC0,0x0C, 0,2, 0,1,
                0,0,0,0x78, 0,2, (byte)0xC0,0x11
        };
        byte[] msg = join(hdr, q, ans);
        DnsClient.ParsedResult r = DnsClient.parseResponse(msg, id);
        assertTrue(r.ok);
        assertEquals(1, r.answerCount);
        assertTrue(r.answers.get(0).startsWith("NS\t"), "Should detect NS record");
    }

    // -----------------------------------------------------------
    // 4. Test: NOTFOUND (RCODE=3)
    // -----------------------------------------------------------
    @Test
    public void detects_notfound_rcode3() {
        short id = (short)0x4444;
        int flags = 0x8183; // QR=1, RCODE=3
        byte[] hdr = header(id, flags, 1, 0, 0, 0);
        byte[] q = join(qname("doesnotexist.test"), new byte[]{0,1, 0,1});
        byte[] msg = join(hdr, q);
        DnsClient.ParsedResult r = DnsClient.parseResponse(msg, id);
        assertTrue(r.ok);
        assertTrue(r.notFound, "Should set notFound=true for RCODE=3");
    }

    // -----------------------------------------------------------
    // 5. Test: ID mismatch → ERROR
    // -----------------------------------------------------------
    @Test
    public void rejects_id_mismatch() {
        short idSent = (short)0x5555;
        short idRecv = (short)0xAAAA;
        int flags = 0x8180;
        byte[] msg = join(header(idRecv, flags, 0, 0, 0, 0));
        DnsClient.ParsedResult r = DnsClient.parseResponse(msg, idSent);
        assertFalse(r.ok);
        assertTrue(r.error.toLowerCase().contains("id mismatch"));
    }

    // -----------------------------------------------------------
    // 6. Test: truncated TC=1 → ERROR
    // -----------------------------------------------------------
    @Test
    public void detects_truncated_response() {
        short id = (short)0x6666;
        int flags = 0x8180 | 0x0200; // TC bit set
        byte[] msg = join(header(id, flags, 1, 0, 0, 0));
        DnsClient.ParsedResult r = DnsClient.parseResponse(msg, id);
        assertFalse(r.ok);
        assertTrue(r.error.contains("Truncated"), "Should detect TC=1");
    }

    // -----------------------------------------------------------
    // 7. Test: malformed short packet
    // -----------------------------------------------------------
    @Test
    public void rejects_too_short() {
        DnsClient.ParsedResult r = DnsClient.parseResponse(new byte[5], (short)1);
        assertFalse(r.ok);
        assertTrue(r.error.contains("short"));
    }
}
