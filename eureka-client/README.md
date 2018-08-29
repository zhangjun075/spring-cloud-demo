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
