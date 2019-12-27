/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.beans.factory;

/**
 * Marker superinterface indicating that a bean is eligible to be
 * notified by the Spring container of a particular framework object
 * through a callback-style method. Actual method signature is
 * determined by individual subinterfaces, but should typically
 * consist of just one void-returning method that accepts a single
 * argument.
 *
 * <p>Note that merely implementing {@link Aware} provides no default
 * functionality. Rather, processing must be done explicitly, for example
 * in a {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessor}.
 * Refer to {@link org.springframework.context.support.ApplicationContextAwareProcessor}
 * and {@link org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory}
 * for examples of processing {@code *Aware} interface callbacks.
 *
 * @author Chris Beams
 * @since 3.1
 */
// 这是一个具有标识作用的超级接口，实现了此接口的 bean 是具有被 Spring 容器通知的能力，通知的方式是采用回调的方式。
// Aware 的含义是感知的、感应的，那么在 Spring 容器中是如何实现感知并设置属性值得呢？
// 我们可以从初始化 bean 方法initializeBean()中调用的激活 Aware 的方法 invokeAwareMethods() 中看到一点点：
    //其实就是Spring 容器在初始化主动检测当前 bean 是否实现了 Aware 接口，
    // 如果实现了则回调其 set 方法将相应的参数设置给该 bean ，
    // 这个时候该 bean 就从 Spring 容器中取得相应的资源。
public interface Aware {

}
