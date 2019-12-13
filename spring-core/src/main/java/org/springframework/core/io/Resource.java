/*
 * Copyright 2002-2012 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

/**
 * 资源描述符的接口，它从基础资源的实际类型中抽象出来，例如文件或类路径资源。
 * Spring 框架所有资源的抽象和访问接口，它继承{@link InputStreamSource}接口。
 * 作为所有资源的统一抽象，Source 定义了一些通用的方法，由子类 AbstractResource 提供统一的默认实现。
 *
 *
 * Resource体系 Resource是对资源的抽象，根据资源的不同类型提供不同的具体实现：
 * @see WritableResource
 * @see ContextResource
 * @see FileSystemResource ：对 java.io.File 类型资源的封装，只要是跟 File 打交道的，基本上与 FileSystemResource 也可以打交道。支持文件和 URL 的形式，实现 WritableResource 接口，且从 Spring Framework 5.0 开始，FileSystemResource 使用NIO.2 API进行读/写交互
 * @see ClassPathResource ：class path 类型资源的实现。使用给定的 ClassLoader 或者给定的 Class 来加载资源。
 * @see UrlResource ：对 java.net.URL类型资源的封装。内部委派 URL 进行具体的资源操作。
 * @see ByteArrayResource ： 对字节数组提供的数据的封装。如果通过 InputStream 形式访问该类型的资源，该实现会根据字节数组的数据构造一个相应的 ByteArrayInputStream。
 * @see InputStreamResource ： 将给定的 InputStream 作为一种资源的 Resource 的实现类。
 * @see PathResource
 *
 * 有了资源就应该有资源加载，Spring 进行统一资源加载的接口是
 * @see ResourceLoader
 */
public interface Resource extends InputStreamSource {

	/**
	 * 资源是否真实存在（物理形式）
	 */
	boolean exists();

	/**
	 * 资源是否可读（通过{@link #getInputStream（）}或{@link #getFile（）}）：
	 * 结果为true也有可能会读取失败，但是false一定是资源不可读的
	 */
	boolean isReadable();

	/**
	 *资源所代表的句柄是否被一个stream打开了
	 */
	boolean isOpen();

	/**
	 * 返回资源的URL的句柄
	 */
	URL getURL() throws IOException;

	/**
	 * 返回资源的URI 句柄
	 */
	URI getURI() throws IOException;

	/**
	 * 返回资源的文件句柄
	 */
	File getFile() throws IOException;

	/**
	 * 资源内容长度
	 */
	long contentLength() throws IOException;

	/**
	 *资源最后修改时间
	 */
	long lastModified() throws IOException;

	/**
	 * 根据资源的相对路径穿件新资源
	 * @param relativePath 相对于此资源的相对路径
	 * @return 相对资源的 资源句柄
	 * @throws IOException 如果无法确定相对资源
	 */
	Resource createRelative(String relativePath) throws IOException;

	/**
	 * 确定资源的文件名称
	 */
	String getFilename();

	/**
	 * 资源描述
	 */
	String getDescription();

}
