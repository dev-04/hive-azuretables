/*******************************************************************************
 * Copyright 2013 Simon Elliston Ball
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.redgate.hadoop.hive.azuretables;

import static com.redgate.hadoop.hive.azuretables.ConfigurationUtil.COLUMN_MAPPING_KEY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde2.AbstractSerDe;
import org.apache.hadoop.hive.serde2.SerDe;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.SerDeStats;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.StringObjectInspector;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
/**
 * 
 * Based heavily on the gdata version
 * 
 * @author simon
 *
 */
@SuppressWarnings("deprecation")
public class AzureTablesSerDe extends AbstractSerDe implements SerDe {

	private final MapWritable cachedWritable = new MapWritable();

	private int fieldCount;
	private ObjectInspector objectInspector;
	private List<String> columnNames;
	private List<String> row;

	@Override
	public void initialize(final Configuration conf, final Properties tbl)
			throws SerDeException {
		final String columnString = tbl.getProperty(COLUMN_MAPPING_KEY);
		if (StringUtils.isBlank(columnString)) {
			throw new SerDeException("No column mapping found, use "
					+ COLUMN_MAPPING_KEY);
		}
		final String[] columnNamesArray = columnString.split(",");
		fieldCount = columnNamesArray.length;
		columnNames = new ArrayList<String>(columnNamesArray.length);
		columnNames.addAll(Arrays.asList(columnNamesArray));

		final List<ObjectInspector> fieldOIs = new ArrayList<ObjectInspector>(
				columnNamesArray.length);
		for (int i = 0; i < columnNamesArray.length; i++) {
			fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
		}
		objectInspector = ObjectInspectorFactory
				.getStandardStructObjectInspector(columnNames, fieldOIs);
		row = new ArrayList<String>(columnNamesArray.length);
	}

	@Override
	public Writable serialize(final Object obj, final ObjectInspector inspector)
			throws SerDeException {
		final StructObjectInspector structInspector = (StructObjectInspector) inspector;
		final List<? extends StructField> fields = structInspector
				.getAllStructFieldRefs();
		if (fields.size() != columnNames.size()) {
			throw new SerDeException(String.format(
					"Required %d columns, received %d.", columnNames.size(),
					fields.size()));
		}

		cachedWritable.clear();
		for (int c = 0; c < fieldCount; c++) {
			StructField structField = fields.get(c);
			if (structField != null) {
				final Object field = structInspector.getStructFieldData(obj,
						fields.get(c));
				final ObjectInspector fieldOI = fields.get(c)
						.getFieldObjectInspector();
				final StringObjectInspector fieldStringOI = (StringObjectInspector) fieldOI;
				Writable value = fieldStringOI
						.getPrimitiveWritableObject(field);
				if (value == null) {
					value = NullWritable.get();
				}
				cachedWritable.put(new Text(columnNames.get(c)), value);
			}
		}
		return cachedWritable;
	}

	@Override
	public Object deserialize(final Writable wr) throws SerDeException {
		if (!(wr instanceof MapWritable)) {
			throw new SerDeException("Expected MapWritable, received "
					+ wr.getClass().getName());
		}

		final MapWritable input = (MapWritable) wr;
		final Text t = new Text();
		row.clear();

		for (int i = 0; i < fieldCount; i++) {
			t.set(columnNames.get(i));
			final Writable value = input.get(t);
			if (value != null && !NullWritable.get().equals(value)) {
				row.add(value.toString());
			} else {
				row.add(null);
			}
		}

		return row;
	}

	@Override
	public Class<? extends Writable> getSerializedClass() {
		return MapWritable.class;
	}

	@Override
	public ObjectInspector getObjectInspector() throws SerDeException {
		return objectInspector;
	}

	@Override
	public SerDeStats getSerDeStats() {
		return null;
	}

}
