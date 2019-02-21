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

import com.google.common.net.HostAndPort;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import java.net.URI;
import java.util.List;

final class LoadBalancers {

    private LoadBalancers() {}

    static LoadBalancer configure(ClientConfiguration config) {
        if (config.meshProxy().isPresent()) {
            return new MeshProxyLoadBalancer(config.meshProxy().get());
        } else {
            List<HostAndPort> nodes = config.uris().stream()
                    .map(URI::create)
                    .map(uri -> HostAndPort.fromParts(uri.getHost(), uri.getPort()))
                    .collect(toList());
            return new DefaultLoadBalancer(nodes, config.nodeSelectionStrategy(), config.failedUrlCooldown());
        }
    }
}
