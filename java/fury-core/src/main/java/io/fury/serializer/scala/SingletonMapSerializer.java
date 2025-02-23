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

package io.fury.serializer.scala;

import io.fury.Fury;
import io.fury.memory.MemoryBuffer;
import io.fury.serializer.collection.AbstractMapSerializer;
import io.fury.util.GraalvmSupport;
import io.fury.util.Platform;
import io.fury.util.Preconditions;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Singleton serializer for scala map. We need this serializer for fury jit serialization, otherwise
 * the case exception will happen is an empty collection is being serialized as a field of an
 * object.
 *
 * @author chaokunyang
 */
@SuppressWarnings("rawtypes")
public class SingletonMapSerializer extends AbstractMapSerializer {
  private final Field field;
  private long offset = -1;

  public SingletonMapSerializer(Fury fury, Class cls) {
    super(fury, cls, false);
    try {
      field = type.getDeclaredField("MODULE$");
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(type + " doesn't have `MODULE$` field", e);
    }
  }

  @Override
  public Map onMapWrite(MemoryBuffer buffer, Object value) {
    throw new IllegalStateException("unreachable");
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {}

  @Override
  public Object read(MemoryBuffer buffer) {
    long offset = this.offset;
    if (offset == -1) {
      Preconditions.checkArgument(!GraalvmSupport.isGraalBuildtime());
      offset = this.offset = Platform.UNSAFE.staticFieldOffset(field);
    }
    return Platform.getObject(type, offset);
  }

  @Override
  public Object onMapRead(Map map) {
    throw new IllegalStateException("unreachable");
  }
}
