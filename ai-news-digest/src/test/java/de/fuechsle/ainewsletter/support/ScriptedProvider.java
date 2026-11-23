package de.fuechsle.ainewsletter.support;

import de.fuechsle.ainewsletter.provider.ModelInvocation;
import de.fuechsle.ainewsletter.provider.ModelResponse;
import de.fuechsle.ainewsletter.provider.NewsProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Provider {@code TEST} for unit tests: replays prepared answers or errors in order and records
 * every invocation.
 */
public final class ScriptedProvider implements NewsProvider {

  public static final String CODE = "TEST";

  private final Deque<Supplier<ModelResponse>> answers = new ArrayDeque<>();
  private final List<ModelInvocation> invocations = new ArrayList<>();

  public void answer(ModelResponse response) {
    answers.add(() -> response);
  }

  public void fail(RuntimeException error) {
    answers.add(() -> {
      throw error;
    });
  }

  public List<ModelInvocation> invocations() {
    return invocations;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public ModelResponse call(ModelInvocation invocation) {
    invocations.add(invocation);
    return answers.removeFirst().get();
  }
}
