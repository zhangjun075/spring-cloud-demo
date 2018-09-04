package com.brave.service;

import com.brave.client.DemoClient;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@DefaultProperties(defaultFallback = "fallback",commandProperties = {@HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "3000")})
@Slf4j
public class DemoService {

    @Autowired DemoClient demoClient;

    public static AtomicInteger number = new AtomicInteger(0);

    public Optional<String> demo() {
        return Optional.ofNullable(demoClient.callDemo());
    }

    @HystrixCommand(commandProperties = {
        @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000"),
        @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold",value = "5"),
        @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds",value = "5000000"),
        @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage",value = "50"),
        @HystrixProperty(name = "circuitBreaker.forceOpen",value = "false"),
        @HystrixProperty(name = "fallback.enabled",value = "true")
    },
        threadPoolProperties = {
            @HystrixProperty(name = "coreSize", value = "30"),
            @HystrixProperty(name = "maxQueueSize", value = "101"),
            @HystrixProperty(name = "keepAliveTimeMinutes", value = "2"),
            @HystrixProperty(name = "queueSizeRejectionThreshold", value = "15"),
            @HystrixProperty(name = "metrics.rollingStats.numBuckets", value = "10"),
            @HystrixProperty(name = "metrics.rollingStats.timeInMilliseconds", value = "10000")
        })
    public String hystrixTest() {
        try {
            TimeUnit.SECONDS.sleep(6000);//为了测试execution.isolation.thread.timeoutInMilliseconds参数
        } catch (InterruptedException e) {
//            e.printStackTrace();
        }
        log.info("into service......");
        test();
        return "hystrix";
    }

    public String fallback() {
        log.info("into fallback....");
        return "fallback";
    }

    public void test() {
        int num = number.addAndGet(1);
        log.info("into test method,not throw exception...");
        if(num>5) {
            log.info("throw an exception....");
            throw new NullPointerException();
        }
    }

    ///////////////////测试默认的超时//////
    @HystrixCommand
    public String defaultTimeoutTest() {
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {

        }
        return "default timeout...";
    }

}
