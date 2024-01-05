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

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.bouncycastle.mail.smime.handlers.multipart_signed;
import org.bouncycastle.mail.smime.handlers.pkcs7_mime;
import org.bouncycastle.mail.smime.handlers.pkcs7_signature;


@TypeHint(
        value = {
                pkcs7_mime.class,
                pkcs7_signature.class,
                multipart_signed.class
        }
)
@OpenAPIDefinition(
        info = @Info(
                title = "Permissions Manager",
                version = "1.0",
                description = "Permissions Manager API"
        )
)
public class RunApplication {
    public static void main(String[] args) {
        Micronaut.run(RunApplication.class, args);
    }
}
