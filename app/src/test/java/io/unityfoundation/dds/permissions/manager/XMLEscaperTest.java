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
import io.unityfoundation.dds.permissions.manager.util.XMLEscaper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class XMLEscaperTest {
    @Inject
    XMLEscaper xmlEscaper;

    @Test
    void testEscapedTopicName() {
        String topicName = "<aa> 'Some\" topic&name </aa>";
        String expected = "&lt;aa&gt; &apos;Some&quot; topic&amp;name &lt;/aa&gt;";
        String actual = xmlEscaper.escape(topicName);
        assertTrue(actual.equals(expected));

        String topicName2 = "Nothing to escape";
        assertTrue(xmlEscaper.escape(topicName2).equals(topicName2));
    }
}
