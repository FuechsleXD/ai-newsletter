package de.fuechsle.ainewsletter.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Gotenberg and Mailpit for tests, with the same images and options as docker-compose.yml. Both are
 * local containers, nothing leaves the machine (golden rule 10). Started once per JVM.
 *
 * <p>Mailpit only accepts recipients whose address starts with {@code ok}; every other recipient is
 * rejected by SMTP, which makes failing deliveries reproducible.
 */
public final class RenderAndMailContainers {

  public static final String ACCEPTED_PREFIX = "ok";

  public static final GenericContainer<?> GOTENBERG =
      new GenericContainer<>("gotenberg/gotenberg:8.40.0-chromium")
          .withCommand(
              "gotenberg",
              "--chromium-deny-public-ips=true",
              "--chromium-deny-private-ips=true",
              "--chromium-disable-javascript=true",
              "--api-disable-download-from=true",
              "--webhook-disable=true")
          .withExposedPorts(3000)
          .waitingFor(Wait.forHttp("/health").forPort(3000));

  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit:v1.34.0")
          .withCommand("--smtp-allowed-recipients=^" + ACCEPTED_PREFIX)
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/livez").forPort(8025));

  static {
    GOTENBERG.start();
    MAILPIT.start();
  }

  private RenderAndMailContainers() {}

  public static String gotenbergUrl() {
    return "http://" + GOTENBERG.getHost() + ":" + GOTENBERG.getMappedPort(3000);
  }

  public static String mailpitApiUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }

  public static int smtpPort() {
    return MAILPIT.getMappedPort(1025);
  }
}
