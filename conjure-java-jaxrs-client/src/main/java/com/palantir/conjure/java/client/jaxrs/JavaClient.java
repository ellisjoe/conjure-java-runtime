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

package com.palantir.conjure.java.client.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.okhttp.OkHttpClients;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.http.DefaultFailoverHttpClient;
import com.palantir.http.FailoverHttpClient;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIoException;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class JavaClient implements Client {
    private static final ObjectMapper mapper = ObjectMappers.newClientObjectMapper();
    private static final Set<String> ignoredHeaders = ignoredHeaders();

    private final FailoverHttpClient client;
    private final UserAgent userAgent;

    JavaClient(FailoverHttpClient client, UserAgent userAgent) {
        this.client = client;
        this.userAgent = userAgent;
    }

    static JavaClient configure(ClientConfiguration config, UserAgent userAgent) {
        return new JavaClient(DefaultFailoverHttpClient.configure(config), userAgent);
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .method(request.method(), bodyPublisher(request));

        Map<String, Collection<String>> headers =
                Maps.filterKeys(request.headers(), key -> !ignoredHeaders.contains(key));
        headers.forEach((key, vals) -> requestBuilder.header(key, Joiner.on(",").join(vals)));
        requestBuilder.setHeader(HttpHeaders.USER_AGENT, UserAgents.format(userAgent));

        HttpResponse<byte[]> response = execute(requestBuilder);

        if (response.statusCode() / 100 == 2) {
            return Response.create(response.statusCode(), "", convert(response.headers().map()), response.body());
        } else {
            throw new RemoteException(deserializeError(response), response.statusCode());
        }
    }

    private HttpResponse<byte[]> execute(HttpRequest.Builder request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new SafeIoException("Failed to complete the request due to an IOException", e);
        }
    }

    private SerializableError deserializeError(HttpResponse<byte[]> response) throws SafeIoException {
        try {
            return mapper.readValue(response.body(), SerializableError.class);
        } catch (Exception e) {
            throw new SafeIoException("Failed to parse response body as SerializableError",
                    SafeArg.of("code", response.statusCode()),
                    UnsafeArg.of("body", new String(response.body(), StandardCharsets.UTF_8)),
                    SafeArg.of("contentType", response.headers().firstValue(HttpHeaders.CONTENT_TYPE)));
        }
    }

    private HttpRequest.BodyPublisher bodyPublisher(Request request) {
        byte[] body = request.body();
        return body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
    }

    // Feign needs a Map<K, Collection<V>> when it should take a Map<K, ? extends Collection<V>>
    private static <K, V> Map<K, Collection<V>> convert(Map<K, ? extends Collection<V>> original) {
        return (Map<K, Collection<V>>) original;
    }

    private static Set<String> ignoredHeaders() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add(HttpHeaders.CONTENT_LENGTH);
        return set;
    }
}
