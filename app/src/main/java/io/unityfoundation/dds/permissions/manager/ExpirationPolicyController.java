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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto.CreateExpirationPolicyDTO;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto.ExpirationPolicyDTO;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.ExpirationPolicyService;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;


@Controller("/api/expiration_policies")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "expiration policies")
public class ExpirationPolicyController {
    private final ExpirationPolicyService expirationPolicyService;

    public ExpirationPolicyController(ExpirationPolicyService expirationPolicyService) {
        this.expirationPolicyService = expirationPolicyService;
    }

    @Get("{?filter,group}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<ExpirationPolicyDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return expirationPolicyService.findAll(pageable, filter, group);
    }

    @Get("/{expirationPolicyId}")
    @ExecuteOn(TaskExecutors.IO)
    public ExpirationPolicyDTO getById(@NotNull Long expirationPolicyId) {
        return expirationPolicyService.findById(expirationPolicyId);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> create(@Body @Valid CreateExpirationPolicyDTO expirationPolicyDTO) {
        return expirationPolicyService.create(expirationPolicyDTO);
    }

    @Put("/{expirationPolicyId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> update(@NotNull Long expirationPolicyId, @Body @Valid ExpirationPolicyDTO expirationPolicyDTO) {
        return expirationPolicyService.update(expirationPolicyId, expirationPolicyDTO);
    }

    @Delete("/{expirationPolicyId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> delete(Long expirationPolicyId) {
        return expirationPolicyService.deleteById(expirationPolicyId);
    }
}