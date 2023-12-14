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

    // application grants
    String APPLICATION_GRANT_NAME_CANNOT_BE_BLANK_OR_NULL = "application-grant.name.cannot-be-blank-or-null";
    String APPLICATION_GRANT_REQUIRES_GROUP_ASSOCIATION = "application-grant.requires-group-association";
    String APPLICATION_GRANT_REQUIRES_DURATION_ASSOCIATION = "application-grant.requires-grant-duration-association";
    String APPLICATION_GRANT_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "application-grant.name.cannot-be-less-than-three-characters";
    String APPLICATION_GRANT_NOT_FOUND = "application-grant.not-found";
    String APPLICATION_GRANT_GROUP_NOT_FOUND = "application-grant.group.not-found";
    String APPLICATION_GRANT_GRANT_DURATION_NOT_FOUND = "application-grant.grant-duration.not-found";
    String APPLICATION_GRANT_GRANT_DURATION_DOES_NOT_BELONG_TO_SAME_GROUP = "application-grant.grant-duration.not.same-group";
    String APPLICATION_GRANT_ALREADY_EXISTS = "application-grant.exists";

    // topic set
    String TOPIC_SET_NOT_FOUND = "topic-set.not-found";
    String TOPIC_SET_NAME_CANNOT_BE_BLANK_OR_NULL = "topic-set.name.cannot-be-blank-or-null";
    String TOPIC_SET_AND_TOPIC_DOES_NOT_BELONG_TO_SAME_GROUP = "topic-set.topic.not.same-group";
    String TOPIC_DOES_NOT_EXISTS_IN_TOPIC_SET = "topic-set.topic.not.exists";
    String TOPIC_SET_REQUIRES_GROUP_ASSOCIATION = "topic-set.requires-group-association";
    String TOPIC_SET_ALREADY_EXISTS = "topic-set.exists";
    String TOPIC_SET_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "topic-set.name.cannot-be-less-than-three-characters";

    // action interval
    String ACTION_INTERVAL_NOT_FOUND = "action-interval.not-found";
    String ACTION_INTERVAL_REQUIRES_GROUP_ASSOCIATION = "action-interval.requires-group-association";
    String ACTION_INTERVAL_ALREADY_EXISTS = "action-interval.exists";
    String ACTION_INTERVAL_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "action-interval.name.cannot-be-less-than-three-characters";
    String ACTION_INTERVAL_NAME_CANNOT_BE_BLANK_OR_NULL = "action-interval.cannot-be-blank-or-null";
    String ACTION_INTERVAL_CANNOT_UPDATE_GROUP_ASSOCIATION = "action-interval.cannot-update-group-association";

    // grant duration
    String GRANT_DURATION_NOT_FOUND = "grant-duration.not-found";
    String GRANT_DURATION_REQUIRES_GROUP_ASSOCIATION = "grant-duration.requires-group-association";
    String GRANT_DURATION_ALREADY_EXISTS = "grant-duration.exists";
    String GRANT_DURATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS = "grant-duration.name.cannot-be-less-than-three-characters";
    String GRANT_DURATION_NAME_CANNOT_BE_BLANK_OR_NULL = "grant-duration.name.cannot-be-less-than-three-characters";
    String GRANT_DURATION_DURATION_CANNOT_BE_BLANK_OR_NULL = "grant-duration.durationInMilliseconds.cannot-be-blank-or-null";
    String GRANT_DURATION_DURATION_CANNOT_BE_A_NEGATIVE_VALUE = "grant-duration.durationInMilliseconds.cannot-be-a-negative-value";
    String GRANT_DURATION_CANNOT_UPDATE_GROUP_ASSOCIATION = "grant-duration.cannot-update-group-association";
    String GRANT_DURATION_HAS_ONE_OR_MORE_GRANT_ASSOCIATIONS = "grant-duration.has-one-or-more-grant-associations";


    // security
    String JWT_REFRESH_TOKEN_PARSE_EXCEPTION = "security.jwt-refresh-token-parse-exception";
    String INVALID_TOKEN = "security.invalid-token";

    String ACTION_NOT_FOUND = "action.not-found";
    String ACTION_REQUIRES_APPLICATION_GRANT_ASSOCIATION = "action.requires-application-grant-association";
    String ACTION_REQUIRES_INTERVAL_ASSOCIATION = "action.requires-action-interval-association";
    String ACTION_INTERVAL_WAS_NOT_FOUND_OR_DOES_BELONG_TO_THE_SAME_GROUP_AS_APPLICATION_GRANT = "action.action-interval.does-not-exist-or-not-in-same-group-as-application-grant";
    String A_PROVIDED_TOPIC_SET_WAS_NOT_FOUND_OR_DOES_NOT_BELONG_TO_SAME_GROUP = "action.topic-set.does-not-exist-or-not-in-same-group-as-application-grant";
    String A_PROVIDED_TOPIC_WAS_NOT_FOUND_OR_DOES_NOT_BELONG_TO_SAME_GROUP = "action.topic.does-not-exist-or-not-in-same-group-as-application-grant";
}
