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

package org.springframework.core.io;

import org.springframework.util.ResourceUtils;

/**
 * Spring 将资源的定义和资源的加载区分开了，Resource 定义了统一的资源，资源的加载则由 ResourceLoader 来统一定义。
 *
 * 加载资源的策略接口(例如classpath 或者文件系统资源等的).
 *
 * An {@link org.springframework.context.ApplicationContext}
 * is required to provide this functionality, plus extended
 * {@link org.springframework.core.io.support.ResourcePatternResolver} support.
 *
 * <p>{@link DefaultResourceLoader} is a standalone implementation that is
 * usable outside an ApplicationContext, also used by {@link ResourceEditor}.
 *
 * <p>Bean properties of type Resource and Resource array can be populated
 * from Strings when running in an ApplicationContext, using the particular
 * context's resource loading strategy.
 *
 * @author Juergen Hoeller
 * @since 10.03.2004
 * @see Resource
 * @see org.springframework.core.io.support.ResourcePatternResolver
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ResourceLoaderAware
 */
//Spring 利用 ResourceLoader 来进行统一资源加载(对各种Resource的加载)
//加载资源的策略接口，支持文件系统加载，classpath加载，相对路径加载。
public interface ResourceLoader {

	/** Pseudo URL prefix for loading from the class path: "classpath:" */
	String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;


	/**
	 * 根据所提供资源的路径 location 返回 Resource 实例。
     * 但是它不确保该 Resource 一定存在，需要调用 {@link Resource#exists}方法判断。该方法支持以下模式的资源加载：
	 * URL位置资源,如: "file:C:/test.dat".
	 * ClassPath位置资源，如："classpath:test.dat".
	 * 相对路径资源，如: "WEB-INF/test.dat".
	 * (This will be implementation-specific, typically provided by an
	 * ApplicationContext implementation.)
	 *
	 *
	 * @param location the resource location
	 * @return a corresponding Resource handle
	 * @see #CLASSPATH_URL_PREFIX
	 * @see org.springframework.core.io.Resource#exists
	 * @see org.springframework.core.io.Resource#getInputStream
	 */
	//ResourceLoader 的 Resource getResource(String location) 每次只能根据 location 返回一个 Resource，
	// 当需要加载多个资源时，我们除了多次调用 getResource() 外别无他法。
	Resource getResource(String location);

	/**
	 * 返回ResourceLoader使用的ClassLoader；
	 * <p>Clients which need to access the ClassLoader directly can do so
	 * in a uniform manner with the ResourceLoader, rather than relying
	 * on the thread context ClassLoader.
	 * @return the ClassLoader (only {@code null} if even the system
	 * ClassLoader isn't accessible)
	 * @see org.springframework.util.ClassUtils#getDefaultClassLoader()
	 */
	ClassLoader getClassLoader();

}
