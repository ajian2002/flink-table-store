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

package org.apache.flink.table.store.file.operation;

import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.runtime.typeutils.RowDataSerializer;
import org.apache.flink.table.store.file.KeyValue;
import org.apache.flink.table.store.file.TestFileStore;
import org.apache.flink.table.store.file.TestKeyValueGenerator;
import org.apache.flink.table.store.file.ValueKind;
import org.apache.flink.table.store.file.manifest.ManifestEntry;
import org.apache.flink.table.store.file.mergetree.compact.DeduplicateMergeFunction;
import org.apache.flink.table.store.file.mergetree.compact.MergeFunction;
import org.apache.flink.table.store.file.mergetree.compact.ValueCountMergeFunction;
import org.apache.flink.table.store.file.utils.RecordReader;
import org.apache.flink.table.store.file.utils.RecordReaderIterator;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.VarCharType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link FileStoreReadImpl}. */
public class FileStoreReadTest {

    @TempDir java.nio.file.Path tempDir;

    @Test
    public void testKeyProjection() throws Exception {
        // (a, b, c) -> (b, a), c is the partition, all integers are in range [0, 2]

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int numRecords = random.nextInt(1000) + 1;
        List<KeyValue> data = new ArrayList<>();
        Map<Integer, Long> expected = new HashMap<>();
        for (int i = 0; i < numRecords; i++) {
            int a = random.nextInt(3);
            int b = random.nextInt(3);
            int c = random.nextInt(3);
            long delta = random.nextLong(21) - 10;
            // count number of occurrence of (b, a)
            expected.compute(b * 10 + a, (k, v) -> v == null ? delta : v + delta);
            data.add(
                    new KeyValue()
                            .replace(
                                    GenericRowData.of(a, b, c),
                                    i,
                                    ValueKind.ADD,
                                    GenericRowData.of(delta)));
        }
        // remove zero occurrence, it might be merged and discarded by the merge tree
        expected.entrySet().removeIf(e -> e.getValue() == 0);

        RowType partitionType =
                RowType.of(new LogicalType[] {new IntType(false)}, new String[] {"c"});
        RowDataSerializer partitionSerializer = new RowDataSerializer(partitionType);
        RowType keyType =
                RowType.of(
                        new LogicalType[] {
                            new IntType(false), new IntType(false), new IntType(false)
                        },
                        new String[] {"a", "b", "c"});
        RowType projectedKeyType = RowType.of(new IntType(false), new IntType(false));
        RowDataSerializer projectedKeySerializer = new RowDataSerializer(projectedKeyType);
        RowType valueType =
                RowType.of(new LogicalType[] {new BigIntType(false)}, new String[] {"count"});
        RowDataSerializer valueSerializer = new RowDataSerializer(valueType);

        TestFileStore store =
                createStore(partitionType, keyType, valueType, new ValueCountMergeFunction());
        List<KeyValue> readData =
                writeThenRead(
                        data,
                        new int[][] {new int[] {1}, new int[] {0}},
                        null,
                        projectedKeySerializer,
                        valueSerializer,
                        store,
                        kv ->
                                partitionSerializer
                                        .toBinaryRow(GenericRowData.of(kv.key().getInt(2)))
                                        .copy());
        Map<Integer, Long> actual = new HashMap<>();
        for (KeyValue kv : readData) {
            assertThat(kv.key().getArity()).isEqualTo(2);
            int key = kv.key().getInt(0) * 10 + kv.key().getInt(1);
            long delta = kv.value().getLong(0);
            actual.compute(key, (k, v) -> v == null ? delta : v + delta);
        }
        actual.entrySet().removeIf(e -> e.getValue() == 0);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testValueProjection() throws Exception {
        // (dt, hr, shopId, orderId, itemId, priceAmount, comment) -> (shopId, itemId, dt, hr)

        TestKeyValueGenerator gen = new TestKeyValueGenerator();
        int numRecords = ThreadLocalRandom.current().nextInt(1000) + 1;
        List<KeyValue> data = new ArrayList<>();
        for (int i = 0; i < numRecords; i++) {
            data.add(gen.next());
        }
        TestFileStore store =
                createStore(
                        TestKeyValueGenerator.DEFAULT_PART_TYPE,
                        TestKeyValueGenerator.KEY_TYPE,
                        TestKeyValueGenerator.DEFAULT_ROW_TYPE,
                        new DeduplicateMergeFunction());

        RowDataSerializer projectedValueSerializer =
                new RowDataSerializer(
                        new IntType(false),
                        new BigIntType(),
                        new VarCharType(false, 8),
                        new IntType(false));
        Map<BinaryRowData, BinaryRowData> expected = store.toKvMap(data);
        expected.replaceAll(
                (k, v) ->
                        projectedValueSerializer
                                .toBinaryRow(
                                        GenericRowData.of(
                                                v.getInt(2),
                                                v.isNullAt(4) ? null : v.getLong(4),
                                                v.getString(0),
                                                v.getInt(1)))
                                .copy());

        List<KeyValue> readData =
                writeThenRead(
                        data,
                        null,
                        new int[][] {new int[] {2}, new int[] {4}, new int[] {0}, new int[] {1}},
                        TestKeyValueGenerator.KEY_SERIALIZER,
                        projectedValueSerializer,
                        store,
                        gen::getPartition);
        for (KeyValue kv : readData) {
            assertThat(kv.value().getArity()).isEqualTo(4);
            BinaryRowData key = TestKeyValueGenerator.KEY_SERIALIZER.toBinaryRow(kv.key());
            BinaryRowData value = projectedValueSerializer.toBinaryRow(kv.value());
            assertThat(expected).containsKey(key);
            assertThat(value).isEqualTo(expected.get(key));
        }
    }

    private List<KeyValue> writeThenRead(
            List<KeyValue> data,
            int[][] keyProjection,
            int[][] valueProjection,
            RowDataSerializer projectedKeySerializer,
            RowDataSerializer projectedValueSerializer,
            TestFileStore store,
            Function<KeyValue, BinaryRowData> partitionCalculator)
            throws Exception {
        store.commitData(data, partitionCalculator, kv -> 0);
        FileStoreScan scan = store.newScan();
        Map<BinaryRowData, List<ManifestEntry>> filesGroupedByPartition =
                scan.withSnapshot(store.snapshotManager().latestSnapshotId()).plan().files()
                        .stream()
                        .collect(Collectors.groupingBy(ManifestEntry::partition));
        FileStoreRead read = store.newRead();
        if (keyProjection != null) {
            read.withKeyProjection(keyProjection);
            read.withDropDelete(false);
        }
        if (valueProjection != null) {
            read.withValueProjection(valueProjection);
        }

        List<KeyValue> result = new ArrayList<>();
        for (Map.Entry<BinaryRowData, List<ManifestEntry>> entry :
                filesGroupedByPartition.entrySet()) {
            RecordReader<KeyValue> reader =
                    read.createReader(
                            entry.getKey(),
                            0,
                            entry.getValue().stream()
                                    .map(ManifestEntry::file)
                                    .collect(Collectors.toList()));
            RecordReaderIterator<KeyValue> actualIterator = new RecordReaderIterator<>(reader);
            while (actualIterator.hasNext()) {
                result.add(
                        actualIterator
                                .next()
                                .copy(projectedKeySerializer, projectedValueSerializer));
            }
        }
        return result;
    }

    private TestFileStore createStore(
            RowType partitionType,
            RowType keyType,
            RowType valueType,
            MergeFunction mergeFunction) {
        return TestFileStore.create(
                "avro", tempDir.toString(), 1, partitionType, keyType, valueType, mergeFunction);
    }
}
