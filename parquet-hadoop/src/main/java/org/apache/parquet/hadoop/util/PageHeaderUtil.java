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
package org.apache.parquet.hadoop.util;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.Preconditions;
import org.apache.parquet.format.PageHeader;
import org.apache.parquet.format.Util;
import org.apache.parquet.hadoop.PageHeaderWithOffset;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;

/**
 * Utility to validate page headers in footer.
 */

public class PageHeaderUtil {

  public static void validatePageHeaders(Path file, ParquetMetadata parquetMetadata) throws IOException {
    FSDataInputStream in = file.getFileSystem(new Configuration()).open(file);

    for (BlockMetaData blockMetaData : parquetMetadata.getBlocks()) {
      for (ColumnChunkMetaData columnChunkMetaData : blockMetaData.getColumns()) {
        List<PageHeaderWithOffset> pageHeaders = columnChunkMetaData.getPageHeaders();
        if (pageHeaders == null || pageHeaders.isEmpty()) {
          continue;
        }
        long startingPos = columnChunkMetaData.getStartingPos();
        long totalSize = columnChunkMetaData.getTotalSize();
        long size = 0;
        int pageIndex = 0;
        // read page header
        while (size < totalSize) {
          in.seek(startingPos);
          PageHeader pageHeader = Util.readPageHeader(in);
          Preconditions.checkArgument(pageHeader.equals(pageHeaders.get(pageIndex).getPageHeader()),
            String.format("header from footer [%s] doesn't match with header fro file [%s]",
              pageHeader.toString(), pageHeaders.get(pageIndex)));
          Preconditions.checkArgument(pageHeaders.get(pageIndex).getOffset() == in.getPos(),
            String.format("invalid offset from footer %d, found in file %d",
              pageHeaders.get(pageIndex).getOffset(), in.getPos()));
          pageIndex++;
          size += pageHeader.getCompressed_page_size() + (in.getPos() - startingPos);
          startingPos = in.getPos() + pageHeader.getCompressed_page_size();
        }
        Preconditions.checkArgument(pageIndex == pageHeaders.size(), String.format("found %d headers, expected %d", pageIndex, pageHeaders.size()));
        Preconditions.checkArgument(size == totalSize, String.format("found %d total size, expected %d", size, totalSize));
      }
    }
  }
}
