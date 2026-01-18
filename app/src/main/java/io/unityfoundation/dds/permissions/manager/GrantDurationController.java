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
import io.unityfoundation.dds.permissions.manager.model.grantduration.GrantDurationService;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.CreateGrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


@Controller("/api/grant_durations")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "durations")
public class GrantDurationController {
    private final GrantDurationService grantDurationService;

    public GrantDurationController(GrantDurationService grantDurationService) {
        this.grantDurationService = grantDurationService;
    }

    @Get("{?filter,group}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<GrantDurationDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return grantDurationService.findAll(pageable, filter, group);
    }

    @Get("/{durationId}")
    @ExecuteOn(TaskExecutors.IO)
    public GrantDurationDTO getById(@NotNull Long durationId) {
        return grantDurationService.findById(durationId);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> create(@Body @Valid CreateGrantDurationDTO grantDurationDTO) {
        return grantDurationService.create(grantDurationDTO);
    }

    @Put("/{durationId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> update(@NotNull Long durationId, @Body @Valid GrantDurationDTO grantDurationDTO) {
        return grantDurationService.update(durationId, grantDurationDTO);
    }

    @Delete("/{durationId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> delete(Long durationId) {
        return grantDurationService.deleteById(durationId);
    }
}