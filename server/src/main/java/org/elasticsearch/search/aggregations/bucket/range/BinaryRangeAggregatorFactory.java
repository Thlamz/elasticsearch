/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.bucket.range;

import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactories.Builder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.CardinalityUpperBound;
import org.elasticsearch.search.aggregations.NonCollectingAggregator;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.aggregations.support.CoreValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BinaryRangeAggregatorFactory extends ValuesSourceAggregatorFactory {

    public static void registerAggregators(ValuesSourceRegistry.Builder builder) {
        builder.register(IpRangeAggregationBuilder.REGISTRY_KEY, CoreValuesSourceType.IP, BinaryRangeAggregator::new, true);
    }

    private final IpRangeAggregatorSupplier aggregatorSupplier;
    private final List<BinaryRangeAggregator.Range> ranges;
    private final boolean keyed;

    public BinaryRangeAggregatorFactory(String name,
            ValuesSourceConfig config,
            List<BinaryRangeAggregator.Range> ranges, boolean keyed,
            AggregationContext context,
            AggregatorFactory parent, Builder subFactoriesBuilder,
            Map<String, Object> metadata,
            IpRangeAggregatorSupplier aggregatorSupplier) throws IOException {
        super(name, config, context, parent, subFactoriesBuilder, metadata);
        this.aggregatorSupplier = aggregatorSupplier;
        this.ranges = ranges;
        this.keyed = keyed;
    }

    @Override
    protected Aggregator createUnmapped(Aggregator parent, Map<String, Object> metadata) throws IOException {
        return new NonCollectingAggregator(name, context, parent, factories, metadata) {
            @Override
            public InternalAggregation[] buildAggregations(long[] owningBucketOrds)
            {
                int totalBuckets = owningBucketOrds.length * ranges.size();
                long[] bucketOrdsToCollect = new long[totalBuckets];
                int bucketOrdIdx = 0;
                for (long owningBucketOrd : owningBucketOrds) {
                    long ord = owningBucketOrd * ranges.size();
                    for (int offsetInOwningOrd = 0; offsetInOwningOrd < ranges.size(); offsetInOwningOrd++) {
                        bucketOrdsToCollect[bucketOrdIdx++] = ord++;
                    }
                }
                InternalAggregation[] results = new InternalAggregation[owningBucketOrds.length];
                for (int owningOrdIdx = 0; owningOrdIdx < owningBucketOrds.length; owningOrdIdx++) {
                    List<InternalBinaryRange.Bucket> buckets = new ArrayList<>(ranges.size());
                    for (int offsetInOwningOrd = 0; offsetInOwningOrd < ranges.size(); offsetInOwningOrd++) {
                        BinaryRangeAggregator.Range range = ranges.get(offsetInOwningOrd);
                        buckets.add(new InternalBinaryRange.Bucket(config.format(), keyed, range.key,
                        range.from, range.to, 0, buildEmptySubAggregations()));
                    }
                    results[owningOrdIdx] = new InternalBinaryRange(name, config.format(), keyed, buckets, metadata());
                }
                return results;
            }

            @Override
            public InternalAggregation buildEmptyAggregation() {
                return new InternalBinaryRange(name, config.format(), keyed, new ArrayList<>(0), metadata);
            }
        };
    }

    @Override
    protected Aggregator doCreateInternal(
        Aggregator parent,
        CardinalityUpperBound cardinality,
        Map<String, Object> metadata
    ) throws IOException {
        return aggregatorSupplier
            .build(name, factories, config.getValuesSource(), config.format(),
                   ranges, keyed, context, parent, cardinality, metadata);
    }

}
