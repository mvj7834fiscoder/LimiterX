package com.learn.demo.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ClientRegistryService {

    private final Environment env;

    public ClientRegistryService(Environment env) {
        this.env = env;
    }

    // 1. Verify if the incoming client secret matches what we have on file
    public boolean isValidClient(String clientId, String clientSecret) {
        String configuredSecret = env.getProperty("trusted.client." + clientId + ".secret");
        return configuredSecret != null && configuredSecret.equals(clientSecret);
    }

}
