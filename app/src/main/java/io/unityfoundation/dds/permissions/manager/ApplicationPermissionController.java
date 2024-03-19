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

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessPermissionBodyDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessPermissionDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.reactivestreams.Publisher;

import static io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService.APPLICATION_GRANT_TOKEN;

@Controller("/api/application_permissions")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "application permissions")
public class ApplicationPermissionController {
    private final ApplicationPermissionService applicationPermissionService;

    public ApplicationPermissionController(ApplicationPermissionService applicationPermissionService) {
        this.applicationPermissionService = applicationPermissionService;
    }

    @Get("/topic/{topic}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<AccessPermissionDTO> indexIndexByTopicId(Long topic, @Valid Pageable pageable) {
        return applicationPermissionService.indexByTopicId(topic, pageable);
    }

    @Get("/application/{application}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<AccessPermissionDTO> indexByApplicationId(Long application, @Valid Pageable pageable) {
        return applicationPermissionService.indexByApplicationId(application, pageable);
    }

    @Post("/{topicId}")
    @ExecuteOn(TaskExecutors.IO)
    public Publisher<HttpResponse<AccessPermissionDTO>> addAccess(Long topicId,
                                                                  @NotBlank @Header(APPLICATION_GRANT_TOKEN) String grantToken,
                                                                  @Valid @Body AccessPermissionBodyDTO accessPermissionBodyDTO) {
        return applicationPermissionService.addAccess(grantToken, topicId, accessPermissionBodyDTO);
    }

    @Put("/{permissionId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<AccessPermissionDTO> updateAccess(Long permissionId,
                                                          @Valid @Body AccessPermissionBodyDTO accessPermissionBodyDTO) {
        return applicationPermissionService.updateAccess(permissionId, accessPermissionBodyDTO);
    }

    @Delete("/{permissionId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse removeAccess(Long permissionId) {
        return applicationPermissionService.deleteById(permissionId);
    }
}
