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

package org.apache.flink.table.store.file;

import org.apache.flink.table.data.GenericArrayData;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.runtime.generated.RecordComparator;
import org.apache.flink.table.runtime.typeutils.RowDataSerializer;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.VarCharType;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.apache.flink.table.store.file.TestKeyValueGenerator.GeneratorMode.MULTI_PARTITIONED;
import static org.apache.flink.table.store.file.TestKeyValueGenerator.GeneratorMode.NON_PARTITIONED;
import static org.apache.flink.table.store.file.TestKeyValueGenerator.GeneratorMode.SINGLE_PARTITIONED;

/** Random {@link KeyValue} generator. */
public class TestKeyValueGenerator {

    // record structure
    //
    // * dt: string, 20201110 ~ 20201111 -------|
    // * hr: int, 9 ~ 10                 -------+----> partition
    //
    // * shopId:  int, 0 ~ 9             -------|
    // * orderId: long, any value        -------+----> primary key
    //
    // * itemId: long, any value
    // * price & amount: int[], [1 ~ 100, 1 ~ 10]
    // * comment: string, length from 10 to 1000
    public static final RowType DEFAULT_ROW_TYPE =
            RowType.of(
                    new LogicalType[] {
                        new VarCharType(false, 8),
                        new IntType(false),
                        new IntType(false),
                        new BigIntType(false),
                        new BigIntType(),
                        new ArrayType(new IntType()),
                        new VarCharType(Integer.MAX_VALUE)
                    },
                    new String[] {
                        "dt", "hr", "shopId", "orderId", "itemId", "priceAmount", "comment"
                    });
    public static final RowType DEFAULT_PART_TYPE =
            new RowType(DEFAULT_ROW_TYPE.getFields().subList(0, 2));

    public static final RowType SINGLE_PARTITIONED_ROW_TYPE =
            RowType.of(
                    new LogicalType[] {
                        new VarCharType(false, 8),
                        new IntType(false),
                        new BigIntType(false),
                        new BigIntType(),
                        new ArrayType(new IntType()),
                        new VarCharType(Integer.MAX_VALUE)
                    },
                    new String[] {"dt", "shopId", "orderId", "itemId", "priceAmount", "comment"});
    public static final RowType SINGLE_PARTITIONED_PART_TYPE =
            RowType.of(SINGLE_PARTITIONED_ROW_TYPE.getTypeAt(0));

    public static final RowType NON_PARTITIONED_ROW_TYPE =
            RowType.of(
                    new LogicalType[] {
                        new IntType(false),
                        new BigIntType(false),
                        new BigIntType(),
                        new ArrayType(new IntType()),
                        new VarCharType(Integer.MAX_VALUE)
                    },
                    new String[] {"shopId", "orderId", "itemId", "priceAmount", "comment"});
    public static final RowType NON_PARTITIONED_PART_TYPE = RowType.of();

    public static final RowType KEY_TYPE =
            RowType.of(
                    new LogicalType[] {new IntType(false), new BigIntType(false)},
                    new String[] {"key_shopId", "key_orderId"});

    public static final RowDataSerializer DEFAULT_ROW_SERIALIZER =
            new RowDataSerializer(DEFAULT_ROW_TYPE);
    public static final RowDataSerializer KEY_SERIALIZER = new RowDataSerializer(KEY_TYPE);
    public static final RecordComparator KEY_COMPARATOR =
            (a, b) -> {
                int firstResult = a.getInt(0) - b.getInt(0);
                if (firstResult != 0) {
                    return firstResult;
                }
                return Long.compare(a.getLong(1), b.getLong(1));
            };

    private final GeneratorMode mode;
    private final Random random;

    private final List<Order> addedOrders;
    private final List<Order> deletedOrders;

    private long sequenceNumber;

    private final RowDataSerializer rowSerializer;
    private final RowDataSerializer partitionSerializer;

    public TestKeyValueGenerator() {
        this(MULTI_PARTITIONED);
    }

    public TestKeyValueGenerator(GeneratorMode mode) {
        this.mode = mode;
        this.random = new Random();

        this.addedOrders = new ArrayList<>();
        this.deletedOrders = new ArrayList<>();

        this.sequenceNumber = 0;

        RowType rowType;
        RowType partitionType;
        switch (mode) {
            case NON_PARTITIONED:
                rowType = NON_PARTITIONED_ROW_TYPE;
                partitionType = NON_PARTITIONED_PART_TYPE;
                break;
            case SINGLE_PARTITIONED:
                rowType = SINGLE_PARTITIONED_ROW_TYPE;
                partitionType = SINGLE_PARTITIONED_PART_TYPE;
                break;
            case MULTI_PARTITIONED:
                rowType = DEFAULT_ROW_TYPE;
                partitionType = DEFAULT_PART_TYPE;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported generator mode: " + mode);
        }
        rowSerializer = new RowDataSerializer(rowType);
        partitionSerializer = new RowDataSerializer(partitionType);
    }

