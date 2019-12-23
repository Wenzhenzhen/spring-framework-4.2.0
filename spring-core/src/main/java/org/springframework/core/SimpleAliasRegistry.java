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

package org.springframework.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Simple implementation of the {@link AliasRegistry} interface.
 * Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

    /**
     * Map from alias to canonical name
     */
    private final Map<String, String> aliasMap = new ConcurrentHashMap<String, String>(16);


    @Override
    public void registerAlias(String name, String alias) {
        //注册的前的去null判断
        Assert.hasText(name, "'name' must not be empty");
        Assert.hasText(alias, "'alias' must not be empty");

        if (alias.equals(name)) {
            //如果beanName与alias相同的话,从aliasMap移除相同的alias
            this.aliasMap.remove(alias);
        } else {
            //alias和beanName不同，不允许别名覆盖
            if (!allowAliasOverriding()) {
                // 从缓存中获取name
                String registeredName = this.aliasMap.get(alias);
                //如果存在,就不要注册了,直接抛异常
                if (registeredName != null && !registeredName.equals(name)) {
                    throw new IllegalStateException("Cannot register alias '" + alias + "' for name '" +
                            name + "': It is already registered for name '" + registeredName + "'.");
                }
            }
            // 当 A --> B 存在时，如果再次出现 A --> B --> C 则抛出异常
            checkForAliasCircle(name, alias);
            // 注册 alias
            this.aliasMap.put(alias, name);
        }
    }

    /**
     * Return whether alias overriding is allowed.
     * Default is {@code true}.
     */
    protected boolean allowAliasOverriding() {
        return true;
    }

    @Override
    public void removeAlias(String alias) {
        String name = this.aliasMap.remove(alias);
        if (name == null) {
            throw new IllegalStateException("No alias '" + alias + "' registered");
        }
    }

    @Override
    public boolean isAlias(String name) {
        return this.aliasMap.containsKey(name);
    }

    @Override
    public String[] getAliases(String name) {
        List<String> result = new ArrayList<String>();
        synchronized (this.aliasMap) {
            retrieveAliases(name, result);
        }
        return StringUtils.toStringArray(result);
    }

    /**
     * Transitively retrieve all aliases for the given name.
     *
     * @param name   the target name to find aliases for
     * @param result the resulting aliases list
     */
    private void retrieveAliases(String name, List<String> result) {
        for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
            String registeredName = entry.getValue();
            if (registeredName.equals(name)) {
                String alias = entry.getKey();//找到别名
                result.add(alias);
                retrieveAliases(alias, result);//找别名的别名。也放入list中。
            }
        }
    }

    /**
     * Resolve all alias target names and aliases registered in this
     * factory, applying the given StringValueResolver to them.
     * <p>The value resolver may for example resolve placeholders
     * in target bean names and even in alias names.
     *
     * @param valueResolver the StringValueResolver to apply
     */
    public void resolveAliases(StringValueResolver valueResolver) {
        Assert.notNull(valueResolver, "StringValueResolver must not be null");
        synchronized (this.aliasMap) {
            Map<String, String> aliasCopy = new HashMap<String, String>(this.aliasMap);
            for (String alias : aliasCopy.keySet()) {
                String registeredName = aliasCopy.get(alias);
                String resolvedAlias = valueResolver.resolveStringValue(alias);
                String resolvedName = valueResolver.resolveStringValue(registeredName);
                if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
                    this.aliasMap.remove(alias);
                } else if (!resolvedAlias.equals(alias)) {
                    String existingName = this.aliasMap.get(resolvedAlias);
                    if (existingName != null && !existingName.equals(resolvedName)) {
                        throw new IllegalStateException(
                                "Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
                                        "') for name '" + resolvedName + "': It is already registered for name '" +
                                        registeredName + "'.");
                    }
                    checkForAliasCircle(resolvedName, resolvedAlias);
                    this.aliasMap.remove(alias);
                    this.aliasMap.put(resolvedAlias, resolvedName);
                } else if (!registeredName.equals(resolvedName)) {
                    this.aliasMap.put(alias, resolvedName);
                }
            }
        }
    }

    /**
     * Determine the raw name, resolving aliases to canonical names.
     * 确定原始名称，将别名解析为规范名称
     * aliasMap中是别名到beanName的映射
     * @param name the user-specified name
     * @return the transformed name
     */
    public String canonicalName(String name) {
        String canonicalName = name;
        // Handle aliasing...
        String resolvedName;
        do {
            resolvedName = this.aliasMap.get(canonicalName);
            if (resolvedName != null) {
                canonicalName = resolvedName;
            }
        }
        while (resolvedName != null);
        return canonicalName;
    }

    /**
     * Check whether the given name points back to given alias as an alias
     * in the other direction, catching a circular reference upfront and
     * throwing a corresponding IllegalStateException.
     *
     * @param name  the candidate name
     * @param alias the candidate alias
     * @see #registerAlias
     */
    protected void checkForAliasCircle(String name, String alias) {
        if (alias.equals(canonicalName(name))) {
            throw new IllegalStateException("Cannot register alias '" + alias +
                    "' for name '" + name + "': Circular reference - '" +
                    name + "' is a direct or indirect alias for '" + alias + "' already");
        }
    }

}
