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

public interface ResponseStatusCodes {
    // generic
    String UNAUTHORIZED = "unauthorized";

    // email
    String INVALID_EMAIL_FORMAT = "email.is-not-format";
    String EMAIL_CANNOT_BE_BLANK_OR_NULL = "email.cannot-be-blank-or-null";

    // user
    String USER_ALREADY_EXISTS = "user.exists";
    String USER_NOT_FOUND = "user.not-found";
    String USER_IS_NOT_VALID = "user.is-not-valid";

    // nonce
    String INVALID_NONCE_FORMAT = "nonce.is-not-valid";

    // group
    String GROUP_NOT_FOUND = "group.not-found";
    String GROUP_ALREADY_EXISTS = "group.exists";
    String GROUP_NAME_CANNOT_BE_BLANK_OR_NULL = "group.name.cannot-be-blank-or-null";
    String GROUP_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "group.name.cannot-be-less-than-three-characters";
    String GROUP_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS = "group.name.cannot-be-more-than-four-thousand-characters";

    // application
    String APPLICATION_NOT_FOUND = "application.not-found";
    String APPLICATION_REQUIRES_GROUP_ASSOCIATION = "application.requires-group-association";
    String APPLICATION_CANNOT_UPDATE_GROUP_ASSOCIATION = "application.cannot-update-group-association";
    String APPLICATION_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP = "application.cannot-create-nor-update-under-private-group";
    String APPLICATION_ALREADY_EXISTS = "application.exists";
    String APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL = "application.name.cannot-be-blank-or-null";
    String APPLICATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "application.name.cannot-be-less-than-three-characters";
    String APPLICATION_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS = "application.name.cannot-be-more-than-four-thousand-characters";
    String APPLICATION_GRANT_TOKEN_PARSE_EXCEPTION = "application.grant-token.parse-exception";
    String IDENTITY_CERT_NOT_FOUND = "application.identity-ca-cert.not-found";
    String PERMISSIONS_CERT_NOT_FOUND = "application.permissions-ca-cert.not-found";
    String GOVERNANCE_FILE_NOT_FOUND = "application.governance-file.not-found";

    // topic
    String TOPIC_NOT_FOUND = "topic.not-found";
    String TOPIC_REQUIRES_GROUP_ASSOCIATION = "topic.requires-group-association";
    String TOPIC_CANNOT_UPDATE_GROUP_ASSOCIATION = "topic.cannot-update-group-association";
    String TOPIC_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP = "topic.cannot-create-nor-update-under-private-group";
    String TOPIC_NAME_UPDATE_NOT_ALLOWED = "topic.name.update-not-allowed";
    String TOPIC_NAME_CANNOT_BE_BLANK_OR_NULL = "topic.name.cannot-be-blank-or-null";
    String TOPIC_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "topic.name.cannot-be-less-than-three-characters";
    String TOPIC_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS = "topic.name.cannot-be-more-than-four-thousand-characters";
    String TOPIC_KIND_UPDATE_NOT_ALLOWED = "topic.kind.update-not-allowed";
    String TOPIC_ALREADY_EXISTS = "topic.exists";

    // group member
    String GROUP_MEMBERSHIP_NOT_FOUND = "user.group-membership.not-found";
    String GROUP_MEMBERSHIP_ALREADY_EXISTS = "user.group-membership.exists";
    String GROUP_MEMBERSHIP_CANNOT_CREATE_WITH_UPDATE = "user.group-membership.cannot_create_with_update";
    String GROUP_MEMBERSHIP_REQUIRES_GROUP_ASSOCIATION = "user.group-membership.requires-group-association";

    // application permission
    String APPLICATION_PERMISSION_NOT_FOUND = "application.permission.not-found";
    String APPLICATION_PERMISSION_ALREADY_EXISTS = "application.permission.exists";

    // expiration policy
    String EXPIRATION_POLICY_NOT_FOUND = "expiration-policy.not-found";
    String EXPIRATION_POLICY_REQUIRES_GROUP_ASSOCIATION = "expiration-policy.requires-group-association";
    String EXPIRATION_POLICY_ALREADY_EXISTS = "expiration-policy.exists";
    String EXPIRATION_POLICY_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "expiration-policy.name.cannot-be-less-than-three-characters";
    String EXPIRATION_POLICY_NAME_CANNOT_BE_BLANK_OR_NULL = "expiration-policy.name.cannot-be-less-than-three-characters";
    String EXPIRATION_POLICY_CANNOT_UPDATE_GROUP_ASSOCIATION = "expiration-policy.cannot-update-group-association";

    // security
    String JWT_REFRESH_TOKEN_PARSE_EXCEPTION = "security.jwt-refresh-token-parse-exception";
    String INVALID_TOKEN = "security.invalid-token";
}
