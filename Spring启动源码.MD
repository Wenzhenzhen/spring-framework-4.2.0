## Spring 启动源码（一）
### 1. 观察者模式——事件机制的底层原理
所涉及到的角色如下：
* 抽象主题（Subject）：提供接口，可以增加和剔除观察者对象。一般用抽象类或者接口实现。
* 抽象观察者（Observer）：提供接口，在得到主题的通知时更新自己。一般用抽象类或者接口实现。
* 具体主题（ConcreteSubject）：将有关状态存入具体观察者，在具体主题的内部状态发生变化时，给所有注册过的观察者发出通知。一般是具体子类实现。
* 具体观察者（ConcreteObserver）：存储与主题的状态自恰的状态。具体观察者角色实现抽象观察者角色所要求的更新接口，以便使本身的状态与主题的状态 像协调。如果需要，具体观察者角色可以保持一个指向具体主题对象的引用
设计模式关系图：![观察者模式](./Spring中设计模式类图/观察者模式.png)
### 2. JDK实现，java.util包下，提供了一个Observable类以及一个Observer接口，构成Java语言对观察者模式的支持。
Observable抽象主题用普通类实现，Observer 抽象观察者，接口实现
### 3. JDK中的事件
JDK 1.0及更早版本的事件模型基于职责链模式，但是这种模型不适用于复杂的系统，因此在JDK 1.1及以后的各个版本中，事件处理模型采用基于观察者模式的委派事件模型(DelegationEvent Model, DEM)，即一个Java组件所引发的事件并不由引发事件的对象自己来负责处理，而是委派给独立的事件处理对象负责。这并不是说事件模型是基于Observer和Observable的，事件模型与Observer和Observable没有任何关系，Observer和Observable只是观察者模式的一种实现而已。
java中的事件机制的参与者有3种角色
* Event Source：事件源，发起事件的主体。
* Event Object：事件状态对象，传递的信息载体，就好比Watcher的update方法的参数，可以是事件源本身，一般作为参数存在于listerner 的方法之中。
* Event Listener：事件监听器，当它监听到event object产生的时候，它就调用相应的方法，进行处理。
* 其实还有个东西比较重要：事件环境，在这个环境中，可以添加事件监听器，可以产生事件，可以触发事件监听器。
* 限于篇幅，具体案例实现就不讲了，大家可以去spring-boot-test获取，在com.lee.jdkevent下，里面注释写的很详细了，大家可以好好看看。
### 4.Spring的事件机制
spring的事件机制也是从java的事件机制拓展而来。
* ApplicationEvent：Spring中所有的事件父接口，继承自java的EventObject
![ApplicationEvent](./Spring事件机制的类图/ApplicationEvent-Spring事件的父接口.png)
ApplicationListener：spring中所有的事件监听器父接口，继承自java的EventListener
```
package org.springframework.context;
 import java.util.EventListener;
 //类似jdk里的Observer
 public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {
 	//类似jdk Observer中的update方法。
 	void onApplicationEvent(E event);
 }
 ```
 ### 5.总结
  #### 5.1观察者模式的优缺点：
 ######优点：
1. 主题与观察者建立一个抽象的耦合而不是紧密的耦合，降低了耦合度；主题只需要维护一个抽象观察者的集合，无需了解具体观察者，使得可以有各种各样不同的观察者实现。
2. 支持广播通信，主题会向所有已注册的观察者对象发送通知，简化了一对多系统设计的难度。
3. 符合“开闭原则”，增加新的具体观察者无须修改原有代码，可拓展性高。
###### 缺点：
1. 如果主题有很多直接或者间接观察者，那么全部通知到会很耗时。
2. 主题与观察者之间如果存在循环依赖，可能导致系统崩溃。
 #### 5.2事件机制
jdk事件实现是基于观察者模式，而spring事件又是在jdk事件的基础上进行了拓展。主要有四个角色：
* 事件源：触发事件的主体，比如jdk事件案例中的UserService、spring事件案例中的MessageService、SpringBoot中的SpringApplication。
* 事件：事件本身，指的是EventObject中的source，具体可以是任何数据（包括事件源），用来传递数据，比如jdk事件案例中MessageEvent、spring事件案例中的MessageEvent。
* 事件监听器：当事件发生时，负责对事件的处理，比如jdk事件案例中MessageListener、spring事件案例中的MessageListener。
* 事件环境：整个事件所处的上下文，对整个事件提供支持，类似web上下文；比如jdk事件案例中的UserService、spring事件案例中的ApplicationContext

