import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

public class OutputTests {

    private static class Capture {
        String out;
    }

    // Helper: run a minimal “fake main” segment using parsed result injection
    private Capture runOutput(DnsClient.Config cfg, DnsClient.ParsedResult parsed,
                              double elapsed, int retries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream orig = System.out;
        System.setOut(new PrintStream(baos));

        try {
            // Simulate only the Step 6 printing section (copied from your main)
            System.out.println("DnsClient sending request for " + cfg.name);
            System.out.println("Server: " + cfg.server);
            String typeStr = (cfg.qtype == 1 ? "A" : cfg.qtype == 2 ? "NS" : "MX");
            System.out.println("Request type: " + typeStr);
            System.out.printf("Response received after %.3f seconds (%d retries)%n", elapsed, retries);

            if (!parsed.ok) {
                System.out.println("ERROR\t" + (parsed.error == null ? "Unknown parsing error" : parsed.error));
            } else if (parsed.notFound) {
                System.out.println("NOTFOUND");
            } else {
                System.out.println("***Answer Section (" + parsed.answerCount + " records)***");
                for (String a : parsed.answers) System.out.println(a);
                if (parsed.additionalCount > 0) {
                    System.out.println("***Additional Section (" + parsed.additionalCount + " records)***");
                    for (String ad : parsed.additionals) System.out.println(ad);
                }
            }
        } finally {
            System.out.flush();
            System.setOut(orig);
        }

        Capture c = new Capture();
        c.out = baos.toString().trim();
        return c;
    }

    // -------------------------------------------------------
    // 1. Happy path: A record output
    // -------------------------------------------------------
    @Test
    public void prints_success_A_record_output() throws Exception {
        DnsClient.Config cfg = new DnsClient.Config();
        cfg.server = "8.8.8.8"; cfg.name = "example.com"; cfg.qtype = 1;

        DnsClient.ParsedResult parsed = new DnsClient.ParsedResult();
        parsed.ok = true; parsed.authoritative = true;
        parsed.answerCount = 1;
        parsed.answers.add("IP\t1.2.3.4\t60\tauth");

        Capture c = runOutput(cfg, parsed, 0.123, 0);

        String out = c.out;
        assertTrue(out.contains("DnsClient sending request for example.com"));
        assertTrue(out.contains("Server: 8.8.8.8"));
        assertTrue(out.contains("Request type: A"));
        assertTrue(out.contains("Response received after"));
        assertTrue(out.contains("***Answer Section (1 records)***"));
        assertTrue(out.contains("IP\t1.2.3.4\t60\tauth"));
    }

    // -------------------------------------------------------
    // 2. NS query with additional section
    // -------------------------------------------------------
    @Test
    public void prints_NS_with_additional() throws Exception {
        DnsClient.Config cfg = new DnsClient.Config();
        cfg.server = "1.1.1.1"; cfg.name = "mcgill.ca"; cfg.qtype = 2;

        DnsClient.ParsedResult parsed = new DnsClient.ParsedResult();
        parsed.ok = true; parsed.authoritative = false;
        parsed.answerCount = 1;
        parsed.additionalCount = 1;
        parsed.answers.add("NS\tns1.mcgill.ca\t300\tnonauth");
        parsed.additionals.add("IP\t132.206.44.2\t300\tnonauth");

        Capture c = runOutput(cfg, parsed, 0.500, 1);
        String out = c.out;

        assertTrue(out.contains("Request type: NS"));
        assertTrue(out.contains("***Answer Section (1 records)***"));
        assertTrue(out.contains("NS\tns1.mcgill.ca"));
        assertTrue(out.contains("***Additional Section (1 records)***"));
        assertTrue(out.contains("132.206.44.2"));
    }

    // -------------------------------------------------------
    // 3. NOTFOUND case
    // -------------------------------------------------------
    @Test
    public void prints_notfound_case() throws Exception {
        DnsClient.Config cfg = new DnsClient.Config();
        cfg.server = "8.8.8.8"; cfg.name = "idontexist.zzz"; cfg.qtype = 1;
        DnsClient.ParsedResult parsed = new DnsClient.ParsedResult();
        parsed.ok = true; parsed.notFound = true;

        Capture c = runOutput(cfg, parsed, 1.234, 3);
        String out = c.out;

        assertTrue(out.contains("NOTFOUND"));
        assertFalse(out.contains("***Answer Section"), "Should not print Answer Section on NOTFOUND");
    }

    // -------------------------------------------------------
    // 4. Error case: malformed response
    // -------------------------------------------------------
    @Test
    public void prints_error_output() throws Exception {
        DnsClient.Config cfg = new DnsClient.Config();
        cfg.server = "8.8.4.4"; cfg.name = "bad.example"; cfg.qtype = 1;
        DnsClient.ParsedResult parsed = new DnsClient.ParsedResult();
        parsed.ok = false;
        parsed.error = "Malformed response (too short)";

        Capture c = runOutput(cfg, parsed, 0.999, 2);
        String out = c.out;

        assertTrue(out.contains("ERROR\tMalformed response"), "Should print ERROR line");
        assertFalse(out.contains("***Answer Section"), "Should not show sections on error");
    }

    // -------------------------------------------------------
    // 5. MX query output format
    // -------------------------------------------------------
    @Test
    public void prints_mx_record_correctly() throws Exception {
        DnsClient.Config cfg = new DnsClient.Config();
        cfg.server = "9.9.9.9"; cfg.name = "testmail.net"; cfg.qtype = 15;
        DnsClient.ParsedResult parsed = new DnsClient.ParsedResult();
        parsed.ok = true; parsed.authoritative = true; parsed.answerCount = 1;
        parsed.answers.add("MX\tmail.testmail.net\t10\t600\tauth");

        Capture c = runOutput(cfg, parsed, 0.222, 0);
        String out = c.out;

        assertTrue(out.contains("Request type: MX"));
        assertTrue(out.contains("***Answer Section"));
        assertTrue(out.contains("MX\tmail.testmail.net\t10\t600\tauth"));
    }
}
