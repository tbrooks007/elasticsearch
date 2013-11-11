/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.bucket.single.missing;

import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.bucket.single.SingleBucketAggregator;
import org.elasticsearch.search.aggregations.context.AggregationContext;
import org.elasticsearch.search.aggregations.context.ValuesSource;
import org.elasticsearch.search.aggregations.context.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.factory.AggregatorFactories;
import org.elasticsearch.search.aggregations.factory.ValueSourceAggregatorFactory;

import java.io.IOException;

/**
 *
 */
public class MissingAggregator extends SingleBucketAggregator {

    private ValuesSource valuesSource;

    public MissingAggregator(String name, AggregatorFactories factories, ValuesSource valuesSource,
                             AggregationContext aggregationContext, Aggregator parent) {
        super(name, factories, aggregationContext, parent);
        this.valuesSource = valuesSource;
    }

    @Override
    public InternalAggregation buildAggregation(long owningBucketOrdinal) {
        return new InternalMissing(name, docCount(owningBucketOrdinal), buildSubAggregations(owningBucketOrdinal));
    }

    @Override
    public void collect(int doc, long owningBucketOrdinal) throws IOException {
        if (valuesSource == null || valuesSource.bytesValues().setDocument(doc) == 0) {
            collectSubAggregators(doc, owningBucketOrdinal);
            counts = BigArrays.grow(counts, owningBucketOrdinal + 1);
            counts.increment(owningBucketOrdinal, 1);
        }
    }

    public static class Factory extends ValueSourceAggregatorFactory {

        public Factory(String name, ValuesSourceConfig valueSourceConfig) {
            super(name, InternalMissing.TYPE.name(), valueSourceConfig);
        }

        @Override
        public BucketAggregationMode bucketMode() {
            return BucketAggregationMode.MULTI_BUCKETS;
        }

        @Override
        protected MissingAggregator createUnmapped(AggregationContext aggregationContext, Aggregator parent) {
            return new MissingAggregator(name, factories, null, aggregationContext, parent);
        }

        @Override
        protected MissingAggregator create(ValuesSource valuesSource, long expectedBucketsCount, AggregationContext aggregationContext, Aggregator parent) {
            return new MissingAggregator(name, factories, valuesSource, aggregationContext, parent);
        }
    }

}

