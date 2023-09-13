# DDS Permissions Manager

DDS stands for [Data Distribution Service](https://www.omg.org/spec/DDS/).
DDS applications communicate by writing and reading samples which are just pieces of data.
For example, a weather station application may write temperature samples that a weather forecasting application reads.
A topic is a named collection of related samples.
Topics are usually named to describe the data that is being shared.
For example, the topic for temperature samples might be named "Temperature".

A [secure DDS](https://www.omg.org/spec/DDS-SECURITY/) application uses a permissions file that controls its ability to write and read topics.
For example, weather stations should be able to write samples to the Temperature topic and forecasting applications should be able to read samples from the Temperature topic.

The DDS Permissions Manager allows users to enter the information necessary for a permissions file.
After entering the information, an application can download its permissions file and other DDS Security documents to interact with other applications.

Inter-organizational collaboration was one of the main motivations when designing the DDS Permissions Manager.
DDS Security is based on public key infrastructure (PKI).
In the DDS Security model, there is an identity certificate authority (CA) that signs certificates issued to DDS participants for mutual authentication and a permissions CA that signs documents that DDS participants use for mutual authorization.
The DDS Permissions Manager plays the role of the identity and permissions CA.
Organizations using a common DDS Permissions Manager instance can share data using DDS because they are using the same identity and permissions CA.

The principle of collaboration applies within an organization as well.
That is, different groups within an organization can use a private instance of the DDS Permissions Manager to organize the DDS security documents for their DDS applications.
In this scenario, the organization can use the UI to enter information or use the API directly.

## User Documentation

The DDS Permissions Manager is built on the following concepts:
* Application - An application represents a secure DDS application.
* Topic - A topic represents a DDS topic.
* Group Membership - A group membership assigned a user (identified by their email address) zero or more roles in a Group.
* Group - A collection of Applications, Topics, and Group Memberships.
* Roles
  * Group Admin - A group admin can add other Group Admins and Topic and Application Admins to their group.
  * Application Admin - An application admin can create/delete applications and generate passwords for applications.
  * Topic Admin - A topic admin can create/delete topics and grant/revoke access to them.
* Super Admin - A super admin is a user that can perform any operation.

### Groups and Super Admins

A Group is just a unique name that acts as a container for Group Memberships, Topics, and Applications.
That is, a Group is associated with every Group Membership, Topic, and Application.
Deleting a group deletes all of its associated Group Memberships, Topics, and Applications.

A Super Admin has unrestricted access and can perform any operation.
Typically, a Super Admin comes from the organization *operating* the DDS Permissions Manager as opposed to an organization that is *using* the DDS Permissions Manager.
The primary activity of a Super Admin is to enroll organizations that wish to use the DDS Permissions Manager.
In this capacity, a Super Admin creates a Group for the organization and adds at least one Group Admin to the Group.
From there, the Group Admin can complete the onboarding process.
To add the Group Admin, the Super Admin creates a Group Membership that includes the 1) email address of the Group Admin, 2) the Group to which the membership applies, and 3) a set of grants, specifically, the Group Admin grant.

The activities that can only be performed by a Super Admin are:
* Add/remove other super admins
* Create/delete groups
* Add the initial Group Admin to a Group

### Group Memberships, Roles, and Group Admins

A Group Membership assigns zero or more roles to a user with reference to a specific Group.
The roles define the capabilities of the user with respect to that particular Group.
The three possible roles are Group Admin, Topic Admin, and Application Admin.
A user can have multiple Group memberships; one for each Group to which they belong.
Furthermore, a user can have different roles for each Group.
For example, the same user could be a Group Admin in one Group while they are a Topic Admin in another.
A user can be added to a group without any roles selected.
In this case, the user can see all of the information for a Group but cannot make any changes.

A Group Admin has the responsibility of maintaining the Group Memberships for their Groups.
A Group Admin can add/remove/edit Group Memberships for Groups for which they are Group Admin
To complete the onboarding process, a Group Admin adds Topic Admins and Applications Admins to their group.
The Group Admin can also add other Group Admins.
To add a user to a group, a Group Admin creates/edits a Group Membership that include the user's email address, Group, and roles.

### Applications and Application Admins

