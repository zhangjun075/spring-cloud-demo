package com.brave.web;

import com.brave.service.DemoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@Slf4j
public class Demo {


    @Autowired DemoService demoService;

    @RequestMapping(value = "/demo",method = RequestMethod.GET)
    public String demo() {
        return demoService.demo().orElse("nothing");
    }


    @RequestMapping(value = "/hys",method = RequestMethod.GET)
    public String hystrix() {
        log.info("into controller...");
        return demoService.hystrixTest();
    }

    @RequestMapping(value = "/timeout",method = RequestMethod.GET)
    public String defaultTimeout() {
        return Optional.ofNullable(demoService.defaultTimeoutTest()).orElse("default time out controller...");
    }
}
