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

import java.io.IOException;

import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.statistics.Statistics;

/**
 * Base class for page holders.
 */
public abstract class PageHolder {
  private final int pageIndex;
  private final int valueCount;
  private final ColumnDescriptor path;
  private Encoding valuesEncoding;
  private BytesInput data;
  private final Statistics statistics;

  public PageHolder(int pageIndex, ColumnDescriptor path, BytesInput data, int valueCount, Encoding valuesEncoding, Statistics statistics) throws IOException {
    this.pageIndex = pageIndex;
    this.path = path;
    this.statistics = statistics;
    this.valueCount = valueCount;
    this.valuesEncoding = valuesEncoding;
    this.data = BytesInput.copy(data);
  }

  public void setData(BytesInput data) {
    // TODO is there way to free old data or wait for GC?
    this.data = data;
  }

  public void setValuesEncoding(Encoding valuesEncoding) {
    this.valuesEncoding = valuesEncoding;
  }

  public int getPageIndex() {
    return pageIndex;
  }

  public BytesInput getData() {
    return data;
  }

  public int getValueCount() {
    return valueCount;
  }

  public int getNonNullValueCount() {
    return valueCount - (int)statistics.getNumNulls();
  }

  public Statistics getStatistics() {
    return statistics;
  }

  public Encoding getValuesEncoding() {
    return valuesEncoding;
  }

  public ColumnDescriptor getPath() {
    return path;
  }

  public abstract int getDataOffset() throws IOException;
}
