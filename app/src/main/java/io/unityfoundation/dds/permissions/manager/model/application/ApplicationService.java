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

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator;
import io.micronaut.security.token.jwt.validator.AuthenticationJWTClaimsSetAdapter;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermission;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ReadPartition;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.WritePartition;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRole;
import io.unityfoundation.dds.permissions.manager.security.ApplicationSecretsClient;
import io.unityfoundation.dds.permissions.manager.security.BCryptPasswordEncoderService;
import io.unityfoundation.dds.permissions.manager.security.PassphraseGenerator;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import io.unityfoundation.dds.permissions.manager.util.XMLEscaper;
import jakarta.inject.Singleton;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.Store;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeMessage;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URI;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.unityfoundation.dds.permissions.manager.security.ApplicationSecretsClient.*;

@Singleton
public class ApplicationService {

    public static final String E_TAG_HEADER_NAME = "ETag";

    @Property(name = "permissions-manager.application.client-certificate.time-expiry", defaultValue = "365")
    protected Long certExpiry;
    @Property(name = "permissions-manager.application.permissions-file.time-expiry", defaultValue = "30")
    protected Long permissionExpiry;
    @Property(name = "permissions-manager.application.permissions-file.domain", defaultValue = "1")
    protected Long permissionDomain;
    @Property(name = "permissions-manager.application.grant-token.time-expiry", defaultValue = "48")
    protected Integer appGrantTokenExpiry;
    private final ApplicationRepository applicationRepository;
    private final GroupRepository groupRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;
    private final ApplicationPermissionService applicationPermissionService;
    private final PassphraseGenerator passphraseGenerator;
    private final BCryptPasswordEncoderService passwordEncoderService;
    private final ApplicationSecretsClient applicationSecretsClient;
    private final TemplateService templateService;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final JWTClaimsSetGenerator jwtClaimsSetGenerator;
    private final XMLEscaper xmlEscaper;
    private final OnUpdateApplicationWebSocket onUpdateApplicationWebSocket;


    public ApplicationService(ApplicationRepository applicationRepository, GroupRepository groupRepository,
                              ApplicationPermissionService applicationPermissionService,
                              SecurityUtil securityUtil, GroupUserService groupUserService,
                              PassphraseGenerator passphraseGenerator,
                              BCryptPasswordEncoderService passwordEncoderService, ApplicationSecretsClient applicationSecretsClient,
                              TemplateService templateService, JwtTokenGenerator jwtTokenGenerator,
                              JWTClaimsSetGenerator jwtClaimsSetGenerator, XMLEscaper xmlEscaper, OnUpdateApplicationWebSocket onUpdateApplicationWebSocket) {
        this.applicationRepository = applicationRepository;
        this.groupRepository = groupRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
        this.applicationPermissionService = applicationPermissionService;
        this.passphraseGenerator = passphraseGenerator;
        this.passwordEncoderService = passwordEncoderService;
        this.applicationSecretsClient = applicationSecretsClient;
        this.templateService = templateService;
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.jwtClaimsSetGenerator = jwtClaimsSetGenerator;
        this.xmlEscaper = xmlEscaper;
        this.onUpdateApplicationWebSocket = onUpdateApplicationWebSocket;
    }

