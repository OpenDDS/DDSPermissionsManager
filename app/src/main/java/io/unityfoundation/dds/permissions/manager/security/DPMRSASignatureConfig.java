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
package io.unityfoundation.dds.permissions.manager.security;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Named("generator")
@Singleton
public class DPMRSASignatureConfig implements RSASignatureGeneratorConfiguration {

    private AuthConfigService authConfigService;

    public DPMRSASignatureConfig(AuthConfigService authConfigService) {
        this.authConfigService = authConfigService;
    }

    @Override
    public RSAPrivateKey getPrivateKey() {

        RSAPrivateKey rsaPrivateKey = null;
        try {
            rsaPrivateKey = authConfigService.readPKCS8PrivateKey();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return rsaPrivateKey;
    }

    @Override
    public JWSAlgorithm getJwsAlgorithm() {
        return JWSAlgorithm.RS256;
    }

    @Override
    public RSAPublicKey getPublicKey() {

        RSAPublicKey rsaPublicKey = null;
        try {
            rsaPublicKey = authConfigService.readX509PublicKey();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return rsaPublicKey;
    }
}
