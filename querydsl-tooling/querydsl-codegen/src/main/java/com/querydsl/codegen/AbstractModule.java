/*
 * Copyright 2015, The Querydsl Team (http://www.querydsl.com/team)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.querydsl.codegen;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.jetbrains.annotations.NotNull;

/**
 * {@code AbstractModule} provides a base class for annotation based dependency injection
 *
 * @author tiwe
 */
public abstract class AbstractModule {

  private final Map<Class<?>, Object> instances = new HashMap<>();

  private final Map<Class<?>, Class<?>> bindings = new HashMap<>();

  private final Map<String, Object> namedInstances = new HashMap<>();

  private final Map<String, Class<?>> namedBindings = new HashMap<>();

  public AbstractModule() {
    configure();
  }

  public final <T> AbstractModule bind(Class<T> clazz) {
    if (clazz.isInterface()) {
      throw new IllegalArgumentException("Interfaces can't be instantiated");
    }
    bind(clazz, clazz);
    return this;
  }

  public final <T> AbstractModule bind(String name, Class<? extends T> implementation) {
    namedBindings.put(name, implementation);
    return this;
  }

  public final <T> AbstractModule bind(String name, T implementation) {
    namedInstances.put(name, implementation);
    return this;
  }

  public final <T> AbstractModule bindInstance(String name, Class<? extends T> implementation) {
    namedInstances.put(name, implementation);
    return this;
  }

  public final <T> AbstractModule bind(Class<T> iface, Class<? extends T> implementation) {
    bindings.put(iface, implementation);
    return this;
  }

  public final <T> AbstractModule bind(Class<T> iface, T implementation) {
    instances.put(iface, implementation);
    return this;
  }

  public final void loadExtensions() {
    ServiceLoader<Extension> loader =
        ServiceLoader.load(Extension.class, Extension.class.getClassLoader());

    for (Extension extension : loader) {
      extension.addSupport(this);
    }
  }

  protected abstract void configure();

  @SuppressWarnings("unchecked")
  public final <T> T get(Class<T> iface) {
    if (instances.containsKey(iface)) {
      return (T) instances.get(iface);
    } else if (bindings.containsKey(iface)) {
      Class<?> implementation = bindings.get(iface);
      var instance = (T) createInstance(implementation);
      instances.put(iface, instance);
      return instance;
    } else {
      throw new IllegalArgumentException(iface.getName() + " is not registered");
    }
  }

  @SuppressWarnings("unchecked")
  public final <T> T get(Class<T> iface, String name) {
    if (namedInstances.containsKey(name)) {
      return (T) namedInstances.get(name);
    } else if (namedBindings.containsKey(name)) {
      Class<?> implementation = namedBindings.get(name);
      if (implementation != null) {
        var instance = (T) createInstance(implementation);
        namedInstances.put(name, instance);
        return instance;
      } else {
        return null;
      }
    } else {
      throw new IllegalArgumentException(iface.getName() + " " + name + " is not registered");
    }
  }

  private <T> T createInstance(Class<? extends T> implementation) {
    var constructor =
        Arrays.stream(implementation.getConstructors())
            .filter(c -> c.isAnnotationPresent(Inject.class))
            .findFirst()
            .orElseGet(() -> fallbackToDefaultConstructor(implementation));

    var args = getConstructorParameters(constructor);
    try {
      //noinspection unchecked
      return (T) constructor.newInstance(args);
      // TODO : populate fields as well?!?
    } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private Object @NotNull [] getConstructorParameters(Constructor<?> constructor) {
    Class<?>[] parameterTypes = constructor.getParameterTypes();
    Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
    var args = new Object[parameterTypes.length];
    for (var i = 0; i < parameterTypes.length; i++) {
      var named = getNamedAnnotation(parameterAnnotations[i]);
      if (named != null) {
        args[i] = get(parameterTypes[i], named.value());
      } else {
        args[i] = get(parameterTypes[i]);
      }
    }
    return args;
  }

  private static <T> @NotNull Constructor<? extends T> fallbackToDefaultConstructor(
      Class<? extends T> implementation) {
    try {
      return implementation.getConstructor();
    } catch (SecurityException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private Named getNamedAnnotation(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType().equals(Named.class)) {
        return (Named) annotation;
      }
    }
    return null;
  }
}
