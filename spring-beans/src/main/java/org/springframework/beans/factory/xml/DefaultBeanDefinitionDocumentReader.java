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

package org.springframework.beans.factory.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 * <p>
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

    public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

    public static final String NESTED_BEANS_ELEMENT = "beans";

    public static final String ALIAS_ELEMENT = "alias";

    public static final String NAME_ATTRIBUTE = "name";

    public static final String ALIAS_ATTRIBUTE = "alias";

    public static final String IMPORT_ELEMENT = "import";

    public static final String RESOURCE_ATTRIBUTE = "resource";

    public static final String PROFILE_ATTRIBUTE = "profile";


    protected final Log logger = LogFactory.getLog(getClass());

    private XmlReaderContext readerContext;

    private BeanDefinitionParserDelegate delegate;


    /**
     * This implementation parses bean definitions according to the "spring-beans" XSD
     * (or DTD, historically).
     * <p>Opens a DOM Document; then initializes the default settings
     * specified at the {@code <beans/>} level; then parses the contained bean definitions.
     */
    @Override
    public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
        // 获得XML描述符
        this.readerContext = readerContext;
        logger.debug("Loading bean definitions");
        // 获得Document的根元素
        Element root = doc.getDocumentElement();
        //解析并注册BeanDefinition。
        doRegisterBeanDefinitions(root);
    }

    /**
     * Return the descriptor for the XML resource that this parser works on.
     */
    protected final XmlReaderContext getReaderContext() {
        return this.readerContext;
    }

    /**
     * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor} to pull the
     * source metadata from the supplied {@link Element}.
     */
    protected Object extractSource(Element ele) {
        return getReaderContext().extractSource(ele);
    }


    /**
     * Register each bean definition within the given root {@code <beans/>} element.
     */
    protected void doRegisterBeanDefinitions(Element root) {
        //任何嵌套的<beans>元素都将导致此方法中的递归。
        // 为了正确传播和保留<beans> default- *属性，请跟踪当前（父）委托，该委托可以为null。
        // 创建一个新的（子）委托并引用其父项以进行回退，然后最终将其重置。将其恢复为原始（父）引用。
        // 此行为模拟了一组委托而实际上并不需要。
        BeanDefinitionParserDelegate parent = this.delegate;
        this.delegate = createDelegate(getReaderContext(), root, parent);
        //1. 程序首先处理 profile属性，profile主要用于我们切换环境，比如切换开发、测试、生产环境，非常方便。
        if (this.delegate.isDefaultNamespace(root)) {
            String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
            if (StringUtils.hasText(profileSpec)) {
                String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
                        profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
                if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
                    return;
                }
            }
        }

        // preProcessXml() 和 postProcessXml() 方法来进行前、后处理，目前这两个方法都是空实现，交由子类来实现。
        //模板方法，可用户自定义
        preProcessXml(root);
        //2. 进行解析
        parseBeanDefinitions(root, this.delegate);
        //模板方法，可用户自定义
        postProcessXml(root);

        this.delegate = parent;
    }

    protected BeanDefinitionParserDelegate createDelegate(
            XmlReaderContext readerContext, Element root, BeanDefinitionParserDelegate parentDelegate) {

        BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
        delegate.initDefaults(root, parentDelegate);
        return delegate;
    }

    /**
     * Parse the elements at the root level in the document:
     * "import", "alias", "bean".
     *
     * @param root the DOM root element of the document
     */
    //解析标签信息，并构造BeanDefinition注册。

    protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
        // Bean定义的Document对象使用了Spring默认的XML命名空间
        if (delegate.isDefaultNamespace(root)) {
            // 获取Bean定义的Document对象根元素的所有子节点
            NodeList nl = root.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                /**
                 * node是xml配置中每一行的内容，包括空行
                 */
                Node node = nl.item(i);
                // 获得Document节点是XML元素节点
                if (node instanceof Element) {
                    Element ele = (Element) node;
                    //这里有两个分支。两种方式的读取和解析都存在较大的差异，所以采用不同的解析方法
                    // Bean定义的Document的元素节点使用的是Spring默认的XML命名空间
                    //一个是处理默认的节点(import、alias、bean、beans)
                    //一个是处理自定义注解方式：<tx:annotation-driven>
                    if (delegate.isDefaultNamespace(ele)) {
                        // 使用Spring的Bean规则解析元素节点（默认解析规则）
                        //注册BeanDefinition对象，和component-scan扫描的bean注册一样。向容器中填充对象。
                        //不管是XML配置的Bean，还是通过component-scan扫描注册的Bean它们最后都是殊途同归的，
                        // 会转换成一个BeanDefinition对象。记录着这个Bean对象的属性和方法，最后都注册到容器中，等待在实例化和IOC的时候遍历它们。
                        parseDefaultElement(ele, delegate);
                    } else {
                        // 没有使用Spring默认的XML命名空间，则使用用户自定义的解析规则解析元素节点
                        // 自定义标签，如：<aop:config>,<context:component-scan>等
                        delegate.parseCustomElement(ele);
                    }
                }
            }
        } else {
            delegate.parseCustomElement(root);
        }
    }

    //分别是对四种不同的标签进行解析，分别是 import、alias、bean、beans。

    private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
        // 对 import 标签的解析
        if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
            importBeanDefinitionResource(ele);
        }
        // 对 alias 标签的解析
        else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
            processAliasRegistration(ele);
        }
        // 对 bean 标签的解析，最重要也是最复杂
        else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
            //解析了bean元素，并完成BeanDefinition的注册。
            processBeanDefinition(ele, delegate);
        }
        // 对 beans 标签的解析
        else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
            //递归
            doRegisterBeanDefinitions(ele);
        }
    }

      /**
       * 解析 import 标签，并将给定资源中的bean加载到beanFactory中
       * 整个过程如下：
       * 1. 获取 source 属性的值，该值表示资源的路径
       * 2. 解析路径中的系统属性，如"${user.dir}"
       * 3. 判断资源路径 location 是绝对路径还是相对路径
       * 3. 如果是绝对路径，则调递归调用 Bean 的解析过程，进行另一次的解析
       * 4. 如果是相对路径，则先计算出绝对路径得到 Resource，然后进行解析
       * 5. 通知监听器，完成解析
       */
      protected void importBeanDefinitionResource(Element ele) {
        // 获取 resource 的属性值  例：<import resource="spring-XXX.xml"/>
        String location = ele.getAttribute(RESOURCE_ATTRIBUTE);

        // 为空，直接退出
        if (!StringUtils.hasText(location)) {
            getReaderContext().error("Resource location must not be empty", ele);
            return;
        }

        // 解析系统属性，格式如 ："${user.dir}"
        location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

        Set<Resource> actualResources = new LinkedHashSet<Resource>(4);

        // 判断 location 是相对路径还是绝对路径
        boolean absoluteLocation = false;
        try {
          // 以 classpath*: 或者 classpath: 开头为绝对路径
          // 能够通过该 location 构建出 java.net.URL为绝对路径
          // 根据 location 构造 java.net.URI 判断调用 isAbsolute() 判断是否为绝对路径
          absoluteLocation =
          ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
        } catch (URISyntaxException ex) {
            // cannot convert to an URI, considering the location relative
            // unless it is the well-known Spring prefix "classpath*:"
        }

        // 绝对路径
        if (absoluteLocation) {
            try {
                // 直接根据地址加载相应的配置文件
                int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
                if (logger.isDebugEnabled()) {
                    logger.debug("Imported " + importCount + " bean definitions from URL location [" + location + "]");
                }
            } catch (BeanDefinitionStoreException ex) {
                getReaderContext().error(
                        "Failed to import bean definitions from URL location [" + location + "]", ele, ex);
            }
        } else {
            // 相对路径则根据相应的地址计算出绝对路径地址
            try {
                int importCount;
                Resource relativeResource = getReaderContext().getResource().createRelative(location);
                if (relativeResource.exists()) {
                    importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
                    actualResources.add(relativeResource);
                } else {
                    String baseLocation = getReaderContext().getResource().getURL().toString();
                    importCount = getReaderContext().getReader().loadBeanDefinitions(
                            StringUtils.applyRelativePath(baseLocation, location), actualResources);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Imported " + importCount + " bean definitions from relative location [" + location + "]");
                }
            } catch (IOException ex) {
                getReaderContext().error("Failed to resolve current resource location", ele, ex);
            } catch (BeanDefinitionStoreException ex) {
                getReaderContext().error("Failed to import bean definitions from relative location [" + location + "]",
                        ele, ex);
            }
        }
        Resource[] actResArray = actualResources.toArray(new Resource[actualResources.size()]);
        // 解析成功后，进行监听器激活处理
        getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
    }

    /**
     * Process the given alias element, registering the alias with the registry.
     */
    protected void processAliasRegistration(Element ele) {
        String name = ele.getAttribute(NAME_ATTRIBUTE);
        String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
        boolean valid = true;
        if (!StringUtils.hasText(name)) {
            getReaderContext().error("Name must not be empty", ele);
            valid = false;
        }
        if (!StringUtils.hasText(alias)) {
            getReaderContext().error("Alias must not be empty", ele);
            valid = false;
        }
        if (valid) {
            try {
                getReaderContext().getRegistry().registerAlias(name, alias);
            } catch (Exception ex) {
                getReaderContext().error("Failed to register alias '" + alias +
                        "' for bean with name '" + name + "'", ele, ex);
            }
            getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
        }
    }

    /**
     * 处理给定的bean标签，解析bean定义并将其注册到注册表中
     */
    protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
        //先解析xml标签生成BeanDefinition，再用BeanDefinitionHolder封装BeanDefinition
        // （BeanDefinitionHolder为持有beanName 和alias的BeanDefinition）。
        BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
        if (bdHolder != null) {
            //装饰bdHolder进行自定义标签处理   (因为我们可能对该标签有自己定义的属性)
            bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
            try {
                // Register the final decorated instance.
                // 完成解析后,对bdHolder进行注册
                // 1.首先是需要一个注册器
                // 2.将注册的任务委托给BeanDefinitionReaderUtils#registerBeanDefinition()方法完成最后的注册
                //   完成beanName到BeanDefinition的映射，alias到beanName的映射。
                BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
            } catch (BeanDefinitionStoreException ex) {
                getReaderContext().error("Failed to register bean definition with name '" +
                        bdHolder.getBeanName() + "'", ele, ex);
            }
            // 发出响应事件，通知相关的监听器，完成 Bean 标签解析
            getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
        }
    }


    /**
     * Allow the XML to be extensible by processing any custom element types first,
     * before we start to process the bean definitions. This method is a natural
     * extension point for any other custom pre-processing of the XML.
     * <p>The default implementation is empty. Subclasses can override this method to
     * convert custom elements into standard Spring bean definitions, for example.
     * Implementors have access to the parser's bean definition reader and the
     * underlying XML resource, through the corresponding accessors.
     *
     * @see #getReaderContext()
     */
    protected void preProcessXml(Element root) {
    }

    /**
     * Allow the XML to be extensible by processing any custom element types last,
     * after we finished processing the bean definitions. This method is a natural
     * extension point for any other custom post-processing of the XML.
     * <p>The default implementation is empty. Subclasses can override this method to
     * convert custom elements into standard Spring bean definitions, for example.
     * Implementors have access to the parser's bean definition reader and the
     * underlying XML resource, through the corresponding accessors.
     *
     * @see #getReaderContext()
     */
    protected void postProcessXml(Element root) {
    }

}
