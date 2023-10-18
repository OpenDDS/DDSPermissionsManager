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
import io.unityfoundation.dds.permissions.manager.model.action.ActionService;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.CreateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.UpdateActionDTO;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;


@Controller("/api/actions")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "actions")
public class ActionController {
    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @Get("{?filter,grantId}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<ActionDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long grantId) {
        return actionService.findAll(pageable, filter, grantId);
    }

    @Get("/{actionId}")
    @ExecuteOn(TaskExecutors.IO)
    public ActionDTO getById(@NotNull Long actionId) {
        return actionService.findById(actionId);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> create(@Body @Valid CreateActionDTO createActionDTO) {
        return actionService.create(createActionDTO);
    }

    @Put("/{actionId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> update(@NotNull Long actionId, @Body @Valid UpdateActionDTO updateActionDTO) {
        return actionService.update(actionId, updateActionDTO);
    }

    @Delete("/{actionId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> delete(Long actionId) {
        return actionService.deleteById(actionId);
    }
}