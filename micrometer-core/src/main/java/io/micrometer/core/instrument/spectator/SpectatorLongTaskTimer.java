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
package io.micrometer.core.instrument.spectator;

import io.micrometer.core.instrument.Meters;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;

import java.util.List;

public class SpectatorLongTaskTimer implements LongTaskTimer {
    private final com.netflix.spectator.api.LongTaskTimer timer;

    public SpectatorLongTaskTimer(com.netflix.spectator.api.LongTaskTimer timer) {
        this.timer = timer;
    }

    @Override
    public long start() {
        return timer.start();
    }

    @Override
    public long stop(long task) {
        return timer.stop(task);
    }

    @Override
    public long duration(long task) {
        return timer.duration(task);
    }

    @Override
    public long duration() {
        return timer.duration();
    }

    @Override
    public int activeTasks() {
        return timer.activeTasks();
    }

    @Override
    public String getName() {
        return timer.id().name();
    }

    @Override
    public Iterable<Tag> getTags() {
        return SpectatorUtils.tags(timer);
    }

    @Override
    public List<Measurement> measure() {
        return SpectatorUtils.measurements(timer);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Meters.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Meters.hashCode(this);
    }
}