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
package io.micrometer.core.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    @DisplayName("multiple increments are maintained")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void increment(MeterRegistry registry) {
        Counter c = registry.meter("myCounter").counter();
        c.increment();
        clock(registry).addAndGet(1, TimeUnit.SECONDS);
        assertThat(c.count()).isEqualTo(1.0, offset(1e-12));
        c.increment();
        c.increment();
        clock(registry).addAndGet(1, TimeUnit.SECONDS);

        // in the case of a step aggregating system will be 2, otherwise 3
        assertThat(c.count()).isGreaterThanOrEqualTo(2.0);
    }

    @DisplayName("increment by a non-negative amount")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void incrementAmount(MeterRegistry registry) {
        Counter c = registry.meter("myCounter").counter();
        c.increment(2);
        c.increment(0);
        clock(registry).addAndGet(1, TimeUnit.SECONDS);
        assertEquals(2L, c.count());
    }

    @DisplayName("heisen-counter increments by change in a monotonically increasing function when observed")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void heisenCounter(MeterRegistry registry) {
        AtomicLong n = registry.meter("heisen").counter(new AtomicLong(0));
        n.incrementAndGet();

        Meter c = registry.findMeter(Meter.class, "heisen").get();

        clock(registry).addAndGet(1, TimeUnit.SECONDS);
        registry.getMeters().forEach(Meter::measure);
        assertThat(c.measure().get(0).getValue()).isEqualTo(1);
    }
}
