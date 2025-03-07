/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.hive;

import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.store.RowDataContainer;
import org.apache.flink.table.store.hive.objectinspector.TableStoreRowDataObjectInspector;

import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.apache.flink.table.store.hive.RandomGenericRowDataGenerator.FIELD_COMMENTS;
import static org.apache.flink.table.store.hive.RandomGenericRowDataGenerator.FIELD_NAMES;
import static org.apache.flink.table.store.hive.RandomGenericRowDataGenerator.TYPE_INFOS;
import static org.apache.flink.table.store.hive.RandomGenericRowDataGenerator.generate;
import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link TableStoreSerDe}. */
public class TableStoreSerDeTest {

    @Test
    public void testInitialize() throws Exception {
        TableStoreSerDe serDe = createInitializedSerDe();
        ObjectInspector o = serDe.getObjectInspector();
        assertThat(o).isInstanceOf(TableStoreRowDataObjectInspector.class);
        TableStoreRowDataObjectInspector oi = (TableStoreRowDataObjectInspector) o;
        GenericRowData rowData = generate();
        List<? extends StructField> structFields = oi.getAllStructFieldRefs();
        for (int i = 0; i < structFields.size(); i++) {
            assertThat(oi.getStructFieldData(rowData, structFields.get(i)))
                    .isEqualTo(rowData.getField(i));
            assertThat(structFields.get(i).getFieldName()).isEqualTo(FIELD_NAMES.get(i));
            assertThat(structFields.get(i).getFieldComment()).isEqualTo(FIELD_COMMENTS.get(i));
        }
    }

    @Test
    public void testDeserialize() throws Exception {
        TableStoreSerDe serDe = createInitializedSerDe();
        GenericRowData rowData = generate();
        RowDataContainer container = new RowDataContainer();
        container.set(rowData);
        assertThat(serDe.deserialize(container)).isEqualTo(rowData);
    }

    private TableStoreSerDe createInitializedSerDe() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("columns", String.join(",", FIELD_NAMES));
        properties.setProperty(
                "columns.types",
                TYPE_INFOS.stream().map(TypeInfo::getTypeName).collect(Collectors.joining(":")));
        properties.setProperty("columns.comments", String.join("\0", FIELD_COMMENTS));

        TableStoreSerDe serDe = new TableStoreSerDe();
        serDe.initialize(null, properties);
        return serDe;
    }
}
