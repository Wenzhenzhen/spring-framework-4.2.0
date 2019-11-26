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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;

/**
 * Factory hook that allows for custom modification of new bean instances,
 * e.g. checking for marker interfaces or wrapping them with proxies.
 * <p>
 * <p>ApplicationContexts can autodetect BeanPostProcessor beans in their
 * bean definitions and apply them to any beans subsequently created.
 * Plain bean factories allow for programmatic registration of post-processors,
 * applying to all beans created through this factory.
 * <p>
 * <p>Typically, post-processors that populate beans via marker interfaces
 * or the like will implement {@link #postProcessBeforeInitialization},
 * while post-processors that wrap beans with proxies will normally
 * implement {@link #postProcessAfterInitialization}.
 *
 * @author Juergen Hoeller
 * @see InstantiationAwareBeanPostProcessor
 * @see DestructionAwareBeanPostProcessor
 * @see ConfigurableBeanFactory#addBeanPostProcessor
 * @see BeanFactoryPostProcessor
 * @since 10.10.2003
 */
//在新的bean实例初始化前后调用.

/**
 * @see org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory
 * 中的initializeBean方法
 */
public interface BeanPostProcessor {

      /**
       * Spring容器初始化bean大致过程 ：
       * 1. 定义bean标签
       * 2. 将bean标签解析成BeanDefinition
       * 3. 调用构造方法实例化(IOC)
       * 4. 属性值的依赖注入(DI)
       * {@link BeanFactoryPostProcessor}方法的执行是发生在第二部之后，第三步之前。
       * {@link BeanPostProcessor}发生在第三步之后。
       *
       * 在bean的初始化方法回调之前（如InitializingBean的{@code afterPropertiesSet}，或者自定义的init-method）
       * 将此BeanPostProcessor应用于给定的新bean实例
       * 该bean将已经用属性值填充. 返回的bean实例可能是原始实例的包装。
       *
       * @param bean the new bean instance
       * @param beanName the name of the bean
       * @return the bean instance to use, either the original or a wrapped one; if {@code null}, no
       *     subsequent BeanPostProcessors will be invoked
       * @throws org.springframework.beans.BeansException in case of errors
       * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
       */
      // 初始化前的后期处理
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException;

    /**
     * 在bean的初始化回调之后（如InitializingBean的{@code afterPropertiesSet}，或者自定义的init-method），
     * 将此BeanPostProcessor应用于给定的新bean实例。 该bean将已经被属性值填充。返回的bean实例可能是原始对象的包装。
     *
     * 对于FactoryBean，该回调将同时应用于FactoryBean实例和由FactoryBean创建的对象（如 2.0版）。
     * 后处理器可以通过相应的{@code bean instanceof FactoryBean}检查来决定是应用到FactoryBean还是其创建的对象，还是两者都应用。
     * 与所有其他BeanPostProcessor回调相反，此回调还将在{@link InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation}方法触发短路后被调用。
     *
     * @param bean     the new bean instance
     * @param beanName the name of the bean
     * @return the bean instance to use, either the original or a wrapped one; if
     * {@code null}, no subsequent BeanPostProcessors will be invoked
     * @throws org.springframework.beans.BeansException in case of errors
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
     * @see org.springframework.beans.factory.FactoryBean
     */
    // 初始化后的后期处理
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException;

}
