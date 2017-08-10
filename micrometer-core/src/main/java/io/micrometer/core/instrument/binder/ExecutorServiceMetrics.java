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
package io.micrometer.core.instrument.binder;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.internal.TimedExecutorService;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.Collections.singletonList;

/**
 * Monitors the status of executor service pools. Does not record timings on operations executed in the {@link ExecutorService},
 * as this requires the instance to be wrapped. Timings are provided separately by wrapping the executor service
 * with {@link TimedExecutorService}.
 *
 * @author Jon Schneider
 * @author Clint Checketts
 */
public class ExecutorServiceMetrics implements MeterBinder {
    private final ExecutorService executorService;
    private final String name;
    private final Iterable<Tag> tags;

    public ExecutorServiceMetrics(ExecutorService executorService, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
        this.executorService = executorService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (executorService == null) {
            return;
        }

        String className = executorService.getClass().getName();

        if (executorService instanceof ThreadPoolExecutor) {
            monitor(registry, (ThreadPoolExecutor) executorService);
        } else if (className.equals("java.util.concurrent.Executors$DelegatedScheduledExecutorService")) {
            monitor(registry, unwrapThreadPoolExecutor(executorService, executorService.getClass()));
        } else if (className.equals("java.util.concurrent.Executors$FinalizableDelegatedExecutorService")) {
            monitor(registry, unwrapThreadPoolExecutor(executorService, executorService.getClass().getSuperclass()));
        } else if (executorService instanceof ForkJoinPool) {
            monitor(registry, (ForkJoinPool) executorService);
        }
    }

    /**
     * Every ScheduledThreadPoolExecutor created by {@link Executors} is wrapped. Also,
     * {@link Executors#newSingleThreadExecutor()} wrap a regular {@link ThreadPoolExecutor}.
     */
    private ThreadPoolExecutor unwrapThreadPoolExecutor(ExecutorService executor, Class<?> wrapper) {
        try {
            Field e = wrapper.getDeclaredField("e");
            e.setAccessible(true);
            return (ThreadPoolExecutor) e.get(executorService);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Do nothing. We simply can't get to the underlying ThreadPoolExecutor.
        }
        return null;
    }

    private void monitor(MeterRegistry registry, ThreadPoolExecutor tp) {
        if (tp == null) {
            return;
        }

        // queued tasks = tasks - completed - active
        registry.meter(name + "_tasks").gauge(tp, tpRef -> tpRef.getTaskCount() + tpRef.getCompletedTaskCount() + tpRef.getActiveCount());
        registry.meter(name + "_completed").gauge(tp, ThreadPoolExecutor::getCompletedTaskCount);

        registry.meter(name + "_active").gauge(tp, ThreadPoolExecutor::getActiveCount);
        registry.meter(name + "_queue_size").tags(tags).gauge(tp, tpRef -> tpRef.getQueue().size());
        registry.meter(name + "_pool_size").tags(tags).gauge(tp, ThreadPoolExecutor::getPoolSize);
    }

    private void monitor(MeterRegistry registry, ForkJoinPool fj) {
        registry.meter(name + "_steal_count").gauge(fj, ForkJoinPool::getStealCount);

        registry.meter(name + "_queued_tasks").gauge(fj, ForkJoinPool::getQueuedTaskCount);
        registry.meter(name + "_active").gauge(fj, ForkJoinPool::getActiveThreadCount);
        registry.meter(name + "_running_threads").gauge(fj, ForkJoinPool::getRunningThreadCount);
    }
}
