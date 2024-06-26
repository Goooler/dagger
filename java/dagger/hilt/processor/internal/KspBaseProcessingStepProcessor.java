/*
 * Copyright (C) 2023 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.processor.internal;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.ksp.KspBasicAnnotationProcessor;
import com.google.common.collect.ImmutableList;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;

/** A KspBasicAnnotationProcessor that contains a single BaseProcessingStep. */
public abstract class KspBaseProcessingStepProcessor extends KspBasicAnnotationProcessor {
  private BaseProcessingStep processingStep;

  public KspBaseProcessingStepProcessor(SymbolProcessorEnvironment symbolProcessorEnvironment) {
    super(symbolProcessorEnvironment, HiltProcessingEnvConfigs.CONFIGS);
  }

  @Override
  public void initialize(XProcessingEnv env) {
    HiltCompilerOptions.checkWrongAndDeprecatedOptions(env);
    processingStep = processingStep();
  }

  protected abstract BaseProcessingStep processingStep();

  @Override
  public void preRound(XProcessingEnv env, XRoundEnv round) {
    processingStep.preRoundProcess(env, round);
  }

  @Override
  public final ImmutableList<XProcessingStep> processingSteps() {
    return ImmutableList.of(processingStep);
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv round) {
    processingStep.postRoundProcess(env, round);
  }
}
