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

package org.apache.flink.table.store.connector.sink;

import org.apache.flink.runtime.io.network.api.writer.SubtaskStateMapper;
import org.apache.flink.runtime.plugable.SerializationDelegate;
import org.apache.flink.streaming.runtime.partitioner.StreamPartitioner;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.store.file.schema.Schema;
import org.apache.flink.table.store.table.sink.SinkRecordConverter;

/** A {@link StreamPartitioner} to partition records by bucket. */
public class BucketStreamPartitioner extends StreamPartitioner<RowData> {

    private final int numBucket;
    private final Schema schema;

    private transient SinkRecordConverter recordConverter;

    public BucketStreamPartitioner(int numBucket, Schema schema) {
        this.numBucket = numBucket;
        this.schema = schema;
    }

    @Override
    public void setup(int numberOfChannels) {
        super.setup(numberOfChannels);
        this.recordConverter = new SinkRecordConverter(numBucket, schema);
    }

    @Override
    public int selectChannel(SerializationDelegate<StreamRecord<RowData>> record) {
        RowData row = record.getInstance().getValue();
        int bucket = recordConverter.bucket(row, recordConverter.primaryKey(row));
        return bucket % numberOfChannels;
    }

    @Override
    public StreamPartitioner<RowData> copy() {
        return this;
    }

    @Override
    public SubtaskStateMapper getDownstreamSubtaskStateMapper() {
        return SubtaskStateMapper.FULL;
    }

    @Override
    public boolean isPointwise() {
        return false;
    }

    @Override
    public String toString() {
        return "bucket-assigner";
    }
}
