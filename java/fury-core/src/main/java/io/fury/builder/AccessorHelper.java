/*
 * Copyright 2023 The Fury Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fury.builder;

import static io.fury.codegen.CodeGenerator.sourcePkgLevelAccessible;

import io.fury.codegen.CodeGenerator;
import io.fury.codegen.CodegenContext;
import io.fury.codegen.CompileUnit;
import io.fury.codegen.JaninoUtils;
import io.fury.type.Descriptor;
import io.fury.util.ClassLoaderUtils;
import io.fury.util.LoggerFactory;
import io.fury.util.Preconditions;
import io.fury.util.ReflectionUtils;
import io.fury.util.StringUtils;
import io.fury.util.record.RecordUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;

/**
 * Define accessor helper methods in beanClass's classloader and same package to avoid reflective
 * call overhead. {@link sun.misc.Unsafe} is another method to avoid reflection cost.
 *
 * @see io.fury.util.UnsafeFieldAccessor
 * @author chaokunyang
 */
public class AccessorHelper {
  private static final Logger LOG = LoggerFactory.getLogger(AccessorHelper.class);
  private static final WeakHashMap<Class<?>, Boolean> defineAccessorStatus = new WeakHashMap<>();
  private static final WeakHashMap<Class<?>, Object> defineAccessorLock = new WeakHashMap<>();
  private static final Object defineLock = new Object();

  private static final String OBJ_NAME = "obj";
  private static final String FIELD_VALUE = "fieldValue";

  public static String accessorClassName(Class<?> beanClass) {
    String name =
        ReflectionUtils.getClassNameWithoutPackage(beanClass)
            + "FuryAccessor_"
            + CodeGenerator.getClassUniqueId(beanClass);
    return name.replace("$", "_");
  }

  public static String qualifiedAccessorClassName(Class<?> beanClass) {
    String pkgName = CodeGenerator.getPackage(beanClass);
    if (StringUtils.isNotBlank(pkgName)) {
      return pkgName + "." + accessorClassName(beanClass);
    } else {
      return accessorClassName(beanClass);
    }
  }

  /** Don't gen code for super classes. */
  public static String genCode(Class<?> beanClass) {
    CodegenContext ctx = new CodegenContext();
    ctx.setPackage(CodeGenerator.getPackage(beanClass));
    String className = accessorClassName(beanClass);
    ctx.setClassName(className);
    boolean isRecord = RecordUtils.isRecord(beanClass);
    // filter out super classes
    Collection<Descriptor> descriptors = Descriptor.getAllDescriptorsMap(beanClass, false).values();
    for (Descriptor descriptor : descriptors) {
      if (Modifier.isPrivate(descriptor.getModifiers())) {
        continue;
      }
      boolean accessible = sourcePkgLevelAccessible(descriptor.getRawType());
      {
        // getter
        String methodName = descriptor.getName();
        String codeBody;
        Class<?> returnType = accessible ? descriptor.getRawType() : Object.class;
        if (isRecord) {
          codeBody =
              StringUtils.format(
                  "return ${obj}.${fieldName}();",
                  "obj",
                  OBJ_NAME,
                  "fieldName",
                  descriptor.getName());
        } else {
          codeBody =
              StringUtils.format(
                  "return ${obj}.${fieldName};",
                  "obj",
                  OBJ_NAME,
                  "fieldName",
                  descriptor.getName());
        }
        ctx.addStaticMethod(methodName, codeBody, returnType, beanClass, OBJ_NAME);
      }
      if (accessible) {
        String methodName = descriptor.getName();
        String codeBody =
            StringUtils.format(
                "${obj}.${fieldName} = ${fieldValue};",
                "obj",
                OBJ_NAME,
                "fieldName",
                descriptor.getName(),
                "fieldValue",
                FIELD_VALUE);
        ctx.addStaticMethod(
            methodName,
            codeBody,
            void.class,
            beanClass,
            OBJ_NAME,
            descriptor.getRawType(),
            FIELD_VALUE);
      }
      // getter/setter may lose some inner state of an object, so we set them to null to avoid
      // creating getter/setter accessor.
    }

    return ctx.genCode();
  }

