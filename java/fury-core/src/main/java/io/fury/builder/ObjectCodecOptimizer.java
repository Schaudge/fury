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

import io.fury.annotation.Internal;
import io.fury.codegen.CodegenContext;
import io.fury.codegen.Expression;
import io.fury.codegen.ExpressionOptimizer;
import io.fury.collection.Tuple3;
import io.fury.type.Descriptor;
import io.fury.type.DescriptorGrouper;
import io.fury.util.function.SerializableSupplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * We took stats for some fields with reference enabled, and split methods based on those metrics.
 * If reference is disabled, the split methods will be small and jit-inline still applies. We don't
 * take stats for disabled reference to reduce complexities: Following stats are for reference only,
 * and it's not precise.
 *
 * <ul>
 *   <li>compiled code for writing every primitive is 22/21 bytes.
 *   <li>compiled code for write/read every boxed primitive is 38/35 bytes.
 *   <li>compiled code for write/read every boxed primitive with ref_tracking is 40/81 bytes.
 *   <li>compiled code for write/read every final field on average is 41/82 bytes.
 *   <li>compiled code for write/read every {@literal List<primitive>} on average is 191/201 bytes.
 *   <li>compiled code for write/read every {@literal List<primitive>} with boxed ref_tracking on
 *       average is 194/240 bytes.
 *   <li>compiled code for write/read every {@literal List<final>} on average is 196/238 bytes.
 *   <li>compiled code for write/read every {@literal Map<boxed, boxed>} on average is 270/196
 *       bytes.
 *   <li>compiled code for write/read every {@literal Map<boxed, boxed>} with boxed ref_tracking on
 *       average is 266/296 bytes.
 *   <li>compiled code for write/read every {@literal Map<final, final>} on average is 268/292
 *       bytes.
 *   <li>compiled code for write/read every non-final field on average is 41/75 bytes.
 * </ul>
 *
 * <p>Split heuristics based on previous code stats. If code stats changes, split heuristics should
 * update too.
 *
 * @see #buildGroups() for detailed heuristic rules.
 * @author chaokunyang
 */
@Internal
public class ObjectCodecOptimizer extends ExpressionOptimizer {
  private final Class<?> cls;
  private final boolean boxedRefTracking;
  private final CodegenContext ctx;
  final DescriptorGrouper descriptorGrouper;
  final List<List<Descriptor>> primitiveGroups = new ArrayList<>();
  final List<List<Descriptor>> boxedWriteGroups = new ArrayList<>();
  final List<List<Descriptor>> boxedReadGroups = new ArrayList<>();
  final List<List<Descriptor>> finalWriteGroups = new ArrayList<>();
  final List<List<Descriptor>> finalReadGroups = new ArrayList<>();
  final List<List<Descriptor>> otherWriteGroups = new ArrayList<>();
  final List<List<Descriptor>> otherReadGroups = new ArrayList<>();

  ObjectCodecOptimizer(
      Class<?> cls,
      DescriptorGrouper descriptorGrouper,
      boolean boxedRefTracking,
      CodegenContext ctx) {
    this.cls = cls;
    this.descriptorGrouper = descriptorGrouper;
    this.boxedRefTracking = boxedRefTracking;
    this.ctx = ctx;
    buildGroups();
  }

  /**
   * Split the method to improve jvm jit-inline. See jvm jit log de-optimization: hot method too
   * big, size > DesiredMethodLimit
   */
  private void buildGroups() {
    // Note get field value also took some byte code if not public.
    List<Descriptor> primitiveDescriptorsList =
        new ArrayList<>(descriptorGrouper.getPrimitiveDescriptors());
    while (primitiveDescriptorsList.size() > 0) {
      int endIndex = Math.min(24, primitiveDescriptorsList.size());
      primitiveGroups.add(primitiveDescriptorsList.subList(0, endIndex));
      primitiveDescriptorsList =
          primitiveDescriptorsList.subList(endIndex, primitiveDescriptorsList.size());
    }
    int boxedWriteWeight = 7;
    int boxedReadWeight = 7;
    if (boxedRefTracking) {
      boxedReadWeight = 4;
    }
    List<Tuple3<List<Descriptor>, Integer, List<List<Descriptor>>>> groups =
        Arrays.asList(
            Tuple3.of(
                new ArrayList<>(descriptorGrouper.getBoxedDescriptors()),
                boxedWriteWeight,
                boxedWriteGroups),
            Tuple3.of(
                new ArrayList<>(descriptorGrouper.getBoxedDescriptors()),
                boxedReadWeight,
                boxedReadGroups),
            Tuple3.of(
                new ArrayList<>(descriptorGrouper.getFinalDescriptors()), 9, finalWriteGroups),
            Tuple3.of(new ArrayList<>(descriptorGrouper.getFinalDescriptors()), 5, finalReadGroups),
            Tuple3.of(new ArrayList<>(descriptorGrouper.getOtherDescriptors()), 5, otherReadGroups),
            Tuple3.of(
                new ArrayList<>(descriptorGrouper.getOtherDescriptors()), 9, otherWriteGroups));
    for (Tuple3<List<Descriptor>, Integer, List<List<Descriptor>>> decs : groups) {
      while (decs.f0.size() > 0) {
        int endIndex = Math.min(decs.f1, decs.f0.size());
        decs.f2.add(decs.f0.subList(0, endIndex));
        decs.f0 = decs.f0.subList(endIndex, decs.f0.size());
      }
    }
  }

  Expression invokeGenerated(
      SerializableSupplier<Expression> groupExpressionsGenerator, String methodPrefix) {
    return invokeGenerated(ctx, groupExpressionsGenerator, methodPrefix);
  }

  Expression invokeGenerated(
      Set<Expression> cutPoint, Expression groupExpressions, String methodPrefix) {
    return invokeGenerated(ctx, cutPoint, groupExpressions, methodPrefix, false);
  }
}
