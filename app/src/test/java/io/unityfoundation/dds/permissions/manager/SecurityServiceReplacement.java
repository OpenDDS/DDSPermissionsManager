// Copyright 2023 DDS Permissions Manager Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.unityfoundation.dds.permissions.manager;

import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.utils.SecurityService;
import jakarta.annotation.PostConstruct;

import java.util.Optional;

public class SecurityServiceReplacement implements SecurityService {

    private ServerAuthentication serverAuthentication;

    @PostConstruct
    void postConstruct() {
        serverAuthentication = new ServerAuthentication("montesm@test.test.com", null, null);
    }

    @Override
    public Optional<String> username() {
        return Optional.empty();
    }

    @Override
    public Optional<Authentication> getAuthentication() {
        return Optional.of(serverAuthentication);
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public boolean hasRole(String role) {
        return false;
    }

    public void setServerAuthentication(ServerAuthentication serverAuthentication) {
        this.serverAuthentication = serverAuthentication;
    }
}
