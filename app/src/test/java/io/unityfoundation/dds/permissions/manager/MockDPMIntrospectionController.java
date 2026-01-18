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


import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.json.JsonMapper;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.endpoints.introspection.IntrospectionConfigurationProperties;
import io.micronaut.security.endpoints.introspection.IntrospectionController;
import io.micronaut.security.endpoints.introspection.IntrospectionProcessor;
import io.micronaut.security.endpoints.introspection.IntrospectionResponse;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.validator.RefreshTokenValidator;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Replaces(IntrospectionController.class)
@Requires(env = Environment.TEST)
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("${" + IntrospectionConfigurationProperties.PREFIX + ".path:/token_info}")
public class MockDPMIntrospectionController {

    private Authentication authentication;

    protected final IntrospectionProcessor processor;
    private final JsonMapper jsonMapper;
    private final GroupUserService groupUserService;

    private final RefreshTokenValidator refreshTokenValidator;

    public MockDPMIntrospectionController(IntrospectionProcessor processor, JsonMapper jsonMapper, GroupUserService groupUserService, RefreshTokenValidator refreshTokenValidator) {
        this.processor = processor;
        this.jsonMapper = jsonMapper;
        this.groupUserService = groupUserService;
        this.refreshTokenValidator = refreshTokenValidator;
    }

    @Get
    @SingleResult
    public Publisher<MutableHttpResponse<?>> echo(@NonNull HttpRequest<?> request) {
        Authentication authentication = this.authentication;
        if (authentication == null) {
            Optional<Cookie> jwtToken = request.getCookies().findCookie("JWT");
            Optional<Cookie> jwtRefreshTokenOptional = request.getCookies().findCookie("JWT_REFRESH_TOKEN");

            if (jwtToken.isEmpty() &&
                    jwtRefreshTokenOptional.isPresent() &&
                    refreshTokenValidator.validate(jwtRefreshTokenOptional.get().getValue()).isPresent()) {
                long maxAge = jwtRefreshTokenOptional.get().getMaxAge();

                // maxAge does not contain the cookie's actual max age which is observable in browser console
                // rather, the value is io.netty.handler.codec.http.cookie.Cookie.UNDEFINED_MAX_AGE.
                // see io.netty.handler.codec.http.cookie.DefaultCookie.maxAge
                return Publishers.just(HttpResponse.ok(Map.of("maxAge", maxAge)));
            }

            return Publishers.just(HttpResponse.noContent());
        }

        return getIntrospectionAndValidResponse(authentication, request);
    }

    private Publisher<MutableHttpResponse<?>> getIntrospectionAndValidResponse(Authentication authentication, HttpRequest<?> request) {
        return Publishers.map(Publishers.map(processor.introspect(authentication, request), (Function<IntrospectionResponse, String>) response -> {
            groupUserService.checkUserValidity().forEach(response::addExtension);
            return introspectionResponseAsJsonString(response);
        }), HttpResponse::ok);
    }

    @NonNull
    private String introspectionResponseAsJsonString(@NonNull IntrospectionResponse introspectionResponse) {
        try {
            return new String(jsonMapper.writeValueAsBytes(introspectionResponse), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{\"active:\" false}";
        }
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }
}
