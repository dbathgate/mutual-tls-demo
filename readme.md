# Overview
* This example shows how to setup a mutual TLS connection between a server and client
* Server is setup with a key pair that is trusted by the Client
* Client is setup with a key pair that is trusted by the Server
* Server will not accept request from a Client that is not trusted
* Client will not call a Server that is not trusted

### Setup Server identity
```bash
# Create Server public/private PEM
openssl req -x509 -new -newkey rsa:3072 -nodes -subj '/C=US/ST=Rhode Island/L=Woonsocket/O=Test/CN=localhost' -keyout server-private-key.pem -out server-public-cert.pem -days 7300

# Convert Server public/private PEM to PKCS12
openssl pkcs12 -export -out server-key.pkcs12 -inkey server-private-key.pem -in server-public-cert.pem -password pass:secret

# Import Server PCKS12 into a JKS file
keytool -importkeystore -srckeystore server-key.pkcs12 -srcstoretype pkcs12 -destkeystore server-identity.jks -storepass secret -keypass secret -srcstorepass secret
```

### Setup Client identity

```bash
# Create Client public/private PEM
openssl req -x509 -new -newkey rsa:3072 -nodes -subj '/C=US/ST=Rhode Island/L=Woonsocket/O=Test/CN=localhost' -keyout client-private-key.pem -out client-public-cert.pem -days 7300

# Convert Client public/private PEM to PKCS12
openssl pkcs12 -export -out client-key.pkcs12 -inkey client-private-key.pem -in client-public-cert.pem -name test -password pass:secret

# Import Client PCKS12 into a JKS file
keytool -importkeystore -srckeystore client-key.pkcs12 -srcstoretype pkcs12 -destkeystore client-identity.jks -alias test -storepass secret -keypass secret -srcstorepass secret
```

### Create Server/Client trust stores
```bash
# Create Client truststore from server public cert
keytool -keystore client-truststore.jks -importcert -file server-public-cert.pem -alias test -storepass secret -noprompt

# Create Server truststore from client public cert
keytool -keystore server-truststore.jks -importcert -file client-public-cert.pem -alias test -storepass secret -noprompt
```

## Testing with Curl
```bash
mvn clean spring-boot:run
curl https://localhost:8443/cred --key client-private-key.pem  --cert client-public-cert.pem -k
```

## Example 1: Using JKS files

* This Example loads keys into a Java Key Store (JKS) file that is protected by a secret
* Using JKS requires creating a file using the `keytool` CLI
* The JSK file is loaded into a `KeyStore` object for use of configuring the `SSLContext`

```java
KeyStore identityKeyStore = KeyStore.getInstance("jks");
KeyStore trustKeyStore = KeyStore.getInstance("jks");

try (InputStream inputStream = new FileInputStream("client-identity.jks")) {
    identityKeyStore.load(inputStream, "secret".toCharArray());
}

trustKeyStore.load(null, "secret".toCharArray());

try (InputStream inputStream = new FileInputStream("client-truststore.jks")) {
    trustKeyStore.load(inputStream, "secret".toCharArray());
}

SSLContext sslContext = SSLContexts.custom()
    .loadKeyMaterial(identityKeyStore, "secret".toCharArray(), (Map<String, PrivateKeyDetails> aliases, Socket socket) -> {
        return "test";
    })
    .loadTrustMaterial(trustKeyStore, null)
    .build();

SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
RestTemplate restTemplate = new RestTemplate(requestFactory);

ResponseEntity<String> response = restTemplate.getForEntity("https://localhost:" + port + "/cred", String.class);
```

### Example 1: Using PEM format
* This example bypasses the need for JKS files by loading the keys directly from their original format
* An empty `KeyStore` object initialized with a secret and the key is read into the `KeyStore` instance

```java
KeyFactory keyFactory = KeyFactory.getInstance("RSA");
CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

KeyStore identityKeyStore = KeyStore.getInstance("jks");
KeyStore trustKeyStore = KeyStore.getInstance("jks");

// need to initialize Keystores with any secret
trustKeyStore.load(null, "secret".toCharArray());
identityKeyStore.load(null, "secret".toCharArray());

try (InputStream publicKeyInput = new FileInputStream("client-public-cert.pem")) {

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    Files.copy(Paths.get("client-private-key.pem"), byteArrayOutputStream);
    String privateKeyString = new String(byteArrayOutputStream.toByteArray(), "UTF-8");

    privateKeyString = privateKeyString.replace("-----BEGIN PRIVATE KEY-----\n", "");
    privateKeyString = privateKeyString.replace("-----END PRIVATE KEY-----", "");
    privateKeyString = privateKeyString.replace("\n", "");

    byte[] keyBytes = Base64.getDecoder().decode(privateKeyString);

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    PrivateKey privateKey = keyFactory.generatePrivate(spec);
    X509Certificate cert = (X509Certificate)certFactory.generateCertificate(publicKeyInput);
    identityKeyStore.setKeyEntry("test", privateKey, "secret".toCharArray(), new X509Certificate[] {cert});
}

try (InputStream inputStream = new FileInputStream("server-public-cert.pem")) {
    X509Certificate cert = (X509Certificate)certFactory.generateCertificate(inputStream);
    trustKeyStore.setCertificateEntry("test", cert);
}

SSLContext sslContext = SSLContexts.custom()
    .loadKeyMaterial(identityKeyStore, "secret".toCharArray(), (Map<String, PrivateKeyDetails> aliases, Socket socket) -> {
        return "test";
    })
    .loadTrustMaterial(trustKeyStore, null)
    .build();

SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
RestTemplate restTemplate = new RestTemplate(requestFactory);

ResponseEntity<String> response = restTemplate.getForEntity("https://localhost:" + port + "/cred", String.class);
```

## Run the tests
```bash
mvn clean test
```