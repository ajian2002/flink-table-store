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

package org.apache.flink.table.store.file.utils;

import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.core.memory.MemorySegmentFactory;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.runtime.typeutils.BinaryRowDataSerializer;
import org.apache.flink.table.types.logical.VarBinaryType;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.apache.flink.table.data.binary.BinarySegmentUtils.copyToBytes;
import static org.apache.flink.table.data.binary.BinarySegmentUtils.copyToView;

/** Utils for serialization. */
public class SerializationUtils {

    /** Serialize a {@code byte[]} bytes with length. */
    public static void serializeBytes(DataOutputView out, byte[] binary) throws IOException {
        out.writeInt(binary.length);
        out.write(binary);
    }

    /** Deserialize a {@code byte[]} bytes with length. */
    public static byte[] deserializedBytes(DataInputView in) throws IOException {
        int len = in.readInt();
        byte[] buf = new byte[len];
        int ret;
        int off = 0;
        for (int toRead = len; toRead > 0; off += ret) {
            ret = in.read(buf, off, toRead);
            if (ret < 0) {
                throw new EOFException();
            }

            toRead -= ret;
        }
        return buf;
    }

    /** Create a bytes type VarBinaryType(Integer.MAX_VALUE). */
    public static VarBinaryType newBytesType(boolean isNullable) {
        return new VarBinaryType(isNullable, Integer.MAX_VALUE);
    }

    /**
     * Serialize {@link BinaryRowData}, the difference between this and {@link
     * BinaryRowDataSerializer} is that arity is also serialized here, so the deserialization is
     * schemaless.
     */
    public static byte[] serializeBinaryRow(BinaryRowData row) {
        byte[] bytes = copyToBytes(row.getSegments(), row.getOffset(), row.getSizeInBytes());
        ByteBuffer buffer = ByteBuffer.allocate(4 + bytes.length);
        buffer.putInt(row.getArity()).put(bytes);
        return buffer.array();
    }

    /** Schemaless deserialization for {@link BinaryRowData}. */
    public static BinaryRowData deserializeBinaryRow(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int arity = buffer.getInt();
        BinaryRowData row = new BinaryRowData(arity);
        row.pointTo(MemorySegmentFactory.wrap(bytes), 4, bytes.length - 4);
        return row;
    }

    /**
     * Serialize {@link BinaryRowData} to a {@link DataOutputView}.
     *
     * @see #serializeBinaryRow(BinaryRowData)
     */
    public static void serializeBinaryRow(BinaryRowData row, DataOutputView out)
            throws IOException {
        out.writeInt(4 + row.getSizeInBytes());
        out.writeInt(row.getArity());
        copyToView(row.getSegments(), row.getOffset(), row.getSizeInBytes(), out);
    }

    /** Schemaless deserialization for {@link BinaryRowData} from a {@link DataInputView}. */
    public static BinaryRowData deserializeBinaryRow(DataInputView input) throws IOException {
        return deserializeBinaryRow(deserializedBytes(input));
    }
}
