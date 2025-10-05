import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.*;

/**
 * Exhaustive end-to-end test suite for DnsClient
 * Covers all lab requirements (Steps 1–6).
 *
 * These tests assume you have an internet connection and
 * that public resolvers (8.8.8.8 / 1.1.1.1) respond correctly.
 */
public class DnsClientIntegrationTests {

    // Helper class to capture stdout from DnsClient.main()
    private static class RunResult {
        String out;
    }

    private static RunResult runClient(String... args) throws Exception {
        PrintStream orig = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        RunResult rr = new RunResult();
        try {
            DnsClient.main(args);
        } finally {
            System.out.flush();
            rr.out = baos.toString();
            System.setOut(orig);
        }
        return rr;
    }

    // ------------------------------------------------------------
    // 1. Basic “A” query with defaults
    // ------------------------------------------------------------
    @Test
    public void query_A_record_defaults() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "www.google.com");
        assertTrue(rr.out.contains("DnsClient sending request for www.google.com"));
        assertTrue(rr.out.contains("Server: 8.8.8.8"));
        assertTrue(rr.out.contains("Request type: A"));
        assertTrue(rr.out.contains("***Answer Section"), "Expected answer section");
        assertTrue(rr.out.contains("IP\t"), "Expected IP record in output");
    }

    // ------------------------------------------------------------
    // 2. NS query
    // ------------------------------------------------------------
    @Test
    public void query_NS_record() throws Exception {
        RunResult rr = runClient("-ns", "@8.8.8.8", "mcgill.ca");
        assertTrue(rr.out.contains("Request type: NS"));
        assertTrue(rr.out.contains("***Answer Section"), "Expected NS answer section");
        assertTrue(rr.out.contains("NS\t"), "Expected NS hostnames");
    }

    // ------------------------------------------------------------
    // 3. MX query
    // ------------------------------------------------------------
    @Test
    public void query_MX_record() throws Exception {
        RunResult rr = runClient("-mx", "@8.8.8.8", "gmail.com");
        assertTrue(rr.out.contains("Request type: MX"));
        assertTrue(rr.out.contains("***Answer Section"), "Expected MX answer section");
        assertTrue(rr.out.matches("(?s).*MX\\t.*\\t\\d+\\t.*"), "Expected MX preference + host");
    }

    // ------------------------------------------------------------
    // 4. NOTFOUND / NXDOMAIN handling
    // ------------------------------------------------------------
    @Test
    public void query_nonexistent_domain() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "thisdomaindoesnotexist1234567890.com");
        assertTrue(rr.out.contains("NOTFOUND") || rr.out.contains("ERROR"),
                "Should print NOTFOUND or ERROR for NXDOMAIN");
    }

    // ------------------------------------------------------------
    // 5. Retry & timeout behavior (unreachable IP)
    // ------------------------------------------------------------
    @Test
    public void query_timeout_and_retries() throws Exception {
        RunResult rr = runClient("-t", "1", "-r", "2", "@192.0.2.55", "example.com"); // TEST-NET-1
        assertTrue(rr.out.contains("ERROR") && rr.out.contains("Maximum number of retries"),
                "Expected retry error when no server replies");
    }

    // ------------------------------------------------------------
    // 6. Input validation errors
    // ------------------------------------------------------------
    @Test
    public void input_missing_server_or_name() throws Exception {
        RunResult rr1 = runClient("@8.8.8.8");
        assertTrue(rr1.out.contains("ERROR") && rr1.out.contains("missing @server or name"));
        RunResult rr2 = runClient("example.com");
        assertTrue(rr2.out.contains("ERROR") && rr2.out.contains("missing @server or name"));
    }

    @Test
    public void input_multiple_server_flags() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "@1.1.1.1", "example.com");
        assertTrue(rr.out.contains("ERROR") && rr.out.contains("multiple @server"));
    }

    @Test
    public void input_invalid_port_range() throws Exception {
        RunResult rr = runClient("-p", "70000", "@8.8.8.8", "example.com");
        assertTrue(rr.out.contains("ERROR") && rr.out.contains("invalid numeric value"));
    }

    // ------------------------------------------------------------
    // 7. CNAME chain presence (e.g., www.mcgill.ca)
    // ------------------------------------------------------------
    @Test
    public void query_with_CNAME_included() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "www.mcgill.ca");
        assertTrue(rr.out.contains("Request type: A"));
        if (rr.out.contains("CNAME")) {
            assertTrue(rr.out.matches("(?s).*CNAME\\t.*\\t.*\\t(auth|nonauth).*"),
                    "Expected CNAME line formatted correctly");
        }
    }

    // ------------------------------------------------------------
    // 8. Additional Section (MX queries often have one)
    // ------------------------------------------------------------
    @Test
    public void query_with_additional_section() throws Exception {
        RunResult rr = runClient("-mx", "@8.8.8.8", "gmail.com");
        if (rr.out.contains("***Additional Section")) {
            assertTrue(rr.out.contains("IP\t"), "Expected IP entries in additional section");
        }
    }

    // ------------------------------------------------------------
    // 9. Timeout and valid retry counting output format
    // ------------------------------------------------------------
    @Test
    public void output_includes_elapsed_time_and_retry_count() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "google.com");
        assertTrue(rr.out.matches("(?s).*Response received after .* seconds \\(\\d+ retries\\).*"),
                "Expected elapsed time and retry count format");
    }

    // ------------------------------------------------------------
    // 10. Case: input mutually exclusive -mx and -ns
    // ------------------------------------------------------------
    @Test
    public void input_conflicting_mx_ns_flags() throws Exception {
        RunResult rr = runClient("-mx", "-ns", "@8.8.8.8", "example.com");
        assertTrue(rr.out.contains("ERROR") && rr.out.contains("-mx") && rr.out.contains("-ns"),
                "Expected mutual exclusivity error");
    }

    // ------------------------------------------------------------
    // 11. Sanity check for valid numeric input handling
    // ------------------------------------------------------------
    @Test
    public void valid_custom_timeout_retry_port() throws Exception {
        RunResult rr = runClient("-t", "3", "-r", "1", "-p", "53", "@1.1.1.1", "google.com");
        assertTrue(rr.out.contains("Response received after"), "Expected normal response");
    }

    // ------------------------------------------------------------
    // 12. Invalid numeric input (non-integer)
    // ------------------------------------------------------------
    @Test
    public void invalid_numeric_args() throws Exception {
        RunResult rr = runClient("-t", "abc", "@8.8.8.8", "example.com");
        assertTrue(rr.out.contains("ERROR") && rr.out.contains("non-integer"),
                "Expected non-integer error for -t abc");
    }

    // ------------------------------------------------------------
    // 13. Verify auth vs nonauth flag presence
    // ------------------------------------------------------------
    @Test
    public void auth_flag_in_answer_lines() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "www.google.com");
        assertTrue(rr.out.matches("(?s).*(auth|nonauth).*"), "Expected auth/nonauth in record lines");
    }
}
