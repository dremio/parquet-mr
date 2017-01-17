/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.hadoop;

import static org.apache.parquet.Log.DEBUG;
import static org.apache.parquet.column.ValuesType.DEFINITION_LEVEL;
import static org.apache.parquet.column.ValuesType.REPETITION_LEVEL;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.statistics.Statistics;
import org.apache.parquet.column.values.ValuesReader;

/**
 * Page (v1) holder that holds on to page data and encoding etc.
 */
public class PageV1Holder extends PageHolder {

  private final Encoding rlEncoding;
  private final Encoding dlEncoding;

  public PageV1Holder(int pageIndex, ColumnDescriptor path, BytesInput bytes, int valueCount, Statistics statistics,
                      Encoding rlEncoding, Encoding dlEncoding, Encoding valuesEncoding) throws IOException {
    super(pageIndex, path, bytes, valueCount, valuesEncoding, statistics);
    this.rlEncoding = rlEncoding;
    this.dlEncoding = dlEncoding;
  }

  public Encoding getRlEncoding() {
    return rlEncoding;
  }

  public Encoding getDlEncoding() {
    return dlEncoding;
  }

  @Override
  public int getDataOffset() throws IOException {
    final ValuesReader rlReader = rlEncoding.getValuesReader(getPath(), REPETITION_LEVEL);
    final ValuesReader dlReader = dlEncoding.getValuesReader(getPath(), DEFINITION_LEVEL);
    final ByteBuffer bytes = getData().toByteBuffer();
    rlReader.initFromPage(getValueCount(), bytes, 0);
    int next = rlReader.getNextOffset();
    dlReader.initFromPage(getValueCount(), bytes, next);
    next = dlReader.getNextOffset();
    return next;
  }
}
