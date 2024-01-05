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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.testing.util.SecurityAssertions;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Collections;

@Property(name = "spec.name", value = "AdminControllerSecuredWithRoleTest")
@Property(name = "micronaut.security.reject-not-found", value = StringUtils.FALSE)
@MicronautTest
class AdminControllerSecuredWithRoleTest {
    @Test
    void getApiAdminsRequiresAdminsRole(@Client("/") HttpClient httpClient) throws IOException {
        SecurityAssertions.assertUnauthorized(httpClient, HttpRequest.GET("/api/admins"));
    }

    @Test
    void postApiAdminsSaveRequiresAdminsRole(@Client("/") HttpClient httpClient) throws IOException {
        SecurityAssertions.assertBadRequest(httpClient, HttpRequest.POST("/api/admins/save", Collections.emptyMap()));
    }

    @Test
    void putApiAdminsRemoveAdminRequiresAdminsRole(@Client("/") HttpClient httpClient) throws IOException {
        SecurityAssertions.assertUnauthorized(httpClient, HttpRequest.PUT("/api/admins/remove_admin/99", Collections.emptyMap()));
    }

    @Requires(property = "spec.name", value = "AdminControllerSecuredWithRoleTest")
    @Singleton
    static class MockAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

        @Override
        public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
            return Publishers.just(Authentication.build("sherlock"));
        }
    }
}