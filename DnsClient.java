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
        int qtype = 1; // default is A which has a value of 1
        String server = null;
        String name = null;
    }

    public static void main(String[] args) {
        try {
            // (1) CLI parse with our helper function
            Config config = parseInput(args);
//            System.out.println(config);
			
            // If config is null, there was an error in parsing input, so we exit.
            if (config == null) return;

            // (2) Create the UDP socket we will use to send/receive DNS packets
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(config.timeoutSec * 1000);

            // Convert the server "a.b.c.d" into 4 raw bytes for InetAddress
            String[] octets = config.server.split("\\.");
            byte[] IP = new byte[] {
                (byte) Integer.parseInt(octets[0]),
                (byte) Integer.parseInt(octets[1]),
                (byte) Integer.parseInt(octets[2]),
                (byte) Integer.parseInt(octets[3])
            };

            // Build destination InetAddress from the raw bytes this is the only thing allowed
            InetAddress dnsServer = InetAddress.getByAddress(IP);
            int dnsPort = config.port;

            // Prepare re-usable receive buffer  big enough for typical DNS replies
            byte[] recvBuf = new byte[2048]; 
                         
            // Prepare the receive packet with the current receive buffer
            DatagramPacket receivePack = new DatagramPacket(recvBuf, recvBuf.length);
            

            // (3) Build query:

            // First 2 bytes of the DNS header are the ID field we want to keep track of it so we can verify the response matches
            Short ID = (short) (Math.random() * 0xFFFF);
            byte[] query = queryMaker(config, ID);

            // (4) Retry loop:
            // long t0=System.nanoTime(); int tries=0; byte[] resp=null;
            // while(true){ send DatagramPacket(query,...);
            // try { receive into buffer; resp=copy; break; }
            // catch(SocketTimeoutException e){ if(tries++>=maxRetries){ print ERROR
            // "Maximum number of retries X exceeded"; return; } }
            // }
            // double elapsed=(System.nanoTime()-t0)/1e9;
            // (4) Retry loop:
            long startTime = System.nanoTime();
            int tries = 0;
            byte[] response = null;

// label to break out once we have a valid reply from the intended server
            outer:
            while (tries <= config.maxRetries) {
                try {
                    // send once per try
                    DatagramPacket sendPack =
                            new DatagramPacket(query, query.length, dnsServer, dnsPort);
                    socket.send(sendPack);

                    while (true) {
                        // reset the receive packet buffer/length every wait
                        receivePack.setData(recvBuf);
                        receivePack.setLength(recvBuf.length);

                        socket.receive(receivePack);

                        // *** new: only accept a reply from the server and port we queried ***
                        if (!receivePack.getAddress().equals(dnsServer)
                                || receivePack.getPort() != dnsPort) {
                            // Ignore stray/broadcast responses; keep listening until timeout.
                            continue;
                        }

                        // Valid source => copy payload and break out
                        response = java.util.Arrays.copyOfRange(
                                receivePack.getData(), 0, receivePack.getLength());
                        break outer;
                    }

                } catch (SocketTimeoutException e) {
                    tries++;
                    if (tries > config.maxRetries) {
                        System.out.println("ERROR\tMaximum number of retries " + config.maxRetries + " exceeded");
                        socket.close();
                        return;
                    }
                    // else retry automatically
                } catch (IOException e) {
                    System.out.println("ERROR\tI/O exception during send/receive: " + e.getMessage());
                    socket.close();
                    return;
                }
            }

// --- If we reach here, a response was received ---
            double elapsedSeconds = (System.nanoTime() - startTime) / 1e9;

            ParsedResult parsed = parseResponse(response, ID);

// (preliminary step 6) Temporary success output (minimal required for tests)
            System.out.println("DnsClient sending request for " + config.name);
            System.out.println("Server: " + config.server);
            String typeStr = (config.qtype == 1 ? "A" : config.qtype == 2 ? "NS" : "MX");
            System.out.println("Request type: " + typeStr);
            System.out.printf("Response received after %.3f seconds (%d retries)%n",
                    elapsedSeconds, tries);

            if (!parsed.ok) {
                System.out.println("ERROR\t" + (parsed.error == null ? "Unknown parsing error" : parsed.error));
                socket.close();
                return;
            }
            if (parsed.notFound) {
                System.out.println("NOTFOUND");
                socket.close();
                return;
            }

            System.out.println("***Answer Section (" + parsed.answerCount + " records)***");
            for (String line : parsed.answers) System.out.println(line);

            if (parsed.additionalCount > 0) {
                System.out.println("***Additional Section (" + parsed.additionalCount + " records)***");
                for (String line : parsed.additionals) System.out.println(line);
            }

            socket.close();



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

    static DnsClient.Config parseInput(String[] args) {
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

    static byte[] queryMaker(DnsClient.Config config, Short ID) {
        try {
            // First we need to build the DNS query packet manually
            // The DNS packet consists of a header and a question section
            // We will use a ByteArrayOutputStream to build the packet dynamically
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Write the header and question section to the DataOutputStream
            // Flags are a bitfield so we need to set each bit individually then combine them 
            Byte QR = 0; // 0 is for query 1 is for response
            Byte OPCODE = 0; // standard query
            Byte AA = 0; // Authoritative Answer
            Byte TC = 0; // Truncation
            Byte RD = 1; // Recursion Desired
            Byte RA = 0; // Recursion Available
            Byte Z = 0; // 3 bit set reserved for future use
            Byte RCODE = 0; // Response code but were a querry so not useful 0 means no error anyways
            Short FLAGS = (short) ((QR << 15) | (OPCODE << 11) | (AA << 10) | (TC << 9) | (RD << 8)
                    | (RA << 7) | (Z << 4) | RCODE);

            // Next we have the counts section of the header each count is a 16 bit unsigned int
            Short QDCOUNT = 1; // we have one question it could be an unsigned 16-bit int but we only have one question
            Short ANCOUNT = 0; // we have no answers in the query
            Short NSCOUNT = 0; // we have no authority records in the query
            Short ARCOUNT = 0; // we have no additional records in the query

            // Now we write the header to the DataOutputStream
            dos.writeShort(ID);
            dos.writeShort(FLAGS);
            dos.writeShort(QDCOUNT);
            dos.writeShort(ANCOUNT);
            dos.writeShort(NSCOUNT);
            dos.writeShort(ARCOUNT);

            // Now we write the question section to the DataOutputStream
            // The question section consists of the QNAME, QTYPE, and QCLASS

            // QNAME is the domain name we are querying for it is represented as a series of labels
            // is a domain name represented by a sequence of labels, where each label begins with a length
            // octet followed by that number of octets. The domain name terminates with the zero-length octet,
            // representing the null label of the root.

            int totalNameLen = 1; // final null terminator
            for (String label : config.name.split("\\.")) {
                byte length = (byte) label.length();

                // Check each label
                if (length == 0 || length > 63) {
                    System.out.println("ERROR\tIncorrect input syntax: invalid label length in name");
                    return null;
                }

                // Check total length BEFORE writing
                if (totalNameLen + length + 1 > 255) {
                    System.out.println("ERROR\tIncorrect input syntax: name too long");
                    return null;
                }

                totalNameLen += length + 1;
                dos.writeByte(length);
                dos.writeBytes(label);
            }
            dos.writeByte(0); // null label of the root

            // QTYPE is a 16-bit code specifying the type of query.
            // Note: The three types relevant to this lab are:
            // 0x0001 for a type-A query (host address)
            // 0x0002 for a type-NS query (name server)
            // 0x000f for a type-MX query (mail server)

            dos.writeShort((short) config.qtype); // QTYPE

            // QCLASS is a 16-bit code specifying the class of the query. You should always use 0x0001 in this field, representing an Internet address.
            dos.writeShort((short) 0x0001); // QCLASS

            // Finally we convert the ByteArrayOutputStream to a byte array to get the final query packet
            byte[] query = baos.toByteArray();
            return query;
        }
        catch (IOException e) {
            System.out.println("ERROR\tIOException while building query: " + e.getMessage());
            return null;
        }
    }

    static final class ParsedResult {
        boolean ok;
        boolean notFound;
        String error;           // if non-null: print "ERROR\t" + error
        boolean authoritative;  // AA flag from header
        int answerCount;
        int additionalCount;
        java.util.List<String> answers = new java.util.ArrayList<>();
        java.util.List<String> additionals = new java.util.ArrayList<>();
    }

    // Unsigned 16/32-bit reads (big-endian)
    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }
    static long u32(byte[] b, int off) {
        return ((long)(b[off] & 0xFF) << 24) |
                ((long)(b[off+1] & 0xFF) << 16) |
                ((long)(b[off+2] & 0xFF) << 8) |
                ((long)(b[off+3] & 0xFF));
    }

    // Decode domain name at 'offset' with DNS compression (RFC 1035).
// Returns name and the next offset to continue parsing (for non-jump path).
    static final class NameDec {
        final String name;
        final int next;
        NameDec(String name, int next) { this.name = name; this.next = next; }
    }
    static NameDec decodeName(byte[] msg, int offset) {
        StringBuilder sb = new StringBuilder();
        int pos = offset;
        int next = -1;
        boolean jumped = false;
        int safety = 0;

        while (true) {
            if (safety++ > 512) throw new IllegalArgumentException("Name compression loop");
            int len = msg[pos] & 0xFF;

            // End of name
            if (len == 0) {
                if (!jumped) next = pos + 1;
                break;
            }

            // Pointer 11xxxxxx xxxxxxxx
            if ((len & 0xC0) == 0xC0) {
                int ptr = ((len & 0x3F) << 8) | (msg[pos + 1] & 0xFF);
                if (!jumped) next = pos + 2;
                pos = ptr;
                jumped = true;
                continue;
            }

            // Label
            pos++;
            if (sb.length() > 0) sb.append('.');
            if (len > 63) throw new IllegalArgumentException("Label length > 63");
            for (int i = 0; i < len; i++) {
                sb.append((char)(msg[pos + i] & 0xFF));
            }
            pos += len;
        }

        return new NameDec(sb.toString(), next);
    }

    static String ipv4(byte[] b, int off) {
        return (b[off] & 0xFF) + "." + (b[off+1] & 0xFF) + "." + (b[off+2] & 0xFF) + "." + (b[off+3] & 0xFF);
    }

    // Parse a single RR starting at offset; return "line" for output and new offset
    static final class RRParse {
        final String line;  // already formatted per type (without section headers)
        final int next;
        RRParse(String line, int next) { this.line = line; this.next = next; }
    }

    static RRParse parseRR(byte[] resp, int off, boolean aaFlag) {
        // NAME
        NameDec name = decodeName(resp, off);
        int p = name.next;

        int type = u16(resp, p); p += 2;
        int clazz = u16(resp, p); p += 2; // expect IN=1
        long ttl = u32(resp, p); p += 4;
        int rdlen = u16(resp, p); p += 2;

        String auth = aaFlag ? "auth" : "nonauth";

        String out = null;
        switch (type) {
            case 1: // A
                if (rdlen == 4) {
                    String ip = ipv4(resp, p);
                    out = "IP\t" + ip + "\t" + ttl + "\t" + auth;
                }
                break;

            case 2: // NS
            {
                NameDec ns = decodeName(resp, p);
                out = "NS\t" + ns.name + "\t" + ttl + "\t" + auth;
                // for NS/CNAME the RDATA is a (possibly compressed) domain name; we must not rely on rdlen to skip, use decoder’s own traversal
                // BUT we still need to advance p by rdlen at the end, not by decoder.next (decoder reads from p via pointer jumps)
            }
            break;

            case 5: // CNAME
            {
                NameDec cn = decodeName(resp, p);
                out = "CNAME\t" + cn.name + "\t" + ttl + "\t" + auth;
            }
            break;

            case 15: // MX
            {
                int pref = u16(resp, p);
                NameDec mx = decodeName(resp, p + 2);
                out = "MX\t" + mx.name + "\t" + pref + "\t" + ttl + "\t" + auth;
            }
            break;

            // You can add AAAA/TXT as needed; spec requires A/NS/MX (+ CNAME if present)
            default:
                // Ignore unknown types for printing, but still advance offset
                break;
        }

        // Advance by RDLENGTH from the start of RDATA
        p = p + rdlen;
        return new RRParse(out, p);
    }

    static ParsedResult parseResponse(byte[] resp, short expectedId) {
        ParsedResult r = new ParsedResult();

        if (resp == null || resp.length < 12) {
            r.error = "Malformed response (too short)";
            return r;
        }
        int id = u16(resp, 0);
        int flags = u16(resp, 2);
        int qd = u16(resp, 4);
        int an = u16(resp, 6);
        int ns = u16(resp, 8);
        int ar = u16(resp, 10);

        if ((id & 0xFFFF) != (expectedId & 0xFFFF)) {
            r.error = "Unexpected response (ID mismatch)";
            return r;
        }

        int qr = (flags >> 15) & 1;
        int aa = (flags >> 10) & 1;
        int tc = (flags >> 9) & 1;
        int rcode = (flags) & 0xF;

        if (qr != 1) {
            r.error = "Unexpected response (QR=0)";
            return r;
        }
        if (tc == 1) {
            r.error = "Truncated response (TC=1)";
            return r;
        }
        if (rcode == 3) { // NXDOMAIN
            r.notFound = true;
            r.authoritative = (aa == 1);
            r.answerCount = 0;
            r.additionalCount = 0;
            r.ok = true;
            return r;
        }
        if (rcode != 0) {
            r.error = "Server returned error RCODE=" + rcode;
            return r;
        }

        r.authoritative = (aa == 1);

        int p = 12;

        // Skip Question section(s) (usually QD=1)
        try {
            for (int i = 0; i < qd; i++) {
                NameDec qn = decodeName(resp, p);
                p = qn.next + 4; // skip QTYPE (2) + QCLASS (2)
            }
        } catch (Exception e) {
            r.error = "Malformed Question section: " + e.getMessage();
            return r;
        }

        // Answer RRs
        try {
            for (int i = 0; i < an; i++) {
                RRParse rr = parseRR(resp, p, r.authoritative);
                p = rr.next;
                if (rr.line != null) r.answers.add(rr.line);
            }
        } catch (Exception e) {
            r.error = "Malformed Answer section: " + e.getMessage();
            return r;
        }

        // Authority section (ns) — not required to print; skip over it correctly
        try {
            for (int i = 0; i < ns; i++) {
                // Even if we don't print, we must advance p over NAME, TYPE, CLASS, TTL, RDLENGTH, RDATA
                NameDec skipName = decodeName(resp, p);
                int q = skipName.next;
                int type = u16(resp, q); q += 2;
                q += 2;            // class
                q += 4;            // ttl
                int rdlen = u16(resp, q); q += 2;
                q += rdlen;        // rdata
                p = q;
            }
        } catch (Exception e) {
            r.error = "Malformed Authority section: " + e.getMessage();
            return r;
        }

        // Additional RRs
        try {
            for (int i = 0; i < ar; i++) {
                RRParse rr = parseRR(resp, p, r.authoritative);
                p = rr.next;
                if (rr.line != null) r.additionals.add(rr.line);
            }
        } catch (Exception e) {
            r.error = "Malformed Additional section: " + e.getMessage();
            return r;
        }

        r.answerCount = r.answers.size();
        r.additionalCount = r.additionals.size();
        r.ok = true;

// Only mark NOTFOUND if RCODE == 0 (no error)
        if (rcode == 0 && r.answerCount == 0 && !r.notFound) {
            r.notFound = true;
        }
        return r;

    }
}
