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

import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public final class ConjureHttpClient {

    public static HttpClient conjureHttpClient(ClientConfiguration conf) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(conf.connectTimeout())
                .proxy(conf.proxy())
                .sslContext(createSslContext(new TrustManager[] {conf.trustManager()}, new KeyManager[]{}));

        conf.proxyCredentials()
                .map(PasswordAuthenticator::new)
                .map(builder::authenticator);

        return builder.build();
    }

    private static SSLContext createSslContext(TrustManager[] trustManagers, KeyManager[] keyManagers) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class PasswordAuthenticator extends Authenticator {
        private final PasswordAuthentication authentication;

        private PasswordAuthenticator(BasicCredentials creds) {
            this.authentication = new PasswordAuthentication(creds.username(), creds.password().toCharArray());
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }
}
