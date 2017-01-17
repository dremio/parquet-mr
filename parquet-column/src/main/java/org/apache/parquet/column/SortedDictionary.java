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
package org.apache.parquet.column;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.parquet.column.page.DictionaryPage;
import org.apache.parquet.column.values.dictionary.DictionaryValuesWriter;
import org.apache.parquet.io.ParquetDecodingException;

/**
 * Sorted dictionary
 */
public class SortedDictionary {

  private final Dictionary dictionary;
  private final Map<Integer, Integer> sortedDictionaryId;
  private final DictionaryPage sortedDictionaryPage;

  public SortedDictionary(DictionaryPage dictionaryPage, final ColumnDescriptor columnDescriptor, ParquetProperties parquetProperties) throws IOException {
    this.sortedDictionaryId = new HashMap<Integer, Integer>();
    this.dictionary = dictionaryPage.getEncoding().initDictionary(columnDescriptor, dictionaryPage);

    final Integer []indices = new Integer[dictionary.getMaxId() + 1];
    for (int i = 0; i < indices.length; ++i) {
      indices[i] = i;
    }
    Arrays.sort(indices, new Comparator<Integer>() {
      @Override
      public int compare(Integer o1, Integer o2) {
        switch (columnDescriptor.getType()) {
          case BOOLEAN: // boolean shouldn't have dictionary encoding
            return Boolean.compare(dictionary.decodeToBoolean(o1), dictionary.decodeToBoolean(o2));
          case BINARY:
          case FIXED_LEN_BYTE_ARRAY:
          case INT96:
            return dictionary.decodeToBinary(o1).compareTo(dictionary.decodeToBinary(o2));
          case INT32:
            return Integer.compare(dictionary.decodeToInt(o1), dictionary.decodeToInt(o2));
          case INT64:
            return Long.compare(dictionary.decodeToLong(o1), dictionary.decodeToLong(o2));
          case DOUBLE:
            return Double.compare(dictionary.decodeToDouble(o1), dictionary.decodeToDouble(o2));
          case FLOAT:
            return Float.compare(dictionary.decodeToFloat(o1), dictionary.decodeToFloat(o2));
          default:
            throw new ParquetDecodingException("Dictionary encoding not supported for type: " + columnDescriptor.getType());
        }
      }
    });

    for (int i = 0; i < indices.length; ++i) {
      // map old dictionary id to new one
      sortedDictionaryId.put(indices[i], i);
    }

    // Create sorted dictionary page.
    final DictionaryValuesWriter dictionaryValuesWriter = parquetProperties.dictionaryWriter(columnDescriptor, dictionaryPage.getDictionarySize());
    try {
      switch (columnDescriptor.getType()) {
        case BOOLEAN: {
          for (int i = 0; i < indices.length; ++i) {
            dictionaryValuesWriter.writeBoolean(dictionary.decodeToBoolean(indices[i]));
          }
          break;
        }

        case BINARY:
        case FIXED_LEN_BYTE_ARRAY:
        case INT96: {
          for (int i = 0; i < indices.length; ++i) {
            dictionaryValuesWriter.writeBytes(dictionary.decodeToBinary(indices[i]));
          }
          break;
        }

        case INT32: {
          for (int i = 0; i < indices.length; ++i) {
            dictionaryValuesWriter.writeInteger(dictionary.decodeToInt(indices[i]));
          }
          break;
        }

        case INT64: {
          for (int i = 0; i < indices.length; ++i) {
            dictionaryValuesWriter.writeLong(dictionary.decodeToLong(indices[i]));
          }
          break;
        }

        case DOUBLE: {
          for (int i = 0; i < indices.length; ++i) {
            dictionaryValuesWriter.writeDouble(dictionary.decodeToDouble(indices[i]));
          }
          break;
        }

        case FLOAT: {
          for (int i = 0; i < indices.length; ++i) {
            dictionaryValuesWriter.writeFloat(dictionary.decodeToFloat(indices[i]));
          }
          break;
        }

        default:
          throw new ParquetDecodingException("Dictionary encoding not supported for type: " + columnDescriptor.getType());
      }
      // consume dictionary before calling toDictPageAndClose
      dictionaryValuesWriter.consumeDictionary();
      this.sortedDictionaryPage = dictionaryValuesWriter.toDictPageAndClose();
    } finally {
      dictionaryValuesWriter.close();
    }
  }

  public Dictionary getDictionary() {
    return dictionary;
  }

  public int getNewId(int id) {
    return sortedDictionaryId.get(id);
  }

  public DictionaryPage getSortedDictionaryPage() {
    return sortedDictionaryPage;
  }
}
