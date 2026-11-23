package de.fuechsle.ainewsletter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the AI News Digest backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiNewsDigestApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiNewsDigestApplication.class, args);
  }
}