    public KeyValue next() {
        int op = random.nextInt(5);
        Order order = null;
        ValueKind kind = ValueKind.ADD;
        if (op == 0 && addedOrders.size() > 0) {
            // delete order
            order = pick(addedOrders);
            deletedOrders.add(order);
            kind = ValueKind.DELETE;
        } else if (op == 1) {
            // update order
            if (random.nextBoolean() && deletedOrders.size() > 0) {
                order = pick(deletedOrders);
            } else if (addedOrders.size() > 0) {
                order = pick(addedOrders);
            }
            if (order != null) {
                order.update();
                addedOrders.add(order);
                kind = ValueKind.ADD;
            }
        }
        if (order == null) {
            // new order
            order = new Order();
            addedOrders.add(order);
            kind = ValueKind.ADD;
        }
        return new KeyValue()
                .replace(
                        KEY_SERIALIZER
                                .toBinaryRow(GenericRowData.of(order.shopId, order.orderId))
                                .copy(),
                        sequenceNumber++,
                        kind,
                        rowSerializer.toBinaryRow(convertToRow(order)).copy());
    }

    private RowData convertToRow(Order order) {
        List<Object> values =
                new ArrayList<>(
                        Arrays.asList(
                                order.shopId,
                                order.orderId,
                                order.itemId,
                                order.priceAmount == null
                                        ? null
                                        : new GenericArrayData(order.priceAmount),
                                StringData.fromString(order.comment)));

        if (mode == MULTI_PARTITIONED) {
            values.add(0, StringData.fromString(order.dt));
            values.add(1, order.hr);
        } else if (mode == SINGLE_PARTITIONED) {
            values.add(0, StringData.fromString(order.dt));
        }
        return GenericRowData.of(values.toArray(new Object[0]));
    }

    public BinaryRowData getPartition(KeyValue kv) {
        Object[] values;
        if (mode == MULTI_PARTITIONED) {
            values = new Object[] {kv.value().getString(0), kv.value().getInt(1)};
        } else if (mode == SINGLE_PARTITIONED) {
            values = new Object[] {kv.value().getString(0)};
        } else {
            values = new Object[0];
        }
        return partitionSerializer.toBinaryRow(GenericRowData.of(values)).copy();
    }

    public static List<String> getPrimaryKeys(GeneratorMode mode) {
        List<String> trimmedPk =
                KEY_TYPE.getFieldNames().stream()
                        .map(f -> f.replaceFirst("key_", ""))
                        .collect(Collectors.toList());
        if (mode != NON_PARTITIONED) {
            trimmedPk = new ArrayList<>(trimmedPk);
            trimmedPk.addAll(
                    mode == MULTI_PARTITIONED
                            ? DEFAULT_PART_TYPE.getFieldNames()
                            : SINGLE_PARTITIONED_PART_TYPE.getFieldNames());
        }
        return trimmedPk;
    }

    public static Map<String, String> toPartitionMap(BinaryRowData partition, GeneratorMode mode) {
        Map<String, String> map = new HashMap<>();
        if (mode == MULTI_PARTITIONED) {
            map.put("dt", partition.getString(0).toString());
            map.put("hr", String.valueOf(partition.getInt(1)));
        } else if (mode == SINGLE_PARTITIONED) {
            map.put("dt", partition.getString(0).toString());
        }
        return map;
    }

    public void sort(List<KeyValue> kvs) {
        kvs.sort(
                (a, b) -> {
                    int keyCompareResult = KEY_COMPARATOR.compare(a.key(), b.key());
                    return keyCompareResult != 0
                            ? keyCompareResult
                            : Long.compare(a.sequenceNumber(), b.sequenceNumber());
                });
    }

    private Order pick(List<Order> list) {
        int idx = random.nextInt(list.size());
        Order tmp = list.get(idx);
        list.set(idx, list.get(list.size() - 1));
        list.remove(list.size() - 1);
        return tmp;
    }

    private class Order {
        private final String dt;
        private final int hr;
        private final int shopId;
        private final long orderId;
        @Nullable private Long itemId;
        @Nullable private int[] priceAmount;
        @Nullable private String comment;

        private Order() {
            dt = String.valueOf(random.nextInt(2) + 20211110);
            hr = random.nextInt(2) + 8;
            shopId = random.nextInt(10);
            orderId = random.nextLong();
            update();
        }

        private String randomString(int length) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                builder.append((char) (random.nextInt(127 - 32) + 32));
            }
            return builder.toString();
        }

        private void update() {
            itemId = random.nextInt(10) == 0 ? null : random.nextLong();
            priceAmount =
                    random.nextInt(10) == 0
                            ? null
                            : new int[] {random.nextInt(100) + 1, random.nextInt(100) + 1};
            comment = random.nextInt(10) == 0 ? null : randomString(random.nextInt(1001 - 10) + 10);
        }
    }

    /** Generator mode for {@link TestKeyValueGenerator}. */
    public enum GeneratorMode {
        NON_PARTITIONED,
        SINGLE_PARTITIONED,
        MULTI_PARTITIONED
    }
}