----
### Spring IOC 之深入分析 BeanPostProcessor
接口定义：
```java
public interface BeanPostProcessor {
      // 初始化前的后期处理
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException;
    // 初始化后的后期处理
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException;
}
```
Spring 初始化bean（加载注册完BeanDefinition之后）
AbstractAutoWireCapableBeanFactory#initializeBean():
在此方法中调用了BeanPostProcessor#postProcessBeforeInitialization()
初始化方法（InitializingBean#afterPropertie()和init-method指定的方法）
BeanPostProcessor#postProcessAfterInitialization()  ---AOP代理在这里完成

容器中的BeanPostProcessor实例全部保存在AbstractBeanFactory#beanPostProcessors，存在list中；
beanPostProcessors中的BeanPostProcessor对该BeanFactory中的全部bean实例适用
* beanFactory容器没有将我们自定义的 BeanPostProcessor加入list中，必须手动调用addBeanPostProcessor()
 将定义的 BeanPostProcessor 注册到相应的 BeanFactory 中才能生效。
* ApplicationContext会自动检测并完成注册，因此在AbstractApplicationContext#refresh()方法中调用了
registerBeanPostProcessors(registerBeanPostProcessors)方法

#### BeanPostProcessor分析
1. BeanPostProcessor的作用域是容器级别的，它只和所在的容器相关，当 BeanPostProcessor 完成注册后，它会应用于所有跟它在同一个容器内的 bean。
2. BeanFactory 和 ApplicationContext 对 BeanPostProcessor 的处理不同，ApplicationContext 会自动检测所有实现了 BeanPostProcessor 接口的 bean，并完成注册，但是使用 BeanFactory 容器时则需要手动调用 addBeanPostProcessor() 完成注册
3. ApplicationContext 的 BeanPostProcessor 支持 Ordered，而 BeanFactory 的 BeanPostProcessor 是不支持的，原因在于ApplicationContext 会对 BeanPostProcessor 进行 Ordered 检测并完成排序，而 BeanFactory 中的 BeanPostProcessor 只跟注册的顺序有关。

----

### Spring IOC 之深入分析 BeanFactoryPostProcessor
接口定义:
```java
public interface BeanFactoryPostProcessor {
	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;
}
```
1. postProcessBeanFactory方法的作用：
在 standard initialization（实在是不知道这个怎么翻译：标准初始化？） 之后（已经就是已经完成了 BeanDefinition 的加载）对 bean factory 容器进行修改。其中参数 beanFactory 应该就是已经完成了 standard initialization 的 BeanFactory。
2. 方法的调用时机：所有的 BeanDefinition 已经完成了加载(即加载至 BeanFactory 中)，但是还没有完成初始化。
```
总结：
postProcessBeanFactory() 工作于 BeanDefinition 加载完成之后，Bean 实例化之前，其主要作用是对加载 BeanDefinition 进行修改。
有一点需要需要注意的是在 postProcessBeanFactory() 中千万不能进行 Bean 的实例化工作，因为这样会导致 bean 过早实例化，
会产生严重后果，我们始终需要注意的是 BeanFactoryPostProcessor 是与 BeanDefinition 打交道的，如果想要与 Bean 打交道，请使用 BeanPostProcessor。
```

一般情况下我们不会自定义BeanFactoryPostProcessor,Spring为我们提供了几个非常有用的BeanFactoryPostProcessor：
* PropertyPlaceholderConfigurer 允许我们在 XML 配置文件中使用占位符并将这些占位符所代表的资源单独配置到简单的 properties 文件中来加载
* PropertyOverrideConfigurer 则允许我们使用占位符来明确表明bean 定义中的 property 与 properties 文件中的各配置项之间的对应关系，这两个类在我们大型项目中有非常重要的作用，后续两篇文章将对其进行详细说明分析。
* Mybatis中对BeanFactoryPostProcessor的使用示例：MapperScannerConfigurer
```
总结：
对于 ApplicationContext 来说，使用 BeanFactoryPostProcessor 非常方便，
因为他会自动识别配置文件中的 BeanFactoryPostProcessor 并且完成注册和调用，
我们只需要简单的配置声明即可。
而对于 BeanFactory 容器来说则不行，他和 BeanPostProcessor 一样需要容器主动去进行注册调用，
方法如下：
BeanFactoryPostProcessor_1 beanFactoryPostProcessor1 = new BeanFactoryPostProcessor_1();
beanFactoryPostProcessor1.postProcessBeanFactory(factory);
```



