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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;

public final class UriSelector implements HttpInterceptor {

    private final List<URI> uris;
    private final NodeSelectionStrategy strategy;
    private final Cache<URI, Unhealthy> unhealthyNodes;

    private volatile int currentIndex;

    public UriSelector(List<URI> uris, NodeSelectionStrategy strategy, Duration coolOffPeriod) {
        this.uris = uris;
        this.strategy = strategy;
        this.currentIndex = 0;
        this.unhealthyNodes = Caffeine.newBuilder()
                .expireAfterWrite(coolOffPeriod)
                .build();
    }

    @Override
    public HttpRequest handleRequest(HttpRequest request) {
        URI healthyUri = nextHealthyUri();
        URI uri = UriBuilder.fromUri(request.uri())
                .host(healthyUri.getHost())
                .port(healthyUri.getPort())
                .build();
        return copy(request)
                .uri(uri)
                .build();
    }

    @Override
    public <T> HttpResponse<T> handleResponse(HttpResponse<T> response) {
        if (response.statusCode() / 100 == 5) {
            unhealthyNodes.put(response.uri(), Unhealthy.INSTANCE);
        }

        return response;
    }

    private synchronized URI nextHealthyUri() {
        int numUris = uris.size();

        if (strategy.equals(NodeSelectionStrategy.ROUND_ROBIN)) {
            currentIndex = (currentIndex + 1) % numUris;
        }

        for (int i = 0; i < numUris; i++) {
            URI uri = uris.get(currentIndex);
            if (unhealthyNodes.getIfPresent(uri) == null) {
                return uri;
            }
            currentIndex = (currentIndex + 1) % numUris;
        }

        throw new IllegalStateException("No healthy uris");
    }

    public HttpRequest.Builder copy(HttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri());

        request.bodyPublisher().ifPresent(pub -> builder.method(request.method(), pub));
        request.timeout().ifPresent(builder::timeout);
        request.headers().map().forEach((key, vals) -> builder.header(key, Joiner.on(",").join(vals)));

        return builder;
    }

    private enum Unhealthy { INSTANCE }
}
