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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.unityfoundation.dds.permissions.manager.exception.DPMErrorResponse;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserResponseDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import jakarta.validation.Valid;

import java.util.Map;

@Controller("/api/group_membership")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "group membership")
public class GroupMembershipController {

    private final GroupUserService groupUserService;

    public GroupMembershipController(GroupUserService groupUserService) {
        this.groupUserService = groupUserService;
    }

    @Get("{?filter,group}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<GroupUserResponseDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return groupUserService.findAll(pageable, filter, group);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    @ApiResponse(
            responseCode = "200",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GroupUser.class))
    )
    @ApiResponse(responseCode = "4xx", description = "Bad Request.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    HttpResponse addMember(@Valid @Body GroupUserDTO dto) {
        return groupUserService.addMember(dto);
    }

    @Put
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse updateMember(@Valid @Body GroupUserDTO groupUser) {
        return groupUserService.updateMember(groupUser);
    }

    @Delete
    @ExecuteOn(TaskExecutors.IO)
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "4xx", description = "Bad Request.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    HttpResponse removeMember(@Body Map payload) {
        Long id = Long.valueOf((Integer) payload.get("id"));
        return groupUserService.removeMember(id);
    }

    @Get("/user_exists/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "4xx", description = "Bad Request.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    HttpResponse checkUserExists(Long id) {
        return groupUserService.checkUserExists(id);
    }
}
