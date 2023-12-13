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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.unityfoundation.dds.permissions.manager.security.AuthConfigService;

@Controller("/auth")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "Auth")
public class AuthController {

    private AuthConfigService authConfigService;

    public AuthController(AuthConfigService authConfigService) {
        this.authConfigService = authConfigService;
    }

    @Get("/public_keys")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getPublicKeys() throws Exception {
        return authConfigService.getPublicKeys();
    }
}
