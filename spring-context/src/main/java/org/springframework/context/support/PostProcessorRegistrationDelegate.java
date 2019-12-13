/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
class PostProcessorRegistrationDelegate {

    public static void invokeBeanFactoryPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

        // Invoke BeanDefinitionRegistryPostProcessors first, if any.
        Set<String> processedBeans = new HashSet<String>();

        if (beanFactory instanceof BeanDefinitionRegistry) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            List<BeanFactoryPostProcessor> regularPostProcessors = new LinkedList<BeanFactoryPostProcessor>();
            List<BeanDefinitionRegistryPostProcessor> registryPostProcessors =
                    new LinkedList<BeanDefinitionRegistryPostProcessor>();

            for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                    BeanDefinitionRegistryPostProcessor registryPostProcessor =
                            (BeanDefinitionRegistryPostProcessor) postProcessor;
                    registryPostProcessor.postProcessBeanDefinitionRegistry(registry);
                    registryPostProcessors.add(registryPostProcessor);
                } else {
                    regularPostProcessors.add(postProcessor);
                }
            }

            // Do not initialize FactoryBeans here: We need to leave all regular beans
            // uninitialized to let the bean factory post-processors apply to them!
            // Separate between BeanDefinitionRegistryPostProcessors that implement
            // PriorityOrdered, Ordered, and the rest.
            String[] postProcessorNames =
                    beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

            // First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
            List<BeanDefinitionRegistryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanDefinitionRegistryPostProcessor>();
            for (String ppName : postProcessorNames) {
                if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                    priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                }
            }
            sortPostProcessors(beanFactory, priorityOrderedPostProcessors);
            registryPostProcessors.addAll(priorityOrderedPostProcessors);
            invokeBeanDefinitionRegistryPostProcessors(priorityOrderedPostProcessors, registry);

            // Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            List<BeanDefinitionRegistryPostProcessor> orderedPostProcessors = new ArrayList<BeanDefinitionRegistryPostProcessor>();
            for (String ppName : postProcessorNames) {
                if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                    orderedPostProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                }
            }
            sortPostProcessors(beanFactory, orderedPostProcessors);
            registryPostProcessors.addAll(orderedPostProcessors);
            invokeBeanDefinitionRegistryPostProcessors(orderedPostProcessors, registry);

            // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
            boolean reiterate = true;
            while (reiterate) {
                reiterate = false;
                postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
                for (String ppName : postProcessorNames) {
                    if (!processedBeans.contains(ppName)) {
                        BeanDefinitionRegistryPostProcessor pp = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
                        registryPostProcessors.add(pp);
                        processedBeans.add(ppName);
                        pp.postProcessBeanDefinitionRegistry(registry);
                        reiterate = true;
                    }
                }
            }

            // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
            invokeBeanFactoryPostProcessors(registryPostProcessors, beanFactory);
            invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
        } else {
            // Invoke factory processors registered with the context instance.
            invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let the bean factory post-processors apply to them!
        String[] postProcessorNames =
                beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

        // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
        List<String> orderedPostProcessorNames = new ArrayList<String>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
        for (String ppName : postProcessorNames) {
            if (processedBeans.contains(ppName)) {
                // skip - already processed in first phase above
            } else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
        sortPostProcessors(beanFactory, priorityOrderedPostProcessors);
        invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

        // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
        List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
        for (String postProcessorName : orderedPostProcessorNames) {
            orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        sortPostProcessors(beanFactory, orderedPostProcessors);
        invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

        // Finally, invoke all other BeanFactoryPostProcessors.
        List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
        for (String postProcessorName : nonOrderedPostProcessorNames) {
            nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

        // Clear cached merged bean definitions since the post-processors might have
        // modified the original metadata, e.g. replacing placeholders in values...
        beanFactory.clearMetadataCache();
    }

    public static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

        //获取所有的BeanPostProcessor的beanName
        //这些beanName已经全部加载到容器中但是还没有实例化
        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

        // 记录所有的beanProcessor数量
        int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
        // 注册 BeanPostProcessorChecker，它主要是用于在 BeanPostProcessor 实例化期间记录日志
        // 当 Spring 中高配置的后置处理器还没有注册就已经开始了 bean 的实例化过程，这个时候便会打印 BeanPostProcessorChecker 中的内容
        beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

        // PriorityOrdered 保证顺序
        List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanPostProcessor>();
        List<BeanPostProcessor> internalPostProcessors = new ArrayList<BeanPostProcessor>();
        //适用Ordered保证顺序
        List<String> orderedPostProcessorNames = new ArrayList<String>();
        //没有顺序
        List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                //调用getBean获取真是的BeanPostProcessor实例对象
                BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
                priorityOrderedPostProcessors.add(pp);
                if (pp instanceof MergedBeanDefinitionPostProcessor) {
                    internalPostProcessors.add(pp);
                }
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // 首先，注册所有实现了PriorityOrdered接口的BeanPostProcessor实例
        //先排序
        sortPostProcessors(beanFactory, priorityOrderedPostProcessors);
        //再注册
        registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

        // 接着，注册所有实现Ordered接口的BeanPostProcessor实例
        List<BeanPostProcessor> orderedPostProcessors = new ArrayList<BeanPostProcessor>();
        for (String ppName : orderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            orderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        sortPostProcessors(beanFactory, orderedPostProcessors);
        registerBeanPostProcessors(beanFactory, orderedPostProcessors);

        // 再接着，注册所有无序的BeanPostProcessor实例
        List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<BeanPostProcessor>();
        for (String ppName : nonOrderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            nonOrderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

        //最后，注册所有的 MergedBeanDefinitionPostProcessor 类型的 BeanPostProcessor
        sortPostProcessors(beanFactory, internalPostProcessors);
        registerBeanPostProcessors(beanFactory, internalPostProcessors);

        // 加入ApplicationListenerDetector（探测器）
        // 重新注册 BeanPostProcessor 以检测内部 bean，因为 ApplicationListeners 将其移动到处理器链的末尾
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
    }

    private static void sortPostProcessors(ConfigurableListableBeanFactory beanFactory, List<?> postProcessors) {
        Comparator<Object> comparatorToUse = null;
        if (beanFactory instanceof DefaultListableBeanFactory) {
            comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
        }
        if (comparatorToUse == null) {
            comparatorToUse = OrderComparator.INSTANCE;
        }
        Collections.sort(postProcessors, comparatorToUse);
    }

    /**
     * Invoke the given BeanDefinitionRegistryPostProcessor beans.
     */
    private static void invokeBeanDefinitionRegistryPostProcessors(
            Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

        for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessBeanDefinitionRegistry(registry);
        }
    }

    /**
     * Invoke the given BeanFactoryPostProcessor beans.
     */
    private static void invokeBeanFactoryPostProcessors(
            Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

        for (BeanFactoryPostProcessor postProcessor : postProcessors) {
            //BeanFactoryPostProcessor中又调用factory的方法。
            //使用了命令模式
            postProcessor.postProcessBeanFactory(beanFactory);
        }
    }

    /**
     * Register the given BeanPostProcessor beans.
     */
    private static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

        for (BeanPostProcessor postProcessor : postProcessors) {
            beanFactory.addBeanPostProcessor(postProcessor);
        }
    }


    /**
     * BeanPostProcessor that logs an info message when a bean is created during
     * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
     * getting processed by all BeanPostProcessors.
     */
    private static class BeanPostProcessorChecker implements BeanPostProcessor {

        private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

        private final ConfigurableListableBeanFactory beanFactory;

        private final int beanPostProcessorTargetCount;

        public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
            this.beanFactory = beanFactory;
            this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean != null && !(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
                    this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
                if (logger.isInfoEnabled()) {
                    logger.info("Bean '" + beanName + "' of type [" + bean.getClass() +
                            "] is not eligible for getting processed by all BeanPostProcessors " +
                            "(for example: not eligible for auto-proxying)");
                }
            }
            return bean;
        }

        private boolean isInfrastructureBean(String beanName) {
            if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
                return RootBeanDefinition.ROLE_INFRASTRUCTURE == bd.getRole();
            }
            return false;
        }
    }


    /**
     * BeanPostProcessor that detects beans which implement the ApplicationListener interface.
     * This catches beans that can't reliably be detected by getBeanNamesForType.
     */
    private static class ApplicationListenerDetector implements MergedBeanDefinitionPostProcessor, DestructionAwareBeanPostProcessor {

        private static final Log logger = LogFactory.getLog(ApplicationListenerDetector.class);

        private final AbstractApplicationContext applicationContext;

        private final Map<String, Boolean> singletonNames = new ConcurrentHashMap<String, Boolean>(64);

        public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
            if (beanDefinition.isSingleton()) {
                this.singletonNames.put(beanName, Boolean.TRUE);
            }
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof ApplicationListener) {
                // potentially not detected as a listener by getBeanNamesForType retrieval
                Boolean flag = this.singletonNames.get(beanName);
                if (Boolean.TRUE.equals(flag)) {
                    // singleton bean (top-level or inner): register on the fly
                    this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
                } else if (flag == null) {
                    if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
                        // inner bean with other scope - can't reliably process events
                        logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
                                "but is not reachable for event multicasting by its containing ApplicationContext " +
                                "because it does not have singleton scope. Only top-level listener beans are allowed " +
                                "to be of non-singleton scope.");
                    }
                    this.singletonNames.put(beanName, Boolean.FALSE);
                }
            }
            return bean;
        }

        @Override
        public void postProcessBeforeDestruction(Object bean, String beanName) {
            if (bean instanceof ApplicationListener) {
                ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
                multicaster.removeApplicationListener((ApplicationListener<?>) bean);
                multicaster.removeApplicationListenerBean(beanName);
            }
        }
    }

}
