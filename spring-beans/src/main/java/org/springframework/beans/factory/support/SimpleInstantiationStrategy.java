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

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * Simple object instantiation strategy for use in a BeanFactory.
 *
 * <p>Does not support Method Injection, although it provides hooks for subclasses
 * to override to add Method Injection support, for example by overriding methods.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
public class SimpleInstantiationStrategy implements InstantiationStrategy {

	private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<Method>();


	/**
	 * Return the factory method currently being invoked or {@code null} if none.
	 * <p>Allows factory method implementations to determine whether the current
	 * caller is the container itself as opposed to user code.
	 */
	public static Method getCurrentlyInvokedFactoryMethod() {
		return currentlyInvokedFactoryMethod.get();
	}

	/**
	 * @see AbstractBeanDefinition#methodOverrides
	 */
	// 构造方法实例化,先判断是否有 MethodOverrides，如果没有则是直接使用反射，如果有则就需要 CGLIB 实例化对象。
	// 在Spring中多处使用了代理，比如对lookup-method和replace-method的处理；AOP的实现

	//默认构造方法
	@Override
	public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner) {
		// Don't override the class with CGLIB if no overrides.
		// 如果MethodOverride不为空，则使用cglib
		if (bd.getMethodOverrides().isEmpty()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse == null) {
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						throw new BeanInstantiationException(clazz, "Specified class is an interface");
					}
					try {
						if (System.getSecurityManager() != null) {
							constructorToUse = AccessController.doPrivileged(new PrivilegedExceptionAction<Constructor<?>>() {
								@Override
								public Constructor<?> run() throws Exception {
									return clazz.getDeclaredConstructor((Class[]) null);
								}
							});
						}
						else {//反射获取无参的构造函数
							constructorToUse =	clazz.getDeclaredConstructor((Class[]) null);
						}
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Exception ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
			//ctor.newInstance(args);反射生成实例bean
			return BeanUtils.instantiateClass(constructorToUse);
		}
		else {
			// Must generate CGLIB subclass.
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}

	/**
	 * Subclasses can override this method, which is implemented to throw
	 * UnsupportedOperationException, if they can instantiate an object with
	 * the Method Injection specified in the given RootBeanDefinition.
	 * Instantiation should use a no-arg constructor.
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, String beanName, BeanFactory owner) {
		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	//指定构造方法
	@Override
	public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner,
			final Constructor<?> ctor, Object... args) {
		// 没有覆盖
		// 直接使用反射实例化即可
		if (bd.getMethodOverrides().isEmpty()) {
			if (System.getSecurityManager() != null) {
				// use own privileged to change accessibility (when security is on)
				// 更改可访问性
				AccessController.doPrivileged(new PrivilegedAction<Object>() {
					@Override
					public Object run() {
						ReflectionUtils.makeAccessible(ctor);
						return null;
					}
				});
			}
			// 通过BeanUtils直接使用构造器对象实例化bean
			// 如果该 bean 没有配置 lookup-method、replaced-method 标签或者 @Lookup 注解，
			// 则直接通过反射的方式实例化 bean 即可
			return BeanUtils.instantiateClass(ctor, args);
		}
		else {
			// 生成CGLIB创建的子类对象
			// 如果存在需要覆盖的方法或者动态替换的方法则需要使用 CGLIB 进行动态代理，
			// 因为可以在创建代理的同时将动态方法织入类中。
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}

	/**
	 * Subclasses can override this method, which is implemented to throw
	 * UnsupportedOperationException, if they can instantiate an object with
	 * the Method Injection specified in the given RootBeanDefinition.
	 * Instantiation should use the given constructor and parameters.
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, String beanName, BeanFactory owner,
			Constructor<?> ctor, Object... args) {

		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	//创建 bean 实例：利用 Java 反射执行工厂方法并返回创建好的实例
	@Override
	public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner,
			Object factoryBean, final Method factoryMethod, Object... args) {

		try {
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged(new PrivilegedAction<Object>() {
					@Override
					public Object run() {
						ReflectionUtils.makeAccessible(factoryMethod);
						return null;
					}
				});
			}
			else {
				//暴力访问
				ReflectionUtils.makeAccessible(factoryMethod);
			}
			//记录先前调用的方法
			Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
			try {
				//设置currentlyInvokedFactoryMethod为当前传入的factoryMethod
				currentlyInvokedFactoryMethod.set(factoryMethod);
				// 执行工厂方法，并返回实例
				return factoryMethod.invoke(factoryBean, args);
			}
			finally {
				//如果先前调用的方法不为null，重新将其赋给currentlyInvokedFactoryMethod
				if (priorInvokedFactoryMethod != null) {
					currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
				}
				else {
					currentlyInvokedFactoryMethod.remove();
				}
			}
		}
		catch (IllegalArgumentException ex) {
			throw new BeanInstantiationException(factoryMethod.getReturnType(),
					"Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
					"args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
		}
		catch (IllegalAccessException ex) {
			throw new BeanInstantiationException(factoryMethod.getReturnType(),
					"Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
		}
		catch (InvocationTargetException ex) {
			String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
			if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
					((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
				msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
						"declaring the factory method as static for independence from its containing instance. " + msg;
			}
			throw new BeanInstantiationException(factoryMethod.getReturnType(), msg, ex.getTargetException());
		}
	}

}
