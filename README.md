# Build

You can use [sbt-extras](https://github.com/dwijnand/sbt-extras) to easily build this project:

`sbt test:compile`

This will download all dependencies, including scala, and compile this project.

# Tests

There are some integration tests included in this project. To run them you'll need an instance of [httpbin](https://httpbin.org) running.

You can use a local instance using:

```
docker pull artifacts.toradex.com/ota-docker-dev-frankfurt/wait-kafka:0.0.1
```

Alternatively, you can edit `src/test/resources/application.conf` to use `http://httpbin.org:80` instead of `http://127.0.0.1:9002`.


You can then run the tests with:

```
sbt test
```

# Build docker containers

Docker containers can be built with sbt:

`sbt docker:publishLocal`

# Creating Required Certificates

This gateway needs a jvm keystore containing the certificates used to validate TLS client certificates and to provide a valid TLS certificate to the client. A valid copy of this keystore was already created and provided. The following documents how this keystore was created and you should not need to run any of these steps unless you need to change the certificates and/or keys used by api-gateway.

This keystore needs the following items:

1. The old auto provisioning certificate, included in the old credentials.zip. This certificate is now expired, and we need the exact certificate included in this keystore so we can validate the client is using this certificate, but ignore the expiration date.

2. The Server CA certificate chain. This is commonly named `server_ca.pem`, and is the certificate chain used to validate the new auto provisioning certificate included in the old credentials.zip, as well as the per-device certificates used after provisioning.

3. The server certificate and keys used for server side TLS.

To create this keystore:

1. When asked for import password for p12 files, press enter
2. When asked for PEM pass phrase use 0000000, or choose a password and use that password in `src/main/resources/application.conf`
3. It's important the p12 files contain the same password as the final jks keystore
4. The `../certs` directory bellow refers to the `certs/` dir in the sorenson deployment.

```
unzip -p ../original_credentials.zip autoprov_credentials.p12 > autoprov.original.nopass.p12

unzip -p ../original_credentials.zip server_ca.pem > server_ca.pem

openssl pkcs12 -in autoprov.original.nopass.p12 -clcerts -nokeys -out autoprov.cert.original.pem

openssl pkcs12 -in autoprov.original.nopass.p12 -nocerts -out autoprov.keys.original.pkey

openssl pkcs12 -export -in autoprov.cert.original.pem -name autprov-cert-old -inkey autoprov.keys.original.pkey -out autoprov.original.with-pass.p12

keytool -v -importkeystore -srckeystore autoprov.original.with-pass.p12 -srcstoretype PKCS12 -destkeystore svrs-sm.jks -deststoretype JKS

keytool -importcert -file server_ca.pem -alias server-ca -keystore svrs-sm.jks

openssl pkcs12 -export -in ../certs/server.crt -inkey ../certs/server.key -chain -CAfile ../certs/server_ca.pem -name "ota.svrs.cc" -out server.p12

keytool -v -importkeystore -srckeystore server.p12 -srcstoretype PKCS12 -destkeystore svrs-sm.jks -deststoretype JKS
```

# Changes Required to the docker-compose deployment

The `deploy/` folder includes the new `docker-compose.yml` file with this gateway included, and some changes required to the nginx device gateway deployment.

The nginx gateway as changed to forward the TCP connection directly to this gateway, instead of doing ssl termination in nginx. This is required because there are other two hosts handled by nginx that are not handled by this gateway.
