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
package io.unityfoundation.dds.permissions.manager.model.application;

import io.micronaut.context.annotation.Property;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

import java.util.function.Predicate;

@ServerWebSocket("/api/applications/{applicationId}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class OnUpdateApplicationWebSocket {

    public static final String APPLICATION_UPDATED = "application_updated";
    public static final String APPLICATION_DELETED = "application_deleted";

    @Property(name = "permissions-manager.websockets.broadcast-changes")
    protected boolean broadcastChanges;

    private final WebSocketBroadcaster broadcaster;

    public OnUpdateApplicationWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    public void broadcastResourceEvent(String event, Long applicationId) {
        if (broadcastChanges) {
            broadcaster.broadcastSync(event, session -> applicationId.equals(session.getUriVariables().get(
                    "applicationId", Long.class, null)));
        }
    }

    @OnMessage
    public void onMessage(Long applicationId, String message, WebSocketSession session) {
//        broadcaster.broadcastSync(message, isValid(applicationId, session));
    }

    private Predicate<WebSocketSession> isValid(Long applicationId, WebSocketSession session) {
        return s -> s != session &&
                applicationId.equals(s.getUriVariables().get("applicationId", Long.class, null));
    }
}