    public Page<ApplicationDTO> findAll(Pageable pageable, String filter, Long applicationId, Long groupId) {
        if(!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        if (applicationId != null) {
            return getApplicationPage(pageable, applicationId, groupId).map(ApplicationDTO::new);
        }

        return getApplicationPage(pageable, filter, groupId).map(ApplicationDTO::new);
    }

    private Page<Application> getApplicationPage(Pageable pageable, String filter, Long groupId) {
        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return applicationRepository.findAll(pageable);
                }
                return applicationRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return applicationRepository.findByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, filter, pageable);
            }

            all = applicationRepository.findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, filter);

            return applicationRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groups = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());

            if (groups.isEmpty() || (groupId != null && !groups.contains(groupId))) {
                return Page.empty();
            }

            if (groupId != null) {
                // implies groupId exists in member's groups
                groups = List.of(groupId);
            }

            if (filter == null) {
                return applicationRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = applicationRepository.findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return applicationRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    private Page<Application> getApplicationPage(Pageable pageable, Long applicationId, Long groupId) {

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (groupId == null) {
                return applicationRepository.findById(applicationId, pageable);
            }

            return applicationRepository.findByIdAndPermissionsGroupId(applicationId, groupId, pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groups = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());

            if (groups.isEmpty() || (groupId != null && !groups.contains(groupId))) {
                return Page.empty();
            }

            if (groupId != null) {
                // implies groupId exists in member's groups
                groups = List.of(groupId);
            }

            return applicationRepository.findByIdAndPermissionsGroupIdIn(applicationId, groups, pageable);
        }
    }

    public MutableHttpResponse<?> save(ApplicationDTO applicationDTO) {
        Optional<Group> groupOptional = groupRepository.findById(applicationDTO.getGroup());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        if (!securityUtil.isCurrentUserAdmin() && !isUserApplicationAdminOfGroup(groupOptional.get())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        // check if application with same name exists in group
        Optional<Application> searchApplicationByNameAndGroup = applicationRepository.findByNameAndPermissionsGroup(
                applicationDTO.getName().trim(), groupOptional.get());

        boolean isPublic = Boolean.TRUE.equals(applicationDTO.getPublic());

        Application application;
        if (applicationDTO.getId() != null) {

            Optional<Application> applicationOptional = applicationRepository.findById(applicationDTO.getId());
            if (applicationOptional.isEmpty()) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
            } else if (!Objects.equals(applicationOptional.get().getPermissionsGroup().getId(), applicationDTO.getGroup())) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_CANNOT_UPDATE_GROUP_ASSOCIATION);
            } else if (searchApplicationByNameAndGroup.isPresent() &&
                    searchApplicationByNameAndGroup.get().getId() != applicationOptional.get().getId()) {
                // attempt to update an existing application with same name/group combo as another
                throw new DPMException(ResponseStatusCodes.APPLICATION_ALREADY_EXISTS);
            } else if (!groupOptional.get().getMakePublic() && isPublic) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP);
            }

            application = applicationOptional.get();
            application.setName(applicationDTO.getName());
            application.setDescription(applicationDTO.getDescription());
            application.setMakePublic(isPublic);

            Application update = applicationRepository.update(application);
            onUpdateApplicationWebSocket.broadcastResourceEvent(OnUpdateApplicationWebSocket.APPLICATION_UPDATED, update.getId());
            return HttpResponse.ok(new ApplicationDTO(update));
        } else {

            if (searchApplicationByNameAndGroup.isPresent()) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_ALREADY_EXISTS);
            }

            if (!groupOptional.get().getMakePublic() && isPublic) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP);
            }

            application = new Application(applicationDTO.getName(), applicationDTO.getDescription(), isPublic);
            application.setId(applicationDTO.getId());
            Group group = groupOptional.get();
            application.setPermissionsGroup(group);
            return HttpResponse.ok(new ApplicationDTO(applicationRepository.save(application)));
        }
    }

    public boolean isUserApplicationAdminOfGroup(Group group) {
        return securityUtil.getCurrentlyAuthenticatedUser()
                .map(user -> groupUserService.isUserApplicationAdminOfGroup(group.getId(), user.getId()))
                .orElse(false);
    }

    public HttpResponse deleteById(Long id) {

        Optional<Application> applicationOptional = applicationRepository.findById(id);
        if (applicationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Application application = applicationOptional.get();

        Optional<Group> groupOptional = groupRepository.findById(application.getPermissionsGroup().getId());
        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (!securityUtil.isCurrentUserAdmin() && !isUserApplicationAdminOfGroup(application.getPermissionsGroup())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        // TODO - Need to investigate cascade management to eliminate this
        applicationPermissionService.deleteAllByApplication(application);

        applicationRepository.deleteById(id);
        onUpdateApplicationWebSocket.broadcastResourceEvent(OnUpdateApplicationWebSocket.APPLICATION_DELETED, id);
        return HttpResponse.seeOther(URI.create("/api/applications"));
    }

    public HttpResponse show(Long id) {
        Optional<Application> applicationOptional = applicationRepository.findById(id);
        if (applicationOptional.isEmpty()) {
            return HttpResponse.notFound();
        }

        Application application = applicationOptional.get();
        if (!application.getMakePublic() && !securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isCurrentUserMemberOfGroup(
                        applicationOptional.get().getPermissionsGroup().getId())
        ){
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        ApplicationDTO applicationDTO = new ApplicationDTO(application);

        return HttpResponse.ok(applicationDTO);
    }

    public HttpResponse existsByName(String name) {
        Optional<Application> byNameEquals = applicationRepository.findByNameEquals(name.trim());

        if (byNameEquals.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(new ApplicationDTO(byNameEquals.get()));
    }

    public List<ApplicationDTO> toDtos(Iterable<Application> results) {
        return StreamSupport.stream(results.spliterator(), false)
                .map(ApplicationDTO::new)
                .collect(Collectors.toList());
    }
    
    public Optional<Application> findById(Long applicationId) {
        return applicationRepository.findById(applicationId);
    }

    public MutableHttpResponse<Object> generateCleartextPassphrase(Long applicationId) {
        Optional<Application> applicationOptional = applicationRepository.findById(applicationId);
        if (applicationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Application application = applicationOptional.get();

        Optional<Group> groupOptional = groupRepository.findById(application.getPermissionsGroup().getId());
        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (!securityUtil.isCurrentUserAdmin() && !isUserApplicationAdminOfGroup(application.getPermissionsGroup())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        String clearTextPassphrase = passphraseGenerator.generatePassphrase();

        application.setEncryptedPassword(passwordEncoderService.encode(clearTextPassphrase));
        applicationRepository.update(application);

        return HttpResponse.ok(clearTextPassphrase);
    }

    public AuthenticationResponse passwordMatches(Long applicationId, String rawPassword) {
        Optional<Application> applicationOptional = applicationRepository.findById(applicationId);
        if (applicationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        Application application = applicationOptional.get();

        if (passwordEncoderService.matches(rawPassword, application.getEncryptedPassword())) {
            return AuthenticationResponse.success(application.getId().toString(), List.of(UserRole.APPLICATION.toString()));
        } else {
            return AuthenticationResponse.failure("Invalid passphrase.");
        }
    }

    public HttpResponse<?> getIdentityCACertificate(String requestEtag) {
        String etag = applicationSecretsClient.getCorrespondingEtag(IDENTITY_CA_CERT);
        if (applicationSecretsClient.hasCachedFileBeenUpdated(IDENTITY_CA_CERT)) {
            etag = applicationSecretsClient.getCorrespondingEtag(IDENTITY_CA_CERT);
        }
        if (requestEtag != null && requestEtag.contentEquals(etag)) {
            return HttpResponse.notModified();
        }

        Optional<String> identityCACert = applicationSecretsClient.getIdentityCACert();
        if (identityCACert.isPresent()) {
            String cert = identityCACert.get();

            return HttpResponse.ok(cert).header(E_TAG_HEADER_NAME, etag);
        }

        throw new DPMException(ResponseStatusCodes.IDENTITY_CERT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public HttpResponse<?> getPermissionsCACertificate(String requestEtag) {
        String etag = applicationSecretsClient.getCorrespondingEtag(PERMISSIONS_CA_CERT);
        if (applicationSecretsClient.hasCachedFileBeenUpdated(PERMISSIONS_CA_CERT)) {
            etag = applicationSecretsClient.getCorrespondingEtag(PERMISSIONS_CA_CERT);
        }
        if (requestEtag != null && requestEtag.contentEquals(etag)) {
            return HttpResponse.notModified();
        }

        Optional<String> permissionsCACert = applicationSecretsClient.getPermissionsCACert();
        if (permissionsCACert.isPresent()) {
            String cert = permissionsCACert.get();

            return HttpResponse.ok(cert).header(E_TAG_HEADER_NAME, etag);
        }

        throw new DPMException(ResponseStatusCodes.PERMISSIONS_CERT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public HttpResponse<?> getGovernanceFile(String requestEtag) {
        String etag = applicationSecretsClient.getCorrespondingEtag(GOVERNANCE_FILE);
        if (applicationSecretsClient.hasCachedFileBeenUpdated(GOVERNANCE_FILE)) {
            etag = applicationSecretsClient.getCorrespondingEtag(GOVERNANCE_FILE);
        }
        if (requestEtag != null && requestEtag.contentEquals(etag)) {
            return HttpResponse.notModified();
        }

        Optional<String> governanceFile = applicationSecretsClient.getGovernanceFile();
        if (governanceFile.isPresent()) {
            String cert = governanceFile.get();

            return HttpResponse.ok(cert).header(E_TAG_HEADER_NAME, etag);
        }

        throw new DPMException(ResponseStatusCodes.GOVERNANCE_FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public HttpResponse<?> getApplicationPrivateKeyAndClientCertificate(String nonce) throws IOException, OperatorCreationException, GeneralSecurityException {

        Optional<String> identityCACert = applicationSecretsClient.getIdentityCACert();
        Optional<String> identityCAKey = applicationSecretsClient.getIdentityCAKey();
        Optional<Application> applicationOptional = securityUtil.getCurrentlyAuthenticatedApplication();

        if (applicationOptional.isPresent() && identityCACert.isPresent() && identityCAKey.isPresent()) {
            Application application = applicationOptional.get();

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X509Certificate x509Certificate = makeV3Certificate(
                    readCertificate(identityCACert.get()),
                    readPrivateKey(identityCAKey.get().trim()),
                    keyPair.getPublic(),
                    application,
                    nonce
            );

            Map creds = Map.of(
                    "private", objectToPEMString(keyPair.getPrivate()),
                    "public", objectToPEMString(x509Certificate)
            );
            return HttpResponse.ok(creds);
        }

        return HttpResponse.notFound();
    }

    public HttpResponse<?> getPermissionsFile(String nonce) throws IOException, GeneralSecurityException, MessagingException, SMIMEException, OperatorCreationException {
        Optional<String> permissionsCAKey = applicationSecretsClient.getPermissionsCAKey();
        Optional<String> permissionsCACert = applicationSecretsClient.getPermissionsCACert();
        Optional<Application> applicationOptional = securityUtil.getCurrentlyAuthenticatedApplication();

        if (applicationOptional.isPresent() && permissionsCAKey.isPresent() && permissionsCACert.isPresent()) {
            //openssl smime -sign -in permissions.ftlx -text -out permissions.ftlx.p7s -signer permissions_ca.pem -inkey permissions_ca_key.pem
            String permissionsXml = generatePermissionsXml(applicationOptional.get(), nonce);

            MimeBodyPart mimeBodyPart = new MimeBodyPart();
            mimeBodyPart.setText(permissionsXml);
            MimeMultipart signedMultipart = createSignedMultipart(
                    readPrivateKey(permissionsCAKey.get()),
                    readCertificate(permissionsCACert.get()),
                    mimeBodyPart);

            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            message.setContent(signedMultipart);

            String result;
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                message.writeTo(byteArrayOutputStream);
                result = byteArrayOutputStream.toString();
            }

            return HttpResponse.ok(result);
        }

        return HttpResponse.notFound();
    }

    public HttpResponse<?> getPermissionJson(String requestEtag) throws NoSuchAlgorithmException {
        Optional<Application> applicationOptional = securityUtil.getCurrentlyAuthenticatedApplication();

        if (applicationOptional.isPresent()) {
            HashMap applicationPermissions = buildApplicationPermissionsJson(applicationOptional.get());
            String etag = generateMD5Hash(applicationPermissions.toString());
            if (requestEtag != null && requestEtag.contentEquals(etag)) {
                return HttpResponse.notModified();
            }

            return HttpResponse.ok(applicationPermissions).header(E_TAG_HEADER_NAME, etag);
        }

        return HttpResponse.notFound();
    }

    private String generateMD5Hash(String str) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(str.getBytes());
        byte[] digest = md.digest();
        return DatatypeConverter.printHexBinary(digest).toUpperCase();
    }

    public static MimeMultipart createSignedMultipart(
            PrivateKey signingKey, X509Certificate signingCert, MimeBodyPart message)
            throws GeneralSecurityException, OperatorCreationException, SMIMEException {
        List<X509Certificate> certList = new ArrayList<X509Certificate>();
        certList.add(signingCert);
        Store certs = new JcaCertStore(certList);
        ASN1EncodableVector signedAttrs = generateSignedAttributes();
        signedAttrs.add(new Attribute(CMSAttributes.signingTime, new DERSet(new Time(new Date()))));
        SMIMESignedGenerator gen = new SMIMESignedGenerator();
        gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder()
                .setSignedAttributeGenerator(new AttributeTable(signedAttrs))
                .build("SHA1WITHECDSA", signingKey, signingCert));
        gen.addCertificates(certs);
        return gen.generate(message);
    }

    private static ASN1EncodableVector generateSignedAttributes() {
        ASN1EncodableVector signedAttrs = new ASN1EncodableVector();
        SMIMECapabilityVector caps = new SMIMECapabilityVector();
        caps.addCapability(SMIMECapability.aES128_CBC);
        caps.addCapability(SMIMECapability.aES192_CBC);
        caps.addCapability(SMIMECapability.aES256_CBC);
        signedAttrs.add(new SMIMECapabilitiesAttribute(caps));
        return signedAttrs;
    }

    private String generatePermissionsXml(Application application, String nonce) throws IOException {
        Map<String, Object> dataModel = buildTemplateDataModel(nonce, application);

        return templateService.mergeDataAndTemplate(dataModel);
    }

    private Map<String, Object> buildTemplateDataModel(String nonce, Application application) {
        HashMap<String, String> oidMap = new HashMap<>();
        oidMap.put("2.5.4.4", "SN");
        oidMap.put("2.5.4.42", "GN");

        HashMap<String, Object> dataModel = new HashMap<>();
        final String sn = (new X500Principal(buildSubject(application, nonce))).getName(X500Principal.RFC2253, oidMap);
        dataModel.put("subject", xmlEscaper.escape(sn));
        dataModel.put("applicationId", application.getId());

        final String validStart = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        final String validEnd = ZonedDateTime.now(ZoneOffset.UTC).plusDays(permissionExpiry).format(DateTimeFormatter.ISO_INSTANT);
        dataModel.put("validStart", validStart);
        dataModel.put("validEnd", validEnd);
        dataModel.put("domain", permissionDomain);

        dataModel.putAll(buildApplicationPermissions(application));

        return dataModel;
    }

    public static class PubSubEntry {
        private List<String> topics;
        private List<String> partitions;

        public PubSubEntry(Set<String> topics, Set<String> partitions) {
            this.topics = new ArrayList<>(topics);
            this.partitions = new ArrayList<>(partitions);
        }

        // Use when a pre-determined order of the items in the data members is desired, e.g. for testing.
        public PubSubEntry(List<String> topics, List<String> partitions) {
            this.topics = topics;
            this.partitions = partitions;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }

        public List<String> getPartitions() {
            return partitions;
        }

        public void setPartitions(List<String> partitions) {
            this.partitions = partitions;
        }
    }

    private HashMap buildApplicationPermissions(Application application) {
        HashMap<String, Object> dataModel = new HashMap<>();
        List<ApplicationPermission> readApplicationPermissions = applicationPermissionService.findAllByApplicationAndReadEqualsTrue(application);
        List<ApplicationPermission> writeApplicationPermissions = applicationPermissionService.findAllByApplicationAndWriteEqualsTrue(application);

        // list of canonical names for each publish-subscribe sections
        List<PubSubEntry> publishList = new ArrayList<>();
        List<PubSubEntry> subscribeList = new ArrayList<>();

        // read
        buildPubSubList(subscribeList, readApplicationPermissions, false);

        // write
        buildPubSubList(publishList, writeApplicationPermissions, true);

        dataModel.put("subscribes", subscribeList);
        dataModel.put("publishes", publishList);

        return dataModel;
    }

    private void buildPubSubList(List<PubSubEntry> list, List<ApplicationPermission> applicationPermissions, boolean publishing) {
        if (applicationPermissions == null) {
            return;
        }

        // Collect all topics that have the same set of partitions and create a new entry for them.
        Map<Set<String>, Set<String>> partitionsTopicMap = new HashMap<>();

        applicationPermissions.forEach(applicationPermission -> {
            Topic topic = applicationPermission.getPermissionsTopic();
            Set<String> partitions = new HashSet<>();
            if (publishing) {
                Set<WritePartition> writePartitions = applicationPermission.getWritePartitions();
                writePartitions.forEach(writePartition -> {
                    partitions.add(xmlEscaper.escape(writePartition.getPartitionName()));
                });
            } else {
                Set<ReadPartition> readPartitions = applicationPermission.getReadPartitions();
                readPartitions.forEach(readPartition -> {
                    partitions.add(xmlEscaper.escape(readPartition.getPartitionName()));
                });
            }
            Set<String> immutablePartitions = Collections.unmodifiableSet(partitions);
            String canonicalTopicName = buildCanonicalName(topic);
            if (partitionsTopicMap.containsKey(immutablePartitions)) {
                partitionsTopicMap.get(immutablePartitions).add(canonicalTopicName);
            } else {
                Set<String> topicNames = new HashSet<>();
                topicNames.add(canonicalTopicName);
                partitionsTopicMap.put(immutablePartitions, topicNames);
            }
        });

        partitionsTopicMap.forEach((partitions, topics) -> {
            list.add(new PubSubEntry(topics, partitions));
        });
    }

    private HashMap buildApplicationPermissionsJson(Application application) {
        HashMap<String, Object> dataModel = new HashMap<>();
        List<ApplicationPermission> readApplicationPermissions = applicationPermissionService.findAllByApplicationAndReadEqualsTrue(application);
        List<ApplicationPermission> writeApplicationPermissions = applicationPermissionService.findAllByApplicationAndWriteEqualsTrue(application);

        List<Object> publishPartitions = new ArrayList<>();
        List<Object> subscribePartitions = new ArrayList<>();

        buildTopicPartitionsMap(publishPartitions, writeApplicationPermissions, true);
        buildTopicPartitionsMap(subscribePartitions, readApplicationPermissions, false);

        dataModel.put("publishes", publishPartitions);
        dataModel.put("subscribes", subscribePartitions);

        return dataModel;
    }

    private void buildTopicPartitionsMap(List<Object> topicPartitionsList,
                                         List<ApplicationPermission> applicationPermissions, boolean publishing) {
        if (applicationPermissions == null) {
            return;
        }

        applicationPermissions.forEach(applicationPermission -> {
            String topicName = buildCanonicalName(applicationPermission.getPermissionsTopic());
            List<String> partitions = new ArrayList<>();
            if (publishing) {
                applicationPermission.getWritePartitions().forEach(writePartition -> {
                    partitions.add(writePartition.getPartitionName());
                });
            } else {
                applicationPermission.getReadPartitions().forEach(readPartition -> {
                    partitions.add(readPartition.getPartitionName());
                });
            }
            Map<String, Object> topicPartitions = new HashMap<>();
            topicPartitions.put("topic", topicName);
            topicPartitions.put("partitions", partitions);
            topicPartitionsList.add(topicPartitions);
        });
    }

    private String buildCanonicalName(Topic permissionsTopic) {
        return permissionsTopic.getKind() + "." +
                permissionsTopic.getPermissionsGroup().getId() + "." +
                xmlEscaper.escape(permissionsTopic.getName());
    }

    public X509Certificate makeV3Certificate(
            X509Certificate caCertificate, PrivateKey caPrivateKey, PublicKey eePublicKey, Application application, String nonce)
            throws GeneralSecurityException, CertIOException, OperatorCreationException {

        X509v3CertificateBuilder v3CertBldr = new JcaX509v3CertificateBuilder(
                caCertificate.getSubjectX500Principal(), // issuer
                BigInteger.valueOf(System.currentTimeMillis()) // serial number
                        .multiply(BigInteger.valueOf(10)),
                new Date(System.currentTimeMillis() - 1000L * 5), // start time
                new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(certExpiry)), // expiry time
                new X500Principal(buildSubject(application, nonce)), // subject
                eePublicKey); // subject public key

        // extensions
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        v3CertBldr.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extUtils.createSubjectKeyIdentifier(eePublicKey));
        v3CertBldr.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                extUtils.createAuthorityKeyIdentifier(caCertificate));
        v3CertBldr.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(false));
        JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA256WITHECDSA");

        return new JcaX509CertificateConverter().getCertificate(v3CertBldr.build(signerBuilder.build(caPrivateKey)));
    }

    private String buildSubject(Application application, String nonce) {
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, application.getId() + "_" + nonce);
        nameBuilder.addRDN(BCStyle.GIVENNAME, application.getName());
        nameBuilder.addRDN(BCStyle.SURNAME, String.valueOf(application.getPermissionsGroup().getId()));
        return nameBuilder.build().toString();
    }

    public String objectToPEMString(X509Certificate certificate) throws IOException {
        StringWriter sWrt = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(sWrt);
        pemWriter.writeObject(certificate);
        pemWriter.close();
        return sWrt.toString();
    }

    public String objectToPEMString(PrivateKey key) throws IOException {
        StringWriter sWrt = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(sWrt);
        pemWriter.writeObject(new JcaPKCS8Generator(key, null));
        pemWriter.close();
        return sWrt.toString();
    }

    public X509Certificate readCertificate(String pemEncoding) throws IOException, CertificateException {
        PEMParser parser = new PEMParser(new StringReader(pemEncoding));
        X509CertificateHolder certHolder = (X509CertificateHolder) parser.readObject();
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    public PrivateKey readPrivateKey(String pemEncoding) throws IOException {
        PEMParser parser = new PEMParser(new StringReader(pemEncoding));
        PEMKeyPair pemKeyPair = (PEMKeyPair) parser.readObject();
        return new JcaPEMKeyConverter().getPrivateKey(pemKeyPair.getPrivateKeyInfo());
    }

    public HttpResponse generateGrantToken(Long applicationId) {
        Optional<Application> applicationOptional = applicationRepository.findById(applicationId);
        if (applicationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        Application application = applicationOptional.get();

        if (!securityUtil.isCurrentUserAdmin() && !isUserApplicationAdminOfGroup(application.getPermissionsGroup())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        Optional<User> currentlyAuthenticatedUser = securityUtil.getCurrentlyAuthenticatedUser();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(applicationId.toString())
                .claim("email", currentlyAuthenticatedUser.get().getEmail())
                .claim("appName", application.getName())
                .claim("groupId", application.getPermissionsGroup().getId())
                .claim("groupName", application.getPermissionsGroup().getName())
                .build();

        Map<String, Object> map = jwtClaimsSetGenerator.generateClaims(
                new AuthenticationJWTClaimsSetAdapter(claimsSet),  appGrantTokenExpiry * 60 * 60);
        Optional<String> token = jwtTokenGenerator.generateToken(map);

        return HttpResponse.ok(token.get());
    }
}
