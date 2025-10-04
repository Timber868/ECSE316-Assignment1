import java.net.*;
import java.io.*;

/**
 * DnsClient — Minimal DNS client (UDP)
 *
 * Usage:
 * java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name
 *
 * Parameters:
 * -t timeout (optional) Seconds to wait for a reply before retrying. Default: 5
 * -r max-retries (optional) Number of retransmissions on timeout. Default: 3
 * -p port (optional) UDP port of the DNS server. Default: 53
 * -mx | -ns (optional) Query type: MX = mail exchangers, NS = name servers.
 * If neither flag is given, the query type is A (IPv4 address).
 * 
 * @server (required) IPv4 address of the DNS server (a.b.c.d)
 *         name (required) Domain name to look up (e.g., www.example.com)
 *
 *         What it does:
 *         - Builds a DNS query packet by hand (no DNS helper libraries) and
 *         sends it via UDP
 *         to @server:port, with a socket timeout and up to max-retries
 *         retransmissions.
 *         - Query type depends on flags:
 *         * default → A (IPv4 address)
 *         * -mx → MX (mail servers + preference)
 *         * -ns → NS (authoritative name servers)
 *         - Parses the DNS response (verifies header, handles compression) and
 *         prints results.
 *
 *         Output format (printed to STDOUT, exact strings expected by the
 *         grader):
 *         DnsClient sending request for <name>
 *         Server: <server IP>
 *         Request type: <A|MX|NS>
 *         Response received after <seconds> seconds (<retries> retries)
 *         ***Answer Section (N records)***
 *         For each record (tab-separated):
 *         A: "IP\t<ipv4>\t<ttl>\t<auth|nonauth>"
 *         CNAME:"CNAME\t<alias>\t<ttl>\t<auth|nonauth>" // printed if present
 *         with A queries
 *         MX: "MX\t<host>\t<pref>\t<ttl>\t<auth|nonauth>"
 *         NS: "NS\t<host>\t<ttl>\t<auth|nonauth>"
 *         If additional records exist, also print:
 *         ***Additional Section (M records)***
 *         If no answers are found:
 *         NOTFOUND
 *         On errors (bad input, timeouts exceeded, malformed response, etc.):
 *         ERROR\t<description>
 *
 *         Notes:
 *         - Use DatagramSocket + setSoTimeout for retries.
 *         - Use InetAddress.getByAddress(byte[4]) for @server (do NOT resolve
 *         names here).
 *         - Construct/parse DNS packets manually (header, QNAME, QTYPE, QCLASS,
 *         RRs).
 */

class DnsClient {

    // Config class that will hold our paramters
    static class Config {
        int timeoutSec = 5;
        int maxRetries = 3;
        int port = 53;
        int qtype = 1; // 1=A, 2=NS, 15=MX
        String server = null;
        String name = null;
    }

    public static void main(String[] args) {
        try {
            // (1) CLI parse with our helper function
            Config config = parseInput(args);

            System.out.println("Config:");
            System.out.println("Timeout: " + config.timeoutSec);
            System.out.println("Max Retries: " + config.maxRetries);
            System.out.println("Port: " + config.port);
            System.out.println("Query Type: " + config.qtype);
            System.out.println("Server: " + config.server);
            System.out.println("Name: " + config.name);

            // (2) Socket + dst:
            // DatagramSocket sock = new DatagramSocket();
            // sock.setSoTimeout(timeoutSec*1000);
            // byte[] ipBytes = parse a.b.c.d → 4 bytes; InetAddress.getByAddress(ipBytes);

            // (3) Build query:
            // ByteBuffer bb(BIG_ENDIAN);
            // putShort(ID=random); putShort(flags with RD=1); putShort(1); putShort(0);
            // putShort(0); putShort(0);
            // write QNAME: for each label of name, len byte + ASCII bytes; then 0x00
            // putShort(qtype); putShort(CLASS_IN);
            // byte[] query = Arrays.copyOf(bb.array(), bb.position());

            // (4) Retry loop:
            // long t0=System.nanoTime(); int tries=0; byte[] resp=null;
            // while(true){ send DatagramPacket(query,...);
            // try { receive into buffer; resp=copy; break; }
            // catch(SocketTimeoutException e){ if(tries++>=maxRetries){ print ERROR
            // "Maximum number of retries X exceeded"; return; } }
            // }
            // double elapsed=(System.nanoTime()-t0)/1e9;

            // (5) Parse response:
            // ByteBuffer r=wrap(resp).order(BIG_ENDIAN);
            // check ID matches; check QR=1; rcode==0; read counts qd,an,ns,ar; aa flag
            // pos=12; decode Question name (handle compression) then skip QTYPE+QCLASS
            // loop an times: read NAME (compression), TYPE, CLASS, TTL, RDLENGTH
            // if TYPE=A and rdlen=4 → print line later as
            // "IP\tx.x.x.x\tTTL\t[auth|nonauth]"
            // if TYPE=CNAME or NS → RDATA is domain name (compression)
            // if TYPE=MX → pref(u16) + domain name
            // collect Additional similarly if you want to print it

            // (6) Output (exact text):
            // System.out.println("DnsClient sending request for "+name);
            // System.out.println("Server: "+serverIp);
            // System.out.println("Request type: "+(qtype==1?"A":qtype==2?"NS":"MX"));
            // System.out.println("Response received after "+format(elapsed)+" seconds
            // ("+tries+" retries)");
            // if answers==0 → System.out.println("NOTFOUND")
            // else:
            // System.out.println("***Answer Section ("+answersCount+" records)***");
            // print tab-separated lines per type
            // if additional>0 → print "***Additional Section (M records)***" then its lines
        } catch (Exception e) {
            // System.out.println("ERROR\t"+e.getMessage());
        }
    }