An Application represents a secure DDS application.
An Application has a name and belongs to a Group.
An Application also has a password that allows it to authenticate to the API and download its DDS Security documents.
Application Admins can edits the list of Applications in a Group and generate Application passwords.
Generating a new password invalidates the existing one and there is no way show the current password.
Consequently, Application Admins should record and distribute the password over secure, encrypted, and authenticated methods, such as a password or secret manager.
Passwords should not be distributed over email, text message, or instant message.

### Topics and Topic Admins

A Topic represents a DDS Topic.
A Topic has a name and belongs to a Group.
A Topic can be configured so that any Application can read it or configured so that the list of reading Applications must be specified.
This cannot be changed after the Topic is created.
Associated with a Topic is a list of Applications and there permissions (read and/or write) for that Topic.
The Application can belong to any Group.
(This allows Applications in different organizations to share data.)
Topic Admins can edit the list of Topics for a Group and edit the access to a Topic.

### Downloading DDS Security Documents

This section describes how an Application authenticates with the DDS Permissions Manager and downloads its security documents.
The process is illustrated using `curl` and assumes that `DPM_URL` is the URL of the DDS Permissions Manager.

1. An Application Admin must generate a password by going to the Application detail screen and clicking "Generate Password."  The Application Admin should also note the "Username" on the same screen.
2. An application requests a JWT from the API.  For, example

        curl -c cookies.txt -H'Content-Type: application/json' -d'{"username":"${USERNAME}","password":"${PASSWORD}"}' ${DPM_URL}/api/login

3. With the JWT stored in `cookies.txt`, the Application can request the Identity CA certificate

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/identity_ca.pem > identity_ca.pem

4. and the Permissions CA certificate

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/permissions_ca.pem > permissions_ca.pem

5. and the Governance file

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/governance.xml.p7s > governance.xml.p7s

6. The Application can request a key and certificate from the API.  The

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/key-pair?nonce=NONCE > key-pair.json

   The response in a JSON object containing the private key and public certificate

        {
          "private": "-----BEGIN PRIVATE KEY-----\nMEEC...",
          "public": "-----BEGIN CERTIFICATE-----\nMIICrT..."
        }

7. Finally, the Application can request the permissions

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/permissions.xml.p7s?nonce=NONCE > permissions.xml.p7s

One thing to note in the sequence above is the `NONCE` parameter that is passed in the request for the key pair and permissions document.
The nonce should be an alphanumeric string that identifies a particular Application instance.
The nonce is used to construct the subject in the public certificate and permissions file.
The subject will consist of a common name (CN) that is the nonce appended to the Application's id, a given name (GN) that is the Application name, and a surname (SN) that is the Group id of the Application.
It is possible to run multiple copies (instances) of the same Application by using different nonces.

### Canonical Topic Names

To facilitate inter-organization interactions, the DDS Permissions Manager constructs a *canonical* topic name which is TOPICKIND.GROUPID.TOPIC_NAME.
Canonical topic names appear on the Topic Detail Screen and the Application Detail Screen.
*DDS developers must use the canonical topic name when creating a topic.*

TOPICKIND will be "B" if any Application can read the Topic and "C" if Applications must be explicitly added to read.
The author of the governance file enforces these semantics by writing appropriate rules in the governance file.
For example,

    <topic_access_rules>
        <topic_rule>
          <topic_expression>B*</topic_expression>
          <enable_discovery_protection>true</enable_discovery_protection>
          <enable_liveliness_protection>true</enable_liveliness_protection>
          <enable_read_access_control>false</enable_read_access_control>
          <enable_write_access_control>true</enable_write_access_control>
          <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
          <data_protection_kind>NONE</data_protection_kind>
        </topic_rule>
        <topic_rule>
          <topic_expression>C*</topic_expression>
          <enable_discovery_protection>true</enable_discovery_protection>
          <enable_liveliness_protection>true</enable_liveliness_protection>
          <enable_read_access_control>true</enable_read_access_control>
          <enable_write_access_control>true</enable_write_access_control>
          <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
          <data_protection_kind>NONE</data_protection_kind>
        </topic_rule>

The topic rule with topic expression `B*` matches topic names that start with "B".
The corresponding rule says that the `enable_read_access_control` is false meaning that any application can read topics whose names start with "B."
The topic rule for `C*` specifies that `enable_read_access_control` is true so applications must have explicit access to read topics whose names start with "C."

