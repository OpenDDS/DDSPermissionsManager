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
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionIntervalService;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.CreateActionIntervalDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


@Controller("/api/action_intervals")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "action intervals")
public class ActionIntervalController {
    private final ActionIntervalService actionIntervalService;

    public ActionIntervalController(ActionIntervalService actionIntervalService) {
        this.actionIntervalService = actionIntervalService;
    }

    @Get("{?filter,group}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<ActionIntervalDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return actionIntervalService.findAll(pageable, filter, group);
    }

    @Get("/{actionIntervalId}")
    @ExecuteOn(TaskExecutors.IO)
    public ActionIntervalDTO getById(@NotNull Long actionIntervalId) {
        return actionIntervalService.findById(actionIntervalId);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> create(@Body @Valid CreateActionIntervalDTO actionIntervalDTO) {
        return actionIntervalService.create(actionIntervalDTO);
    }

    @Put("/{actionIntervalId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> update(@NotNull Long actionIntervalId, @Body @Valid ActionIntervalDTO actionIntervalDTO) {
        return actionIntervalService.update(actionIntervalId, actionIntervalDTO);
    }

    @Delete("/{actionIntervalId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> delete(Long actionIntervalId) {
        return actionIntervalService.deleteById(actionIntervalId);
    }
}