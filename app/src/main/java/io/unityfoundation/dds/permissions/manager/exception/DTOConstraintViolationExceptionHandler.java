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
package io.unityfoundation.dds.permissions.manager.exception;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.security.PassphraseGenerator;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.*;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Produces
@Singleton
@Primary
@Requires(classes = {ConstraintViolationException.class, ExceptionHandler.class})
public class DTOConstraintViolationExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(DTOConstraintViolationExceptionHandler.class);

    private final PassphraseGenerator passphraseGenerator;

    public DTOConstraintViolationExceptionHandler(PassphraseGenerator passphraseGenerator) {
        this.passphraseGenerator = passphraseGenerator;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
        MutableHttpResponse<?> response = HttpResponse.badRequest();
        response.body(buildBody(constraintViolations));
        exception.printStackTrace();
        return response;
    }

    private List<DPMErrorResponse> buildBody(Set<ConstraintViolation<?>> errorContext) {
        return errorContext.stream().map(this::buildMessageBody).collect(Collectors.toList());
    }

    private DPMErrorResponse buildMessageBody(ConstraintViolation violation) {
        String code = violation.getPropertyPath().toString();
        ConstraintDescriptor descriptor = violation.getConstraintDescriptor();

        if (code.endsWith(".email")) {
            if (descriptor.getAnnotation() instanceof Email) {
                code = ResponseStatusCodes.INVALID_EMAIL_FORMAT;
            } else if (descriptor.getAnnotation() instanceof NotBlank) {
                code = ResponseStatusCodes.EMAIL_CANNOT_BE_BLANK_OR_NULL;
            }
        } else if (code.contains(".group.")) {
            // group
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.GROUP_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.GROUP_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".description")) {
                if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.GROUP_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS;
                }
            }
        } else if (code.contains(".application.") || code.contains("checkApplicationExistence.application")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".group")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.APPLICATION_REQUIRES_GROUP_ASSOCIATION;
                }
            } else if (code.contains("checkApplicationExistence.application")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".description")) {
                if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.APPLICATION_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS;
                }
            }
        } else if (code.contains(".topicSetDTO.")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.TOPIC_SET_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.TOPIC_SET_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".groupId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.TOPIC_SET_REQUIRES_GROUP_ASSOCIATION;
                }
            }
        } else if (code.contains(".topic.")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".group")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.TOPIC_REQUIRES_GROUP_ASSOCIATION;
                }
            } else if (code.endsWith(".description")) {
                if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.TOPIC_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS;
                }
            }
        } else if (code.contains(".actionIntervalDTO.")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.ACTION_INTERVAL_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.ACTION_INTERVAL_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".groupId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.ACTION_INTERVAL_REQUIRES_GROUP_ASSOCIATION;
                }
            }
        } else if (code.contains(".grantDurationDTO.")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.GRANT_DURATION_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.GRANT_DURATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".groupId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.GRANT_DURATION_REQUIRES_GROUP_ASSOCIATION;
                }
            } else if (code.endsWith(".durationInMilliseconds")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.GRANT_DURATION_DURATION_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof PositiveOrZero) {
                    code = ResponseStatusCodes.GRANT_DURATION_DURATION_CANNOT_BE_A_NEGATIVE_VALUE;
                }
            }
        } else if (code.contains(".createGrantDTO.") || code.contains(".updateGrantDTO.")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.APPLICATION_GRANT_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.APPLICATION_GRANT_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".groupId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.APPLICATION_GRANT_REQUIRES_GROUP_ASSOCIATION;
                }
            } else if (code.endsWith(".grantDurationId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.APPLICATION_GRANT_REQUIRES_DURATION_ASSOCIATION;
                }
            }
        } else if (code.contains(".createActionDTO.") || code.contains(".updateActionDTO.")) {
            if (code.endsWith(".applicationGrantId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.ACTION_REQUIRES_APPLICATION_GRANT_ASSOCIATION;
                }
            } else if (code.endsWith(".actionIntervalId")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.ACTION_REQUIRES_INTERVAL_ASSOCIATION;
                }
            }
        } else if (code.endsWith("dto.permissionsGroup")) {
            if (descriptor.getAnnotation() instanceof NotNull) {
                code = ResponseStatusCodes.GROUP_MEMBERSHIP_REQUIRES_GROUP_ASSOCIATION;
            }
        }

        String errorId = passphraseGenerator.generatePassphrase();
        LOG.error("Id: {} Code: {} Violation: {}. ", errorId, code, violation.getMessage());

        return new DPMErrorResponse(errorId, code);
    }

}
