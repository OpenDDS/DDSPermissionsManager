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
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessPermissionDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessType;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import static io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService.APPLICATION_BIND_TOKEN;

@Controller("/api/application_permissions")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "application permissions")
public class ApplicationPermissionController {
    private final ApplicationPermissionService applicationPermissionService;

    public ApplicationPermissionController(ApplicationPermissionService applicationPermissionService) {
        this.applicationPermissionService = applicationPermissionService;
    }

    @Get("{?application,topic")
    @ExecuteOn(TaskExecutors.IO)
    public Page<AccessPermissionDTO> index(@Nullable Long application, @Nullable Long topic, @Valid Pageable pageable) {
        return applicationPermissionService.findAll(application, topic, pageable);
    }

    @Get("access_types")
    public AccessType[] getAccessTypes() {
        return AccessType.values();
    }

    @Post("/{topicId}/{access}")
    @ExecuteOn(TaskExecutors.IO)
    public Publisher<HttpResponse<AccessPermissionDTO>> addAccess(Long topicId,
                                                                  AccessType access,
                                                                  @NotBlank @Header(APPLICATION_BIND_TOKEN) String bindToken) {
        return applicationPermissionService.addAccess(bindToken, topicId, access);
    }

    @Put("/{permissionId}/{access}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<AccessPermissionDTO> updateAccess(Long permissionId, AccessType access) {
        return applicationPermissionService.updateAccess(permissionId, access);
    }

    @Delete("/{permissionId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse removeAccess(Long permissionId) {
        return applicationPermissionService.deleteById(permissionId);
    }
}
