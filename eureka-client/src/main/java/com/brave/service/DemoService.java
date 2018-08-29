package com.brave.service;

import com.brave.client.DemoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DemoService {

    @Autowired DemoClient demoClient;

    public Optional<String> demo() {
        return Optional.ofNullable(demoClient.callDemo());
    }
}
