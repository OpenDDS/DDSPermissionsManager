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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.token.response.DefaultOpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserService;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.*;

@Replaces(DefaultOpenIdAuthenticationMapper.class)
@Singleton
@Named("google")
public class PermissionsManagerAuthenticationMapper implements OpenIdAuthenticationMapper {

    private final UserService userService;
    private final GroupUserService groupUserService;

    public PermissionsManagerAuthenticationMapper(UserService userService, GroupUserService groupUserService) {
        this.userService = userService;
        this.groupUserService = groupUserService;
    }

    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(String providerName,
                                                                                   OpenIdTokenResponse tokenResponse,
                                                                                   OpenIdClaims openIdClaims,
                                                                                   State state) {
        return getAuthenticationResponse(openIdClaims.getEmail());
    }

    public Publisher<AuthenticationResponse> getAuthenticationResponse(String userEmail) {
        return Publishers.just(
                Optional.ofNullable(userEmail)
                .flatMap(userService::getUserByEmail)
                .map(user -> isNonAdminAndNotAMemberOfAnyGroups(user) ?
                        AuthenticationResponse.failure(AuthenticationFailureReason.USER_DISABLED) :
                        AuthenticationResponse.success(
                                userEmail,
                                Collections.emptyList(),
                                userAttributes(userEmail, user)
                        ))
                .orElseGet(() -> AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND))
        );
    }

    private boolean isNonAdminAndNotAMemberOfAnyGroups(User user) {
        return !user.isAdmin() && groupUserService.countMembershipsByUserId(user.getId()) == 0;
    }

    private HashMap<String, Object> userAttributes(String userEmail, User user) {
        HashMap<String, Object> attributes = new HashMap<>();
        List<Map<String, Object>> permissions = groupUserService.getAllPermissionsPerGroupUserIsMemberOf(user.getId());
        attributes.put("name", userEmail);
        attributes.put("permissionsByGroup", permissions);
        attributes.put("permissionsLastUpdated", user.getPermissionsLastUpdated());
        return attributes;
    }
}