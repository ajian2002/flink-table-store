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

package org.apache.flink.table.store.connector.sink.global;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.connector.sink2.CommittableMessage;
import org.apache.flink.streaming.api.connector.sink2.CommittableMessageTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.functions.sink.DiscardingSink;
import org.apache.flink.streaming.runtime.operators.sink.SinkWriterOperatorFactory;

import java.io.IOException;
import java.io.UncheckedIOException;

/** A translator for the {@link GlobalCommittingSink}. */
public class GlobalCommittingSinkTranslator {

    private static final String WRITER_NAME = "Writer";

    private static final String LOCAL_COMMITTER_NAME = "Local Committer";

    private static final String GLOBAL_COMMITTER_NAME = "Global Committer";

    public static <T, CommT, GlobalCommT> DataStreamSink<?> translate(
            DataStream<T> input, GlobalCommittingSink<T, CommT, GlobalCommT> sink) {
        TypeInformation<CommittableMessage<CommT>> commitType =
                CommittableMessageTypeInfo.of(sink::getCommittableSerializer);

        SingleOutputStreamOperator<CommittableMessage<CommT>> written =
                input.transform(WRITER_NAME, commitType, new SinkWriterOperatorFactory<>(sink))
                        .setParallelism(input.getParallelism());

        SingleOutputStreamOperator<CommittableMessage<CommT>> local =
                written.transform(
                                LOCAL_COMMITTER_NAME,
                                commitType,
                                new LocalCommitterOperator<>(
                                        () -> {
                                            try {
                                                return sink.createCommitter();
                                            } catch (IOException e) {
                                                throw new UncheckedIOException(e);
                                            }
                                        },
                                        sink::getCommittableSerializer))
                        .setParallelism(written.getParallelism());

        SingleOutputStreamOperator<?> committed =
                local.global()
                        .transform(
                                GLOBAL_COMMITTER_NAME,
                                commitType,
                                new GlobalCommitterOperator<>(
                                        sink::createGlobalCommitter,
                                        sink::getGlobalCommittableSerializer))
                        .setParallelism(1)
                        .setMaxParallelism(1);
        return committed.addSink(new DiscardingSink<>()).name("end").setParallelism(1);
    }
}