### Spring IOC各个组件
文章来源：[深入理解Spring IOC](http://cmsblogs.com/?p=2652)
各个组件：以下五个体系可以说是 Spring IoC 中最核心的部分
1. Resource体系：Resource，对资源的抽象，它的每一个实现类都代表了一种资源的访问策略，如ClasspathResource 、 URLResource ，FileSystemResource 等。
    Resource资源的加载ResourceLoader,资源加载的统一抽象。
    * Spring 提供了 Resource 和 ResourceLoader 来统一抽象整个资源及其定位。使得资源与资源的定位有了一个更加清晰的界限，并且提供了合适的 Default 类，使得自定义实现更加方便和清晰。
    * AbstractResource 为 Resource 的默认实现，它对 Resource 接口做了一个统一的实现，子类继承该类后只需要覆盖相应的方法即可，同时对于自定义的 Resource 我们也是继承该类。
    * DefaultResourceLoader 同样也是 ResourceLoader 的默认实现，在自定 ResourceLoader 的时候我们除了可以继承该类外还可以实现 ProtocolResolver 接口来实现自定资源加载协议。
    * DefaultResourceLoader 每次只能返回单一的资源，所以 Spring 针对这个提供了另外一个接口 ResourcePatternResolver ，该接口提供了根据指定的 locationPattern 返回多个资源的策略。其子类 PathMatchingResourcePatternResolver 是一个集大成者的 ResourceLoader ，因为它即实现了 Resource getResource(String location) 也实现了 Resource[] getResources(String locationPattern)。
2. BeanFactory 体系 BeanFactory 是一个非常纯粹的 bean 容器，它是 IOC 必备的数据结构，其中 BeanDefinition 是她的基本结构，它内部维护着一个 BeanDefinition map ，并可根据 BeanDefinition 的描述进行 bean 的创建和管理。 
    
    BeanFacoty 有三个直接子类 ListableBeanFactory、HierarchicalBeanFactory 和 AutowireCapableBeanFactory，DefaultListableBeanFactory 为最终默认实现，它实现了所有接口。
3. Beandefinition 体系 BeanDefinition 用来描述 Spring 中的 Bean 对象。
4. BeandefinitionReader体系 BeanDefinitionReader 的作用是读取 Spring 的配置文件的内容，并将其转换成 Ioc 容器内部的数据结构：BeanDefinition。
5. BeanFactory 的扩展升级版，如果BeanFactory 是屌丝的话，那么 ApplicationContext 则是名副其实的高富帅。
由于 ApplicationContext 的结构就决定了它与 BeanFactory 的不同，其主要区别有： 
* 继承 MessageSource，提供国际化的标准访问策略。 
* 继承 ApplicationEventPublisher ，提供强大的事件机制。 
* 扩展 ResourceLoader，可以用来加载多个 Resource，可以灵活访问不同的资源。
* 对 Web 应用的支持。 

### IOC 之 Spring 统一资源加载策略
Spring 提供了 Resource 和 ResourceLoader 来统一抽象整个资源及其定位

### IOC之加载bean
```
// 根据 Xml 配置文件创建 Resource 资源对象。ClassPathResource 是 Resource 接口的子类，bean.xml 文件中的内容是我们定义的 Bean 信息。
ClassPathResource resource = new ClassPathResource("bean.xml");
// 创建一个 BeanFactory，DefaultListableBeanFactory 是其子类，是真正可以独立使用的 IOC 容器，它是整个 Spring IOC 的始祖
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
// 创建 XmlBeanDefinitionReader 读取器，用于载入 BeanDefinition 。
XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
// 开启 Bean 的载入和注册进程，完成后的 Bean 放置在 IOC 容器中。
reader.loadBeanDefinitions(resource);
```
这段代码是 Spring 中编程式使用 IOC 容器，通过这四段简单的代码，我们可以初步判断 IOC 容器的使用过程。
* 获取资源
* 获取 BeanFactory
* 根据新建的 BeanFactory 创建一个BeanDefinitionReader对象，该Reader 对象为资源的解析器
* 装载资源（IOC 容器的初始化过程） 整个过程就分为三个步骤：Resource 定位、BeanDefinition 的载入和解析、BeanDefinition 注册，如下：
![装载资源](./SpringIOC容器示意图/装载资源的过程.png)
  - 资源定位：
  我们一般用外部资源(例如.xml文件)来描述 Bean 对象，所以在初始化 IOC 容器的第一步就是需要定位这个外部资源。
  - 装载：
  装载就是BeanDefinition的载入。BeanDefinitionReader 读取、解析 Resource 资源，也就是将用户定义的 Bean 表示成 IOC 容器的内部数据结构：BeanDefinition。在 IOC 容器内部维护着一个 BeanDefinition Map 的数据结构，在配置文件中每一个< bean >都对应着一个BeanDefinition对象。
  - 注册：
  向IOC容器注册在第二步解析好的BeanDefinition，这个过程是通过 BeanDefinitionRegistry 接口来实现的。在 IOC 容器内部其实是将第二个过程解析得到的 BeanDefinition 注入到一个 HashMap 容器中，IOC 容器就是通过这个 HashMap 来维护这些 BeanDefinition 的。在这里需要注意的一点是这个过程并没有完成依赖注入，依赖注册是发生在应用第一次调用 getBean() 向容器索要 Bean 时。当然我们可以通过设置预处理，即对某个 Bean 设置 lazyinit 属性，那么这个 Bean 的依赖注入就会在容器初始化的时候完成。 
  
#### bean的加载过程
加载资源的真正实现：{@link XmlBeanDefinitionReader#loadBeanDefinitions(Resource)}
![bean装载的过程](./SpringIOC容器示意图/bean装载的过程.png)
即加载资源的入口是：reader.loadBeanDefinitions(resource) 
loadBeanDefinitions()
* resourcesCurrentlyBeingLoaded.get() 来获取已经加载过的资源'
* 然后将 encodedResource 加入其中
* 如果 resourcesCurrentlyBeingLoaded 中已经存在该资源，则抛出 BeanDefinitionStoreException 异常。
* 完成后从 encodedResource 获取封装的 Resource 资源并从 Resource 中获取相应的 InputStream ，最后将 InputStream 封装为 InputSource 调用 doLoadBeanDefinitions()。

方法 doLoadBeanDefinitions() 为从 xml 文件中加载 Bean Definition 的真正逻辑:
 1. 调用 getValidationModeForResource() 获取 xml 文件的验证模式  
 2. 调用 loadDocument() 根据 xml 文件获取相应的 Document 实例。 
 3. 调用 registerBeanDefinitions() 注册 Bean 实例。
 
#### 获取验证模型
getValidationModeForResource() 获取 xml 文件的验证模式 ；文件的验证模式保证了 XML 文件的正确性

    主要是通过读取 XML 文件的内容，判断内容中是否包含有 DOCTYPE ，
    如果是 则为 DTD，否则为 XSD，当然只会读取到 第一个 "<" 处，
    因为 验证模式一定会在第一个 “<” 之前。如果当中出现了 CharConversionException 异常，
    则为 XSD模式。 
    
#### 获取Document实例
一、 loadDocument() 根据 xml 文件获取相应的 Document 实例
```java
//DocumentLoader接口只有一个方法
public interface DocumentLoader {

	/**
	 * 从提供的 {@link InputSource source}加载{@link Document document} .
	 * @param inputSource 要加载的文档来源
	 * @param entityResolver 用于解析实体的解析器
	 * @param errorHandler 在文档加载过程中处理错误
	 * @param validationMode 验证模型
	 * {@link org.springframework.util.xml.XmlValidationModeDetector#VALIDATION_DTD DTD}
	 * 或者{@link org.springframework.util.xml.XmlValidationModeDetector#VALIDATION_XSD XSD})
	 * @param namespaceAware 命名空间支持，如果要提供对 XML 名称空间的支持，则为true
	 * @return the loaded {@link Document document}
	 * @throws Exception if an error occurs
	 */
	Document loadDocument(
			InputSource inputSource, EntityResolver entityResolver,
			ErrorHandler errorHandler, int validationMode, boolean namespaceAware)
			throws Exception;

}
```
该方法由 DocumentLoader 的默认实现类 DefaultDocumentLoader 实现，如下:
```
public Document loadDocument(InputSource inputSource, EntityResolver entityResolver,
            ErrorHandler errorHandler, int validationMode, boolean namespaceAware) throws Exception {

        DocumentBuilderFactory factory = createDocumentBuilderFactory(validationMode, namespaceAware);
        if (logger.isDebugEnabled()) {
            logger.debug("Using JAXP provider [" + factory.getClass().getName() + "]");
        }
        DocumentBuilder builder = createDocumentBuilder(factory, entityResolver, errorHandler);
        return builder.parse(inputSource);
}
```
    首先调用createDocumentBuilderFactory() 创建 DocumentBuilderFactory ，
    再通过该 factory 创建 DocumentBuilder，最后解析 InputSource 返回 Document 对象。
二、loadDocument() 获取 Document 对象时，有一个参数 entityResolver ，该参数是通过 getEntityResolver() 获取的。


---
### 注册 BeanDefinition
获取 Document 对象后，会根据该对象和 Resource 资源对象调用 registerBeanDefinitions() 方法，开始注册 BeanDefinitions 之旅。如下：
* 解析Spring默认的命名空间：例如beans

默认标签有：import，bean，beans,alias

其中bean标签及其属性如下：
```xml
 <bean  id="" name="" class=""  abstract="true" 
        autowire-candidate="default" autowire="default"
        dependency-check="default" depends-on="" destroy-method=""
        factory-bean="" factory-method="" init-method="" 
        lazy-init="default" parent="" primary="true" scope="">

        <constructor-arg> </constructor-arg>
        <description> </description>
        <lookup-method />
        <meta key="" value="" />
        <property name=""> </property>
        <qualifier> </qualifier>
        <replaced-method> </replaced-method>
 </bean>
```
### IOC容器的初始化过程
![bean装载的过程](./SpringIOC容器示意图/spring-IOC容器初始化过程.png)
