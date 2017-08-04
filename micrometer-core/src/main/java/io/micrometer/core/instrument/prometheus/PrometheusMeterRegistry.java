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
package io.micrometer.core.instrument.prometheus;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.AbstractMeterRegistry;
import io.micrometer.core.instrument.util.MapAccess;
import io.micrometer.core.instrument.util.MeterId;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private static final PrometheusTagFormatter tagFormatter = new PrometheusTagFormatter();
    private final CollectorRegistry registry;

    private final ConcurrentMap<String, Collector> collectorMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<MeterId, Meter> meterMap = new ConcurrentHashMap<>();

    public PrometheusMeterRegistry() {
        this(new CollectorRegistry());
    }

    public PrometheusMeterRegistry(CollectorRegistry registry) {
        this(registry, Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(CollectorRegistry registry, Clock clock) {
        super(clock);
        this.registry = registry;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    /**
     * Content that should be included in the response body for an endpoint designate for
     * Prometheus to scrape from.
     */
    public String scrape() {
        Writer writer = new StringWriter();
        try {
            TextFormat.write004(writer, registry.metricFamilySamples());
        } catch (IOException e) {
            // This actually never happens since StringWriter::write() doesn't throw any IOException
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    @Override
    public <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> id.getTags().containsAll(tagsToMatch))
                .findAny()
                .map(meterMap::get)
                .filter(mClass::isInstance)
                .map(mClass::cast);
    }

    public Optional<Meter> findMeter(Meter.Type type, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> id.getTags().containsAll(tagsToMatch))
                .findAny()
                .map(meterMap::get)
                .filter(m -> m.getType().equals(type));
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        MeterId id = new MeterId(name, withCommonTags(tags));
        io.prometheus.client.Counter counter = collectorByName(io.prometheus.client.Counter.class, name,
                n -> buildCollector(id, io.prometheus.client.Counter.build()));
        return MapAccess.computeIfAbsent(meterMap, id, c -> new PrometheusCounter(id, child(counter, id.getTags())));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        Iterable<Tag> allTags = withCommonTags(tags);
        MeterId id = new MeterId(name, allTags);
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, name,
                n -> new CustomPrometheusSummary(name, stream(allTags.spliterator(), false).map(Tag::getKey).collect(toList())).register(registry));
        return MapAccess.computeIfAbsent(meterMap, id, t -> new PrometheusDistributionSummary(id, summary.child(allTags, quantiles, histogram)));
    }

    @Override
    protected io.micrometer.core.instrument.Timer timer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        Iterable<Tag> allTags = withCommonTags(tags);
        MeterId id = new MeterId(name, allTags);
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, name,
                n -> new CustomPrometheusSummary(name, stream(allTags.spliterator(), false).map(Tag::getKey).collect(toList())).register(registry));
        return MapAccess.computeIfAbsent(meterMap, id, t -> new PrometheusTimer(id, summary.child(allTags, quantiles, histogram), getClock()));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        Iterable<Tag> allTags = withCommonTags(tags);
        MeterId id = new MeterId(name, allTags);
        final CustomPrometheusLongTaskTimer longTaskTimer = collectorByName(CustomPrometheusLongTaskTimer.class, name,
                n -> new CustomPrometheusLongTaskTimer(name, stream(allTags.spliterator(), false).map(Tag::getKey).collect(toList()), getClock()).register(registry));
        return MapAccess.computeIfAbsent(meterMap, id, t -> new PrometheusLongTaskTimer(id, longTaskTimer.child(allTags)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);

        Iterable<Tag> allTags = withCommonTags(tags);

        MeterId id = new MeterId(name, allTags);
        io.prometheus.client.Gauge gauge = collectorByName(Gauge.class, name,
                i -> buildCollector(id, io.prometheus.client.Gauge.build()));

        MapAccess.computeIfAbsent(meterMap, id, g -> {
            String[] labelValues = id.getTags().stream()
                    .map(Tag::getValue)
                    .collect(Collectors.toList())
                    .toArray(new String[]{});

            Gauge.Child child = new Gauge.Child() {
                @Override
                public double get() {
                    final T obj = ref.get();
                    return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
                }
            };

            gauge.setChild(child, labelValues);
            return new PrometheusGauge(id, child);
        });

        return obj;
    }

    @Override
    public Meter meter(Meter meter) {
        Collector collector = new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                List<MetricFamilySamples.Sample> samples = meter.measure().stream()
                        .map(m -> {
                            Iterable<Tag> allTags = withCommonTags(m.getTags());
                            List<String> tagKeys = new ArrayList<>();
                            List<String> tagValues = new ArrayList<>();
                            for (Tag tag : allTags) {
                                tagKeys.add(tagFormatter.formatTagKey(tag.getKey()));
                                tagValues.add(tagFormatter.formatTagValue(tag.getValue()));
                            }
                            return new MetricFamilySamples.Sample(tagFormatter.formatName(m.getName()), tagKeys, tagValues, m.getValue());
                        })
                        .collect(toList());

                Type type = Type.UNTYPED;
                switch (meter.getType()) {
                    case Counter:
                        type = Type.COUNTER;
                        break;
                    case Gauge:
                        type = Type.GAUGE;
                        break;
                    case DistributionSummary:
                    case Timer:
                        type = Type.SUMMARY;
                        break;
                }

                return Collections.singletonList(new MetricFamilySamples(meter.getName(), type, " ", samples));
            }
        };
        registry.register(collector);
        collectorMap.put(meter.getName(), collector);
        meterMap.put(new MeterId(meter.getName(), meter.getTags()), meter);
        return meter;
    }

    /**
     * @return The underlying Prometheus {@link CollectorRegistry}.
     */
    public CollectorRegistry getPrometheusRegistry() {
        return registry;
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> C buildCollector(MeterId id,
                                                                                                        SimpleCollector.Builder<B, C> builder) {
        return builder
                .name(tagFormatter.formatName(id.getName()))
                .help(" ")
                .labelNames(id.getTags().stream()
                        .map(t -> tagFormatter.formatTagKey(t.getKey()))
                        .collect(Collectors.toList())
                        .toArray(new String[]{}))
                .register(registry);
    }

    private <C extends SimpleCollector<D>, D> D child(C collector, List<Tag> tags) {
        return collector.labels(tags.stream()
                .map(t -> tagFormatter.formatTagValue(t.getValue()))
                .collect(Collectors.toList())
                .toArray(new String[]{}));
    }

    private <C extends Collector> C collectorByName(Class<C> collectorType, String name, Function<String, C> ifAbsent) {
        C collector = MapAccess.computeIfAbsent(collectorMap, name, ifAbsent);
        if (!collectorType.isInstance(collector)) {
            throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
        }
        return collector;
    }
}
