package io.unityfoundation.dds.permissions.manager;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;

import java.util.Map;
import java.util.Optional;

@Controller("/group_membership")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "group membership")
public class GroupMembershipController {

    private final GroupUserService groupUserService;

    public GroupMembershipController(GroupUserService groupUserService) {
        this.groupUserService = groupUserService;
    }

    @Post
    @ApiResponse(
            responseCode = "200",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GroupUser.class))
    )
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    HttpResponse addMember(@Body GroupUserDTO dto) {

        if (groupUserService.isAdminOrGroupAdmin(dto.getPermissionsGroup())) {
            return groupUserService.addMember(dto);
        } else {
            return HttpResponse.unauthorized();
        }
    }

    @Delete
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    HttpResponse removeMember(@Body Map payload) {
        Long id = Long.valueOf((Integer) payload.get("id"));
        Optional<GroupUser> groupUser = groupUserService.findById(id);

        if (groupUser.isPresent()) {
            Long groupId = groupUser.get().getPermissionsGroup();
            if (groupUserService.isAdminOrGroupAdmin(groupId)) {
                if (groupUserService.removeMember(id)) {
                    return HttpResponse.ok();
                }
            } else {
                return HttpResponse.unauthorized();
            }
        }

        return HttpResponse.notFound();
    }
}
