/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.file.predicate;

import org.apache.flink.table.store.file.stats.FieldStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A {@link CompoundPredicate.Function} to eval or. */
public class Or implements CompoundPredicate.Function {

    private static final long serialVersionUID = 1L;

    public static final Or INSTANCE = new Or();

    private Or() {}

    @Override
    public boolean test(Object[] values, List<Predicate> children) {
        for (Predicate child : children) {
            if (child.test(values)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean test(long rowCount, FieldStats[] fieldStats, List<Predicate> children) {
        for (Predicate child : children) {
            if (child.test(rowCount, fieldStats)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Predicate> negate(List<Predicate> children) {
        List<Predicate> negatedChildren = new ArrayList<>();
        for (Predicate child : children) {
            Optional<Predicate> negatedChild = child.negate();
            if (negatedChild.isPresent()) {
                negatedChildren.add(negatedChild.get());
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(new CompoundPredicate(And.INSTANCE, negatedChildren));
    }
}