The TOPICKIND convention is an assumption of the DDS Permissions Manager that allows one to write a generic governance file.
Future versions of the DDS Permissions Manager might provide support for additional topic kinds.

The GROUPID is included in the canonical topic name to disambiguate topics with the same name belonging to different groups.

### Limitations

* The DDS Permissions Manager assumes topic rules for supporting the different topic kinds.  See the Canonical Topic Name section.
* The certificate expiration is configurable at a global level and not for specific applications.
* The DDS Permissions Manager does not allow a free-form permissions file.
* The DDS Permissions Manager does not currently support for all permissions document fields
  * Validity is configurable at a global level and not for specific applications and/or topics.
  * There is no way to specify a list of partitions.

## Operator Documentation

### General Application Architecture

                                                 +--------------+
                                              +->| Secret Store |
                                              |  +--------------+
              +------------+     +---------+  |  +----------+
              | Web App UI |<--->| Web API |<-+->| Database |
              +------------+     +---------+  |  +----------+
                                              |  +---------------+
                                              +->| Auth Provider |
                                                 +---------------+

The DDS Permissions Manager consists of

* A Web App UI that can either be served by the Web API or independently
* A Web API that serves data to the UI and DDS Applications
* A Secret Store for storing keys and other things related to DDS Security
* A Database for persistent storage
* An Auth Provider for authenticating users

The Web Application UI is built using Svelte and is served by the Web API.
The Web API is built using the Micronaut&reg; framework.
The Web API is horizontally scalable.

### Building the Application

The Web Application UI requires the URLs of the API when built.
This is set using the VITE_BACKEND_URL and VITE_WEBSOCKET_URL environment variable.
If the Web API will serve the UI, then set VITE_BACKEND_URL to `/api`.
Otherwise, set VITE_BACKEND_URL to the full URL of the API (which should end in `/api`).
The VITE_WEBSOCKET_URL should be set to 'ws://localhost:8080/ws' for running locally.
The VITE_BACKEND_URL and VITE_WEBSOCKET_URL variable can also be set in `frontend/.env`.

The application is built with gradle, e.g., `./gradlew app:build`.
This builds both the API and the UI.
When run this way, gradle will produce three jar files:

* `app/build/libs/app-VERSION-all.jar` - a runnable jar that contains the API, the dependencies for the API, the resources for the API, and the UI as static assets.
* `app/build/libs/app-VERSION-runner.jar` - a runnable jar that only contains the API (no dependencies, resources, or UI).
* `app/build/libs/app-VERSION.jar` - a non-runnable jar that contains the API, the resources for the API, and the UI as static assets.

### Containerization

For convenience, the UI and API can be built into a single container image.
A suitable version of Java (11 and up) and npm must be available.
The following snippet illustrates how to build a container image:

    # Set the database dependency.
    DPM_DATABASE_DEPENDENCY="mysql:mysql-connector-java:8.0.31,com.google.cloud.sql:mysql-socket-factory-connector-j-8:1.7.2"
    # Set the API url.
    VITE_BACKEND_URL=/api
    ./gradlew dockerfile
    ./gradlew buildLayers
    docker build -t my-dpm app/build/docker/main

### The Secret Store

Currently, the DDS Permissions Manager supports the following Secret Stores:

* GCP Secret Manager

At a minimum, the secret store should contain the following documents

* `governance_xml_p7s` - The signed governance file that will be served to the applications.
* `identity_ca_key_pem` - The private key of the Identity CA in PEM format.
* `identity_ca_pem` - The public certificate of the Identity CA in PEM format.
* `permissions_ca_key_pem` - The private key of the Permissions CA in PEM format.
* `permissions_ca_pem` - The public certificate of the Permissions CA in PEM format.

`identity_ca_key_pem` and `permissions_ca_key_pem` must not include elliptic curve (EC) parameters.

