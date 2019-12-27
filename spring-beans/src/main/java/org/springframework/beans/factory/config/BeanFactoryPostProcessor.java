package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;

/**
 * 允许自定义修改应用程序上下文的Bean定义，以适应上下文的基础Bean工厂的Bean属性值。
 *
 * <p>Application contexts can auto-detect BeanFactoryPostProcessor beans in
 * their bean definitions and apply them before any other beans get created.
 *
 * <p>Useful for custom config files targeted at system administrators that
 * override bean properties configured in the application context.
 *
 * <p>See PropertyResourceConfigurer and its concrete implementations
 * for out-of-the-box solutions that address such configuration needs.
 *
 * <p>A BeanFactoryPostProcessor may interact with and modify bean
 * definitions, but never bean instances. Doing so may cause premature bean
 * instantiation, violating the container and causing unintended side-effects.
 * If bean instance interaction is required, consider implementing
 * {@link BeanPostProcessor} instead.
 *
 * @author Juergen Hoeller
 * @since 06.07.2003
 * @see BeanPostProcessor
 * @see PropertyResourceConfigurer
 */
//在 Spring 容器启动阶段，Spring 也提供了一种容器扩展机制：BeanFactoryPostProcessor
// 该机制作用于容器启动阶段，允许我们在容器实例化 Bean 之前对注册到该容器的 BeanDefinition 做出修改。
	// bean 实例化之前最后一次修改 BeanDefinition 的机会
	// BeanFactoryPostProcessor 是与 BeanDefinition 打交道
	// 如果想要与 Bean 打交道，请使用 BeanPostProcessor。
public interface BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for overriding or adding
	 * properties even to eager-initializing beans.
	 * @param beanFactory the bean factory used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	//1.表示了该方法的作用：其中参数 beanFactory 应该就是已经完成了 standard initialization 的 BeanFactory。
	//  可以让程序员干预bean工厂的初始化过程（重点会考）,
	//  说白了就是——beanFactory怎么new出来的（实例化）BeanFactoryPostProcessor是干预不了的，
	//  但是beanFactory new出来之后各种属性的填充或者修改（初始化）是可以通过BeanFactoryPostProcessor来干预；
	//2.表示作用时机：所有的 BeanDefinition 已经完成了加载（即：把类变成beanDefinition然后put到map之中），但是还没有完成初始化。
	// 此方法工作于BeanDefinition加载完成之后，Bean实例化之前，主要作用是对BeanDefinition 做修改
	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;

}
