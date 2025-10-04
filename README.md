# ECSE316-Assignment1

# DnsClient

M# DnsClient

Minimal DNS client over UDP (Java). Builds DNS queries manually (no DNS libs), sends them to a DNS server, and prints results in the required format.

## Build
$ javac DnsClient.java

## Usage
$ java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name
- -t  timeout seconds (default 5)
- -r  max retries (default 3)
- -p  UDP port (default 53)
- -mx query MX records
- -ns query NS records
  (If neither flag is set → A (IPv4) query)
- @server DNS server IPv4 (e.g., @8.8.8.8)
- name    domain (e.g., www.mcgill.ca)

## Examples
# A (default)
$ java DnsClient @8.8.8.8 www.mcgill.ca

# MX
$ java DnsClient -mx @8.8.8.8 mcgill.ca

# NS with custom timeout/retries
$ java DnsClient -ns -t 3 -r 1 @8.8.8.8 mcgill.ca

## Output shape
DnsClient sending request for <name>
Server: <server IP>
Request type: <A|MX|NS>
Response received after <secs> seconds (<retries> retries)
***Answer Section (N records)***
IP    <ip>    <ttl>    <auth|nonauth>
CNAME <alias> <ttl>    <auth|nonauth>
MX    <host>  <pref>   <ttl> <auth|nonauth>
NS    <host>  <ttl>    <auth|nonauth>
***Additional Section (M records)***   # if present
NOTFOUND                               # if no answers
ERROR   <description>                  # on errors

## Notes
- Uses DatagramSocket with setSoTimeout + retry loop.
- Uses InetAddress.getByAddress(byte[4]) for @server (no name lookups).
- Supports A/MX/NS queries; prints A/CNAME/MX/NS; manual packet build/parse.
