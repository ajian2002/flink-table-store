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

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.connector.file.table.FileSystemConnectorOptions;
import org.apache.flink.connector.file.table.RowDataPartitionComputer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.store.file.FileStoreOptions;
import org.apache.flink.table.store.file.data.DataFilePathFactory;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.LogicalTypeDataTypeConverter;
import org.apache.flink.table.utils.PartitionPathUtils;
import org.apache.flink.util.Preconditions;

import javax.annotation.concurrent.ThreadSafe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Factory which produces {@link Path}s for manifest files. */
@ThreadSafe
public class FileStorePathFactory {

    private final Path root;
    private final String uuid;
    private final RowDataPartitionComputer partitionComputer;
    private final String formatIdentifier;

    private final AtomicInteger manifestFileCount;
    private final AtomicInteger manifestListCount;

    public FileStorePathFactory(Path root) {
        this(
                root,
                RowType.of(),
                FileSystemConnectorOptions.PARTITION_DEFAULT_NAME.defaultValue(),
                FileStoreOptions.FILE_FORMAT.defaultValue());
    }

    // for tables without partition, partitionType should be a row type with 0 columns (not null)
    public FileStorePathFactory(
            Path root, RowType partitionType, String defaultPartValue, String formatIdentifier) {
        this.root = root;
        this.uuid = UUID.randomUUID().toString();

        this.partitionComputer = getPartitionComputer(partitionType, defaultPartValue);
        this.formatIdentifier = formatIdentifier;

        this.manifestFileCount = new AtomicInteger(0);
        this.manifestListCount = new AtomicInteger(0);
    }

    @VisibleForTesting
    public static RowDataPartitionComputer getPartitionComputer(
            RowType partitionType, String defaultPartValue) {
        String[] partitionColumns = partitionType.getFieldNames().toArray(new String[0]);
        return new RowDataPartitionComputer(
                defaultPartValue,
                partitionColumns,
                partitionType.getFields().stream()
                        .map(f -> LogicalTypeDataTypeConverter.toDataType(f.getType()))
                        .toArray(DataType[]::new),
                partitionColumns);
    }

    public Path newManifestFile() {
        return new Path(
                root + "/manifest/manifest-" + uuid + "-" + manifestFileCount.getAndIncrement());
    }

    public Path newManifestList() {
        return new Path(
                root
                        + "/manifest/manifest-list-"
                        + uuid
                        + "-"
                        + manifestListCount.getAndIncrement());
    }

    public Path toManifestFilePath(String manifestFileName) {
        return new Path(root + "/manifest/" + manifestFileName);
    }

    public Path toManifestListPath(String manifestListName) {
        return new Path(root + "/manifest/" + manifestListName);
    }

    public DataFilePathFactory createDataFilePathFactory(BinaryRowData partition, int bucket) {
        return new DataFilePathFactory(
                root, getPartitionString(partition), bucket, formatIdentifier);
    }

    /** IMPORTANT: This method is NOT THREAD SAFE. */
    public String getPartitionString(BinaryRowData partition) {
        return PartitionPathUtils.generatePartitionPath(
                partitionComputer.generatePartValues(
                        Preconditions.checkNotNull(
                                partition, "Partition row data is null. This is unexpected.")));
    }

    @VisibleForTesting
    public String uuid() {
        return uuid;
    }

    /** Cache for storing {@link DataFilePathFactory}s. */
    public static class DataFilePathFactoryCache {

        private final FileStorePathFactory pathFactory;
        private final Map<BinaryRowData, Map<Integer, DataFilePathFactory>> cache;

        public DataFilePathFactoryCache(FileStorePathFactory pathFactory) {
            this.pathFactory = pathFactory;
            this.cache = new HashMap<>();
        }

        public DataFilePathFactory getDataFilePathFactory(BinaryRowData partition, int bucket) {
            return cache.compute(partition, (p, m) -> m == null ? new HashMap<>() : m)
                    .compute(
                            bucket,
                            (b, f) ->
                                    f == null
                                            ? pathFactory.createDataFilePathFactory(
                                                    partition, bucket)
                                            : f);
        }
    }
}