The governance file must contain certain rules.
See [Canonical Topic Names](#canonical-topic-names) for more details.

In addition to the documents necessary for DDS Security, the Secret Store is a good candidate for database credentials and the secrets for JWT generation.

### Steps to produce necessary artifacts (Identity CA, Permissions CA, governance file)

There are a number of different ways to produce a viable Identity CA, Permissions CA, and governance file.
This sections describes one way of producing these artifacts.

First, let `identity.cnf` be a valid `openssl` configuration file:

    #
    # OpenSSL example Certificate Authority configuration file.

    ####################################################################
    [ ca ]
    default_ca  = CA_default    # The default ca section

    ####################################################################
    [ CA_default ]

    dir    = .      # Where everything is kept
    certs    = $dir/certs    # Where the issued certs are kept
    crl_dir    = $dir/crl    # Where the issued crl are kept
    database  = $dir/index.txt  # database index file.

    new_certs_dir  = $dir

    certificate  = $dir/identity_ca.pem      # The CA certificate
    serial    = $dir/serial             # The current serial number
    crlnumber  = $dir/crlnumber          # the current crl number
                      # must be commented out to leave a V1 CRL
    crl    = $dir/crl.pem             # The current CRL
    private_key  = $dir/identity_ca_key.pem      # The private key
    RANDFILE  = $dir/.rand            # private random number file

    #x509_extensions  = usr_cert    # The extentions to add to the cert

    # Comment out the following two lines for the "traditional"
    # (and highly broken) format.
    name_opt   = ca_default    # Subject Name options
    cert_opt   = ca_default    # Certificate field options

    # Extension copying option: use with caution.
    # copy_extensions = copy

    # Extensions to add to a CRL. Note: Netscape communicator chokes on V2 CRLs
    # so this is commented out by default to leave a V1 CRL.
    # crlnumber must also be commented out to leave a V1 CRL.
    # crl_extensions  = crl_ext

    default_days  = 3650      # how long to certify for
    default_crl_days= 30      # how long before next CRL
    default_md  = sha256    # which md to use.
    preserve  = no      # keep passed DN ordering

    # A few difference way of specifying how similar the request should look
    # For type CA, the listed attributes must be the same, and the optional
    # and supplied fields are just that :-)
    policy    = policy_strict

    [ policy_strict ]
    # The root CA should only sign intermediate certificates that match.
    # See the POLICY FORMAT section of `man ca`.
    countryName             = match
    stateOrProvinceName     = match
    organizationName        = match
    organizationalUnitName  = optional
    commonName              = supplied
    emailAddress            = optional

    [ policy_loose ]
    # Allow the intermediate CA to sign a more diverse range of certificates.
    # See the POLICY FORMAT section of the `ca` man page.
    countryName             = optional
    stateOrProvinceName     = optional
    localityName            = optional
    organizationName        = optional
    organizationalUnitName  = optional
    commonName              = supplied
    emailAddress            = optional

    [ req ]
    prompt                  = no
    default_bits    = 2048
    #default_keyfile   = privkey.pem
    distinguished_name  = req_distinguished_name
    #attributes    = req_attributes
    default_md          = sha256
    x509_extensions  = v3_ca  # The extentions to add to the self signed cert
    string_mask         = utf8only

    [ req_distinguished_name ]
    countryName         = US
    stateOrProvinceName = MO
    localityName        = Saint Louis
    0.organizationName  = UNITY
    organizationalUnitName = CommUNITY
    commonName          = Identity CA
    emailAddress        = info@test.test

    [ v3_ca ]
    # Extensions for a typical CA (`man x509v3_config`).
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid:always,issuer
    basicConstraints = critical, CA:true
    keyUsage = critical, digitalSignature, cRLSign, keyCertSign

    [ v3_intermediate_ca ]
    # Extensions for a typical intermediate CA (`man x509v3_config`).
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid:always,issuer
    basicConstraints = critical, CA:true, pathlen:0
    keyUsage = critical, digitalSignature, cRLSign, keyCertSign

    [ usr_cert ]
    # Extensions for client certificates (`man x509v3_config`).
    basicConstraints = CA:FALSE
    nsCertType = client, email
    nsComment = "OpenSSL Generated Client Certificate"
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid,issuer
    keyUsage = critical, nonRepudiation, digitalSignature, keyEncipherment
    extendedKeyUsage = clientAuth, emailProtection

    [ server_cert ]
    # Extensions for server certificates (`man x509v3_config`).
    basicConstraints = CA:FALSE
    nsCertType = server
    nsComment = "OpenSSL Generated Server Certificate"
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid,issuer:always
    keyUsage = critical, digitalSignature, keyEncipherment
    extendedKeyUsage = serverAuth

    [ crl_ext ]
    # Extension for CRLs (`man x509v3_config`).
    authorityKeyIdentifier=keyid:always

Second, let `permissions.cnf` be a valid `openssl` configuration file:

    #
    # OpenSSL example Certificate Authority configuration file.

    ####################################################################
    [ ca ]
    default_ca  = CA_default    # The default ca section

    ####################################################################
    [ CA_default ]

    dir    = .      # Where everything is kept
    certs    = $dir/certs    # Where the issued certs are kept
    crl_dir    = $dir/crl    # Where the issued crl are kept
    database  = $dir/index.txt  # database index file.

    new_certs_dir  = $dir

    certificate  = $dir/ca.pem      # The CA certificate
    serial    = $dir/serial             # The current serial number
    crlnumber  = $dir/crlnumber          # the current crl number
                      # must be commented out to leave a V1 CRL
    crl    = $dir/crl.pem             # The current CRL
    private_key  = $dir/ca_key.pem      # The private key
    RANDFILE  = $dir/.rand            # private random number file

    #x509_extensions  = usr_cert    # The extentions to add to the cert

    # Comment out the following two lines for the "traditional"
    # (and highly broken) format.
    name_opt   = ca_default    # Subject Name options
    cert_opt   = ca_default    # Certificate field options

    # Extension copying option: use with caution.
    # copy_extensions = copy

    # Extensions to add to a CRL. Note: Netscape communicator chokes on V2 CRLs
    # so this is commented out by default to leave a V1 CRL.
    # crlnumber must also be commented out to leave a V1 CRL.
    # crl_extensions  = crl_ext

    default_days  = 3650      # how long to certify for
    default_crl_days= 30      # how long before next CRL
    default_md  = sha256    # which md to use.
    preserve  = no      # keep passed DN ordering

    # A few difference way of specifying how similar the request should look
    # For type CA, the listed attributes must be the same, and the optional
    # and supplied fields are just that :-)
    policy    = policy_strict

    [ policy_strict ]
    # The root CA should only sign intermediate certificates that match.
    # See the POLICY FORMAT section of `man ca`.
    countryName             = match
    stateOrProvinceName     = match
    organizationName        = match
    organizationalUnitName  = optional
    commonName              = supplied
    emailAddress            = optional

    [ policy_loose ]
    # Allow the intermediate CA to sign a more diverse range of certificates.
    # See the POLICY FORMAT section of the `ca` man page.
    countryName             = optional
    stateOrProvinceName     = optional
    localityName            = optional
    organizationName        = optional
    organizationalUnitName  = optional
    commonName              = supplied
    emailAddress            = optional

    [ req ]
    prompt                  = no
    default_bits    = 2048
    #default_keyfile   = privkey.pem
    distinguished_name  = req_distinguished_name
    #attributes    = req_attributes
    default_md          = sha256
    x509_extensions  = v3_ca  # The extentions to add to the self signed cert
    string_mask         = utf8only

    [ req_distinguished_name ]
    countryName         = US
    stateOrProvinceName = MO
    localityName        = Saint Louis
    0.organizationName  = UNITY
    organizationalUnitName = CommUNITY
    commonName          = Permissions CA
    emailAddress        = info@test.test

    [ v3_ca ]
    # Extensions for a typical CA (`man x509v3_config`).
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid:always,issuer
    basicConstraints = critical, CA:true
    keyUsage = critical, digitalSignature, cRLSign, keyCertSign

    [ v3_intermediate_ca ]
    # Extensions for a typical intermediate CA (`man x509v3_config`).
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid:always,issuer
    basicConstraints = critical, CA:true, pathlen:0
    keyUsage = critical, digitalSignature, cRLSign, keyCertSign

    [ usr_cert ]
    # Extensions for client certificates (`man x509v3_config`).
    basicConstraints = CA:FALSE
    nsCertType = client, email
    nsComment = "OpenSSL Generated Client Certificate"
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid,issuer
    keyUsage = critical, nonRepudiation, digitalSignature, keyEncipherment
    extendedKeyUsage = clientAuth, emailProtection

    [ server_cert ]
    # Extensions for server certificates (`man x509v3_config`).
    basicConstraints = CA:FALSE
    nsCertType = server
    nsComment = "OpenSSL Generated Server Certificate"
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid,issuer:always
    keyUsage = critical, digitalSignature, keyEncipherment
    extendedKeyUsage = serverAuth

    [ crl_ext ]
    # Extension for CRLs (`man x509v3_config`).
    authorityKeyIdentifier=keyid:always

Third, let `governance.xml` be a valid DDS Security Governance file:

    <?xml version="1.0" encoding="UTF-8"?>
    <dds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.omg.org/spec/DDS-SECURITY/20170901/omg_shared_ca_permissions.xsd">
      <domain_access_rules>
        <domain_rule>
          <domains>
            <id>1</id>
          </domains>
          <allow_unauthenticated_participants>false</allow_unauthenticated_participants>
          <enable_join_access_control>true</enable_join_access_control>
          <discovery_protection_kind>ENCRYPT</discovery_protection_kind>
          <liveliness_protection_kind>ENCRYPT</liveliness_protection_kind>
          <rtps_protection_kind>ENCRYPT</rtps_protection_kind>
          <topic_access_rules>
            <topic_rule>
              <topic_expression>B*</topic_expression>
              <enable_discovery_protection>true</enable_discovery_protection>
              <enable_liveliness_protection>true</enable_liveliness_protection>
              <enable_read_access_control>false</enable_read_access_control>
              <enable_write_access_control>true</enable_write_access_control>
              <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
              <data_protection_kind>NONE</data_protection_kind>
            </topic_rule>
            <topic_rule>
              <topic_expression>C*</topic_expression>
              <enable_discovery_protection>true</enable_discovery_protection>
              <enable_liveliness_protection>true</enable_liveliness_protection>
              <enable_read_access_control>true</enable_read_access_control>
              <enable_write_access_control>true</enable_write_access_control>
              <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
              <data_protection_kind>NONE</data_protection_kind>
            </topic_rule>
          </topic_access_rules>
        </domain_rule>
      </domain_access_rules>
    </dds>

See [Canonical Topic Names](#canonical-topic-names) for an explanation of the two topic rules.

The following shell script shows how to create an Identity CA and Permissions CA and how to sign the governance file:

    ####################################################################################################
    # The following steps are not performed in the DDS Permissions Manager but are here for reference. #
    ####################################################################################################

    echo -n '' > index.txt
    echo -n '' > index.txt.attr
    echo -n '01' > serial

    # Generate a self-signed certificate for the identity CA.
    openssl ecparam -name prime256v1 -genkey -noout -out identity_ca_key.pem
    openssl req -config ./identity.cnf -days 3650 -new -x509 -extensions v3_ca -key identity_ca_key.pem -out identity_ca.pem

    echo "Identity CA key in identity_ca_key.pem"
    echo "Identity CA certificate in identity_ca.pem"

    # Generate a self-signed certificate for the permissions CA.
    openssl ecparam -name prime256v1 -genkey -noout -out permissions_ca_key.pem
    openssl req -config ./permissions.cnf -days 3650 -new -x509 -extensions v3_ca -key permissions_ca_key.pem -out permissions_ca.pem

    echo "Permissions CA key in permissions_ca_key.pem"
    echo "Permissions CA certificate in permissions_ca.pem"

    # Sign the governance file with the permissions CA.
    openssl smime -sign -in governance.xml -text -out governance.xml.p7s -signer permissions_ca.pem -inkey permissions_ca_key.pem

    echo "Signed governance file in governance.xml.p7s"

See [The Secret Store](#the-secret-store) for how these artifacts should be named when uploaded into the secret store.

### Configuring GCP Secret Manager as a Secret Store

The following environment variables should be set to enable GCP Secret Manager:

* GCP_CREDENTIALS_ENABLED - true
* GCP_PROJECT_ID - the GCP Project ID

Beyond this, the service account used by the Web API will need access to the Secret Manager.

### The Database

Currently, the DDS Permissions Manager supports the following Databases:

| Database     | Versions     | Driver                   | Reference                                                                                                                 |
|--------------|--------------|--------------------------|---------------------------------------------------------------------------------------------------------------------------|
| MySQL Server | 8.0 and 5.7  | com.mysql.cj.jdbc.Driver | [link](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-versions.html)                                            |
| PostgreSQL   | 8.2 or newer | org.postgresql.Driver    | [link](https://jdbc.postgresql.org/documentation/#:~:text=The%20current%20version%20of%20the,(JDBC%204.2)%20and%20above.) |

To connect to a database, the following environment variables must be set for the Web API:

* DPM_JDBC_URL - The JDBC URL of the database
* DPM_JDBC_DRIVER - The driver to use. See Driver column for values.
* DPM_JDBC_USER - The database user name.
* DPM_JDBC_PASSWORD - The database user password.
* DPM_AUTO_SCHEMA_GEN (Options include `none`, `create-only`, `drop`, `create`, `create-drop`, `validate`, and `update` (default value))

Note: In a deployed environment, it is recommended to set the `MICRONAUT_ENVIRONMENTS` environment variable to include
the `prod` value so that the above environments variables are registered. Otherwise, in a local development environment,
please feel free to update datasource configuration either in the application.yml or application-dev.yml files 
`app/src/main/resources` directory.

The following describes the options for DPM_AUTO_SCHEMA_GEN environment variable in detail:

* *none** - No action will be performed.
* *create-only** - Database creation will be generated.
* *drop** - Database dropping will be generated.
* *create** - Database dropping will be generated followed by database creation.
* *create-drop** - Drop the schema and recreate it on SessionFactory startup. Additionally, drop the schema on SessionFactory shutdown.
* *validate** - Validate the database schema.
* *update** - Update the database schema.

The DPM_DATABASE_DEPENDENCY environment variable must be set when building the application to inject the correct driver.
Examples include `mysql:mysql-connector-java:8.0.31` and `org.postgresql:postgresql:42.4.2`.
Multiple drivers can be specified.
For example, `mysql:mysql-connector-java:8.0.31,com.google.cloud.sql:mysql-socket-factory-connector-j-8:1.7.2`.

### Initial User

Permissions Manager requires an initial super administrator for the purpose of logging in and adding other super admins
or regular users. To add an initial super admin, connect to the database which DDS Permissions Manager will connect to and execute the
following SQL statement where `$EMAIL` is the email address of the super admin:

```sql
INSERT INTO permissions_user (admin, email)
VALUES (true, '$EMAIL');
```

### Configuring Google as an auth provider

Set the following environment variables to enable Google as an auth provider:

* MICRONAUT_SECURITY_OAUTH2_CLIENTS_GOOGLE_CLIENT_ID - The id of oauth client.
* MICRONAUT_SECURITY_OAUTH2_CLIENTS_GOOGLE_CLIENT_SECRET - The secret of the oauth client.
* MICRONAUT_SECURITY_OAUTH2_CLIENTS_GOOGLE_OPENID_ISSUER - Set to "https://accounts.google.com"

### Configuring the Web API

The following environment variables should be set to configure the application:

* MICRONAUT_SECURITY_TOKEN_JWT_SIGNATURES_SECRET_GENERATOR_SECRET - Secret uses to sign JWTs.
* MICRONAUT_SECURITY_TOKEN_JWT_GENERATOR_REFRESH_TOKEN_SECRET - Secret for JWT renewal tokens.
* MICRONAUT_SECURITY_REDIRECT_LOGIN_SUCCESS - Typically the URL of the Web Application UI, e.g., https://dpm.my.domain.com
* MICRONAUT_SECURITY_REDIRECT_LOGIN_FAILURE - Typically the `failed-auth` URL of the Web Application UI, e.g., https://dpm.my.domain.com/failed-auth
* MICRONAUT_SECURITY_REDIRECT_LOGOUT - Typically the URL of the Web Application UI, e.g., https://dpm.my.domain.com
* DPM_WEBSOCKETS_BROADCAST_CHANGES - Whether the application should broadcast a message if a Topic or Application is updated or deleted. Default value is `false`.

The following environment variables can be used to set the validity of DDS Security Documents produced by the API:

* PERMISSIONS_MANAGER_APPLICATION_CLIENT_CERTIFICATES_TIME_EXPIRY - Days that certificates are valid.
* PERMISSIONS_MANAGER_APPLICATION_PERMISSIONS_FILE_TIME_EXPIRY - Days that permissions files are valid.
* PERMISSIONS_MANAGER_APPLICATION_PERMISSIONS_FILE_DOMAIN - The DDS domain in use.
* PERMISSIONS_MANAGER_APPLICATION_PASSPHRASE_LENGTH - The length of the passwords generated for applications.

See `app/src/main/resources/application.yml` for a complete list of configuration options.

Copyright 2023 DDS Permissions Manager Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
