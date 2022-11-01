package io.unityfoundation.dds.permissions.manager.security;

import io.micronaut.context.env.Environment;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.micronaut.security.errors.IssuingAnAccessTokenErrorCode.INVALID_CLIENT;
import static io.micronaut.security.errors.IssuingAnAccessTokenErrorCode.INVALID_GRANT;

@Singleton
public class RefreshTokenPersistenceImpl implements RefreshTokenPersistence {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenPersistenceImpl.class);

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
                } else if ((username.equals("unity") || username.equals("unity-admin"))) {
                    if (environment.getActiveNames().contains("dev") || environment.getActiveNames().contains("test")) {
                        // test/dev login
                        return Publishers.just(Authentication.build(username,
                                (username.equals("unity-admin") ? List.of(UserRole.ADMIN.toString()) : Collections.emptyList())
                        ));
                    }
                }

                // oauth user login
                Optional<Authentication> authentication =
                        authenticationMapper.getAuthenticationResponse(username).getAuthentication();
                if (authentication.isEmpty()) {
                    throw new OauthErrorResponseException(INVALID_CLIENT);
                }
                return Publishers.just(authentication.get());
            }
        } else {
            throw new OauthErrorResponseException(INVALID_GRANT, "refresh token not found", null);
        }
    }
}
