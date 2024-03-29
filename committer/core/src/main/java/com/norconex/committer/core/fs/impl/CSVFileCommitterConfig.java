/* Copyright 2023 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.committer.core.fs.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.committer.core.fs.BaseFSCommitterConfig;
import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * Commits documents to CSV files (Comma Separated Value).
 * There are two kinds of document representations: upserts and deletions.
 * </p>
 * <p>
 * If you request to split upserts and deletions into separate files,
 * the generated files will start with "upsert-" (for additions/modifications)
 * and "delete-" (for deletions).
 * A request "type" field is always added when both upserts and deletes are
 * added to the same file.  Default header name for it is <code>type</code>,
 * but you can supply your own name with {@link #setTypeHeader(String)}.
 * </p>
 * <p>
 * The generated files are never updated.  Sending a modified document with the
 * same reference will create a new entry and won't modify any existing ones.
 * You can think of the generated files as a set of commit instructions.
 * </p>
 * <p>
 * The generated CSV file names are made of a timestamp and a sequence number.
 * </p>
 * <p>
 * You have the option to give a prefix or suffix to
 * files that will be created (default does not add any).
 * </p>
 *
 * <h3>Content handling</h3>
 * <p>
 * The document content is represented by creating a column with a blank or
 * <code>null</code> field name. When requested, the "content" column
 * will always be present for both upserts and deletes, even if deletes do not
 * have content, for consistency.
 * </p>
 *
 * <h3>Truncate long values</h3>
 * <p>
 * By default, values longer than {@value #DEFAULT_TRUNCATE_AT} are truncated.
 * You can specify a different maximum length globally, or for each column.
 * Use <code>-1</code> for unlimited lenght, or <code>0</code> to use the
 * the global value, or {@value #DEFAULT_TRUNCATE_AT} if the global value
 * is also zero.
 * </p>
 *
 * <h3>CSV Format</h3>
 * <p>
 * Applications consuming CSV files often have different expectations.
 * Subtle format differences that can make opening or parsing a generated
 * CSV file difficult. To help with this, there are preset CSV formats
 * you can chose from:
 * </p>
 * <ul>
 *   <li>DEFAULT</li>
 *   <li>EXCEL</li>
 *   <li>INFORMIX_UNLOAD1.3</li>
 *   <li>INFORMIX_UNLOAD_CSV1.3</li>
 *   <li>MONGO_CSV1.7</li>
 *   <li>MONGO_TSV1.7</li>
 *   <li>MYSQL</li>
 *   <li>ORACLE1.6</li>
 *   <li>POSTGRESSQL_CSV1.5</li>
 *   <li>POSTGRESSQL_TEXT1.5</li>
 *   <li>RFC-4180</li>
 *   <li>TDF</li>
 * </ul>
 *
 * <p>
 * More information on those can be obtained on
 * <a href="https://commons.apache.org/proper/commons-csv/user-guide.html">
 * Apache Commons CSV</a> website.
 * Other formatting options you explicitely configure will overwrite
 * the corresponding setting for the chosen format.
 * </p>
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.core.fs.impl.CSVFileCommitter"
 *     format="(see class documentation)"
 *     showHeaders="[false|true]"
 *     delimiter="(single delimiter character)"
 *     quote="(single quote character)"
 *     escape="(single escape character)"
 *     multiValueJoinDelimiter="(delimiter string)"
 *     typeHeader="(header name for commit request type column)"
 *     truncateAt="(truncate after N characters, default: 5096, unlimited: -1)">
 *   <!-- Repeat "col" for every desired column. -->
 *   <col
 *       field="(source field name, omit or leave blank for document content)"
 *       header="(optional column header name)"
 *       truncateAt="(overwrite truncate)"/>
 *
 *   {@nx.include com.norconex.committer.core.fs.AbstractFSCommitter#options}
 *
 * </committer>
 * }
 *
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
@FieldNameConstants
@NoArgsConstructor
public class CSVFileCommitterConfig extends BaseFSCommitterConfig {

    public static final int DEFAULT_TRUNCATE_AT = 5096;

    public enum CSVFormat {
        DEFAULT,
        EXCEL,
        INFORMIX_UNLOAD1_3,
        INFORMIX_UNLOAD_CSV1_3,
        MONGO_CSV1_7,
        MONGO_TSV1_7,
        MYSQL,
        ORACLE1_6,
        POSTGRESSQL_CSV1_5,
        POSTGRESSQL_TEXT1_5,
        RFC_4180,
        TDF,
    }

    private CSVFormat format;
    private Character delimiter;
    private Character quote;
    private boolean showHeaders;
    private Character escape;
    private int truncateAt = DEFAULT_TRUNCATE_AT;
    private String multiValueJoinDelimiter;
    private String typeHeader;

    private final List<CSVColumn> columns = new ArrayList<>();

    public List<CSVColumn> getColumns() {
        return Collections.unmodifiableList(columns);
    }
    public CSVFileCommitterConfig setColumns(List<CSVColumn> columns) {
        CollectionUtil.setAll(this.columns, columns);
        return this;
    }
}

