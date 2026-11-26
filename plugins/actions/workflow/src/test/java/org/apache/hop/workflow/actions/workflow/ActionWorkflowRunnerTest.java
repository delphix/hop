/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.workflow.actions.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hop.core.Result;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.junit.jupiter.api.Test;

class ActionWorkflowRunnerTest {

  @SuppressWarnings("unchecked")
  private final IWorkflowEngine<WorkflowMeta> workflow = mock(IWorkflowEngine.class);

  private final ILogChannel log = mock(ILogChannel.class);

  @Test
  void outOfMemoryErrorFailsTheActionAndMarksItFinished() {
    OutOfMemoryError oom = new OutOfMemoryError("Java heap space");
    when(workflow.startExecution()).thenThrow(oom);
    Result parentResult = new Result();
    parentResult.setResult(true);
    ActionWorkflowRunner runner = new ActionWorkflowRunner(workflow, parentResult, 0, log);

    runner.run();

    assertTrue(runner.isFinished());
    assertNotSame(parentResult, runner.getResult());
    assertFalse(runner.getResult().getResult());
    assertEquals(1, runner.getResult().getNrErrors());
    assertTrue(parentResult.getResult());
    assertEquals(0, parentResult.getNrErrors());
    verify(workflow).stopExecution();
    verify(workflow).setResult(runner.getResult());
    verify(workflow).setFinished(true);
    verify(log).logError(eq("UnexpectedError: "), same(oom));
  }

  @Test
  void otherThrowableFailsTheActionWithTheWorkflowErrorMessage() {
    when(workflow.startExecution()).thenThrow(new IllegalStateException("boom"));
    ActionWorkflowRunner runner = new ActionWorkflowRunner(workflow, new Result(), 0, log);

    runner.run();

    assertTrue(runner.isFinished());
    assertFalse(runner.getResult().getResult());
    verify(workflow).stopExecution();
    verify(log).logError(contains("java.lang.IllegalStateException: boom"), any(Throwable.class));
  }

  @Test
  void failureToStopTheChildWorkflowStillMarksItFinished() {
    when(workflow.startExecution()).thenThrow(new OutOfMemoryError("Java heap space"));
    doThrow(new OutOfMemoryError("again")).when(workflow).stopExecution();
    ActionWorkflowRunner runner = new ActionWorkflowRunner(workflow, new Result(), 0, log);

    runner.run();

    assertTrue(runner.isFinished());
    assertFalse(runner.getResult().getResult());
    verify(workflow).setFinished(true);
  }

  @Test
  void successfulRunKeepsTheWorkflowResult() {
    Result workflowResult = new Result();
    workflowResult.setResult(true);
    when(workflow.startExecution()).thenReturn(workflowResult);
    ActionWorkflowRunner runner = new ActionWorkflowRunner(workflow, new Result(), 0, log);

    runner.run();

    assertTrue(runner.isFinished());
    assertSame(workflowResult, runner.getResult());
    verify(workflow).setResult(workflowResult);
    verify(workflow).setFinished(true);
  }

  @Test
  void stoppedWorkflowIsMarkedFinished() {
    when(workflow.isStopped()).thenReturn(true);
    ActionWorkflowRunner runner = new ActionWorkflowRunner(workflow, new Result(), 0, log);

    runner.run();

    assertTrue(runner.isFinished());
    assertEquals(0, runner.getResult().getNrErrors());
  }
}
