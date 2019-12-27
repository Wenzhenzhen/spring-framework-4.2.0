/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Interface responsible for creating instances corresponding to a root bean definition.
 *
 * <p>This is pulled out into a strategy as various approaches are possible,
 * including using CGLIB to create subclasses on the fly to support Method Injection.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */

// 实例化bean的策略接口,使用了策略模式
// 实例化 bean有反射和 CGLIB 动态字节码两种方式
// 根据创建对象情况的不同,提供了三种策略：无参构造方法、有参构造方法、工厂方法。
// 子类 SimpleInstantiationStrategy 提供了基于反射来实例化对象的功能，但是不支持方法注入方式的对象实例化。
// CglibSubclassingInstantiationStrategy 继承 SimpleInstantiationStrategy，他除了拥有父类以反射实例化对象的功能外，还提供了通过 CGLIB 的动态字节码的功能进而支持方法注入所需的对象实例化需求。

public interface InstantiationStrategy {

	/**
	 * Return an instance of the bean with the given name in this factory.
	 * @param bd the bean definition
	 * @param beanName the name of the bean when it's created in this context.
	 * The name can be {@code null} if we're autowiring a bean which doesn't
	 * belong to the factory.
	 * @param owner the owning BeanFactory
	 * @return a bean instance for this bean definition
	 * @throws BeansException if the instantiation attempt failed
	 */
	//默认构造方法
	Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner)
			throws BeansException;

	/**
	 * Return an instance of the bean with the given name in this factory,
	 * creating it via the given constructor.
	 * @param bd the bean definition
	 * @param beanName the name of the bean when it's created in this context.
	 * The name can be {@code null} if we're autowiring a bean which doesn't
	 * belong to the factory.
	 * @param owner the owning BeanFactory
	 * @param ctor the constructor to use
	 * @param args the constructor arguments to apply
	 * @return a bean instance for this bean definition
	 * @throws BeansException if the instantiation attempt failed
	 */
	//指定构造方法
	Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner,
			Constructor<?> ctor, Object... args) throws BeansException;

	/**
	 * Return an instance of the bean with the given name in this factory,
	 * creating it via the given factory method.
	 * @param bd the bean definition
	 * @param beanName the name of the bean when it's created in this context.
	 * The name can be {@code null} if we're autowiring a bean which doesn't
	 * belong to the factory.
	 * @param owner the owning BeanFactory
	 * @param factoryBean the factory bean instance to call the factory method on,
	 * or {@code null} in case of a static factory method
	 * @param factoryMethod the factory method to use
	 * @param args the factory method arguments to apply
	 * @return a bean instance for this bean definition
	 * @throws BeansException if the instantiation attempt failed
	 */
	//工厂方法
	Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner,
			Object factoryBean, Method factoryMethod, Object... args) throws BeansException;

}
