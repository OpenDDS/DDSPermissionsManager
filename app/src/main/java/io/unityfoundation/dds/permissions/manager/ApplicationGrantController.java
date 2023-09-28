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
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrantService;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.CreateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.UpdateGrantDTO;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import static io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService.APPLICATION_GRANT_TOKEN;

@Controller("/api/application_grants")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "application grants")
public class ApplicationGrantController {
    private final ApplicationGrantService applicationGrantService;

    public ApplicationGrantController(ApplicationGrantService applicationGrantService) {
        this.applicationGrantService = applicationGrantService;
    }

    @Get("{?filter,group}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<GrantDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return applicationGrantService.findAll(pageable, filter, group);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    public Publisher<HttpResponse<GrantDTO>> create(@NotBlank @Header(APPLICATION_GRANT_TOKEN) String grantToken,
                                                    @Valid @Body CreateGrantDTO createGrantDTO) {
        return applicationGrantService.create(grantToken, createGrantDTO);
    }

    @Put("/{grantId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<GrantDTO> update(Long grantId,
                                               @Valid @Body UpdateGrantDTO grantDTO) {
        return applicationGrantService.update(grantId, grantDTO);
    }

    @Delete("/{grantId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse delete(Long grantId) {
        return applicationGrantService.deleteById(grantId);
    }
}
