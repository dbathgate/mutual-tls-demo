
### Create Server public/private PEM
```bash
openssl req -x509 -new -newkey rsa:3072 -nodes -subj '/C=US/ST=Rhode Island/L=Woonsocket/O=Test/CN=localhost' -keyout server-private-key.pem -out server-public-cert.pem -days 7300
```

### Convert Server public/private PEM to PKCS12
```bash
openssl pkcs12 -export -out server-key.pkcs12 -inkey server-private-key.pem -in server-public-cert.pem -password pass:secret
```

### Import Server PCKS12 into a JKS file
```bash
keytool -importkeystore -srckeystore server-key.pkcs12 -srcstoretype pkcs12 -destkeystore server-identity.jks -storepass secret -keypass secret -srcstorepass secret
```

### Create Client truststore from server public cert
```bash
keytool -keystore client-truststore.jks -importcert -file server-public-cert.pem -alias test -storepass secret -noprompt
```

### Create Client public/private PEM
```bash
openssl req -x509 -new -newkey rsa:3072 -nodes -subj '/C=US/ST=Rhode Island/L=Woonsocket/O=Test/CN=localhost' -keyout client-private-key.pem -out client-public-cert.pem -days 7300
```

### Convert Client public/private PEM to PKCS12
```bash
openssl pkcs12 -export -out client-key.pkcs12 -inkey client-private-key.pem -in client-public-cert.pem -name test -password pass:secret
```

### Import Client PCKS12 into a JKS file
```bash
keytool -importkeystore -srckeystore client-key.pkcs12 -srcstoretype pkcs12 -destkeystore client-identity.jks -alias test -storepass secret -keypass secret -srcstorepass secret
```

### Create Server truststore from client public cert
```bash
keytool -keystore server-truststore.jks -importcert -file client-public-cert.pem -alias test -storepass secret -noprompt
```

### Convert PEM Private Key to PCKS8 DER key
```bash
openssl pkcs8 -topk8 -inform PEM -outform DER -in client-private-key.pem -out client-private-key.der -nocrypt
```

```bash
curl https://localhost:8443/cred --key client-private-key.pem  --cert client-public-cert.pem -k
```


```bash
openssl x509 -inform der -in digiroot.cer -out digiroot.pem
```