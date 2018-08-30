# eureka-client
[eureka-client配置](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-eureka-client/src/main/java/org/springframework/cloud/netflix/eureka/EurekaClientConfigBean.java)
[eureka-client配置](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-eureka-client/src/main/java/org/springframework/cloud/netflix/eureka/EurekaInstanceConfigBean.java)
[eureka-github](https://github.com/Netflix/eureka/wiki/Configuring-Eureka)

# eureka使用注意点:eureka默认使用Ribbon做负载均衡
    
* 实际在使用springcloud-feign的过程中，如果你有多个服务，如果服务在处理的过程中有超时，这个时候，你会发现一个坑：就是另外一个服务也会收到请求。如果第一个服务只是处理的很慢，而这个服务没有做过幂等控制，那么就会有重复的请求过来。
在本地起两个节点的服务，在服务中增加sleep，让客户端错觉超时。
```$xslt
@RequestMapping(value = "hello",method = RequestMethod.GET)
    public String hello() {
        log.info("you are into this method.....hello.......");
        try {
            TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "hello world.";
    }
```
- 在客户端发器请求，发现两个服务端都会打印：
```$xslt
you are into this method.....hello.......
```

- 我们看feignclientsConfiguration中的代码发现：默认是不会retry的。但是为什么还会再发一次？
```$xslt
@Bean
	@ConditionalOnMissingBean
	public Retryer feignRetryer() {
		return Retryer.NEVER_RETRY;
	}

	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	public Feign.Builder feignBuilder(Retryer retryer) {
		return Feign.builder().retryer(retryer);
	}

	@Bean
	@ConditionalOnMissingBean(FeignLoggerFactory.class)
	public FeignLoggerFactory feignLoggerFactory() {
		return new DefaultFeignLoggerFactory(logger);
	}
```
- 通过日志发现，调用的是如下类：
```$xslt
at org.springframework.cloud.netflix.feign.ribbon.LoadBalancerFeignClient.execute(LoadBalancerFeignClient.java:63) ~[spring-cloud-netflix-core-1.4.5.RELEASE.jar:1.4.5.RELEASE]
	at feign.SynchronousMethodHandler.executeAndDecode(SynchronousMethodHandler.java:97) ~[feign-core-9.5.0.jar:na]
  
```
feign调用的ribbon的负载均衡。
```$xslt
AbstractLoadBalancerAwareClient.java类：
protected LoadBalancerCommand<T> buildLoadBalancerCommand(final S request, final IClientConfig config) {
		RequestSpecificRetryHandler handler = getRequestSpecificRetryHandler(request, config);
		LoadBalancerCommand.Builder<T> builder = LoadBalancerCommand.<T>builder()
				.withLoadBalancerContext(this)
				.withRetryHandler(handler)
				.withLoadBalancerURI(request.getUri());
		customizeLoadBalancerCommandBuilder(request, config, builder);
		return builder.build();
	}
```
- 在handler类里，注入的是RequestSpecificRetryHandler类，通过debug,这个类的retryNextServer=1。在本地cache的队列里，如果有多个server，那么会默认会重试下一个节点。
- 跟进getRequestSpecificRetryHandler这个方法,进入到ribbon这个package的FeignLoadBalancer类中：
```$xslt
@Override
	public RequestSpecificRetryHandler getRequestSpecificRetryHandler(
			RibbonRequest request, IClientConfig requestConfig) {
		if (this.clientConfig.get(CommonClientConfigKey.OkToRetryOnAllOperations,
				false)) {
			return new RequestSpecificRetryHandler(true, true, this.getRetryHandler(),
					requestConfig);
		}
		if (!request.toRequest().method().equals("GET")) {
			return new RequestSpecificRetryHandler(true, false, this.getRetryHandler(),
					requestConfig);
		}
		else {
			return new RequestSpecificRetryHandler(true, true, this.getRetryHandler(),
					requestConfig);
		}
	}
```
这里是实例化类RequestSpecificRetryHandler这个类。这个类我们看下他的构造函数：

```$xslt
public RequestSpecificRetryHandler(boolean okToRetryOnConnectErrors, boolean okToRetryOnAllErrors, RetryHandler baseRetryHandler, @Nullable IClientConfig requestConfig) {
        Preconditions.checkNotNull(baseRetryHandler);
        this.okToRetryOnConnectErrors = okToRetryOnConnectErrors;
        this.okToRetryOnAllErrors = okToRetryOnAllErrors;
        this.fallback = baseRetryHandler;
        if (requestConfig != null) {
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetries)) {
                retrySameServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetries); 
            }
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetriesNextServer)) {
                retryNextServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetriesNextServer); 
            } 
        }
    }
```
这里默认的okToRetryOnConnectErrors=true;okToRetryOnAllErrors=true。也就是说在链接错误的时候，会去重试；在所有error发生的时候回去重试。这里重试的handler是defaultLoadBalancerRetryHandler（我们稍后来看）。我们接着跟进去，发现这行代码：
```$xslt
retryNextServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetriesNextServer);
```
也就是说这个值是从requestConfig里面来的。那么requestConfig是什么？我们返回之前的代码可以看到，他是在LoadBalancerFeignClient类中的方法构造出来的：

```$xslt
IClientConfig getClientConfig(Request.Options options, String clientName) {
		IClientConfig requestConfig;
		if (options == DEFAULT_OPTIONS) {
			requestConfig = this.clientFactory.getClientConfig(clientName);
		} else {
			requestConfig = new FeignOptionsClientConfig(options);
		}
		return requestConfig;
	}
```
这里我们没有自己配置，所以采用了默认的，也就是说走到了options == DEFAULT_OPTIONS这个分支里。那么如果走else分支，我们是否可以修改这个变量的值，我们看下FeignOptionsClientConfig这个类：
```$xslt
static class FeignOptionsClientConfig extends DefaultClientConfigImpl {

		public FeignOptionsClientConfig(Request.Options options) {
			setProperty(CommonClientConfigKey.ConnectTimeout,
					options.connectTimeoutMillis());
			setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
		}

		@Override
		public void loadProperties(String clientName) {

		}

		@Override
		public void loadDefaultValues() {

		}

	}
```
这个构造函数顶多是重置了connectTimeout和readtimeout的值。Request.Options中也只有着两个值。问题到这里告一段落。我们继续看父类方法：DefaultClientConfigImpl.java
```$xslt
protected Object getProperty(String key) {
        if (enableDynamicProperties) {
            String dynamicValue = null;
            DynamicStringProperty dynamicProperty = dynamicProperties.get(key);
            if (dynamicProperty != null) {
                dynamicValue = dynamicProperty.get();
            }
            if (dynamicValue == null) {
                dynamicValue = DynamicProperty.getInstance(getConfigKey(key)).getString();
                if (dynamicValue == null) {
                    dynamicValue = DynamicProperty.getInstance(getDefaultPropName(key)).getString();
                }
            }
            if (dynamicValue != null) {
                return dynamicValue;
            }
        }
        return properties.get(key);
    }
```
这个方法是关键。获取properties中的变量。{server_name}.ribbon.{property}。大功告成。

在properties中添加：
```$xslt
ribbon:
      MaxAutoRetriesNextServer: -1
```
跟进代码，看到这个值已经改变，这个时候看了下代码，对于请求只会发生一次了。

- 对于使用feign的应用，如果设置超时时间，可以这么来做
```$xslt
@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients
public class EurekaClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaClientApplication.class,args);
    }

    @Bean
    Request.Options options() {
        return new Request.Options(1000*10,40*1000);
    }
}
```
重写options类。

# hystrix 

```$xslt
A service failure in the lower level of services can cause cascading failure all the way up to the user. 
When calls to a particular service is greater than circuitBreaker.requestVolumeThreshold (default: 20 requests) and failue percentage is greater than circuitBreaker.errorThresholdPercentage (default: >50%) in a rolling window defined by metrics.rollingStats.timeInMilliseconds (default: 10 seconds), 
the circuit opens and the call is not made. In cases of error and an open circuit a fallback can be provided by the developer.
```
* How to Include Hystrix
    * To include Hystrix in your project use the starter with group org.springframework.cloud and artifact id spring-cloud-starter-hystrix
    * you can see [here](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-javanica#configuration) for more deatils.
    


* Default fallback method should not have any parameters except extra one to get execution exception and shouldn't throw any exceptions. Below fallbacks listed in descending order of priority:
    - command fallback defined using fallbackMethod property of @HystrixCommand
    - command default fallback defined using defaultFallback property of @HystrixCommand
    - class default fallback defined using defaultFallback property of @DefaultProperties

* 对于hystrix究竟是使用thread和semaphore，官网解释如下：
    - thread:请求是在一个单独的线程中执行，当前请求受限与线程池中线程的数量
    - semaphore：请求在当前线程执行，但是当前请求数受限于信号量计数大小。
    
    对于使用线程池的，有一层额外的保护防止网络超时。所以需要配置超时的，可以采用线程池的方式。
    在你的请求量很大，通常情况下达到每秒上百甚至更多的情况下，单个线程的请求量已经过高，此种情况才使用信号量。这种典型仅仅应用于非网络请求。
    
* hystrix断路器说明
  我们看下目前代码中的配置：
  ```
  @HystrixCommand(commandProperties = {
        @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000"),
        @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold",value = "5"),
        @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds",value = "5000000"),
        @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage",value = "10"),
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
        log.info("into service......");
        test();
        return "hystrix";
    }
  ```
  - requestVolumeThreshold = 5,这里配置的是5，也就说时间窗口内最小的请求数达到5，如果是4，哪怕4个请求全部有问题，都不会出发断路器打开。有一点你要注意，你这里配置了fallback,且默认都fallback是开启的，在异常捕获的时候，也没有忽略任何异常，所以每次请求都会进入fallback方法，service方法也会进去。断路器没有打开。
  - 当你超过了5个请求，这个时候，你可以看到断路器开启了。此时fallback方法调用了，但是你进入不了service方法。sleepWindowInMilliseconds这个参数，如果设置了永久，那么你的断路器永远不会关闭。

* 我们来测试下，超过5个请求后，percentage生效的情况。在第6池开始抛异常，我们看下日志：
```
2018-08-30 15:35:21.079  INFO 47508 --- [nio-8086-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 18 ms
2018-08-30 15:35:21.103  INFO 47508 --- [nio-8086-exec-1] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:21.318  INFO 47508 --- [x-DemoService-1] com.brave.service.DemoService            : into service......
2018-08-30 15:35:21.318  INFO 47508 --- [x-DemoService-1] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:35:22.242  INFO 47508 --- [trap-executor-0] c.n.d.s.r.aws.ConfigClusterResolver      : Resolving eureka endpoints via configuration
2018-08-30 15:35:22.295  INFO 47508 --- [nio-8086-exec-3] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:22.297  INFO 47508 --- [x-DemoService-2] com.brave.service.DemoService            : into service......
2018-08-30 15:35:22.297  INFO 47508 --- [x-DemoService-2] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:35:23.184  INFO 47508 --- [nio-8086-exec-4] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:23.186  INFO 47508 --- [x-DemoService-3] com.brave.service.DemoService            : into service......
2018-08-30 15:35:23.186  INFO 47508 --- [x-DemoService-3] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:35:24.048  INFO 47508 --- [nio-8086-exec-5] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:24.049  INFO 47508 --- [x-DemoService-4] com.brave.service.DemoService            : into service......
2018-08-30 15:35:24.049  INFO 47508 --- [x-DemoService-4] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:35:24.863  INFO 47508 --- [nio-8086-exec-6] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:24.865  INFO 47508 --- [x-DemoService-5] com.brave.service.DemoService            : into service......
2018-08-30 15:35:24.865  INFO 47508 --- [x-DemoService-5] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:35:26.328  INFO 47508 --- [nio-8086-exec-7] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:26.329  INFO 47508 --- [x-DemoService-6] com.brave.service.DemoService            : into service......
2018-08-30 15:35:26.329  INFO 47508 --- [x-DemoService-6] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:35:26.329  INFO 47508 --- [x-DemoService-6] com.brave.service.DemoService            : throw an exception....
2018-08-30 15:35:27.787  INFO 47508 --- [nio-8086-exec-8] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:29.945  INFO 47508 --- [nio-8086-exec-9] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:32.246  INFO 47508 --- [trap-executor-0] c.n.d.s.r.aws.ConfigClusterResolver      : Resolving eureka endpoints via configuration
2018-08-30 15:35:34.064  INFO 47508 --- [io-8086-exec-10] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:35.770  INFO 47508 --- [nio-8086-exec-2] com.brave.web.Demo                       : into controller...
2018-08-30 15:35:36.971  INFO 47508 --- [nio-8086-exec-1] com.brave.web.Demo                       : into controller...
```
我们可以看到，在第6哥请求的时候抛了一个异常。第7个请求无法进入，因为断路器开启了。我们设置第percentage是10%。符合预期。如果把这个值放大，会是什么结果？继续看下：

```
@Service
@DefaultProperties(defaultFallback = "fallback")
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
        log.info("into service......");
        test();
        return "hystrix";
    }

    public String fallback() {
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

}
```
   这里，把percentage调整到了50，我们看下日志：
   
```
2018-08-30 15:39:30.690  INFO 47514 --- [nio-8086-exec-1] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:30.892  INFO 47514 --- [x-DemoService-1] com.brave.service.DemoService            : into service......
2018-08-30 15:39:30.892  INFO 47514 --- [x-DemoService-1] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:31.235  INFO 47514 --- [nio-8086-exec-3] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:31.237  INFO 47514 --- [x-DemoService-2] com.brave.service.DemoService            : into service......
2018-08-30 15:39:31.237  INFO 47514 --- [x-DemoService-2] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:31.969  INFO 47514 --- [nio-8086-exec-4] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:31.971  INFO 47514 --- [x-DemoService-3] com.brave.service.DemoService            : into service......
2018-08-30 15:39:31.971  INFO 47514 --- [x-DemoService-3] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:32.507  INFO 47514 --- [trap-executor-0] c.n.d.s.r.aws.ConfigClusterResolver      : Resolving eureka endpoints via configuration
2018-08-30 15:39:32.849  INFO 47514 --- [nio-8086-exec-5] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:32.851  INFO 47514 --- [x-DemoService-4] com.brave.service.DemoService            : into service......
2018-08-30 15:39:32.851  INFO 47514 --- [x-DemoService-4] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:33.947  INFO 47514 --- [nio-8086-exec-6] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:33.949  INFO 47514 --- [x-DemoService-5] com.brave.service.DemoService            : into service......
2018-08-30 15:39:33.949  INFO 47514 --- [x-DemoService-5] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:35.329  INFO 47514 --- [nio-8086-exec-7] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:35.330  INFO 47514 --- [x-DemoService-6] com.brave.service.DemoService            : into service......
2018-08-30 15:39:35.331  INFO 47514 --- [x-DemoService-6] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:35.331  INFO 47514 --- [x-DemoService-6] com.brave.service.DemoService            : throw an exception....
2018-08-30 15:39:37.292  INFO 47514 --- [nio-8086-exec-8] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:37.293  INFO 47514 --- [x-DemoService-7] com.brave.service.DemoService            : into service......
2018-08-30 15:39:37.293  INFO 47514 --- [x-DemoService-7] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:37.293  INFO 47514 --- [x-DemoService-7] com.brave.service.DemoService            : throw an exception....
2018-08-30 15:39:40.416  INFO 47514 --- [nio-8086-exec-9] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:40.417  INFO 47514 --- [x-DemoService-8] com.brave.service.DemoService            : into service......
2018-08-30 15:39:40.418  INFO 47514 --- [x-DemoService-8] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:39:40.418  INFO 47514 --- [x-DemoService-8] com.brave.service.DemoService            : throw an exception....
2018-08-30 15:39:41.848  INFO 47514 --- [io-8086-exec-10] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:42.512  INFO 47514 --- [trap-executor-0] c.n.d.s.r.aws.ConfigClusterResolver      : Resolving eureka endpoints via configuration
2018-08-30 15:39:47.632  INFO 47514 --- [nio-8086-exec-2] com.brave.web.Demo                       : into controller...
2018-08-30 15:39:48.811  INFO 47514 --- [nio-8086-exec-1] com.brave.web.Demo                       : into controller...
```
我们看到从第六次开始抛异常，一直到第9次，然后就没有第10次了。>=50%了，断路器打开了。符合预期。
以上大家可以参照我测试第过程，便于大家更清楚的知道断路器的原理。至于scrollwindow和bucket，大家继续再按此方法测试吧，本地模拟不太方便。

* 我们继续来看下execution.isolation.thread.timeoutInMilliseconds这个参数，我这里设置成5000，也就是说该方法5秒超时，在方法里,sleep了6秒，执行结果看下
```
2018-08-30 15:53:22.137  INFO 47619 --- [nio-8086-exec-2] com.brave.web.Demo                       : into controller...
2018-08-30 15:53:27.337  INFO 47619 --- [x-DemoService-1] com.brave.service.DemoService            : into service......
2018-08-30 15:53:27.337  INFO 47619 --- [x-DemoService-1] com.brave.service.DemoService            : into test method,not throw exception...
2018-08-30 15:53:27.347  INFO 47619 --- [ HystrixTimer-1] com.brave.service.DemoService            : into fallback....
```
  * 方法超时6秒，过了6秒继续执行，由于超时了，fallback也执行了。 

网上很多杂七杂八的说明，其实都是翻译的官网，很多人看了一知半解，希望这个对大家了解参数有帮助。
