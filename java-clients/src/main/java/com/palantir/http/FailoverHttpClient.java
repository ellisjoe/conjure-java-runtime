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

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Similar to {@link java.net.http.HttpClient} but handles things like routing and failover between nodes.
 */
public interface FailoverHttpClient {
    <T> HttpResponse<T> send(
            HttpRequest.Builder request,
            HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException;

    <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest.Builder request,
            HttpResponse.BodyHandler<T> responseBodyHandler);

    <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest.Builder request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler);
}
