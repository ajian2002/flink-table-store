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

package org.apache.flink.table.store.file.mergetree.compact;

import org.apache.flink.table.store.file.KeyValue;
import org.apache.flink.table.store.file.utils.RecordReader;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * This reader is to concatenate a list of {@link RecordReader}s and read them sequentially. The
 * input list is already sorted by key and sequence number, and the key intervals do not overlap
 * each other.
 */
public class ConcatRecordReader implements RecordReader<KeyValue> {

    private final Queue<ReaderSupplier> queue;

    private RecordReader<KeyValue> current;

    protected ConcatRecordReader(List<ReaderSupplier> readerFactories) {
        readerFactories.forEach(
                supplier ->
                        Preconditions.checkNotNull(supplier, "Reader factory must not be null."));
        this.queue = new LinkedList<>(readerFactories);
    }

    public static RecordReader<KeyValue> create(List<ReaderSupplier> readers) throws IOException {
        return readers.size() == 1 ? readers.get(0).get() : new ConcatRecordReader(readers);
    }

    @Nullable
    @Override
    public RecordIterator<KeyValue> readBatch() throws IOException {
        while (true) {
            if (current != null) {
                RecordIterator<KeyValue> iterator = current.readBatch();
                if (iterator != null) {
                    return iterator;
                }
                current.close();
                current = null;
            } else if (queue.size() > 0) {
                current = queue.poll().get();
            } else {
                return null;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (current != null) {
            current.close();
        }
    }

    /** Supplier to get {@link RecordReader}. */
    @FunctionalInterface
    public interface ReaderSupplier {
        RecordReader<KeyValue> get() throws IOException;
    }
}
