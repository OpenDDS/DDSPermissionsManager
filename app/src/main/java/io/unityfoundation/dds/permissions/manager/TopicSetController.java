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
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSetService;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.CreateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.TopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.UpdateTopicSetDTO;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;


@Controller("/api/topic-sets")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "topic sets")
public class TopicSetController {
    private final TopicSetService topicSetService;

    public TopicSetController(TopicSetService topicSetService) {
        this.topicSetService = topicSetService;
    }

    @Get("{?filter,group}")
    @ExecuteOn(TaskExecutors.IO)
    public Page<TopicSetDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return topicSetService.findAll(pageable, filter, group);
    }

    @Get("/{topicSetId}")
    @ExecuteOn(TaskExecutors.IO)
    public TopicSetDTO byTopicId(@NotNull Long topicSetId) {
        return topicSetService.findById(topicSetId);
    }

    @Post
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> save(@Body @Valid CreateTopicSetDTO topicSetDTO) {
        return topicSetService.create(topicSetDTO);
    }

    @Put("/{topicSetId}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> save(@NotNull Long topicSetId, @Body @Valid UpdateTopicSetDTO topicSetDTO) {
        return topicSetService.update(topicSetId, topicSetDTO);
    }

    @Delete("/{topicSet}")
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> removeAccess(Long topicSet) {
        return topicSetService.deleteById(topicSet);
    }


    @Post("/{topicSetId}/{topicId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<TopicSetDTO> addTopic(@NotNull Long topicSetId, @NotNull Long topicId) {
        return topicSetService.addTopic(topicSetId, topicId);
    }

    @Delete("/{topicSetId}/{topicId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<TopicSetDTO> removeTopic(@NotNull Long topicSetId, @NotNull Long topicId) {
        return topicSetService.removeTopic(topicSetId, topicId);
    }
}
