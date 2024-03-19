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
package io.unityfoundation.dds.permissions.manager.search;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;


@Serdeable
public class UniversalSearchParams {

    private HttpRequest<?> httpRequest;

    @Nullable
    @QueryValue
    private String query;

    @Nullable
    @QueryValue
    private Boolean groups;

    @Nullable
    @QueryValue
    private Boolean topics;

    @Nullable
    @QueryValue
    private Boolean applications;

    @Valid
    private Pageable pageable;

    public UniversalSearchParams(HttpRequest<?> httpRequest, @Nullable String query, @Nullable Boolean groups, @Nullable Boolean topics, @Nullable Boolean applications, @Nullable Pageable pageable) {
        this.httpRequest = httpRequest;
        this.query = query;
        this.groups = groups;
        this.topics = topics;
        this.applications = applications;
        this.pageable = pageable;
    }

    public HttpRequest<?> getHttpRequest() {
        return httpRequest;
    }

    public void setHttpRequest(HttpRequest<?> httpRequest) {
        this.httpRequest = httpRequest;
    }

    @Nullable
    public String getQuery() {
        return query;
    }

    public void setQuery(@Nullable String query) {
        this.query = query;
    }

    @Nullable
    public Boolean getGroups() {
        return groups;
    }

    public void setGroups(@Nullable Boolean groups) {
        this.groups = groups;
    }

    @Nullable
    public Boolean getTopics() {
        return topics;
    }

    public void setTopics(@Nullable Boolean topics) {
        this.topics = topics;
    }

    @Nullable
    public Boolean getApplications() {
        return applications;
    }

    public void setApplications(@Nullable Boolean applications) {
        this.applications = applications;
    }

    public Pageable getPageable() {
        return pageable;
    }

    public void setPageable(@Nullable Pageable pageable) {
        this.pageable = pageable;
    }
}
