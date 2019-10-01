package io.pivotal.credhubdemo.controller;

import org.apache.http.ssl.SSLContexts;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cred")
public class CredhubController {

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> getCred() {
        return ResponseEntity.ok("secret");
    }
}