    private static DnsClient.Config parseInput(String[] args) {
        // make a helper function that will parse the input arguments
        DnsClient.Config config = new DnsClient.Config();
        boolean seenMx = false, seenNs = false; // track mutual exclusivity
    
        // Traverse through the args array if a flag is found, set the corresponding value in config.
        // The corresponding value is the next element in the array, so increment i after reading it.
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-t":
                    if (i + 1 >= args.length) { System.out.println("ERROR\tIncorrect input syntax: missing value after -t"); return null; }
                    try {
                        config.timeoutSec = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR\tIncorrect input syntax: non-integer for -t"); return null;
                    }
                    break;
    
                case "-r":
                    if (i + 1 >= args.length) { System.out.println("ERROR\tIncorrect input syntax: missing value after -r"); return null; }
                    try {
                        config.maxRetries = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR\tIncorrect input syntax: non-integer for -r"); return null;
                    }
                    break;
    
                case "-p":
                    if (i + 1 >= args.length) { System.out.println("ERROR\tIncorrect input syntax: missing value after -p"); return null; }
                    try {
                        config.port = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR\tIncorrect input syntax: non-integer for -p"); return null;
                    }
                    break;
    
                // MX and NS are mutually exclusive, so if one is found, set qtype accordingly.
                // MX is 15, NS is 2.
                case "-mx":
                    if (seenNs) { System.out.println("ERROR\tIncorrect input syntax: -mx and -ns both given"); return null; }
                    seenMx = true;
                    config.qtype = 15;
                    break;
    
                case "-ns":
                    if (seenMx) { System.out.println("ERROR\tIncorrect input syntax: -mx and -ns both given"); return null; }
                    seenNs = true; // NS
                    config.qtype = 2; // NS
                    break;
    
                default:
                    // If the argument starts with @, it's the server address. So strip the @ and set server.
                    if (args[i].startsWith("@")) {
                        if (config.server != null) { System.out.println("ERROR\tIncorrect input syntax: multiple @server"); return null; }
                        config.server = args[i].substring(1);
    
                    // If name is not set, this argument is the name.
                    } else if (config.name == null) {
                        config.name = args[i];
    
                    // If we reach here, the input is invalid (unexpected argument).
                    } else {
                        System.out.println("ERROR\tIncorrect input syntax: unexpected argument " + args[i]);
                        return null;
                    }
            }
        }
    
        // Validate required parameters
        if (config.server == null || config.name == null) {
            System.out.println("ERROR\tIncorrect input syntax: missing @server or name");
            return null;
        }
    
        // Basic IPv4 sanity check (a.b.c.d)
        String[] oct = config.server.split("\\.");
        if (oct.length != 4) {
            System.out.println("ERROR\tIncorrect input syntax: @server must be IPv4 a.b.c.d");
            return null;
        }
        for (String s : oct) {
            try {
                int v = Integer.parseInt(s);
                if (v < 0 || v > 255) {
                    System.out.println("ERROR\tIncorrect input syntax: IPv4 octet out of range");
                    return null;
                }
            } catch (NumberFormatException e) {
                System.out.println("ERROR\tIncorrect input syntax: IPv4 octet not a number");
                return null;
            }
        }
    
        // (optional) simple numeric range checks to avoid nonsense values
        if (config.timeoutSec <= 0 || config.maxRetries < 0 || config.port <= 0 || config.port > 65535) {
            System.out.println("ERROR\tIncorrect input syntax: invalid numeric value");
            return null;
        }
    
        // Return the config object
        return config;
    }    
}
