# eureka-server
[github:eureka](https://github.com/Netflix/eureka/wiki/Eureka-at-a-glance)

## peer awareness mode

* 引入jar包
```$xslt
<dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure-processor</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-eureka-server</artifactId>
      <version>RELEASE</version>
    </dependency>
```

* 在启动类中加入启动注解
```$xslt
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class,args);
    }
}
```

* eureka 服务端采用两两注册端模式,即a->b,c;b->a,c;c->a,b;
```$xslt
server:
  port: 8083
  host: localhost
spring:
  application:
    name: eureka-server
  profiles: peer1
eureka:
  instance:
    hostname: eureka-server1
    prefer-ip-address: true
    instance-id: ${spring.cloud.client.ipAddress}:${server.port}
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url: http://${server.host}:8084/eureka/,http://${server.host}:8085/eureka/
---
server:
  port: 8084
  host: localhost
spring:
  application:
    name: eureka-server
  profiles: peer2
eureka:
  instance:
    hostname: eureka-server2
    prefer-ip-address: true
    instance-id: ${spring.cloud.client.ipAddress}:${server.port}
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url: http://${server.host}:8083/eureka/,http://${server.host}:8085/eureka/

---
server:
  port: 8085
  host: localhost
spring:
  application:
    name: eureka-server
  profiles: peer3
eureka:
  instance:
    hostname: eureka-server3
    prefer-ip-address: true
    instance-id: ${spring.cloud.client.ipAddress}:${server.port}
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url: http://${server.host}:8083/eureka/,http://${server.host}:8084/eureka/


```
* 启动的时候，用配置来启动java -jar -Dspring.profiles.active=peerx xxxx.jar

* 注意，目前比较稳定的版本如下，springboot2.0版本会出现很多莫名其妙错误。
```$xslt
<parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>1.5.13.RELEASE</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Edgware.SR4</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

* monitoring
Eureka uses servo/spectator to track a lot information in both the client and the server for performance, monitoring and alerting.The data is typically available in the JMX registry and can be exported to Amazon Cloud Watch.

- 官网提到：why is it so slow to register a service
```$xslt
Being an instance also involves a periodic heartbeat to the registry (via the client’s serviceUrl) with default duration 30 seconds. A service is not available for discovery by clients until the instance, the server and the client all have the same metadata in their local cache (so it could take 3 heartbeats). You can change the period using eureka.instance.leaseRenewalIntervalInSeconds and this will speed up the process of getting clients connected to other services. In production it’s probably better to stick with the default because there are some computations internally in the server that make assumptions about the lease renewal period.
```


