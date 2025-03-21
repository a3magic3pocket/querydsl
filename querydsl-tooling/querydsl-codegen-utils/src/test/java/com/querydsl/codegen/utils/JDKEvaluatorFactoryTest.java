/*
 * Copyright (c) 2010 Mysema Ltd.
 * All rights reserved.
 *
 */
package com.querydsl.codegen.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.codegen.utils.support.ClassLoaderWrapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class JDKEvaluatorFactoryTest {

  public static class TestEntity {

    private final String name;

    public TestEntity(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private EvaluatorFactory factory;

  private List<String> names = Arrays.asList("a", "b");

  private List<Class<?>> ints = Arrays.<Class<?>>asList(int.class, int.class);

  private List<Class<?>> strings = Arrays.<Class<?>>asList(String.class, String.class);

  private List<Class<?>> string_int = Arrays.<Class<?>>asList(String.class, int.class);

  @Before
  public void setUp() throws IOException {
    factory = new JDKEvaluatorFactory(getClass().getClassLoader());
  }

  @Test
  public void Simple() {
    for (String expr : Arrays.asList("a.equals(b)", "a.startsWith(b)", "a.equalsIgnoreCase(b)")) {
      var start = System.currentTimeMillis();
      evaluate(
          expr,
          boolean.class,
          names,
          strings,
          Arrays.asList("a", "b"),
          Collections.<String, Object>emptyMap());
      var duration = System.currentTimeMillis() - start;
      System.err.println(expr + " took " + duration + "ms\n");
    }

    for (String expr : Arrays.asList("a != b", "a < b", "a > b", "a <= b", "a >= b")) {
      var start = System.currentTimeMillis();
      evaluate(
          expr,
          boolean.class,
          names,
          ints,
          Arrays.asList(0, 1),
          Collections.<String, Object>emptyMap());
      var duration = System.currentTimeMillis() - start;
      System.err.println(expr + " took " + duration + "ms\n");
    }
  }

  @Test
  public void Results() {
    // String + String
    test("a + b", String.class, names, strings, Arrays.asList("Hello ", "World"), "Hello World");

    // String + int
    test(
        "a.substring(b)",
        String.class,
        names,
        string_int,
        Arrays.<Object>asList("Hello World", 6),
        "World");

    // int + int
    test("a + b", int.class, names, ints, Arrays.asList(1, 2), 3);
  }

  @Test
  public void WithConstants() {
    Map<String, Object> constants = new HashMap<>();
    constants.put("x", "Hello World");
    List<Class<?>> types = Arrays.<Class<?>>asList(String.class);
    List<String> names = Arrays.asList("a");
    assertThat(
            evaluate(
                "a.equals(x)",
                boolean.class,
                names,
                types,
                Arrays.asList("Hello World"),
                constants))
        .isEqualTo(Boolean.TRUE);
    assertThat(
            evaluate("a.equals(x)", boolean.class, names, types, Arrays.asList("Hello"), constants))
        .isEqualTo(Boolean.FALSE);
  }

  @Test
  public void CustomType() {
    test(
        "a.getName()",
        String.class,
        Collections.singletonList("a"),
        Collections.<Class<?>>singletonList(TestEntity.class),
        Arrays.asList(new TestEntity("Hello World")),
        "Hello World");
  }

  private void test(
      String source,
      Class<?> projectionType,
      List<String> names,
      List<Class<?>> types,
      List<?> args,
      Object expectedResult) {
    assertThat(
            evaluate(
                source, projectionType, names, types, args, Collections.<String, Object>emptyMap()))
        .isEqualTo(expectedResult);
  }

  private Object evaluate(
      String source,
      Class<?> projectionType,
      List<String> names,
      List<Class<?>> types,
      List<?> args,
      Map<String, Object> constants) {
    Evaluator<?> evaluator =
        factory.createEvaluator(
            "return " + source + ";",
            projectionType,
            names.toArray(String[]::new),
            types.toArray(Class[]::new),
            constants);
    return evaluator.evaluate(args.toArray());
  }

  @Test
  public void CustomClassLoader() {
    var source = "a.getName()";
    var projectionType = String.class;
    var names = Collections.singletonList("a");
    var types = Collections.<Class<?>>singletonList(TestEntity.class);
    var args = Arrays.asList(new TestEntity("Hello World"));

    var classLoader = new ClassLoaderWrapper(getClass().getClassLoader());
    var factory = new JDKEvaluatorFactory(classLoader);

    assertThat(
            factory
                .createEvaluator(
                    "return " + source + ";",
                    projectionType,
                    names.toArray(String[]::new),
                    types.toArray(Class[]::new),
                    Collections.<String, Object>emptyMap())
                .evaluate(args.toArray()))
        .isEqualTo("Hello World");
  }
}
