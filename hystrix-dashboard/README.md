# hystrix-dashboard & turbine
对于Hystrix-dashboard，主要是展示的hystrix.stream这个endpoint暴露出来的信息。所以，对于springboot应用，需要添加spring-boot-starter-actuator。

## hystrix-dashboard的使用
一般对于单个应用来说，hystrix-dashboard收集来应用的hystrix.stream的信息。而我们的应用一般都是集群部署，所以对于单个应用的Hystrix监控，可以直接用hystrix-dashboard即可
* 引入spring-cloud-starter-hystrix-dashboard的Jar包
* 在启动类中加入"@EnableHystrixDashboard"即可
* 访问Http://xxx:port/hystrix.stream

## turbine使用
turbine.stream会聚合所有hystrix.stream的数据信息。所以在集群的时候，可以用Turbine来监控集群中hystrix的metrics信息。

官网描述，turbine会默认的在eureka上寻找暴露出来的hystrix.stream的实体应用。

The configuration key turbine.appConfig is a list of eureka serviceIds that turbine will use to lookup instances。
turbine.appconfig这个配置存储的就是turbine在eureka上寻找的服务id的列表。一般我们会做如下配置：
```$xslt
turbine:
  aggregator:
    clusterConfig: CUSTOMERS
  appConfig: customers
```
这种配置，你需要在turbine.stream的页面加入cluster=CUSTOMERS的后缀，显示对于customers应用的Hystrix.stream的metric信息。如果不想麻烦，直接配置如下：
```$xslt
turbine:
  appConfig: customers,stores
  clusterNameExpression: "'default'"
```

## 例子
* 启动类
```$xslt
@EnableEurekaClient
@EnableHystrixDashboard
@SpringBootApplication
@EnableTurbine
public class HystrixDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(HystrixDashboardApplication.class,args);
    }
}
```

* 在配置文件中加入：
```$xslt
server:
  port: 9999
  host: localhost
spring:
  application:
    name: hystrix-dashboard
turbine:
#  aggregator:
#    cluster-config: EUREKA-CLIENT
  app-config: eureka-client
  clusterNameExpression: "'default'"
eureka:
  instance:
    hostname: eureka-client
    prefer-ip-address: true
    instance-id: ${spring.cloud.client.ipAddress}:${server.port}
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://${server.host}:8083/eureka/,http://${server.host}:8084/eureka/,http://${server.host}:8085/eureka/
    registry-fetch-interval-seconds: 30
    eureka-service-url-poll-interval-seconds: 10

```

* 请求的时候:http://localhost:9999/hystrix.stream,在Url中添加：http://localhost:9999/turbine.stream即可
