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

package org.elasticsearch.search.aggregations.bucket;

import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.ValuesSourceAggregator;
import org.elasticsearch.search.aggregations.context.AggregationContext;
import org.elasticsearch.search.aggregations.context.ValuesSource;

import java.io.IOException;
import java.util.List;

/**
 * A base class for aggregators that build buckets. Buckets aggregators build buckets that group documents that share a common theme together.
 * This class should be used as a base class for all aggregator where the common theme is based on values the documents share. Values can either come
 * from a field or are dynamically generated by a script. The source from which the values are resolved per document is abstracted by a
 * {@link ValuesSource) class.<br/>
 *
 * <pre>
 * NOTE:    For buckets that are based on common document theme other than shared values, see {@link BucketsAggregator}.
 * </pre>
 */
public abstract class ValuesSourceBucketsAggregator<VS extends ValuesSource> extends ValuesSourceAggregator<VS> {

    public ValuesSourceBucketsAggregator(String name,
                                         VS valuesSource,
                                         AggregationContext aggregationContext,
                                         Aggregator parent) {

        super(name, valuesSource, aggregationContext, parent);
    }

    /**
     * A base class for a collector that collects/aggregates the document in each bucket (you have a bucket collector per bucket). One can see
     * A bucket collector as a runtime representation of the bucket.
     *
     * @param <VS>
     */
    protected static abstract class BucketCollector<VS extends ValuesSource> implements Collector {

        protected final Aggregator aggregator;
        protected final VS valuesSource;
        public final Aggregator[] subAggregators;
        public final Collector[] collectors;

        /**
         * Creates a new bucket level collector with already initialized sub-aggregators. This ctor will normally be used
         * in those bucket aggregators that have pre-defined & fixed number of buckets (e.g. range and geo_distance).
         *
         * @param valuesSource      The values source this collector works with
         * @param subAggregators    The sub-aggregators (aggregator per bucket) of the "owning" bucket aggregator
         * @param aggregator        The bucket aggregator "owning" this collector
         */
        public BucketCollector(VS valuesSource, Aggregator[] subAggregators, Aggregator aggregator) {
            this.aggregator = aggregator;
            this.valuesSource = valuesSource;
            this.subAggregators = subAggregators;
            this.collectors = new Collector[subAggregators.length];
            for (int i = 0; i < subAggregators.length; i++) {
                collectors[i] = subAggregators[i].collector();
            }
        }

        /**
         * Creates a new bucket level collector that works on the given values source and has the given aggregator factories. This ctor
         * will be used in bucket aggregators that are more dynamic in nature, that is, where the buckets are created dynamically during
         * the get process (e.g. histogram). The reason for having this ctor is that when a bucket is created during the get
         * process, there's already a context that the collectors of the aggregators need to be initialize in (ie. reader, scorer & get
         * context) - this ctor takes care of this initialization.
         *
         * @param valuesSource  The value source this collector works with
         * @param factories     The factories for all the sub-aggregators of the bucket
         * @param aggregator    The "owning" aggregator (the aggregator this collector belongs to)
         */
        public BucketCollector(VS valuesSource, List<Aggregator.Factory> factories, Aggregator aggregator) {

            this.aggregator = aggregator;
            this.valuesSource = valuesSource;
            this.subAggregators = new Aggregator[factories.size()];
            this.collectors = new Collector[subAggregators.length];
            int i = 0;
            for (Aggregator.Factory factory : factories) {
                subAggregators[i] = factory.create(aggregator.context(), aggregator);
                collectors[i] = subAggregators[i].collector();
                i++;
            }
        }

        @Override
        public final void postCollection() {
            for (int i = 0; i < collectors.length; i++) {
                if (collectors[i] != null) {
                    collectors[i].postCollection();
                }
            }
            doPostCollection();
        }

        @Override
        public final void collect(int doc) throws IOException {
            if (onDoc(doc)) {
                for (int i = 0; i < collectors.length; i++) {
                    if (collectors[i] != null) {
                        collectors[i].collect(doc);
                    }
                }
            }
        }

        /**
         * This method is called for every document along with the current aggregation value space. The method returns the value space
         * that should be used for all sub-aggregations in the bucket this collector represents.
         *
         * @param doc           The document id
         * @return              {@code true} if the give doc falls in the bucket, {@code false} otherwise.
         * @throws IOException
         */
        protected abstract boolean onDoc(int doc) throws IOException;

        /**
         * Called after all documents in the context where collected.
         */
        protected void doPostCollection() {
        }

    }

}