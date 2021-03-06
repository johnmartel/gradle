/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestDirectoryProvider

@CompileStatic
class GradleExecuterBackedSession implements GradleSession {

    final GradleInvocationSpec invocation

    private final TestDirectoryProvider testDirectoryProvider

    private GradleExecuter executer

    GradleExecuterBackedSession(GradleInvocationSpec invocation, TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
        this.invocation = invocation
    }

    @Override
    void prepare() {
        cleanup()
    }


    Runnable runner(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def runner = createExecuter(invocationInfo, invocationCustomizer)
        return {
            if (invocation.expectFailure) {
                runner.runWithFailure()
            } else {
                runner.run()
            }
        }
    }

    @Override
    void cleanup() {
        if (executer != null) {
            try {
                executer.withTasks().withArgument("--stop")
                executer.run()
            } finally {
                executer.stop()
                executer = null
            }
        }
    }

    private GradleExecuter createExecuter(BuildExperimentInvocationInfo invocationInfo, InvocationCustomizer invocationCustomizer) {
        def invocation = invocationCustomizer ? invocationCustomizer.customize(invocationInfo, this.invocation) : this.invocation

        if (executer == null) {
            executer = invocation.gradleDistribution.executer(testDirectoryProvider)
        } else {
            executer.reset()
        }

        executer.
            requireOwnGradleUserHomeDir().
            requireGradleDistribution().
            requireIsolatedDaemons().
            expectDeprecationWarning().
            withStackTraceChecksDisabled().
            withArgument('-u').
            inDirectory(invocation.workingDirectory).
            withTasks(invocation.tasksToRun)


        executer.withBuildJvmOpts('-XX:+PerfDisableSharedMem') // reduce possible jitter caused by slow /tmp
        executer.withBuildJvmOpts(invocation.jvmOpts)

        invocation.args.each { executer.withArgument(it) }

        if (invocation.useDaemon) {
            executer.requireDaemon()
        }

        executer
    }
}
