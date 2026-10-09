/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.core.row.value;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.BaseDatabaseMeta;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.junit.rules.RestoreHopEnvironment;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class ValueMetaBaseSetPreparedStmntValueTest {

  @ClassRule public static RestoreHopEnvironment env = new RestoreHopEnvironment();

  private DatabaseMeta dbMeta;
  private PreparedStatement ps;
  private Date date;
  private Timestamp ts;

  @Before
  public void setUp() {
    dbMeta = mock(DatabaseMeta.class);
    IDatabase iDb = mock(BaseDatabaseMeta.class);
    when(dbMeta.supportsTimeStampToDateConversion()).thenReturn(true);
    when(dbMeta.getIDatabase()).thenReturn(iDb);
    ps = mock(PreparedStatement.class);
    date = new Date(System.currentTimeMillis());
    ts = new Timestamp(System.currentTimeMillis());
  }

  @After
  public void tearDown() {
    System.clearProperty(Const.HOP_COMPATIBILITY_DB_IGNORE_TIMEZONE);
  }

  @Test
  public void testXMLParsingWithNoDataFormatLocale() throws IOException {
    IValueMeta r1 = new ValueMetaString("value");
    r1.setDateFormatLocale(null);
    IRowMeta row = new RowMeta();
    row.setValueMetaList(new ArrayList<>(Arrays.asList(r1)));

    row.getMetaXml();
  }

  @Test
  public void testDateRegular() throws Exception {

    ValueMetaBase valueMeta = new ValueMetaDate("");
    valueMeta.setPrecision(1);
    valueMeta.setPreparedStatementValue(dbMeta, ps, 1, date);

    verify(ps).setDate(eq(1), any(java.sql.Date.class), any(Calendar.class));
  }

  @Test
  public void testTimestampRegular() throws Exception {

    ValueMetaBase valueMeta = new ValueMetaDate("");
    valueMeta.setPreparedStatementValue(dbMeta, ps, 1, ts);

    verify(ps).setTimestamp(eq(1), any(Timestamp.class), any(Calendar.class));
  }

  @Test
  public void testConvertedTimestampRegular() throws Exception {

    ValueMetaBase valueMeta = new ValueMetaDate("");
    valueMeta.setPreparedStatementValue(dbMeta, ps, 1, date);
    valueMeta.setStorageType(IValueMeta.STORAGE_TYPE_NORMAL);

    verify(ps).setTimestamp(eq(1), any(Timestamp.class), any(Calendar.class));
  }

  @Test
  public void testTimestampWithIgnoreTimezoneSkipsCalendarOverload() throws Exception {
    System.setProperty(Const.HOP_COMPATIBILITY_DB_IGNORE_TIMEZONE, "Y");

    ValueMetaBase valueMeta = new ValueMetaDate("");
    valueMeta.setPreparedStatementValue(dbMeta, ps, 1, ts);

    verify(ps).setTimestamp(eq(1), any(Timestamp.class));
    verify(ps, never()).setTimestamp(eq(1), any(Timestamp.class), any(Calendar.class));
  }

  @Test
  public void testDateWithIgnoreTimezoneSkipsCalendarOverload() throws Exception {
    System.setProperty(Const.HOP_COMPATIBILITY_DB_IGNORE_TIMEZONE, "Y");

    ValueMetaBase valueMeta = new ValueMetaDate("");
    valueMeta.setPrecision(1);
    valueMeta.setPreparedStatementValue(dbMeta, ps, 1, date);

    verify(ps).setDate(eq(1), any(java.sql.Date.class));
    verify(ps, never()).setDate(eq(1), any(java.sql.Date.class), any(Calendar.class));
  }
}
