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

package org.springframework.beans;

import java.beans.PropertyDescriptor;

/**
 * The central interface of Spring's low-level JavaBeans infrastructure.
 *
 * <p>Typically not used directly but rather implicitly via a
 * {@link org.springframework.beans.factory.BeanFactory} or a
 * {@link org.springframework.validation.DataBinder}.
 *
 * <p>Provides operations to analyze and manipulate standard JavaBeans:
 * the ability to get and set property values (individually or in bulk),
 * get property descriptors, and query the readability/writability of properties.
 *
 * <p>This interface supports <b>nested properties</b> enabling the setting
 * of properties on subproperties to an unlimited depth.
 *
 * <p>A BeanWrapper's default for the "extractOldValueForEditor" setting
 * is "false", to avoid side effects caused by getter method invocations.
 * Turn this to "true" to expose present property values to custom editors.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 13 April 2001
 * @see PropertyAccessor
 * @see PropertyEditorRegistry
 * @see PropertyAccessorFactory#forBeanPropertyAccess
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.validation.BeanPropertyBindingResult
 * @see org.springframework.validation.DataBinder#initBeanPropertyAccess()
 */
// BeanWrapper 是一个从 BeanDefinition 到 Bean 直接的中间产物，是Spring 的 低级 JavaBean 基础结构的接口，
// 一般不会直接使用，而是通过 BeanFactory 或者 DataBinder 隐式使用。
// 它提供分析和操作标准 JavaBeans 的操作：获取和设置属性值、获取属性描述符以及查询属性的可读性/可写性的能力。

// BeanWrapper 主要继承三个核心接口：PropertyAccessor, PropertyEditorRegistry, TypeConverter
// 1. PropertyAccessor:可以访问属性的通用型接口（例如对象的 bean 属性或者对象中的字段），作为 BeanWrapper 的基础接口。
// 2. PropertyEditorRegistry:用于注册 JavaBean 的 PropertyEditors，对 PropertyEditorRegistrar 起核心作用的中心接口。
// 由 BeanWrapper 扩展，BeanWrapperImpl 和 DataBinder 实现.PropertyEditorRegistry 就是用于 PropertyEditor 的注册和发现，而 PropertyEditor 是 Java 内省里面的接口，用于改变指定 property 属性的类型。
// 3. TypeConverter:定义类型转换的接口，通常与 PropertyEditorRegistry 接口一起实现（但不是必须）
// 那么BeanWrapper就具有三重身份：属性编辑器，属性编辑器注册表，类型转换器

// BeanWrapper 继承 ConfigurablePropertyAccessor 接口，该接口除了继承上面介绍的三个接口外还集成了 Spring 的 ConversionService 类型转换体系


public interface BeanWrapper extends ConfigurablePropertyAccessor {

	/**
	 * Return the bean instance wrapped by this object, if any.
	 * @return the bean instance, or {@code null} if none set
	 */
	Object getWrappedInstance();

	/**
	 * Return the type of the wrapped JavaBean object.
	 * @return the type of the wrapped bean instance,
	 * or {@code null} if no wrapped object has been set
	 */
	Class<?> getWrappedClass();

	/**
	 * Obtain the PropertyDescriptors for the wrapped object
	 * (as determined by standard JavaBeans introspection).
	 * @return the PropertyDescriptors for the wrapped object
	 */
	PropertyDescriptor[] getPropertyDescriptors();

	/**
	 * Obtain the property descriptor for a specific property
	 * of the wrapped object.
	 * @param propertyName the property to obtain the descriptor for
	 * (may be a nested path, but no indexed/mapped property)
	 * @return the property descriptor for the specified property
	 * @throws InvalidPropertyException if there is no such property
	 */
	PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException;

	/**
	 * Specify a limit for array and collection auto-growing.
	 * <p>Default is unlimited on a plain BeanWrapper.
	 */
	void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);

	/**
	 * Return the limit for array and collection auto-growing.
	 */
	int getAutoGrowCollectionLimit();

}
