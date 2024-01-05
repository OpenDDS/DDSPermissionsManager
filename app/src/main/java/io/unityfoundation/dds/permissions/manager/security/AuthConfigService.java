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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpResponse;
import io.unityfoundation.dds.permissions.manager.Bootstrap;
import jakarta.inject.Singleton;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;

@Singleton
public class AuthConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthConfigService.class);

    @Property(name = "permissions-manager.application.jwt.signature.public")
    protected String publicKey;

    @Property(name = "permissions-manager.application.jwt.signature.private")
    protected String privateKey;

    public AuthConfigService() {
    }

    public HttpResponse<?> getPublicKeys() throws Exception {
        String publicKey = objectToPEMString(readX509PublicKey());
        return HttpResponse.ok(List.of(Map.of("public", publicKey)));
    }

    public RSAPublicKey readX509PublicKey() throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");

        File file;
        if (publicKey != null ) {
            file = new File(publicKey);
            if (!file.exists()) {
                LOG.warn("Could not find public key with given path. Using fallback resource key.");
                file = getFileFromResource(publicKey);
            }
        } else {
            LOG.warn("Public Key is null. Using fallback resource key.");
            file = getFileFromResource(publicKey);
        }

        try (FileReader keyReader = new FileReader(file);
             PemReader pemReader = new PemReader(keyReader)) {

            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
            return (RSAPublicKey) factory.generatePublic(pubKeySpec);
        }
    }

    public RSAPrivateKey readPKCS8PrivateKey() throws IOException, URISyntaxException {
        File file;
        if (privateKey != null ) {
            file = new File(privateKey);
            if (!file.exists()) {
                LOG.warn("Could not find private key with given path. Using fallback resource key.");
                file = getFileFromResource(privateKey);
            }
        } else {
            LOG.warn("Private Key is null. Using fallback resource key.");
            file = getFileFromResource(privateKey);
        }

        try (FileReader keyReader = new FileReader(file)) {

            PEMParser pemParser = new PEMParser(keyReader);
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());

            return (RSAPrivateKey) converter.getPrivateKey(privateKeyInfo);
        }
    }

    public File getFileFromResource(String fileName) throws URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("File not found! " + fileName);
        } else {
            return new File(resource.toURI());
        }
    }

    public String objectToPEMString(PublicKey certificate) throws IOException {
        StringWriter sWrt = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(sWrt);
        pemWriter.writeObject(certificate);
        pemWriter.close();
        return sWrt.toString();
    }
}
