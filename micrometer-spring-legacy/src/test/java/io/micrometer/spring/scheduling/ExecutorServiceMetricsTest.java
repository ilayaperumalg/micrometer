/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.scheduling;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import io.micrometer.spring.SpringMeters;
import org.junit.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Jon Schneider
 * @author Clint Checketts
 */
public class ExecutorServiceMetricsTest {
    private MeterRegistry registry;

    @Before
    public void before() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    public void threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.initialize();

        SpringMeters.monitor(registry, exec, "exec");
        assertThreadPoolExecutorMetrics("exec");
    }

    @Test
    public void taskScheduler() {
        ThreadPoolTaskScheduler sched = new ThreadPoolTaskScheduler();
        sched.initialize();

        SpringMeters.monitor(registry, sched, "sched");
        assertThreadPoolExecutorMetrics("sched");
    }

    private void assertThreadPoolExecutorMetrics(String name) {
        assertThat(registry.findMeter(Meter.Type.Counter, name + "_tasks")).isPresent();
        assertThat(registry.findMeter(Gauge.class, name + "_queue_size")).isPresent();
        assertThat(registry.findMeter(Gauge.class, name + "_pool_size")).isPresent();
    }
}
