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

package org.apache.flink.formats.orc.writers;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.core.fs.FSDataOutputStream;
import org.apache.flink.formats.orc.vectorizer.Vectorizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.impl.WriterImpl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A factory that creates an ORC {@link BulkWriter}. The factory takes a user
 * supplied{@link Vectorizer} implementation to convert the element into an
 * {@link org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch}.
 *
 * @param <T> The type of element to write.
 */
@PublicEvolving
public class OrcBulkWriterFactory<T> implements BulkWriter.Factory<T> {

	/*
	A dummy Hadoop Path to work around the current implementation of ORC WriterImpl which
	works on the basis of a Hadoop FileSystem and Hadoop Path but since we use a customised
	ORC PhysicalWriter implementation that uses Flink's own FSDataOutputStream as the
	underlying/internal stream instead of Hadoop's FSDataOutputStream, we don't have to worry
	about this usage.
	 */
	private static final Path FIXED_PATH = new Path(".");

	private final Vectorizer<T> vectorizer;
	private final Properties writerProperties;
	private final Map<String, String> confMap;
	private final TypeDescription schema;
	private OrcFile.WriterOptions writerOptions;

	/**
	 * Creates a new OrcBulkWriterFactory using the provided Vectorizer
	 * implementation and schema.
	 *
	 * @param vectorizer The vectorizer implementation to convert input
	 *                   record to a VectorizerRowBatch.
	 * @param schema 	 The schema defining the types of the ORC file.
	 */
	public OrcBulkWriterFactory(Vectorizer<T> vectorizer, TypeDescription schema) {
		this(vectorizer, new Configuration(), schema);
	}

	/**
	 * Creates a new OrcBulkWriterFactory using the provided Vectorizer, Hadoop
	 * Configuration and the schema.
	 *
	 * @param vectorizer The vectorizer implementation to convert input
	 *                   record to a VectorizerRowBatch.
	 * @param conf       Hadoop Configuration to be used when building the Orc Writer.
	 * @param schema	 The schema defining the types of the ORC file.
	 */
	public OrcBulkWriterFactory(Vectorizer<T> vectorizer, Configuration conf, TypeDescription schema) {
		this(vectorizer, null, conf, schema);
	}

	/**
	 * Creates a new OrcBulkWriterFactory using the provided Vectorizer, Hadoop
	 * Configuration, ORC writer properties and the schema.
	 *
	 * @param vectorizer 		The vectorizer implementation to convert input
	 *                          record to a VectorizerRowBatch.
	 * @param writerProperties  Properties that can be used in ORC WriterOptions.
	 * @param conf				Hadoop Configuration to be used when building the Orc Writer.
	 * @param schema            The schema defining the types of the ORC file.
	 */
	public OrcBulkWriterFactory(Vectorizer<T> vectorizer, Properties writerProperties,
								Configuration conf, TypeDescription schema) {
		this.vectorizer = checkNotNull(vectorizer);
		this.schema = checkNotNull(schema);
		this.writerProperties = writerProperties;
		this.confMap = new HashMap<>();

		// Todo: Replace the Map based approach with a better approach
		for (Map.Entry<String, String> entry : conf) {
			confMap.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public BulkWriter<T> create(FSDataOutputStream out) throws IOException {
		OrcFile.WriterOptions opts = getWriterOptions();
		opts.physicalWriter(new PhysicalWriterImpl(out, opts));

		return new OrcBulkWriter<>(vectorizer, new WriterImpl(null, FIXED_PATH, opts));
	}

	private OrcFile.WriterOptions getWriterOptions() {
		if (null == writerOptions) {
			Configuration conf = new Configuration();
			for (Map.Entry<String, String> entry : confMap.entrySet()) {
				conf.set(entry.getKey(), entry.getValue());
			}

			writerOptions = OrcFile.writerOptions(writerProperties, conf);
			writerOptions.setSchema(this.schema);
		}

		return writerOptions;
	}
}

