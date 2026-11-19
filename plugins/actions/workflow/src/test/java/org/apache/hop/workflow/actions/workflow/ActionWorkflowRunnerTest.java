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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
    when(workflow.startExecution()).thenThrow(new OutOfMemoryError("Java heap space"));
    Result result = new Result();
    result.setResult(true);
    ActionWorkflowRunner runner = new ActionWorkflowRunner(workflow, result, 0, log);

    runner.run();

    assertTrue(runner.isFinished());
    assertFalse(runner.getResult().getResult());
    assertTrue(runner.getResult().getNrErrors() >= 1);
    verify(workflow).setFinished(true);
    verify(log)
        .logError(contains("java.lang.OutOfMemoryError: Java heap space"), any(Throwable.class));
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
