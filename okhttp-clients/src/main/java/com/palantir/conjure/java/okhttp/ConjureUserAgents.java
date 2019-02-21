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

package com.palantir.conjure.java.okhttp;

import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgents;

public final class ConjureUserAgents {

    private ConjureUserAgents() {}

    /**
     * Adds informational {@link UserAgent.Agent}s to the given {@link UserAgent}, one for the conjure-java-runtime
     * library and one for the given service class. Version strings are extracted from the packages'
     * {@link Package#getImplementationVersion implementation version}, defaulting to 0.0.0 if no version can be found.
     */
    public static UserAgent augmentUserAgent(UserAgent agent, Class<?> serviceClass) {
        UserAgent augmentedAgent = agent;

        String maybeServiceVersion = serviceClass.getPackage().getImplementationVersion();
        augmentedAgent = augmentedAgent.addAgent(UserAgent.Agent.of(
                serviceClass.getSimpleName(),
                maybeServiceVersion != null ? maybeServiceVersion : "0.0.0"));

        String maybeRemotingVersion = ConjureUserAgents.class.getPackage().getImplementationVersion();
        augmentedAgent = augmentedAgent.addAgent(UserAgent.Agent.of(
                UserAgents.CONJURE_AGENT_NAME,
                maybeRemotingVersion != null ? maybeRemotingVersion : "0.0.0"));
        return augmentedAgent;
    }

}
