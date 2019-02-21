/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.http;

import static java.util.stream.Collectors.toList;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.List;

public final class DefaultLoadBalancer implements LoadBalancer {

    private final List<HostAndPort> nodes;
    private final NodeSelectionStrategy strategy;
    private final Cache<HostAndPort, Unhealthy> unhealthyNodes;

    private volatile int currentIndex;

    DefaultLoadBalancer(List<HostAndPort> nodes, NodeSelectionStrategy strategy, Duration coolOffPeriod) {
        this.nodes = nodes;
        this.strategy = strategy;
        this.currentIndex = 0;
        this.unhealthyNodes = Caffeine.newBuilder()
                .expireAfterWrite(coolOffPeriod)
                .build();
    }

    @Override
    public synchronized HostAndPort selectNode(HttpHeaders headers) {
        if (strategy.equals(NodeSelectionStrategy.ROUND_ROBIN)) {
            currentIndex = nextIndex();
        }

        for (int i = 0; i < nodes.size(); i++) {
            HostAndPort node = nodes.get(currentIndex);
            if (unhealthyNodes.getIfPresent(node) == null) {
                return node;
            }
            currentIndex = nextIndex();
        }

        throw new IllegalStateException("No healthy uris");
    }

    @Override
    public synchronized void unhealthyNode(HostAndPort node) {
        unhealthyNodes.put(node, Unhealthy.INSTANCE);

        if (strategy.equals(NodeSelectionStrategy.PIN_UNTIL_ERROR)) {
            currentIndex = nextIndex();
        }
    }

    private int nextIndex() {
        return (currentIndex + 1) % nodes.size();
    }

    @Override
    public void rateLimited(HostAndPort node) {
        // TODO(jellis): implement
    }

    private enum Unhealthy {
        INSTANCE
    }
}
