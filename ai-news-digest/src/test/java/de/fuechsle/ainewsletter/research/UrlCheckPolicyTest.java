package de.fuechsle.ainewsletter.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** IP literals only: no DNS query in this test. */
class UrlCheckPolicyTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0.0.0.0",
        "0.1.2.3",
        "10.0.0.1",
        "100.64.0.1",
        "100.127.255.254",
        "127.0.0.1",
        "169.254.169.254",
        "172.16.0.1",
        "172.31.255.255",
        "192.0.0.8",
        "192.168.1.1",
        "198.18.0.1",
        "224.0.0.1",
        "240.0.0.1",
        "255.255.255.255",
        "::",
        "::1",
        "::127.0.0.1",
        "::ffff:10.0.0.1",
        "64:ff9b::a00:1",
        "fc00::1",
        "fd12:3456::1",
        "fe80::1",
        "fec0::1",
        "ff02::1"
      })
  void nonPublicAddressesAreBlocked(String address) throws UnknownHostException {
    assertThat(UrlCheckPolicy.PUBLIC_ONLY.allowsAddress(InetAddress.getByName(address))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"8.8.8.8", "100.63.255.255", "100.128.0.1", "172.32.0.1", "2606:4700::1111"})
  void publicAddressesAreAllowed(String address) throws UnknownHostException {
    assertThat(UrlCheckPolicy.PUBLIC_ONLY.allowsAddress(InetAddress.getByName(address))).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "https, -1, true",
    "http, -1, true",
    "https, 443, true",
    "http, 80, true",
    "https, 8443, false",
    "http, 443, false",
    "ftp, -1, false",
    "file, -1, false",
    ", -1, false"
  })
  void onlyHttpOnDefaultPorts(String scheme, int port, boolean allowed) {
    assertThat(UrlCheckPolicy.PUBLIC_ONLY.allowsTarget(scheme, port)).isEqualTo(allowed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"127.0.0.1", "::1"})
  void testPolicyAllowsOnlyLoopbackInAddition(String address) throws UnknownHostException {
    UrlCheckPolicy stub = new UrlCheckPolicy(true, false);

    assertThat(stub.allowsAddress(InetAddress.getByName(address))).isTrue();
    assertThat(stub.allowsAddress(InetAddress.getByName("10.0.0.1"))).isFalse();
    assertThat(stub.allowsAddress(InetAddress.getByName("169.254.169.254"))).isFalse();
    assertThat(stub.allowsTarget("http", 8080)).isTrue();
  }
}
