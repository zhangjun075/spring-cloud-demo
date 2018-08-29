package com.brave;

import com.brave.client.DemoClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Java6Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DemoClientTest {

    @Autowired DemoClient demoClient;

    @Test
    public void testHello() {
        assertThat(demoClient.callDemo()).contains("hello world");
    }

}
