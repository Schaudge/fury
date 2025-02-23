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

package io.fury.graalvm;

import io.fury.Fury;
import io.fury.util.Preconditions;

import java.util.List;
import java.util.Map;

public class Example {
  static Fury fury;

  static {
    fury = Fury.builder().requireClassRegistration(true).build();
    // register and generate serializer code.
    fury.register(Foo.class, true);
  }

  public static void main(String[] args) {
    Preconditions.checkArgument("abc".equals(fury.deserialize(fury.serialize("abc"))));
    Preconditions.checkArgument(List.of(1,2,3).equals(fury.deserialize(fury.serialize(List.of(1,2,3)))));
    Map<String, Integer> map = Map.of("k1", 1, "k2", 2);
    Preconditions.checkArgument(map.equals(fury.deserialize(fury.serialize(map))));
    Foo foo = new Foo(10, "abc", List.of("str1", "str2"), Map.of("k1", 10L, "k2", 20L));
    byte[] bytes = fury.serialize(foo);
    System.out.println(fury.getClassResolver().getSerializer(Foo.class));
    Object o = fury.deserialize(bytes);
    System.out.println(foo);
    System.out.println(o);
    Preconditions.checkArgument(foo.equals(o));
  }
}
