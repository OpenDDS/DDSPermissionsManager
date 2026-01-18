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
package io.unityfoundation.dds.permissions.manager.security;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.errors.OauthErrorResponseException;
import io.micronaut.security.token.event.RefreshTokenGeneratedEvent;
import io.micronaut.security.token.refresh.RefreshTokenPersistence;
import io.unityfoundation.dds.permissions.manager.model.user.UserRole;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static io.micronaut.security.errors.IssuingAnAccessTokenErrorCode.INVALID_GRANT;

@Singleton
public class RefreshTokenPersistenceImpl implements RefreshTokenPersistence {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenPersistenceImpl.class);

    @Nullable
    @Property(name = "permissions-manager.test.username")
    protected String testUsername;

    @Property(name = "permissions-manager.test.is-admin", defaultValue = "false")
    protected boolean testUserIsAdmin;

    private final RefreshTokenRepository refreshTokenRepository;
    private final PermissionsManagerAuthenticationMapper authenticationMapper;
    private final Environment environment;

    public RefreshTokenPersistenceImpl(RefreshTokenRepository refreshTokenRepository, PermissionsManagerAuthenticationMapper authenticationMapper, Environment environment) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationMapper = authenticationMapper;
        this.environment = environment;
    }

    @Override
    public void persistToken(RefreshTokenGeneratedEvent event) {
        String refreshToken = event.getRefreshToken();
        Authentication authentication = event.getAuthentication();
        if (refreshToken != null && authentication != null &&
                authentication.getName() != null) {
            refreshTokenRepository.save(authentication.getName(), refreshToken, false);
        } else {
            LOG.debug("DEBUG in persistToken");
        }
    }

    @Override
    public Publisher<Authentication> getAuthentication(String refreshToken) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByRefreshToken(refreshToken);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            if (token.getRevoked()) {
                throw new OauthErrorResponseException(INVALID_GRANT, "refresh token revoked", null);
            } else {
                String username = token.getUsername();
                if (username.matches("\\d+")) {
                    // application login
                    return Publishers.just(Authentication.build(username, List.of(UserRole.APPLICATION.toString())));
                } else if ( testUsername != null && username.equals(testUsername)  &&
                        (environment.getActiveNames().contains("dev") || environment.getActiveNames().contains("test")) ) {
                        // test/dev login
                    return Publishers.just(Authentication.build(username));
                }

                // oauth user login
                Publisher<Authentication> authentication =
                        Publishers.map(authenticationMapper.getAuthenticationResponse(username), authenticationResponse -> authenticationResponse.getAuthentication().get());
                return authentication;
            }
        } else {
            throw new OauthErrorResponseException(INVALID_GRANT, "refresh token not found", null);
        }
    }
}
