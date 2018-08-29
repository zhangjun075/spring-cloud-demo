package com.brave.client;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient("eureka-client2")
public interface DemoClient {

    @RequestMapping(value = "/demo/hello",method = RequestMethod.GET)
    public String callDemo();

}
