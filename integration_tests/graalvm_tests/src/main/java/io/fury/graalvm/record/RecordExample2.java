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

package io.fury.graalvm.record;

import io.fury.Fury;
import io.fury.util.Preconditions;

import java.util.List;
import java.util.Map;

public class RecordExample2 {
  private record Record (
    int f1,
    String f2,
    List<String> f3,
    Map<String, Long> f4) {
  }

  static Fury fury;

  static {
    fury = Fury.builder().requireClassRegistration(true).build();
    // register and generate serializer code.
    fury.register(Record.class, true);
    fury.register(Foo.class, true);
  }

  public static void main(String[] args) {
    Record record = new Record(10, "abc", List.of("str1", "str2"), Map.of("k1", 10L, "k2", 20L));
    byte[] bytes = fury.serialize(record);
    Object o = fury.deserialize(bytes);
    Preconditions.checkArgument(record.equals(o));
    Foo foo = new Foo(10, "abc", List.of("str1", "str2"), Map.of("k1", 10L, "k2", 20L));
    Object o2 = fury.deserialize(fury.serialize(foo));
    Preconditions.checkArgument(foo.equals(o2));
  }
}
