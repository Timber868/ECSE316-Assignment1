import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.*;

/**
 * DnsClientIntegrationTests
 * Full coverage for COMP 316 DNS Client Lab (Steps 1–6)
 *
 * NOTE: These tests assume internet access and that 8.8.8.8 and 1.1.1.1 respond normally.
 * They can be reduced or skipped in restricted networks.
 */
public class DnsClientIntegrationTests {

    // Utility ----------------------------------------------------
    private static class RunResult {
        String out;
    }

    private static RunResult runClient(String... args) throws Exception {
        PrintStream orig = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        RunResult rr = new RunResult();
        try { DnsClient.main(args); }
        finally {
            System.out.flush();
            rr.out = baos.toString();
            System.setOut(orig);
        }
        return rr;
    }

    // ============================================================
    // Step 1 – CLI PARSING BASICS
    // ============================================================

    @Test
    public void default_A_query_success() throws Exception {
        RunResult rr = runClient("@8.8.8.8", "google.com");
        assertTrue(rr.out.contains("DnsClient sending request for google.com"));
        assertTrue(rr.out.contains("Server: 8.8.8.8"));
        assertTrue(rr.out.contains("Request type: A"));
    }

    @Test
    public void missing_server_or_name() throws Exception {
        assertTrue(runClient("@8.8.8.8").out.contains("missing @server and/or name"));
        assertTrue(runClient("example.com").out.contains("missing @server and/or name"));
    }

    @Test
    public void duplicate_server_flags() throws Exception {
        assertTrue(runClient("@1.1.1.1", "@8.8.8.8", "example.com").out.contains("multiple @server"));
    }

    @Test
    public void conflicting_mx_and_ns() throws Exception {
        String out = runClient("-mx", "-ns", "@8.8.8.8", "example.com").out;
        assertTrue(out.contains("Incorrect input syntax") && out.contains("-mx") && out.contains("-ns"));
    }

    @Test
    public void unexpected_extra_argument() throws Exception {
        String out = runClient("@8.8.8.8", "example.com", "extra").out;
        assertTrue(out.contains("unexpected argument"));
    }

    // ============================================================
    // Step 2 – CLI NUMERIC VALIDATION
    // ============================================================

    @Test
    public void valid_numeric_options() throws Exception {
        String out = runClient("-t", "3", "-r", "2", "-p", "53", "@1.1.1.1", "mcgill.ca").out;
        assertTrue(out.contains("Response received after"));
    }

    @Test
    public void non_integer_timeout() throws Exception {
        assertTrue(runClient("-t", "abc", "@8.8.8.8", "example.com").out.contains("non-integer for -t"));
    }

    @Test
    public void invalid_range_values() throws Exception {
        assertTrue(runClient("-p", "70000", "@8.8.8.8", "example.com").out.contains("invalid numeric value"));
        assertTrue(runClient("-t", "0", "@8.8.8.8", "example.com").out.contains("invalid numeric value"));
        assertTrue(runClient("-r", "-1", "@8.8.8.8", "example.com").out.contains("invalid numeric value"));
    }

    // ============================================================
    // Step 3 – QUERY CONSTRUCTION VALIDATION
    // ============================================================

    @Test
    public void mx_and_ns_query_type_switch() throws Exception {
        String mx = runClient("-mx", "@8.8.8.8", "gmail.com").out;
        String ns = runClient("-ns", "@8.8.8.8", "mcgill.ca").out;
        assertTrue(mx.contains("Request type: MX"));
        assertTrue(ns.contains("Request type: NS"));
    }

    @Test
    public void invalid_label_length_over_63() throws Exception {
        String longLabel = "a".repeat(64) + ".mcgill.ca";
        String out = runClient("@8.8.8.8", longLabel).out;
        assertTrue(out.contains("invalid label length"));
    }

