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

package org.apache.hop.core;

import static org.apache.hop.core.Condition.Function;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.junit.rules.RestoreHopEnvironment;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class ConditionTest {
  @ClassRule public static RestoreHopEnvironment env = new RestoreHopEnvironment();

  @Test
  public void testNegatedTrueFuncEvaluatesAsFalse() throws Exception {
    String left = "test_filed";
    String right = "test_value";
    Function func = Function.TRUE;
    boolean negate = true;

    Condition condition = new Condition(negate, left, func, right, null);
    assertFalse(condition.evaluate(new RowMeta(), new Object[] {"test"}));
  }

  @Test
  public void testCacheInvalidationTest() throws Exception {
    IRowMeta rowMeta1 = new RowMeta();
    rowMeta1.addValueMeta(new ValueMetaNumber("name1"));
    rowMeta1.addValueMeta(new ValueMetaNumber("name2"));
    rowMeta1.addValueMeta(new ValueMetaNumber("name3"));

    IRowMeta rowMeta2 = new RowMeta();
    rowMeta2.addValueMeta(new ValueMetaNumber("name2"));
    rowMeta2.addValueMeta(new ValueMetaNumber("name1"));
    rowMeta2.addValueMeta(new ValueMetaNumber("name3"));

    String left = "name1";
    String right = "name3";
    Condition condition = new Condition(left, Function.EQUAL, right, null);

    assertTrue(condition.evaluate(rowMeta1, new Object[] {1.0, 2.0, 1.0}));
    assertTrue(condition.evaluate(rowMeta2, new Object[] {2.0, 1.0, 1.0}));
  }

  @Test
  public void testNullLessThanNumberEvaluatesAsFalse() throws Exception {
    IRowMeta rowMeta1 = new RowMeta();
    rowMeta1.addValueMeta(new ValueMetaInteger("name1"));

    String left = "name1";
    ValueMetaAndData rightExact = new ValueMetaAndData(new ValueMetaInteger("name1"), -10L);

    Condition condition = new Condition(left, Function.SMALLER, null, rightExact);
    assertFalse(condition.evaluate(rowMeta1, new Object[] {null, "test"}));

    condition = new Condition(left, Function.SMALLER_EQUAL, null, rightExact);
    assertFalse(condition.evaluate(rowMeta1, new Object[] {null, "test"}));
  }

  @Test
  public void testConstantRightValueStaysCorrectAcrossRows() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("name1"));

    ValueMetaAndData rightExact = new ValueMetaAndData(new ValueMetaInteger("name1"), 10000L);
    Condition condition = new Condition("name1", Function.SMALLER, null, rightExact);

    // The right value is built once and then shared, so repeated evaluation must not drift.
    assertTrue(condition.evaluate(rowMeta, new Object[] {5000L}));
    assertFalse(condition.evaluate(rowMeta, new Object[] {15000L}));
    assertTrue(condition.evaluate(rowMeta, new Object[] {9999L}));
    assertFalse(condition.evaluate(rowMeta, new Object[] {10000L}));
  }

  @Test
  public void testConstantRightValueIsBuiltOnlyOnce() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("name1"));

    ValueMetaAndData rightExact = new ValueMetaAndData(new ValueMetaInteger("name1"), 10000L);
    Condition condition = new Condition("name1", Function.SMALLER, null, rightExact);
    assertTrue(condition.evaluate(rowMeta, new Object[] {5000L}));

    Condition.CValue rightValue = condition.getRightValue();
    assertSame(rightValue.getCachedValueMeta(), rightValue.getCachedValueMeta());
    assertSame(rightValue.getCachedValueData(), rightValue.getCachedValueData());
    // createValueMeta() must keep handing fresh objects to callers that modify the result.
    assertNotSame(rightValue.getCachedValueMeta(), rightValue.createValueMeta());
  }

  @Test
  public void testConstantRightValueIsRebuiltAfterMutation() throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("name1"));

    ValueMetaAndData rightExact = new ValueMetaAndData(new ValueMetaInteger("name1"), 10000L);
    Condition condition = new Condition("name1", Function.SMALLER, null, rightExact);
    assertTrue(condition.evaluate(rowMeta, new Object[] {5000L}));

    // Mutating the right value must discard the cache rather than keep comparing against 10000.
    condition.getRightValue().setText("1000");
    assertFalse(condition.evaluate(rowMeta, new Object[] {5000L}));
    assertTrue(condition.evaluate(rowMeta, new Object[] {999L}));

    condition.getRightValue().setNullValue(true);
    assertFalse(condition.evaluate(rowMeta, new Object[] {999L}));
  }

  /**
   * Pins the performance contract itself: evaluate() must hand the same right-side instances to
   * every row rather than rebuilding them. Asserting on the cache accessors alone is not enough,
   * because they would keep returning a stable instance even if evaluate() went back to calling
   * createValueMeta() / createValueData() per row.
   */
  @Test
  public void testEvaluateReusesTheRightValueAcrossRows() throws Exception {
    CapturingValueMeta leftMeta = new CapturingValueMeta("name1");
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(leftMeta);

    ValueMetaAndData rightExact = new ValueMetaAndData(new ValueMetaInteger("name1"), 10000L);
    Condition condition = new Condition("name1", Function.SMALLER, null, rightExact);

    assertTrue(condition.evaluate(rowMeta, new Object[] {5000L}));
    assertTrue(condition.evaluate(rowMeta, new Object[] {6000L}));

    assertEquals(2, leftMeta.rightMetas.size());
    assertSame(leftMeta.rightMetas.get(0), leftMeta.rightMetas.get(1));
    assertSame(leftMeta.rightData.get(0), leftMeta.rightData.get(1));
  }

  /** Records the right-side metadata and data that evaluate() passes into the left value. */
  private static final class CapturingValueMeta extends ValueMetaInteger {
    private final List<IValueMeta> rightMetas = new ArrayList<>();
    private final List<Object> rightData = new ArrayList<>();

    private CapturingValueMeta(String name) {
      super(name);
    }

    @Override
    public int compare(Object data1, IValueMeta meta2, Object data2) throws HopValueException {
      rightMetas.add(meta2);
      rightData.add(data2);
      return super.compare(data1, meta2, data2);
    }
  }

  /**
   * One Condition is shared by every copy of a transform, so the cached right value has to tolerate
   * concurrent first access. This exercises the accessors directly rather than evaluate(), which
   * writes leftFieldIndex, rightFieldIndex, rightValue, inList and rightString on the shared
   * instance and so is not thread safe regardless of any caching.
   */
  @Test
  public void testCachedRightValueTolerateConcurrentColdAccess() throws Exception {
    ValueMetaAndData rightExact = new ValueMetaAndData(new ValueMetaInteger("name1"), 10000L);
    Condition condition = new Condition("name1", Function.SMALLER, null, rightExact);
    Condition.CValue rightValue = condition.getRightValue();

    int threads = 8;
    CountDownLatch readySetGo = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<?>> results = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        results.add(
            pool.submit(
                () -> {
                  readySetGo.await();
                  for (int n = 0; n < 500; n++) {
                    assertNotNull(rightValue.getCachedValueMeta());
                    assertEquals(Long.valueOf(10000L), rightValue.getCachedValueData());
                  }
                  return null;
                }));
      }
      // Release every thread onto a cold cache at the same moment.
      readySetGo.countDown();
      for (Future<?> result : results) {
        // Rethrows any assertion failure or exception raised on a worker thread.
        result.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void testSerialization() throws Exception {
    Document document = XmlHandler.loadXmlFile(getClass().getResourceAsStream("/condition.xml"));
    Node node = XmlHandler.getSubNode(document, Condition.XML_TAG);

    Condition condition = new Condition(node);

    Assert.assertNotNull(condition);
    Assert.assertEquals(2, condition.getChildren().size());
    Condition c1 = condition.getChildren().get(0);
    Assert.assertEquals("stateCode", c1.getLeftValueName());
    Assert.assertEquals("FL", c1.getRightValueString());

    Condition c2 = condition.getChildren().get(1);
    Assert.assertEquals("housenr", c2.getLeftValueName());
    Assert.assertEquals("100", c2.getRightValueString());
  }

  @Test
  public void testSerialization2() throws Exception {
    Document document = XmlHandler.loadXmlFile(getClass().getResourceAsStream("/condition2.xml"));
    Node node = XmlHandler.getSubNode(document, Condition.XML_TAG);

    Condition condition = new Condition(node);

    Assert.assertNotNull(condition);
    Assert.assertEquals(0, condition.getChildren().size());

    Assert.assertEquals("id1", condition.getLeftValueName());
    Assert.assertEquals("rangeStart", condition.getRightValueName());
    Assert.assertNull(condition.getRightValue());
    Assert.assertEquals(Function.LARGER_EQUAL, condition.getFunction());
  }
}