  /** Don't define accessor for super classes, because they maybe in different package. */
  public static boolean defineAccessorClass(Class<?> beanClass) {
    ClassLoader classLoader = beanClass.getClassLoader();
    if (classLoader == null) {
      // Maybe return null if this class was loaded by the bootstrap class loader.
      return false;
    }
    String qualifiedClassName = qualifiedAccessorClassName(beanClass);
    try {
      classLoader.loadClass(qualifiedClassName);
      return true;
    } catch (ClassNotFoundException ignored) {
      Object lock;
      synchronized (defineLock) {
        if (defineAccessorStatus.containsKey(beanClass)) {
          return defineAccessorStatus.get(beanClass);
        } else {
          lock = getDefineLock(beanClass);
        }
      }
      synchronized (lock) {
        if (defineAccessorStatus.containsKey(beanClass)) {
          return defineAccessorStatus.get(beanClass);
        }
        long startTime = System.nanoTime();
        String code = genCode(beanClass);
        long durationMs = (System.nanoTime() - startTime) / 1000_000;
        LOG.info("Generate code {} take {} ms", qualifiedClassName, durationMs);
        String pkg = CodeGenerator.getPackage(beanClass);
        CompileUnit compileUnit = new CompileUnit(pkg, accessorClassName(beanClass), code);
        Map<String, byte[]> classByteCodes = JaninoUtils.toBytecode(classLoader, compileUnit);
        boolean succeed =
            ClassLoaderUtils.tryDefineClassesInClassLoader(
                    qualifiedClassName,
                    beanClass,
                    classLoader,
                    classByteCodes.values().iterator().next())
                != null;
        defineAccessorStatus.put(beanClass, succeed);
        if (!succeed) {
          LOG.info("Define accessor {} in classloader {} failed.", qualifiedClassName, classLoader);
        }
        return succeed;
      }
    }
  }

  private static Object getDefineLock(Class<?> clazz) {
    synchronized (defineLock) {
      return defineAccessorLock.computeIfAbsent(clazz, k -> new Object());
    }
  }

  public static Class<?> getAccessorClass(Class<?> beanClass) {
    Preconditions.checkArgument(defineAccessorClass(beanClass));
    ClassLoader classLoader = beanClass.getClassLoader();
    String qualifiedClassName = qualifiedAccessorClassName(beanClass);
    try {
      return classLoader.loadClass(qualifiedClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("unreachable code", e);
    }
  }

  /** Should be invoked only when {@link #defineAccessor} returns true. */
  public static Class<?> getAccessorClass(Field field) {
    Class<?> beanClass = field.getDeclaringClass();
    return getAccessorClass(beanClass);
  }

  /** Should be invoked only when {@link #defineAccessor} returns true. */
  public static Class<?> getAccessorClass(Method method) {
    Class<?> beanClass = method.getDeclaringClass();
    return getAccessorClass(beanClass);
  }

  public static boolean defineAccessor(Field field) {
    Class<?> beanClass = field.getDeclaringClass();
    return defineAccessorClass(beanClass);
  }

  public static boolean defineAccessor(Method method) {
    Class<?> beanClass = method.getDeclaringClass();
    return defineAccessorClass(beanClass);
  }

  public static boolean defineSetter(Field field) {
    if (ReflectionUtils.isPrivate(field.getType()) || !sourcePkgLevelAccessible(field.getType())) {
      return false;
    }
    Class<?> beanClass = field.getDeclaringClass();
    return defineAccessorClass(beanClass);
  }

  public static boolean defineSetter(Method method) {
    if (ReflectionUtils.isPrivate(method.getReturnType())
        || !sourcePkgLevelAccessible(method.getReturnType())) {
      return false;
    }
    Class<?> beanClass = method.getDeclaringClass();
    return defineAccessorClass(beanClass);
  }
}
