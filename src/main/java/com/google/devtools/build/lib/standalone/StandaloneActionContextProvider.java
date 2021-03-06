// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.standalone;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionContext;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactResolver;
import com.google.devtools.build.lib.actions.ExecutionStrategy;
import com.google.devtools.build.lib.actions.ResourceManager;
import com.google.devtools.build.lib.analysis.test.TestActionContext;
import com.google.devtools.build.lib.exec.ActionContextProvider;
import com.google.devtools.build.lib.exec.ExecutionOptions;
import com.google.devtools.build.lib.exec.FileWriteStrategy;
import com.google.devtools.build.lib.exec.SpawnRunner;
import com.google.devtools.build.lib.exec.StandaloneTestStrategy;
import com.google.devtools.build.lib.exec.TestStrategy;
import com.google.devtools.build.lib.exec.apple.XCodeLocalEnvProvider;
import com.google.devtools.build.lib.exec.local.LocalEnvProvider;
import com.google.devtools.build.lib.exec.local.LocalExecutionOptions;
import com.google.devtools.build.lib.exec.local.LocalSpawnRunner;
import com.google.devtools.build.lib.exec.local.PosixLocalEnvProvider;
import com.google.devtools.build.lib.exec.local.WindowsLocalEnvProvider;
import com.google.devtools.build.lib.rules.cpp.IncludeScanningContext;
import com.google.devtools.build.lib.rules.cpp.SpawnGccStrategy;
import com.google.devtools.build.lib.rules.test.ExclusiveTestStrategy;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.util.OS;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import java.io.IOException;

/**
 * Provide a standalone, local execution context.
 */
public class StandaloneActionContextProvider extends ActionContextProvider {

  /**
   * a IncludeScanningContext that does nothing. Since local execution does not need to
   * discover inclusion in advance, we do not need include scanning.
   */
  @ExecutionStrategy(contextType = IncludeScanningContext.class)
  class DummyIncludeScanningContext implements IncludeScanningContext {
    @Override
    public void extractIncludes(
        ActionExecutionContext actionExecutionContext,
        Action resourceOwner,
        Artifact primaryInput,
        Artifact primaryOutput)
        throws IOException {
      FileSystemUtils.writeContent(primaryOutput.getPath(), new byte[]{});
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
      return env.getSkyframeBuildView().getArtifactFactory();
    }
  }

  private final CommandEnvironment env;

  public StandaloneActionContextProvider(CommandEnvironment env) {
    this.env = env;
  }

  @Override
  public Iterable<? extends ActionContext> getActionContexts() {
    ExecutionOptions executionOptions = env.getOptions().getOptions(ExecutionOptions.class);
    Path testTmpRoot =
        TestStrategy.getTmpRoot(env.getWorkspace(), env.getExecRoot(), executionOptions);

    TestActionContext testStrategy =
        new StandaloneTestStrategy(
            executionOptions,
            env.getBlazeWorkspace().getBinTools(),
            testTmpRoot);
    // Order of strategies passed to builder is significant - when there are many strategies that
    // could potentially be used and a spawnActionContext doesn't specify which one it wants, the
    // last one from strategies list will be used
    return ImmutableList.of(
        new StandaloneSpawnStrategy(env.getExecRoot(), createLocalRunner(env)),
        new DummyIncludeScanningContext(),
        new SpawnGccStrategy(),
        testStrategy,
        new ExclusiveTestStrategy(testStrategy),
        new FileWriteStrategy());
  }

  private static SpawnRunner createLocalRunner(CommandEnvironment env) {
    LocalExecutionOptions localExecutionOptions =
        env.getOptions().getOptions(LocalExecutionOptions.class);
    LocalEnvProvider localEnvProvider =
        OS.getCurrent() == OS.DARWIN
            ? new XCodeLocalEnvProvider()
            : (OS.getCurrent() == OS.WINDOWS
                ? WindowsLocalEnvProvider.INSTANCE
                : PosixLocalEnvProvider.INSTANCE);
    return
        new LocalSpawnRunner(
            env.getExecRoot(),
            localExecutionOptions,
            ResourceManager.instance(),
            env.getRuntime().getProductName(),
            localEnvProvider);
  }
}
