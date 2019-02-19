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
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public final class InterceptingHttpClient extends HttpClient {
    private final HttpClient delegate;
    private final HttpInterceptor interceptor;

    public InterceptingHttpClient(HttpClient delegate, HttpInterceptor interceptor) {
        this.delegate = delegate;
        this.interceptor = interceptor;
    }

    // Send methods overridden with interceptors

    @Override
    public <T> HttpResponse<T> send(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        HttpResponse<T> response = delegate.send(
                interceptor.handleRequest(request),
                responseBodyHandler);
        return interceptor.handleResponse(response);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
        CompletableFuture<HttpResponse<T>> response = delegate.sendAsync(
                interceptor.handleRequest(request),
                responseBodyHandler);
        return response.thenApply(interceptor::handleResponse);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        CompletableFuture<HttpResponse<T>> response = delegate.sendAsync(
                interceptor.handleRequest(request),
                responseBodyHandler,
                pushPromiseHandler);
        return response.thenApply(interceptor::handleResponse);
    }

    // Just delegate the rest

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return delegate.newWebSocketBuilder();
    }
}
