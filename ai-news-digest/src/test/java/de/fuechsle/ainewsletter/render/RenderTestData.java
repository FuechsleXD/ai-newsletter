package de.fuechsle.ainewsletter.render;

import de.fuechsle.ainewsletter.aggregate.DigestAggregate;
import de.fuechsle.ainewsletter.support.Fixtures;
import de.fuechsle.ainewsletter.support.TestProperties;
import java.time.LocalDate;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tools.jackson.databind.json.JsonMapper;

/** Example digests ({@code fixtures/digest/short|normal|long.json}) and a template engine. */
final class RenderTestData {

  static final LocalDate FROM = LocalDate.of(2026, 11, 16);
  static final LocalDate TO = LocalDate.of(2026, 11, 22);
  static final LocalDate CREATED = LocalDate.of(2026, 11, 23);

  private RenderTestData() {}

  static DigestAggregate aggregate(String name) {
    return new JsonMapper()
        .readValue(Fixtures.read("digest/" + name + ".json"), DigestAggregate.class);
  }

  static DigestView view(String name) {
    return view(aggregate(name));
  }

  static DigestView view(DigestAggregate aggregate) {
    return new DigestViewFactory(TestProperties.defaults())
        .create(aggregate, "Java", FROM, TO, CREATED);
  }

  /** Same resolver settings as the Spring Boot default (classpath:templates/, .html). */
  static ITemplateEngine templateEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
