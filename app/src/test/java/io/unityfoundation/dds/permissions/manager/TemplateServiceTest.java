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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationService;
import io.unityfoundation.dds.permissions.manager.model.application.TemplateService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class TemplateServiceTest {
    @Inject
    TemplateService templateService;

    @Test
    void testPopulatingTemplate() {
        String expect = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<dds xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://www.omg.org/spec/DDS-SECURITY/20160303/omg_shared_ca_permissions.xsd\">\n" +
                "    <permissions>\n" +
                "        <grant name=\"application_20\">\n" +
                "            <subject_name>CN=Alice,C=US</subject_name>\n" +
                "            <validity>\n" +
                "                <not_before>today</not_before>\n" +
                "                <not_after>tomorrow</not_after>\n" +
                "            </validity>\n" +
                "            <allow_rule>\n" +
                "                <domains>\n" +
                "                    <id>123</id>\n" +
                "                </domains>\n" +
                "                <publish>\n" +
                "                    <topics>\n" +
                "                        <topic>topicA</topic>\n" +
                "                        <topic>topicB</topic>\n" +
                "                    </topics>\n" +
                "                    <partitions>\n" +
                "                        <partition>partition1</partition>\n" +
                "                        <partition>partition2</partition>\n" +
                "                    </partitions>\n" +
                "                    <validity>\n" +
                "                        <not_before>startA</not_before>\n" +
                "                        <not_after>endA</not_after>\n" +
                "                    </validity>\n" +
                "                </publish>\n" +
                "                <publish>\n" +
                "                    <topics>\n" +
                "                        <topic>topicC</topic>\n" +
                "                    </topics>\n" +
                "                    <validity>\n" +
                "                        <not_before>startC</not_before>\n" +
                "                        <not_after>endC</not_after>\n" +
                "                    </validity>\n" +
                "                </publish>\n" +
                "            </allow_rule>\n" +
                "            <default>DENY</default>\n" +
                "        </grant>\n" +
                "    </permissions>\n" +
                "</dds>";

        HashMap<String, Object> dataModel = new HashMap<>();
        dataModel.put("subject", "CN=Alice,C=US");
        dataModel.put("applicationId", 20);
        dataModel.put("validStart", "today");
        dataModel.put("validEnd", "tomorrow");
        dataModel.put("domain", 123);

        List<ApplicationService.PubSubEntry> publishList = new ArrayList<>();

        // A publish section with partitions
        List<String> pubTopics1 = List.of("topicA", "topicB");
        List<String> pubPartitions1 = List.of("partition1", "partition2");

        publishList.add(new ApplicationService.PubSubEntry(pubTopics1, pubPartitions1, "startA", "endA"));

        // Another publish section with no partitions
        List<String> pubTopics2 = List.of("topicC");
        publishList.add(new ApplicationService.PubSubEntry(pubTopics2, new ArrayList<>(), "startC", "endC"));

        dataModel.put("publishes", publishList);
        dataModel.put("subscribes", new ArrayList<>());

        try {
            String content = templateService.mergeDataAndTemplate(dataModel);
            content = content.replaceAll("\\s", "");
            expect = expect.replaceAll("\\s", "");
            assertTrue(expect.equals(content));
        } catch (IOException exception) {
            System.out.println("Exception: " + exception.toString());
        }

        String expect2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<dds xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://www.omg.org/spec/DDS-SECURITY/20160303/omg_shared_ca_permissions.xsd\">\n" +
                "    <permissions>\n" +
                "        <grant name=\"application_20\">\n" +
                "            <subject_name>CN=Alice,C=US</subject_name>\n" +
                "            <validity>\n" +
                "                <not_before>today</not_before>\n" +
                "                <not_after>tomorrow</not_after>\n" +
                "            </validity>\n" +
                "            <allow_rule>\n" +
                "                <domains>\n" +
                "                    <id>123</id>\n" +
                "                </domains>\n" +
                "                <publish>\n" +
                "                    <topics>\n" +
                "                        <topic>topicA</topic>\n" +
                "                        <topic>topicB</topic>\n" +
                "                    </topics>\n" +
                "                    <partitions>\n" +
                "                        <partition>partition1</partition>\n" +
                "                        <partition>partition2</partition>\n" +
                "                    </partitions>\n" +
                "                    <validity>\n" +
                "                        <not_before>startA</not_before>\n" +
                "                        <not_after>endA</not_after>\n" +
                "                    </validity>\n" +
                "                </publish>\n" +
                "                <publish>\n" +
                "                    <topics>\n" +
                "                        <topic>topicC</topic>\n" +
                "                    </topics>\n" +
                "                    <validity>\n" +
                "                        <not_before>startC</not_before>\n" +
                "                        <not_after>endC</not_after>\n" +
                "                    </validity>\n" +
                "                </publish>\n" +
                "                <subscribe>\n" +
                "                    <topics>\n" +
                "                        <topic>topicB</topic>\n" +
                "                        <topic>topicC</topic>\n" +
                "                    </topics>\n" +
                "                    <partitions>\n" +
                "                        <partition>partition3</partition>\n" +
                "                        <partition>partition4</partition>\n" +
                "                    </partitions>\n" +
                "                    <validity>\n" +
                "                        <not_before>startD</not_before>\n" +
                "                        <not_after>endD</not_after>\n" +
                "                    </validity>\n" +
                "                </subscribe>\n" +
                "            </allow_rule>\n" +
                "            <default>DENY</default>\n" +
                "        </grant>\n" +
                "    </permissions>\n" +
                "</dds>";

        List<String> subTopics = List.of("topicB", "topicC");
        List<String> subPartitions = List.of("partition3", "partition4");
        List<ApplicationService.PubSubEntry> subscribeList = new ArrayList<>();
        subscribeList.add(new ApplicationService.PubSubEntry(subTopics, subPartitions, "startD", "endD"));
        dataModel.put("subscribes", subscribeList);

        try {
            String content2 = templateService.mergeDataAndTemplate(dataModel);
            content2 = content2.replaceAll("\\s", "");
            expect2 = expect2.replaceAll("\\s", "");
            assertTrue(expect2.equals(content2));
        } catch (IOException exception) {
            System.out.println("Exception: " + exception.toString());
        }
    }
}
