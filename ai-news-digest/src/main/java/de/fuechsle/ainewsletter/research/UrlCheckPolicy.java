package de.fuechsle.ainewsletter.research;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Which targets the URL check may connect to (SSRF protection, docs/06-security.md). URLs come from
 * an LLM and are not trustworthy: only {@code http}/{@code https}, only the default ports, and no
 * address that is not publicly routable (loopback, private, link-local including the instance
 * metadata 169.254.169.254, carrier-grade NAT, multicast, reserved, and their IPv6 counterparts).
 *
 * @param allowLoopback only for tests against a local stub server; every other non-public address
 *     stays blocked
 * @param defaultPortsOnly {@code false} only for tests against a local stub server
 */
public record UrlCheckPolicy(boolean allowLoopback, boolean defaultPortsOnly) {

  /** The policy of the application. */
  public static final UrlCheckPolicy PUBLIC_ONLY = new UrlCheckPolicy(false, true);

  /** Whether scheme and port are allowed; {@code port} is -1 if the URL has none. */
  boolean allowsTarget(String scheme, int port) {
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      return false;
    }
    if (port == -1 || !defaultPortsOnly) {
      return true;
    }
    return ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
  }

  boolean allowsAddress(InetAddress address) {
    return (allowLoopback && address.isLoopbackAddress()) || isPublic(address);
  }

  /** Whether the address is publicly routable. */
  static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      return isPublicIpv4(bytes);
    }
    if (address instanceof Inet6Address) {
      return isPublicIpv6(bytes);
    }
    return false;
  }

  private static boolean isPublicIpv4(byte[] b) {
    int first = b[0] & 0xff;
    int second = b[1] & 0xff;
    return first != 0 // this network
        && !(first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
        && !(first == 192 && second == 0 && (b[2] & 0xff) == 0) // IETF protocol assignments
        && !(first == 198 && (second == 18 || second == 19)) // benchmarking
        && first < 240; // reserved and broadcast
  }

  private static boolean isPublicIpv6(byte[] b) {
    int first = b[0] & 0xff;
    if ((first & 0xfe) == 0xfc) { // unique local fc00::/7
      return false;
    }
    if (embedsIpv4(b, 0, 12) || isNat64(b)) {
      return isPublic(ipv4(b));
    }
    return true;
  }

  /** IPv4-compatible ::a.b.c.d (IPv4-mapped addresses are already Inet4Address in Java). */
  private static boolean embedsIpv4(byte[] b, int from, int to) {
    for (int i = from; i < to; i++) {
      if (b[i] != 0) {
        return false;
      }
    }
    return true;
  }

  /** NAT64 well-known prefix 64:ff9b::/96. */
  private static boolean isNat64(byte[] b) {
    return (b[0] & 0xff) == 0x00
        && (b[1] & 0xff) == 0x64
        && (b[2] & 0xff) == 0xff
        && (b[3] & 0xff) == 0x9b
        && embedsIpv4(b, 4, 12);
  }

  private static InetAddress ipv4(byte[] b) {
    try {
      return InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]});
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }
}
