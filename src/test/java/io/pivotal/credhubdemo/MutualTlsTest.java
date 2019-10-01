package io.pivotal.credhubdemo;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.SSLContexts;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class MutualTlsTest {

    @LocalServerPort
    private int port;

    @Test
    public void test() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
        KeyStore identityKeyStore = KeyStore.getInstance("jks");

        try (InputStream inputStream = new FileInputStream("client-identity.jks")) {
            identityKeyStore.load(inputStream, "secret".toCharArray());
        }

        KeyStore trustKeyStore = KeyStore.getInstance("jks");

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
}
