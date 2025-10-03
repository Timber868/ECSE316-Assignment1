/**
 * DnsClient — Minimal DNS client (UDP)
 *
 * Usage:
 *   java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name
 *
 * Parameters:
 *   -t timeout       (optional) Seconds to wait for a reply before retrying. Default: 5
 *   -r max-retries   (optional) Number of retransmissions on timeout.     Default: 3
 *   -p port          (optional) UDP port of the DNS server.               Default: 53
 *   -mx | -ns        (optional) Query type: MX = mail exchangers, NS = name servers.
 *                    If neither flag is given, the query type is A (IPv4 address).
 *   @server          (required) IPv4 address of the DNS server (a.b.c.d)
 *   name             (required) Domain name to look up (e.g., www.example.com)
 *
 * What it does:
 *   - Builds a DNS query packet by hand (no DNS helper libraries) and sends it via UDP
 *     to @server:port, with a socket timeout and up to max-retries retransmissions.
 *   - Query type depends on flags:
 *       * default → A (IPv4 address)
 *       * -mx    → MX (mail servers + preference)
 *       * -ns    → NS (authoritative name servers)
 *   - Parses the DNS response (verifies header, handles compression) and prints results.
 *
 * Output format (printed to STDOUT, exact strings expected by the grader):
 *   DnsClient sending request for <name>
 *   Server: <server IP>
 *   Request type: <A|MX|NS>
 *   Response received after <seconds> seconds (<retries> retries)
 *   ***Answer Section (N records)***
 *   For each record (tab-separated):
 *     A:   "IP\t<ipv4>\t<ttl>\t<auth|nonauth>"
 *     CNAME:"CNAME\t<alias>\t<ttl>\t<auth|nonauth>"   // printed if present with A queries
 *     MX:  "MX\t<host>\t<pref>\t<ttl>\t<auth|nonauth>"
 *     NS:  "NS\t<host>\t<ttl>\t<auth|nonauth>"
 *   If additional records exist, also print:
 *     ***Additional Section (M records)***
 *   If no answers are found:
 *     NOTFOUND
 *   On errors (bad input, timeouts exceeded, malformed response, etc.):
 *     ERROR\t<description>
 *
 * Notes:
 *   - Use DatagramSocket + setSoTimeout for retries.
 *   - Use InetAddress.getByAddress(byte[4]) for @server (do NOT resolve names here).
 *   - Construct/parse DNS packets manually (header, QNAME, QTYPE, QCLASS, RRs).
 */

class DnsClient {
    static final int TYPE_A=1, TYPE_NS=2, TYPE_MX=15, CLASS_IN=1;
  
    public static void main(String[] args) {
      try {
        // parse CLI: timeout=5, retries=3, port=53, qtype=A; read @server and name  [spec]
        // (1) CLI parse: set timeout=5, retries=3, port=53, qtype=TYPE_A; read @server and name.
        // Validate: at most one of -mx/-ns; require @server + name; else:
        //   System.out.println("ERROR\tIncorrect input syntax: ..."); return;
  
        // (2) Socket + dst:
        // DatagramSocket sock = new DatagramSocket();
        // sock.setSoTimeout(timeoutSec*1000);
        // byte[] ipBytes = parse a.b.c.d → 4 bytes; InetAddress.getByAddress(ipBytes);
  
        // (3) Build query:
        // ByteBuffer bb(BIG_ENDIAN);
        // putShort(ID=random); putShort(flags with RD=1); putShort(1); putShort(0); putShort(0); putShort(0);
        // write QNAME: for each label of name, len byte + ASCII bytes; then 0x00
        // putShort(qtype); putShort(CLASS_IN);
        // byte[] query = Arrays.copyOf(bb.array(), bb.position());
  
        // (4) Retry loop:
        // long t0=System.nanoTime(); int tries=0; byte[] resp=null;
        // while(true){ send DatagramPacket(query,...);
        //   try { receive into buffer; resp=copy; break; }
        //   catch(SocketTimeoutException e){ if(tries++>=maxRetries){ print ERROR "Maximum number of retries X exceeded"; return; } }
        // }
        // double elapsed=(System.nanoTime()-t0)/1e9;
  
        // (5) Parse response:
        // ByteBuffer r=wrap(resp).order(BIG_ENDIAN);
        // check ID matches; check QR=1; rcode==0; read counts qd,an,ns,ar; aa flag
        // pos=12; decode Question name (handle compression) then skip QTYPE+QCLASS
        // loop an times: read NAME (compression), TYPE, CLASS, TTL, RDLENGTH
        //   if TYPE=A and rdlen=4 → print line later as "IP\tx.x.x.x\tTTL\t[auth|nonauth]"
        //   if TYPE=CNAME or NS → RDATA is domain name (compression)
        //   if TYPE=MX → pref(u16) + domain name
        // collect Additional similarly if you want to print it
  
        // (6) Output (exact text):
        // System.out.println("DnsClient sending request for "+name);
        // System.out.println("Server: "+serverIp);
        // System.out.println("Request type: "+(qtype==1?"A":qtype==2?"NS":"MX"));
        // System.out.println("Response received after "+format(elapsed)+" seconds ("+tries+" retries)");
        // if answers==0 → System.out.println("NOTFOUND")
        // else:
        //   System.out.println("***Answer Section ("+answersCount+" records)***");
        //   print tab-separated lines per type
        // if additional>0 → print "***Additional Section (M records)***" then its lines
      } catch (Exception e) {
        // System.out.println("ERROR\t"+e.getMessage());
      }
    }
  }
  