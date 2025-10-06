// File: DnsClientParseInputTests.java
// Compile:
//   javac -cp .:junit-platform-console-standalone-1.10.2.jar DnsClient.java DnsClientParseInputTests.java
// Run:
//   java -jar junit-platform-console-standalone-1.10.2.jar -cp . --scan-classpath

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

public class DnsClientParseInputTests {

  // Helper: capture System.out while calling parseInput
  private static class OutAndCfg {
    String out;
    DnsClient.Config cfg;
  }
  private OutAndCfg callParse(String... args) {
    PrintStream orig = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos));
    OutAndCfg res = new OutAndCfg();
    try {
      res.cfg = DnsClient.parseInput(args);  // ensure parseInput is accessible to tests
    } finally {
      System.out.flush();
      res.out = baos.toString();
      System.setOut(orig);
    }
    return res;
  }

  @Test
  public void valid_minimal_defaults_A() {
    OutAndCfg r = callParse("@8.8.8.8", "www.mcgill.ca");
    assertNotNull(r.cfg);
    assertEquals(5, r.cfg.timeoutSec);
    assertEquals(3, r.cfg.maxRetries);
    assertEquals(53, r.cfg.port);
    assertEquals(1, r.cfg.qtype); // A
    assertEquals("8.8.8.8", r.cfg.server);
    assertEquals("www.mcgill.ca", r.cfg.name);
    assertTrue(r.out.isEmpty(), "No ERROR output expected");
  }

  @Test
  public void valid_ns_with_options() {
    OutAndCfg r = callParse("-t","3","-r","2","-p","53","-ns","@1.1.1.1","mcgill.ca");
    assertNotNull(r.cfg);
    assertEquals(3, r.cfg.timeoutSec);
    assertEquals(2, r.cfg.maxRetries);
    assertEquals(53, r.cfg.port);
    assertEquals(2, r.cfg.qtype); // NS
    assertEquals("1.1.1.1", r.cfg.server);
    assertEquals("mcgill.ca", r.cfg.name);
  }

  @Test
  public void valid_mx_sets_qtype_15() {
    OutAndCfg r = callParse("-mx","@8.8.8.8","mcgill.ca");
    assertNotNull(r.cfg);
    assertEquals(15, r.cfg.qtype);
  }

  @Test
  public void error_both_mx_and_ns() {
    OutAndCfg r = callParse("-mx","-ns","@8.8.8.8","example.com");
    assertNull(r.cfg);
    assertTrue(r.out.contains("Incorrect input syntax") && r.out.contains("-mx") && r.out.contains("-ns"));
  }

  @Test
  public void error_missing_value_after_t() {
    OutAndCfg r = callParse("-t","@8.8.8.8","example.com");
    assertNull(r.cfg);
    assertTrue(r.out.contains("non-integer for -t"));
  }

  @Test
  public void error_non_integer_for_r() {
    OutAndCfg r = callParse("-r","abc","@8.8.8.8","example.com");
    assertNull(r.cfg);
    assertTrue(r.out.contains("non-integer for -r"));
  }

  @Test
  public void error_missing_server_or_name() {
    OutAndCfg r1 = callParse("@8.8.8.8");
    assertNull(r1.cfg);
    assertTrue(r1.out.contains("missing @server and/or name"));

    OutAndCfg r2 = callParse("example.com");
    assertNull(r2.cfg);
    assertTrue(r2.out.contains("missing @server and/or name"));
  }

  @Test
  public void error_multiple_server_flags() {
    OutAndCfg r = callParse("@8.8.8.8","@1.1.1.1","example.com");
    assertNull(r.cfg);
    assertTrue(r.out.contains("multiple @server"));
  }

  @Test
  public void error_bad_ipv4_octet_count() {
    OutAndCfg r = callParse("@8.8.8","example.com");
    assertNull(r.cfg);
    assertTrue(r.out.contains("@server must be IPv4"));
  }

  @Test
  public void error_ipv4_octet_out_of_range() {
    OutAndCfg r = callParse("@999.8.8.8","example.com");
    assertNull(r.cfg);
    assertTrue(r.out.contains("IPv4 octet out of range") || r.out.contains("not a number"));
  }

  @Test
  public void error_unexpected_extra_positional_arg() {
    OutAndCfg r = callParse("@8.8.8.8","example.com","extra");
    assertNull(r.cfg);
    assertTrue(r.out.contains("unexpected argument"));
  }

  @Test
  public void error_invalid_numeric_ranges() {
    // port > 65535
    OutAndCfg r1 = callParse("-p","70000","@8.8.8.8","example.com");
    assertNull(r1.cfg);
    assertTrue(r1.out.contains("invalid numeric value"));

    // timeout <= 0
    OutAndCfg r2 = callParse("-t","0","@8.8.8.8","example.com");
    assertNull(r2.cfg);
    assertTrue(r2.out.contains("invalid numeric value"));

    // retries < 0
    OutAndCfg r3 = callParse("-r","-1","@8.8.8.8","example.com");
    assertNull(r3.cfg);
    assertTrue(r3.out.contains("invalid numeric value"));
  }
}
