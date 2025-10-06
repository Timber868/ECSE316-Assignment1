import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

public class DnsClientSendRetryReceiveTest {

    // Helper to capture System.out output while running DnsClient.main(args)
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

    // ---------------------------------------------------------------------------
    // 1. Test that client prints retry-exceeded error when no server responds
    // ---------------------------------------------------------------------------
    @Test
    public void error_on_timeout_and_max_retries_exceeded() throws Exception {
        // Use an unroutable IP (RFC 5737 TEST-NET-1)
        RunResult rr = runClient("-t", "1", "-r", "2", "@192.0.2.123", "example.com");
        assertTrue(rr.out.contains("ERROR") && rr.out.contains("Maximum number of retries"),
                "Expected timeout error message when no response received");
    }

    // ---------------------------------------------------------------------------
    // 2. Test that client succeeds when a mock UDP server replies on first try
    // ---------------------------------------------------------------------------
    @Test
    public void success_on_first_reply() throws Exception {
        int port = 55000 + (int)(Math.random() * 1000);
        // Prepare fake UDP server that immediately echoes back the request
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> serverFuture = exec.submit(() -> {
            try (DatagramSocket server = new DatagramSocket(port)) {
                byte[] buf = new byte[2048];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                server.receive(pkt);
                // echo same payload back to sender
                DatagramPacket resp = new DatagramPacket(pkt.getData(), pkt.getLength(),
                        pkt.getAddress(), pkt.getPort());
                server.send(resp);
            } catch (IOException e) { /* ignore for test */ }
        });

        RunResult rr = runClient("-t","2","-r","1","-p",Integer.toString(port),
                "@127.0.0.1","www.mcgill.ca");
        serverFuture.get(3, TimeUnit.SECONDS); // wait for server thread
        exec.shutdownNow();

        assertTrue(rr.out.contains("Response received after"),
                "Expected success message when mock server replies");
        assertFalse(rr.out.contains("ERROR"), "No ERROR expected on success");
    }

    // ---------------------------------------------------------------------------
    // 3. Test that retries actually happen (server replies only after N attempts)
    // ---------------------------------------------------------------------------
    @Test
    public void retries_until_success() throws Exception {
        int port = 56000 + (int)(Math.random() * 1000);
        final int replyOnAttempt = 2; // drop first packet, reply on second
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> serverFuture = exec.submit(() -> {
            try (DatagramSocket server = new DatagramSocket(port)) {
                int count = 0;
                byte[] buf = new byte[2048];
                while (true) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    server.receive(pkt);
                    count++;
                    if (count == replyOnAttempt) {
                        DatagramPacket resp = new DatagramPacket(pkt.getData(), pkt.getLength(),
                                pkt.getAddress(), pkt.getPort());
                        server.send(resp);
                        break;
                    }
                }
            } catch (IOException e) { /* ignore */ }
        });

        RunResult rr = runClient("-t","1","-r","3","-p",Integer.toString(port),
                "@127.0.0.1","mcgill.ca");
        serverFuture.get(4, TimeUnit.SECONDS);
        exec.shutdownNow();

        // Expected to succeed, but should mention retries >= 1
        assertTrue(rr.out.contains("Response received after"), "Expected success message");
        assertTrue(rr.out.contains("(1 retries)") || rr.out.contains("(2 retries)"),
                "Output should report at least one retry before success");
    }
}
