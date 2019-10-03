package io.pivotal.credhubdemo;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.SSLContexts;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MutualTlsTest {

    @LocalServerPort
    private int port;

    @Test
    public void testWithJks() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
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

        assertEquals("secret", response.getBody());
    }

    @Test
    public void testPem() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException, InvalidKeySpecException {
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

        assertEquals("secret", response.getBody());
    }
}
