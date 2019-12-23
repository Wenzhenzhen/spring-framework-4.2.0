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

package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 * <p>
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 * <p>
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 * <p>
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * @since 2.0
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

    /**
     * Internal marker for a null singleton object:
     * used as marker value for concurrent Maps (which don't support null values).
     */
    protected static final Object NULL_OBJECT = new Object();


    /**
     * Logger available to subclasses
     */
    protected final Log logger = LogFactory.getLog(getClass());

    // getSingleton(String beanName, boolean allowEarlyReference) 中
    //spring创建单例bean的时候，存在循环依赖的问题。比如创建bean a的时候发现bean a引用了bean b，
    // 此时会去创建bean b，但又发现bean b引用了bean c，所以此时会去创建bean c，在创建bean c的过程中发现bean c引用bean a。
    // 这三个bean就形成了一个环。为了解决循环依赖的问题，spring采取了一种将创建的bean实例提早暴露加入到缓存中，
    // 一旦下一个bean创建的时候需要依赖上个bean，则直接使用ObjectFactory来获取bean

    // singletonObjects和earlySingletonObjects的区别：
    // 在于earlySingletonObjects是为了解决循环依赖设置的，储存的是提前暴露的bean name –> bean instance，
    // 而singletonObjects存储的是完全实例化的bean name –> bean instance。
    // 提前暴露的bean实例是指bean实例创建完成，但是还没有对属性进行注入，即没有注入依赖的对象，因为循环依赖中，依赖的对象还没有，为null，
    // 循环依赖通过这种方法可以解决

    // Spring解决循环依赖的关键因素1 -----三级缓存：
    /**
     * Cache of singleton objects: bean name --> bean instance
     */
    //一级-完全实例化的bean name –> bean instance。
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(64);
    /**
     * Cache of early singleton objects: bean name --> bean instance
     */
    //二级-存储那些提前暴露的，未对属性进行注入的bean，用于处理循环依赖。(Spring解决循环依赖的核心在于提前曝光bean)
    private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);
    /**
     * Cache of singleton factories: bean name --> ObjectFactory
     */
    //三级-用于保存beanName和创建的bean的工厂之间的关系.即:beanName ->ObjectFactory
    private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);

    // Spring解决循环依赖的关键因素2 -----isSingletonCurrentlyInCreation() 和 allowEarlyReference
    // （1）isSingletonCurrentlyInCreation()判断当前 singleton bean 是否处于创建中。
    // bean 处于创建中也就是说 bean 在初始化但是没有完成初始化，有一个这样的过程其实和
    // Spring 解决 bean 循环依赖的理念相辅相成，因为 Spring 解决 singleton bean 的核心就在于提前曝光 bean。
    // （2）allowEarlyReference从字面意思上面理解就是允许提前拿到引用。
    // 其实真正的意思是是否允许从 singletonFactories 缓存中通过 getObject() 拿到对象，
    // 为什么会有这样一个字段呢？原因就在于 singletonFactories 才是 Spring 解决 singleton bean 的诀窍所在，这个我们后续分析。
    /**
     * Names of beans that are currently in creation
     */
    //记录正在创建的bean。
    private final Set<String> singletonsCurrentlyInCreation =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));

    /**
     * Set of registered singletons, containing the bean names in registration order
     */
    private final Set<String> registeredSingletons = new LinkedHashSet<String>(64);

    /**
     * Names of beans currently excluded from in creation checks
     */
    private final Set<String> inCreationCheckExclusions =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));

    /**
     * List of suppressed Exceptions, available for associating related causes
     */
    private Set<Exception> suppressedExceptions;

    /**
     * Flag that indicates whether we're currently within destroySingletons
     */
    private boolean singletonsCurrentlyInDestruction = false;

    /**
     * Disposable bean instances: bean name --> disposable instance
     */
    private final Map<String, Object> disposableBeans = new LinkedHashMap<String, Object>();

    /**
     * Map between containing bean names: bean name --> Set of bean names that the bean contains
     */
    private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<String, Set<String>>(16);

    /**
     * Map between dependent bean names: bean name --> Set of dependent bean names
     */
    //dependentBeanMap保存依赖 beanName 之间的映射关系：beanName - > 依赖 beanName 的集合
    private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<String, Set<String>>(64);

    /**
     * Map between depending bean names: bean name --> Set of bean names for the bean's dependencies
     */
    //dependenciesForBeanMap保存被依赖的 beanName 之间的映射关系：beanName - > 被依赖 beanName 的集合
    private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<String, Set<String>>(64);


    @Override
    public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
        Assert.notNull(beanName, "'beanName' must not be null");
        synchronized (this.singletonObjects) {
            Object oldObject = this.singletonObjects.get(beanName);
            if (oldObject != null) {
                throw new IllegalStateException("Could not register object [" + singletonObject +
                        "] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
            }
            addSingleton(beanName, singletonObject);
        }
    }

    /**
     * Add the given singleton object to the singleton cache of this factory.
     * <p>To be called for eager registration of singletons.
     *
     * @param beanName        the name of the bean
     * @param singletonObject the singleton object
     */
    /**
     * 添加到单例缓存池
     * 删除该beanName对应的ObjectFactory
     * 从提前暴露的缓冲区中删除beanName对应的Entry
     * 添加到已经注册的单例bean的集合中。
     */
    //此方法的调用链路：doGetBean() 处理不同 scope 时，如果是 singleton，则调用 getSingleton()
    // 在getSingleton()方法中调用此方法加入缓存
    //添加一级缓存，并且从二、三级缓存中删除
    protected void addSingleton(String beanName, Object singletonObject) {
        synchronized (this.singletonObjects) {
            this.singletonObjects.put(beanName, (singletonObject != null ? singletonObject : NULL_OBJECT));
            this.singletonFactories.remove(beanName);
            this.earlySingletonObjects.remove(beanName);
            this.registeredSingletons.add(beanName);
        }
    }

    /**
     * Add the given singleton factory for building the specified singleton
     * if necessary.
     * <p>To be called for eager registration of singletons, e.g. to be able to
     * resolve circular references.
     *
     * @param beanName         the name of the bean
     * @param singletonFactory the factory for the singleton object
     */
    // singletonFactories 这个三级缓存才是解决 Spring Bean 循环依赖的诀窍所在
    // 此方法的引用发生在createBeanInstance() 方法之后，
    // 也就是说这个 bean 其实已经被创建出来了，但是它还不是很完美（没有进行属性填充和初始化），
    // 但是对于其他依赖它的对象而言已经足够了（可以根据对象引用定位到堆中对象），
    // 能够被认出来了，所以 Spring 在这个时候选择将该对象提前曝光出来让大家认识认识。
    // 介绍到这里我们发现三级缓存 singletonFactories 和 二级缓存 earlySingletonObjects 中的值都有出处了，
    // 那一级缓存在哪里设置的呢？在类 DefaultSingletonBeanRegistry #addSingleton() 方法
    protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(singletonFactory, "Singleton factory must not be null");
        synchronized (this.singletonObjects) {
            // 若一级缓存singletonObjects中不包含此bean的实例
            // 加入三级缓存
            if (!this.singletonObjects.containsKey(beanName)) {
                this.singletonFactories.put(beanName, singletonFactory);
                this.earlySingletonObjects.remove(beanName);
                this.registeredSingletons.add(beanName);
            }
        }
    }

    @Override
    public Object getSingleton(String beanName) {
        return getSingleton(beanName, true);
    }

      /**
       * Return the (raw) singleton object registered under the given name.
       *
       * <p>Checks already instantiated singletons and also allows for an early reference to a currently
       * created singleton (resolving a circular reference).
       *
       * @param beanName the name of the bean to look for
       * @param allowEarlyReference whether early references should be created or not
       * @return the registered singleton object, or {@code null} if none found
       * @see DefaultSingletonBeanRegistry#getSingleton(String, ObjectFactory)
       */
      //这个方法主要是从三个缓存中获取，分别是：singletonObjects、earlySingletonObjects、singletonFactories：
      // 1. 首先是从一级缓存singletonObjects获取bean
      // 2. 如果没拿到且当前bean正在创建中,从二级缓存earlySingletonObjects中获取bean
      // 3. 还是没拿到且允许提早创建,从singletonFactories中获取bean
      // 4. 若不为null,则调用getObject()方法创建bean对象,并保存到earlySingletonObjects中,同时将自身中的objectFactory移除
      // 问题：缓存中的数据在何时加入呢？
      // 解答: 在 AbstractAutowireCapableBeanFactory#doCreateBean()方法中调用addSingletonFactory()方法加入三级缓存，
      //       在此处加入二级缓存，
      //       在类 DefaultSingletonBeanRegistry#getSingleton(String, ObjectFactory) 中调用#addSingleton() 方法加入一级缓存
      protected Object getSingleton(String beanName, boolean allowEarlyReference) {

          //1. 从singletonObjects和earlySingletonObjects中取实例。
        Object singletonObject = this.singletonObjects.get(beanName);
          //2. 如果为null且当前的单例的bean正在创建过程中
        if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
            // 锁定该全局变量singletonObjects进行相关的处理
            synchronized (this.singletonObjects) {
                // 从提前暴露的单例bean缓存中去获取
                singletonObject = this.earlySingletonObjects.get(beanName);
                //prototype时通常禁用allowEarlyReference
                //3. 如果为null,且允许提前创建
                if (singletonObject == null && allowEarlyReference) {
                    // 三级缓存 singletonFactories 获取ObjectFactory
                    ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                    if (singletonFactory != null) {
                        // 通过ObjectFactory的getObject() 获取对象，
                        // 并将其加入到二级缓存 earlySingletonObjects 中 从三级缓存 singletonFactories 删除ObjectFactory
                        singletonObject = singletonFactory.getObject();
                        this.earlySingletonObjects.put(beanName, singletonObject);
                        // ObjectFactory只使用一次
                        this.singletonFactories.remove(beanName);
                    }
                }
            }
        }
        return (singletonObject != NULL_OBJECT ? singletonObject : null);
    }

    /**
     * 返回在给定名称下注册的（raw）singleton对象，如果尚未注册，则创建并注册一个新对象。
     *
     * @param beanName         the name of the bean
     * @param singletonFactory the ObjectFactory to lazily create the singleton
     *                         with, if necessary
     * @return the registered singleton object
     */
    public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
        Assert.notNull(beanName, "'beanName' must not be null");
        synchronized (this.singletonObjects) {
            //1. 再次检查缓存是否已经加载过，如果已经加载了则直接返回，否则开始加载过程。
            // 因为 singleton 模式其实就是复用已经创建的 bean 所以这步骤必须检查
            Object singletonObject = this.singletonObjects.get(beanName);
            //  为空，开始加载过程
            if (singletonObject == null) {
                //判断当前bean是否是销毁状态
                if (this.singletonsCurrentlyInDestruction) {
                    throw new BeanCreationNotAllowedException(beanName,
                            "Singleton bean creation not allowed while the singletons of this factory are in destruction " +
                                    "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
                }
                //2. 前置处理
                //将beanName添加到singletonsCurrentlyInCreation中
                beforeSingletonCreation(beanName);
                boolean newSingleton = false;
                //suppressedExceptions用来保存单例bean获取过程中的不必要的异常
                boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = new LinkedHashSet<Exception>();     //记录异常。
                }
                try {
                    //3. 初始化 bean
                    //使用ObjectFactory中的getObject方法调用createBean方法。（真正获取单例 bean 的方法）
                    singletonObject = singletonFactory.getObject();
                    newSingleton = true;
                } catch (IllegalStateException ex) {
                    // Has the singleton object implicitly appeared in the meantime ->
                    // if yes, proceed with it since the exception indicates that state.
                    singletonObject = this.singletonObjects.get(beanName);
                    if (singletonObject == null) {
                        throw ex;
                    }
                } catch (BeanCreationException ex) {
                    if (recordSuppressedExceptions) {
                        for (Exception suppressedException : this.suppressedExceptions) {
                            ex.addRelatedCause(suppressedException);
                        }
                    }
                    throw ex;
                } finally {
                    if (recordSuppressedExceptions) {
                        this.suppressedExceptions = null;
                    }
                    //4. 后置处理
                    //把beanName从singletonsCurrentlyInCreation中移除
                    afterSingletonCreation(beanName);
                }
                //5. 加入缓存中
                if (newSingleton) {
                    //删除该beanName对应的ObjectFactory
                    //从提前暴露的缓冲区中删除beanName对应的Entry
                    //添加到已经注册的单例bean的集合中。
                    addSingleton(beanName, singletonObject);
                }
            }
            return (singletonObject != NULL_OBJECT ? singletonObject : null);
        }
    }

    /**
     * Register an Exception that happened to get suppressed during the creation of a
     * singleton bean instance, e.g. a temporary circular reference resolution problem.
     *
     * @param ex the Exception to register
     */
    protected void onSuppressedException(Exception ex) {
        synchronized (this.singletonObjects) {
            if (this.suppressedExceptions != null) {
                this.suppressedExceptions.add(ex);
            }
        }
    }

    /**
     * Remove the bean with the given name from the singleton cache of this factory,
     * to be able to clean up eager registration of a singleton if creation failed.
     *
     * @param beanName the name of the bean
     * @see #getSingletonMutex()
     */
    protected void removeSingleton(String beanName) {
        synchronized (this.singletonObjects) {
            this.singletonObjects.remove(beanName);
            this.singletonFactories.remove(beanName);
            this.earlySingletonObjects.remove(beanName);
            this.registeredSingletons.remove(beanName);
        }
    }

    @Override
    public boolean containsSingleton(String beanName) {
        return this.singletonObjects.containsKey(beanName);
    }

    @Override
    public String[] getSingletonNames() {
        synchronized (this.singletonObjects) {
            return StringUtils.toStringArray(this.registeredSingletons);
        }
    }

    @Override
    public int getSingletonCount() {
        synchronized (this.singletonObjects) {
            return this.registeredSingletons.size();
        }
    }


    public void setCurrentlyInCreation(String beanName, boolean inCreation) {
        Assert.notNull(beanName, "Bean name must not be null");
        if (!inCreation) {
            this.inCreationCheckExclusions.add(beanName);
        } else {
            this.inCreationCheckExclusions.remove(beanName);
        }
    }

    public boolean isCurrentlyInCreation(String beanName) {
        Assert.notNull(beanName, "Bean name must not be null");
        return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
    }

    protected boolean isActuallyInCreation(String beanName) {
        return isSingletonCurrentlyInCreation(beanName);
    }

    /**
     * Return whether the specified singleton bean is currently in creation
     * (within the entire factory).
     *
     * @param beanName the name of the bean
     */
    public boolean isSingletonCurrentlyInCreation(String beanName) {
        return this.singletonsCurrentlyInCreation.contains(beanName);
    }

    /**
     * Callback before singleton creation.
     * <p>The default implementation register the singleton as currently in creation.
     *
     * @param beanName the name of the singleton about to be created
     * @see #isSingletonCurrentlyInCreation
     */
    protected void beforeSingletonCreation(String beanName) {
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }
    }

    /**
     * Callback after singleton creation.
     * <p>The default implementation marks the singleton as not in creation anymore.
     *
     * @param beanName the name of the singleton that has been created
     * @see #isSingletonCurrentlyInCreation
     */
    protected void afterSingletonCreation(String beanName) {
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
            throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
        }
    }


    /**
     * Add the given bean to the list of disposable beans in this registry.
     * <p>Disposable beans usually correspond to registered singletons,
     * matching the bean name but potentially being a different instance
     * (for example, a DisposableBean adapter for a singleton that does not
     * naturally implement Spring's DisposableBean interface).
     *
     * @param beanName the name of the bean
     * @param bean     the bean instance
     */
    public void registerDisposableBean(String beanName, DisposableBean bean) {
        synchronized (this.disposableBeans) {
            this.disposableBeans.put(beanName, bean);
        }
    }

    /**
     * Register a containment relationship between two beans,
     * e.g. between an inner bean and its containing outer bean.
     * <p>Also registers the containing bean as dependent on the contained bean
     * in terms of destruction order.
     *
     * @param containedBeanName  the name of the contained (inner) bean
     * @param containingBeanName the name of the containing (outer) bean
     * @see #registerDependentBean
     */
    public void registerContainedBean(String containedBeanName, String containingBeanName) {
        // A quick check for an existing entry upfront, avoiding synchronization...
        Set<String> containedBeans = this.containedBeanMap.get(containingBeanName);
        if (containedBeans != null && containedBeans.contains(containedBeanName)) {
            return;
        }

        // No entry yet -> fully synchronized manipulation of the containedBeans Set
        synchronized (this.containedBeanMap) {
            containedBeans = this.containedBeanMap.get(containingBeanName);
            if (containedBeans == null) {
                containedBeans = new LinkedHashSet<String>(8);
                this.containedBeanMap.put(containingBeanName, containedBeans);
            }
            containedBeans.add(containedBeanName);
        }
        registerDependentBean(containedBeanName, containingBeanName);
    }

    /**
     * Register a dependent bean for the given bean,
     * to be destroyed before the given bean is destroyed.
     *
     * @param beanName          the name of the bean
     * @param dependentBeanName the name of the dependent bean
     */
    //依赖注册
    public void registerDependentBean(String beanName, String dependentBeanName) {
        // A quick check for an existing entry upfront, avoiding synchronization...
        //快速检查
        String canonicalName = canonicalName(beanName);
        Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
        if (dependentBeans != null && dependentBeans.contains(dependentBeanName)) {
            return;
        }

        // No entry yet -> fully synchronized manipulation of the dependentBeans Set
        synchronized (this.dependentBeanMap) {
            dependentBeans = this.dependentBeanMap.get(canonicalName);
            if (dependentBeans == null) {
                dependentBeans = new LinkedHashSet<String>(8);
                this.dependentBeanMap.put(canonicalName, dependentBeans);
            }
            dependentBeans.add(dependentBeanName);
        }
        synchronized (this.dependenciesForBeanMap) {
            Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(dependentBeanName);
            if (dependenciesForBean == null) {
                dependenciesForBean = new LinkedHashSet<String>(8);
                this.dependenciesForBeanMap.put(dependentBeanName, dependenciesForBean);
            }
            dependenciesForBean.add(canonicalName);
        }
    }

    /**
     * Determine whether the specified dependent bean has been registered as
     * dependent on the given bean or on any of its transitive dependencies.
     *
     * @param beanName          the name of the bean to check
     * @param dependentBeanName the name of the dependent bean
     * @since 4.0
     */
    //校验该依赖是否已经注册给当前 bean。
    protected boolean isDependent(String beanName, String dependentBeanName) {
        return isDependent(beanName, dependentBeanName, null);
    }

    //dependentBeanMap保存依赖 beanName 之间的映射关系：beanName - > 依赖 beanName 的集合
    // alreadySeen 已经检测的依赖 bean
    private boolean isDependent(String beanName, String dependentBeanName, Set<String> alreadySeen) {
        // 获取原始beanName
        String canonicalName = canonicalName(beanName);
        // alreadySeen 已经检测的依赖 bean
        if (alreadySeen != null && alreadySeen.contains(beanName)) {
            return false;
        }
        // 获取当前 beanName 的依赖集合
        Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
        if (dependentBeans == null) {
            return false;
        }
        // 存在，则证明存在已经注册的依赖
        if (dependentBeans.contains(dependentBeanName)) {
            return true;
        }
        // 递归检测依赖
        for (String transitiveDependency : dependentBeans) {
            if (alreadySeen == null) {
                alreadySeen = new HashSet<String>();
            }
            alreadySeen.add(beanName);
            if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether a dependent bean has been registered for the given name.
     *
     * @param beanName the name of the bean to check
     */
    protected boolean hasDependentBean(String beanName) {
        return this.dependentBeanMap.containsKey(beanName);
    }

    /**
     * Return the names of all beans which depend on the specified bean, if any.
     *
     * @param beanName the name of the bean
     * @return the array of dependent bean names, or an empty array if none
     */
    public String[] getDependentBeans(String beanName) {
        Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
        if (dependentBeans == null) {
            return new String[0];
        }
        return StringUtils.toStringArray(dependentBeans);
    }

    /**
     * Return the names of all beans that the specified bean depends on, if any.
     *
     * @param beanName the name of the bean
     * @return the array of names of beans which the bean depends on,
     * or an empty array if none
     */
    public String[] getDependenciesForBean(String beanName) {
        Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
        if (dependenciesForBean == null) {
            return new String[0];
        }
        return dependenciesForBean.toArray(new String[dependenciesForBean.size()]);
    }

    public void destroySingletons() {
        if (logger.isDebugEnabled()) {
            logger.debug("Destroying singletons in " + this);
        }
        synchronized (this.singletonObjects) {
            this.singletonsCurrentlyInDestruction = true;
        }

        String[] disposableBeanNames;
        synchronized (this.disposableBeans) {
            disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
        }
        for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
            destroySingleton(disposableBeanNames[i]);
        }

        this.containedBeanMap.clear();
        this.dependentBeanMap.clear();
        this.dependenciesForBeanMap.clear();

        synchronized (this.singletonObjects) {
            this.singletonObjects.clear();
            this.singletonFactories.clear();
            this.earlySingletonObjects.clear();
            this.registeredSingletons.clear();
            this.singletonsCurrentlyInDestruction = false;
        }
    }

    /**
     * Destroy the given bean. Delegates to {@code destroyBean}
     * if a corresponding disposable bean instance is found.
     *
     * @param beanName the name of the bean
     * @see #destroyBean
     */
    public void destroySingleton(String beanName) {
        // Remove a registered singleton of the given name, if any.
        removeSingleton(beanName);

        // Destroy the corresponding DisposableBean instance.
        DisposableBean disposableBean;
        synchronized (this.disposableBeans) {
            disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
        }
        destroyBean(beanName, disposableBean);
    }

    /**
     * Destroy the given bean. Must destroy beans that depend on the given
     * bean before the bean itself. Should not throw any exceptions.
     *
     * @param beanName the name of the bean
     * @param bean     the bean instance to destroy
     */
    protected void destroyBean(String beanName, DisposableBean bean) {
        // Trigger destruction of dependent beans first...
        Set<String> dependencies = this.dependentBeanMap.remove(beanName);
        if (dependencies != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
            }
            for (String dependentBeanName : dependencies) {
                destroySingleton(dependentBeanName);
            }
        }

        // Actually destroy the bean now...
        if (bean != null) {
            try {
                bean.destroy();
            } catch (Throwable ex) {
                logger.error("Destroy method on bean with name '" + beanName + "' threw an exception", ex);
            }
        }

        // Trigger destruction of contained beans...
        Set<String> containedBeans = this.containedBeanMap.remove(beanName);
        if (containedBeans != null) {
            for (String containedBeanName : containedBeans) {
                destroySingleton(containedBeanName);
            }
        }

        // Remove destroyed bean from other beans' dependencies.
        synchronized (this.dependentBeanMap) {
            for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Set<String>> entry = it.next();
                Set<String> dependenciesToClean = entry.getValue();
                dependenciesToClean.remove(beanName);
                if (dependenciesToClean.isEmpty()) {
                    it.remove();
                }
            }
        }

        // Remove destroyed bean's prepared dependency information.
        this.dependenciesForBeanMap.remove(beanName);
    }

    /**
     * Exposes the singleton mutex to subclasses and external collaborators.
     * <p>Subclasses should synchronize on the given Object if they perform
     * any sort of extended singleton creation phase. In particular, subclasses
     * should <i>not</i> have their own mutexes involved in singleton creation,
     * to avoid the potential for deadlocks in lazy-init situations.
     */
    public final Object getSingletonMutex() {
        return this.singletonObjects;
    }

}
