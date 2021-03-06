/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.azure;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Factory class which returns {@link AzureDiscoveryStrategy} to Discovery SPI
 */
public class AzureDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

    private static final Collection<PropertyDefinition> ALL_PROPERTY_DEFINITIONS;
    private static final Collection<PropertyDefinition> REQUIRED_PROPERTY_DEFINITIONS;

    static {
        List<PropertyDefinition> requiredPropertyDefinitions = new ArrayList<PropertyDefinition>();
        requiredPropertyDefinitions.add(AzureProperties.CLUSTER_ID);
        requiredPropertyDefinitions.add(AzureProperties.GROUP_NAME);
        requiredPropertyDefinitions.add(AzureProperties.SUBSCRIPTION_ID);
        REQUIRED_PROPERTY_DEFINITIONS = Collections.unmodifiableCollection(requiredPropertyDefinitions);

        List<PropertyDefinition> allPropertyDefinitions = new ArrayList<PropertyDefinition>(requiredPropertyDefinitions);
        allPropertyDefinitions.add(AzureProperties.CLIENT_ID);
        allPropertyDefinitions.add(AzureProperties.CLIENT_SECRET);
        allPropertyDefinitions.add(AzureProperties.TENANT_ID);
        ALL_PROPERTY_DEFINITIONS = Collections.unmodifiableCollection(allPropertyDefinitions);
    }

    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return AzureDiscoveryStrategy.class;
    }

    @Override
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode node, ILogger logger, Map<String, Comparable> properties) {
        // validate configuration
        for (PropertyDefinition prop : REQUIRED_PROPERTY_DEFINITIONS) {
            if (StringUtils.isBlank(AzureProperties.<String>getOrNull(prop, properties))) {
                throw new IllegalArgumentException("Property, " + prop.key() + " cannot be null");
            }
        }

        return new AzureDiscoveryStrategy(properties);
    }

    /**
     * Gets the configuration property definitions
     *
     * @return {@code Collection<PropertyDefinition>} the property defitions for the AzureDiscoveryStrategy
     */
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return ALL_PROPERTY_DEFINITIONS;
    }
}
