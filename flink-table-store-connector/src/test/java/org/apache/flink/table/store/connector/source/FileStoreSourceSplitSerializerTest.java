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

package org.apache.flink.table.store.connector.source;

import org.apache.flink.core.io.SimpleVersionedSerialization;
import org.apache.flink.table.store.file.data.DataFileMeta;
import org.apache.flink.table.store.file.stats.StatsTestUtils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.apache.flink.table.store.file.mergetree.compact.CompactManagerTest.row;
import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the {@link FileStoreSourceSplitSerializer}. */
public class FileStoreSourceSplitSerializerTest {

    @Test
    public void serializeSplit() throws Exception {
        final FileStoreSourceSplit split =
                new FileStoreSourceSplit("id", row(1), 2, Arrays.asList(newFile(0), newFile(1)));

        final FileStoreSourceSplit deSerialized = serializeAndDeserialize(split);

        assertSplitsEqual(split, deSerialized);
    }

    @Test
    public void serializeSplitWithReaderPosition() throws Exception {
        final FileStoreSourceSplit split =
                new FileStoreSourceSplit(
                        "id", row(1), 2, Arrays.asList(newFile(0), newFile(1)), 29);

        final FileStoreSourceSplit deSerialized = serializeAndDeserialize(split);

        assertSplitsEqual(split, deSerialized);
    }

    @Test
    public void repeatedSerialization() throws Exception {
        final FileStoreSourceSplit split =
                new FileStoreSourceSplit(
                        "id", row(1), 2, Arrays.asList(newFile(0), newFile(1)), 29);

        serializeAndDeserialize(split);
        serializeAndDeserialize(split);
        final FileStoreSourceSplit deSerialized = serializeAndDeserialize(split);

        assertSplitsEqual(split, deSerialized);
    }

    // ------------------------------------------------------------------------
    //  test utils
    // ------------------------------------------------------------------------

    public static DataFileMeta newFile(int level) {
        return new DataFileMeta(
                "",
                0,
                1,
                row(0),
                row(0),
                StatsTestUtils.newEmptyTableStats(),
                StatsTestUtils.newEmptyTableStats(),
                0,
                1,
                0,
                level);
    }

    private static FileStoreSourceSplit serializeAndDeserialize(FileStoreSourceSplit split)
            throws IOException {
        final FileStoreSourceSplitSerializer serializer = new FileStoreSourceSplitSerializer();
        final byte[] bytes =
                SimpleVersionedSerialization.writeVersionAndSerialize(serializer, split);
        return SimpleVersionedSerialization.readVersionAndDeSerialize(serializer, bytes);
    }

    static void assertSplitsEqual(FileStoreSourceSplit expected, FileStoreSourceSplit actual) {
        assertThat(actual).isEqualTo(expected);
    }
}