    @Test
    public void invalid_total_length_over_255() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) { if (i > 0) sb.append('.'); sb.append("abcde"); }
        String out = runClient("@8.8.8.8", sb.toString()).out;
        assertTrue(out.contains("name too long"));
    }

    // ============================================================
    // Step 4 – SEND / RETRY / RECEIVE LOGIC
    // ============================================================

    @Test
    public void timeout_and_retries_exceeded() throws Exception {
        String out = runClient("-t", "1", "-r", "1", "@192.0.2.55", "example.com").out;
        assertTrue(out.contains("ERROR") && out.contains("Maximum number of retries"));
    }

    @Test
    public void valid_response_from_real_server() throws Exception {
        String out = runClient("@8.8.8.8", "google.com").out;
        assertTrue(out.contains("Response received after"));
    }

    // ============================================================
    // Step 5 – RESPONSE PARSING EDGE CASES
    // ============================================================

    @Test
    public void query_notfound_domain() throws Exception {
        String out = runClient("@8.8.8.8", "idontexist1234567890.com").out;
        assertTrue(out.contains("NOTFOUND") || out.contains("ERROR"));
    }

    @Test
    public void cname_chain_present_if_applicable() throws Exception {
        String out = runClient("@8.8.8.8", "www.mcgill.ca").out;
        if (out.contains("CNAME")) {
            assertTrue(out.matches("(?s).*CNAME\\t.*\\t\\d+\\t(auth|nonauth).*"));
        }
    }

    @Test
    public void mx_record_fields_present() throws Exception {
        String out = runClient("-mx", "@8.8.8.8", "gmail.com").out;
        assertTrue(out.matches("(?s).*MX\\t.*\\t\\d+\\t\\d+\\t(auth|nonauth).*"));
    }

    @Test
    public void ns_record_fields_present() throws Exception {
        String out = runClient("-ns", "@8.8.8.8", "mcgill.ca").out;
        assertTrue(out.contains("NS\t") && out.matches("(?s).*NS\\t.*\\t\\d+\\t(auth|nonauth).*"));
    }

    @Test
    public void response_includes_auth_or_nonauth() throws Exception {
        String out = runClient("@8.8.8.8", "google.com").out;
        assertTrue(out.contains("auth") || out.contains("nonauth"));
    }

    @Test
    public void additional_section_present_for_mx() throws Exception {
        String out = runClient("-mx", "@8.8.8.8", "gmail.com").out;
        if (out.contains("***Additional Section")) {
            assertTrue(out.contains("IP\t"));
        }
    }

    @Test
    public void handles_rcode_errors_gracefully() throws Exception {
        // using an invalid TLD often yields rcode ≠ 0
        String out = runClient("@8.8.8.8", "fake.invalidtld").out;
        assertTrue(out.contains("ERROR") || out.contains("NOTFOUND"));
    }

    // ============================================================
    // Step 6 – OUTPUT FORMAT VALIDATION
    // ============================================================

    @Test
    public void output_includes_all_required_headers() throws Exception {
        String out = runClient("@8.8.8.8", "google.com").out;
        assertTrue(out.contains("DnsClient sending request for"));
        assertTrue(out.contains("Server:"));
        assertTrue(out.contains("Request type:"));
        assertTrue(out.matches("(?s).*Response received after .* seconds \\(\\d+ retries\\).*"));
    }

    @Test
    public void output_notfound_prints_only_NOTFOUND() throws Exception {
        String out = runClient("@8.8.8.8", "idontexist-abc123.com").out;
        if (out.contains("NOTFOUND"))
            assertFalse(out.contains("***Answer Section"), "NOTFOUND should have no Answer section");
    }

    @Test
    public void output_answer_section_line_format_IP() throws Exception {
        String out = runClient("@8.8.8.8", "google.com").out;
        if (out.contains("IP\t")) {
            assertTrue(out.matches("(?s).*IP\\t\\d+\\.\\d+\\.\\d+\\.\\d+\\t\\d+\\t(auth|nonauth).*"));
        }
    }

    @Test
    public void output_mx_line_format() throws Exception {
        String out = runClient("-mx", "@8.8.8.8", "gmail.com").out;
        if (out.contains("MX\t"))
            assertTrue(out.matches("(?s).*MX\\t.*\\t\\d+\\t\\d+\\t(auth|nonauth).*"));
    }

    @Test
    public void output_includes_additional_header_when_present() throws Exception {
        String out = runClient("-mx", "@8.8.8.8", "gmail.com").out;
        if (out.contains("***Additional Section"))
            assertTrue(out.contains("***Additional Section ("));
    }

    @Test
    public void output_handles_nonauth_answers() throws Exception {
        String out = runClient("@1.1.1.1", "google.com").out;
        assertTrue(out.contains("nonauth") || out.contains("auth"));
    }

    @Test
    public void output_cannot_crash_on_short_or_malformed_responses() throws Exception {
        // supply intentionally broken server address -> should print ERROR not crash
        String out = runClient( "@255.255.255.255", "example.com").out;
        System.out.println(out);
        assertTrue(out.contains("ERROR"));
    }
}
