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

package org.apache.flink.table.store.hive.objectinspector;

import org.apache.flink.table.data.StringData;

import org.apache.hadoop.hive.common.type.HiveVarchar;
import org.apache.hadoop.hive.serde2.io.HiveVarcharWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.HiveVarcharObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.VarcharTypeInfo;

/** {@link AbstractPrimitiveJavaObjectInspector} for VARCHAR type. */
public class TableStoreVarcharObjectInspector extends AbstractPrimitiveJavaObjectInspector
        implements HiveVarcharObjectInspector {

    private final int len;

    public TableStoreVarcharObjectInspector(VarcharTypeInfo typeInfo) {
        super(typeInfo);
        this.len = typeInfo.getLength();
    }

    @Override
    public HiveVarchar getPrimitiveJavaObject(Object o) {
        return o == null ? null : new HiveVarchar(o.toString(), len);
    }

    @Override
    public HiveVarcharWritable getPrimitiveWritableObject(Object o) {
        HiveVarchar hiveVarchar = getPrimitiveJavaObject(o);
        return hiveVarchar == null ? null : new HiveVarcharWritable(hiveVarchar);
    }

    @Override
    public Object copyObject(Object o) {
        if (o instanceof StringData) {
            return StringData.fromString(o.toString());
        } else if (o instanceof HiveVarchar) {
            HiveVarchar hiveVarchar = (HiveVarchar) o;
            return new HiveVarchar(hiveVarchar, len);
        } else {
            return o;
        }
    }
}
