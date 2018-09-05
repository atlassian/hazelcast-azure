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

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.credentials.MSICredentials;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static com.hazelcast.azure.AzureProperties.CLIENT_ID;
import static com.hazelcast.azure.AzureProperties.CLIENT_SECRET;
import static com.hazelcast.azure.AzureProperties.TENANT_ID;
import static com.hazelcast.azure.AzureProperties.SUBSCRIPTION_ID;

/**
 * Azure client helper
 */
public final class AzureClientHelper {

    private static final ILogger LOGGER = Logger.getLogger(AzureClientHelper.class);

    private AzureClientHelper() {
    }

    /**
     * Create a compute manager client to manage compute resources
     *
     * @param properties the properties Map provided by Hazelcast
     * @return ComputeManager a client to manage compute resources
     */
    public static ComputeManager getComputeManager(Map<String, Comparable> properties) {
        AzureTokenCredentials atc;
        String clientId = AzureProperties.getOrNull(CLIENT_ID, properties);
        if (StringUtils.isBlank(clientId)) {
            LOGGER.info("Using managed system identity credentials");
            atc = new MSICredentials();
        } else {
            atc = new ApplicationTokenCredentials(
                    clientId,
                    AzureProperties.<String>getOrNull(TENANT_ID, properties),
                    AzureProperties.<String>getOrNull(CLIENT_SECRET, properties), null);
        }
        return ComputeManager.authenticate(atc, AzureProperties.<String>getOrNull(SUBSCRIPTION_ID, properties));
    }
}
