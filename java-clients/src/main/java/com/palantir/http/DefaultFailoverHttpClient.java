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

import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.exceptions.SafeIoException;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.OpenSpan;
import com.palantir.tracing.api.SpanType;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.ws.rs.core.UriBuilder;
import okhttp3.Request;

public final class DefaultFailoverHttpClient implements FailoverHttpClient {

    /** The HTTP header used to communicate API endpoint names internally. Not considered public API. */
    public static final String PATH_TEMPLATE_HEADER = "hr-path-template";

    private final HttpClient client;
    private final LoadBalancer loadBalancer;
    private final Supplier<BackoffStrategy> backoff;

    public DefaultFailoverHttpClient(HttpClient client, LoadBalancer loadBalancer, Supplier<BackoffStrategy> backoff) {
        this.client = client;
        this.loadBalancer = loadBalancer;
        this.backoff = backoff;
    }

    public static DefaultFailoverHttpClient configure(ClientConfiguration config) {
        return new DefaultFailoverHttpClient(
                HttpClients.configure(config),
                LoadBalancers.configure(config),
                () -> ExponentialBackoff.configure(config));
    }

    @Override
    public <T> HttpResponse<T> send(
            HttpRequest.Builder builder,
            HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        HttpRequest request = builder.build();

        OpenSpan span = Tracer.startSpan(spanName(request), SpanType.CLIENT_OUTGOING);

        try {
            addSpanHeaders(builder, span);
            return untracedSend(builder, responseBodyHandler, request);
        } finally {
            Tracer.completeSpan();
        }
    }

    private <T> HttpResponse<T> untracedSend(
            HttpRequest.Builder builder,
            HttpResponse.BodyHandler<T> responseBodyHandler, HttpRequest request)
            throws InterruptedException, SafeIoException {
        BackoffStrategy backoffStrategy = backoff.get();

        while (true) {
            HostAndPort node = loadBalancer.selectNode(request.headers());
            HttpRequest currentRequest = builder
                    // .setHeader(HttpHeaders.HOST, request.uri().getHost())
                    .uri(createRequest(node, request.uri()))
                    .build();

            try {
                HttpResponse<T> response = client.send(currentRequest, responseBodyHandler);
                if (response.statusCode() == 503) {
                    loadBalancer.unhealthyNode(node);
                    syncBackoff(backoffStrategy);
                } else if (response.statusCode() == 429) {
                    loadBalancer.rateLimited(node);
                    syncBackoff(backoffStrategy);
                } else {
                    return response;
                }
            } catch (IOException e) {
                loadBalancer.unhealthyNode(node);
                syncBackoff(backoffStrategy);
            }
        }
    }

    private void syncBackoff(BackoffStrategy backoffStrategy) throws InterruptedException, SafeIoException {
        Optional<Duration> backoffDuration = backoffStrategy.nextBackoff();
        if (backoffDuration.isPresent()) {
            Thread.sleep(backoffDuration.get().toMillis());
        } else {
            throw new IllegalStateException("Failed after retrying maximum number of times");
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest.Builder builder,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
        HttpRequest request = builder.build();
        HostAndPort node = loadBalancer.selectNode(request.headers());
        HttpRequest currentRequest = builder.uri(createRequest(node, request.uri())).build();

        return client.sendAsync(currentRequest, responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest.Builder builder,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        HttpRequest request = builder.build();
        HostAndPort node = loadBalancer.selectNode(request.headers());
        HttpRequest currentRequest = builder.uri(createRequest(node, request.uri())).build();

        return client.sendAsync(currentRequest, responseBodyHandler, pushPromiseHandler);
    }

    private URI createRequest(HostAndPort node, URI originalUri) {
        return UriBuilder.fromUri(originalUri)
                .host(node.getHost())
                .port(node.getPort())
                .build();
    }

    private void addSpanHeaders(HttpRequest.Builder builder, OpenSpan span) {
        builder.header(TraceHttpHeaders.TRACE_ID, Tracer.getTraceId())
                .header(TraceHttpHeaders.SPAN_ID, span.getSpanId())
                .header(TraceHttpHeaders.IS_SAMPLED, Tracer.isTraceObservable() ? "1" : "0");
        span.getParentSpanId().ifPresent(parent -> builder.header(TraceHttpHeaders.PARENT_SPAN_ID, parent));
    }

    private String spanName(HttpRequest request) {
        StringBuilder span = new StringBuilder("Client: ");
        request.headers().firstValue(PATH_TEMPLATE_HEADER).ifPresent(span::append);
        return span.toString();
    }

}
