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

import static org.apache.parquet.column.ParquetProperties.WriterVersion.PARQUET_2_0;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.column.SortedDictionary;
import org.apache.parquet.column.page.DictionaryPage;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.column.page.PageReader;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.recordlevel.PhoneBookWriter;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.hadoop.util.PageHeaderUtil;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test Dictionary encoding.
 */
public class TestDictionaryEncoding {
  // Dictionary encoding for columns phone number kind
  // Partial dictionary encoding for lon, lang

  private static String [] kinds = { "landline", "home", "mobile", "cell", "work" };

  public static List<PhoneBookWriter.User> makeUsers() {
    List<PhoneBookWriter.User> users = new ArrayList<PhoneBookWriter.User>();
    for (int i = 0; i < 100; i++) {
      PhoneBookWriter.Location location = new PhoneBookWriter.Location((double)i % 5, (double) i% 6);
      users.add(new PhoneBookWriter.User(i, "p" + i, Arrays.asList(new PhoneBookWriter.PhoneNumber(i, kinds[i%kinds.length])), location));
    }

    for (int i = 100; i < 500; i++) {
      PhoneBookWriter.Location location = new PhoneBookWriter.Location((double)i, (double) i); // no duplicates should enforce partial dictionary pages
      users.add(new PhoneBookWriter.User(i, "p" + i, Arrays.asList(new PhoneBookWriter.PhoneNumber(i, kinds[i%kinds.length])), location));
    }
    return users;
  }

  private static File phonebookFile;
  private static List<PhoneBookWriter.User> users;

  @BeforeClass
  public static void setup() throws IOException {
    users = makeUsers();
    phonebookFile = PhoneBookWriter.writeToFile(users, 100, 512);
  }

  @Test
  public void validateData() throws IOException {
    List<Group> found =  PhoneBookWriter.readFile(phonebookFile, FilterCompat.NOOP);
    List<Group> expected = new ArrayList<Group>();
    for (PhoneBookWriter.User u : users) {
      expected.add(PhoneBookWriter.groupFromUser(u));
    }
    assertEquals(expected.size(), found.size());
    Iterator<Group> expectedIter = expected.iterator();
    Iterator<Group> foundIter = found.iterator();
    while (expectedIter.hasNext()) {
      assertEquals(expectedIter.next().toString(), foundIter.next().toString());
    }
  }

  @Test
  public void validatePageHeader() throws Exception {
    Path path = new Path(phonebookFile.toURI());
    ParquetMetadata footer = ParquetFileReader.readFooter(new Configuration(), path, ParquetMetadataConverter.NO_FILTER);
    PageHeaderUtil.validatePageHeaders(path, footer);
  }

  @Test
  public void testPartialDictionaryEncoding() throws IOException {
    // make sure all pages for lat long do not use dictionary encoding
    Path path = new Path(phonebookFile.toURI());
    Configuration conf = new Configuration();
    ParquetMetadata readFooter = ParquetFileReader.readFooter(conf, path);
    String [] latPath = { "location", "lat"};
    String [] lonPath = { "location", "lon"};
    String [] kindPath = { "phoneNumbers", "phone", "kind"};

    ColumnDescriptor latColumn = PhoneBookWriter.getColumnDescriptor(latPath);
    ColumnDescriptor lonColumn = PhoneBookWriter.getColumnDescriptor(lonPath);
    ColumnDescriptor kindColumn = PhoneBookWriter.getColumnDescriptor(kindPath);

    List<ColumnDescriptor> columns = Arrays.asList(latColumn, lonColumn, kindColumn);

    ParquetFileReader parquetFileReader = new ParquetFileReader(
      conf,
      readFooter.getFileMetaData(),
      path,
      readFooter.getBlocks(),
      columns);

    long recordCount = 0;
    while (true) {
      PageReadStore pages = parquetFileReader.readNextRowGroup();
      if (pages == null) {
        break;
      }
      recordCount += pages.getRowCount();
      for (ColumnDescriptor column : columns) {
        PageReader pageReader = pages.getPageReader(column);
        if (column == kindColumn) {
          assertNotNull(pageReader.readDictionaryPage());
        } else {
          assertNull(pageReader.readDictionaryPage());
        }
      }
    }
    parquetFileReader.close();
    assertEquals(500, recordCount);
  }

  @Test
  public void testSortedDictionaries() throws Exception {
    // make sure dictionary contents are sorted
    Path path = new Path(phonebookFile.toURI());
    Configuration conf = new Configuration();
    ParquetMetadata readFooter = ParquetFileReader.readFooter(conf, path);
    String [] kindPath = { "phoneNumbers", "phone", "kind"};
    ColumnDescriptor kindColumn = PhoneBookWriter.getColumnDescriptor(kindPath);
    final ParquetProperties parquetProperties = new ParquetProperties(4096, PARQUET_2_0, true);
    List<ColumnDescriptor> columns = Arrays.asList(kindColumn);

    ParquetFileReader parquetFileReader = new ParquetFileReader(
      conf,
      readFooter.getFileMetaData(),
      path,
      readFooter.getBlocks(),
      columns);

    long recordCount = 0;
    while (true) {
      PageReadStore pages = parquetFileReader.readNextRowGroup();
      if (pages == null) {
        break;
      }
      recordCount += pages.getRowCount();
      PageReader pageReader = pages.getPageReader(kindColumn);
      DictionaryPage dictionaryPage = pageReader.readDictionaryPage();
      assertNotNull(dictionaryPage);
      SortedDictionary sortedDictionary = new SortedDictionary(dictionaryPage, kindColumn, parquetProperties);
      assertEquals(kinds.length, dictionaryPage.getDictionarySize());
      for (int i = 0; i < dictionaryPage.getDictionarySize(); ++i) {
        assertEquals(i, sortedDictionary.getNewId(i));
      }
    }
    parquetFileReader.close();
    assertEquals(500, recordCount);
  }
}
