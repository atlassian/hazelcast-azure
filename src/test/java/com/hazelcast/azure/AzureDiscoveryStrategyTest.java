/*
 * Copyright (c) 2016, Microsoft Corporation. All Rights Reserved.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.partitiongroup.PartitionGroupMetaData;
import com.hazelcast.test.HazelcastTestSupport;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.compute.*;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.management.network.NicIPConfiguration;
import com.microsoft.azure.management.network.PublicIPAddress;
import com.microsoft.azure.management.network.VirtualMachineScaleSetNetworkInterface;
import com.microsoft.rest.RestException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static com.hazelcast.azure.AzureClientHelper.getComputeManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(fullyQualifiedNames = {
        "com.microsoft.windowsazure.core.*",
        "com.microsoft.azure.management.compute.*",
        "com.microsoft.azure.management.network.*",
        "com.hazelcast.azure.AzureClientHelper"
})
public class AzureDiscoveryStrategyTest extends HazelcastTestSupport {

    private Map<String, Comparable> properties;
    private ArrayList<VirtualMachine> virtualMachines;
    private ComputeManager computeManager = mock(ComputeManager.class);
    private VirtualMachines vmService = mock(VirtualMachines.class);
    private VirtualMachineScaleSets scaleSetService = mock(VirtualMachineScaleSets.class);

    {
        properties = new HashMap<String, Comparable>();
        properties.put("client-id", "test-value");
        properties.put("client-secret", "test-value");
        properties.put("subscription-id", "test-value");
        properties.put("cluster-id", "cluster000");
        properties.put("tenant-id", "test-value");
        properties.put("group-name", "test-value");
        virtualMachines = new ArrayList<VirtualMachine>();
    }

    private final int FAULT_DOMAIN_ID = 2099;

    @Before
    public void setup() {
        PowerMockito.mockStatic(AzureClientHelper.class);
        Mockito.when(getComputeManager(properties)).thenReturn(computeManager);
        when(computeManager.virtualMachines()).thenReturn(vmService);
        when(computeManager.virtualMachineScaleSets()).thenReturn(scaleSetService);
    }

    private void buildFakeVmList(int count) {
        virtualMachines.clear();
        for (int i = 0; i < count; i++) {
            createVMWithIp(i, null);
        }
        PagedList<VirtualMachine> machinesPage = new PagedList<VirtualMachine>() {
            @Override
            public Page<VirtualMachine> nextPage(String s) throws RestException {
                return null;
            }
        };
        machinesPage.addAll(virtualMachines);
        when(vmService.listByResourceGroup(eq("test-value"))).thenReturn(machinesPage);
        PagedList<VirtualMachineScaleSet> scaleSetsPage = new PagedList<VirtualMachineScaleSet>() {
            @Override
            public Page<VirtualMachineScaleSet> nextPage(String s) throws RestException {
                return null;
            }
        };
        when(scaleSetService.listByResourceGroup(eq("test-value"))).thenReturn(scaleSetsPage);
    }

    private void buildFakeVm(int count, String ip) {
        virtualMachines.clear();
        createVMWithIp(count, ip);
        PagedList<VirtualMachine> machinesPage = new PagedList<VirtualMachine>() {
            @Override
            public Page<VirtualMachine> nextPage(String s) throws RestException {
                return null;
            }
        };
        machinesPage.addAll(virtualMachines);
        when(vmService.listByResourceGroup(eq("test-value"))).thenReturn(machinesPage);
    }

    private void createVMWithIp(int i, String ipAddress) {
        VirtualMachine vm = mock(VirtualMachine.class);
        when(vm.tags()).thenReturn(ImmutableMap.of(properties.get("cluster-id").toString(), "5701"));
        when(vm.powerState()).thenReturn(PowerState.RUNNING);
        VirtualMachineInstanceView vmInstance = mock(VirtualMachineInstanceView.class);
        when(vm.instanceView()).thenReturn(vmInstance);
        when(vmInstance.platformFaultDomain()).thenReturn(FAULT_DOMAIN_ID);

        NetworkInterface networkInterface = mock(NetworkInterface.class);
        when(vm.getPrimaryNetworkInterface()).thenReturn(networkInterface);
        NicIPConfiguration ipConfiguration = mock(NicIPConfiguration.class);
        when(networkInterface.ipConfigurations()).thenReturn(ImmutableMap.of("nic-name", ipConfiguration));
        when(ipConfiguration.privateIPAddress()).thenReturn("10.0.5." + i);

        PublicIPAddress publicIPAddress = mock(PublicIPAddress.class);
        when(ipConfiguration.getPublicIPAddress()).thenReturn(publicIPAddress);
        if (ipAddress == null) {
            when(publicIPAddress.ipAddress()).thenReturn("44.18.12." + i);
        } else {
            when(publicIPAddress.ipAddress()).thenReturn(ipAddress);
        }

        virtualMachines.add(vm);
    }

    private void testDiscoverNodesMocked(int vmCount) {
        testDiscoverNodesMockedWithSkip(vmCount, -1);
    }

    private void testDiscoverNodesMockedWithSkip(int vmCount, int skipIndex) {
        AzureDiscoveryStrategyFactory factory = new AzureDiscoveryStrategyFactory();
        AzureDiscoveryStrategy strategy = (AzureDiscoveryStrategy) factory.newDiscoveryStrategy(null, null, properties);

        strategy.start();
        Iterator<DiscoveryNode> nodes = strategy.discoverNodes().iterator();

        assertNotNull(nodes);

        ArrayList<DiscoveryNode> nodeList = new ArrayList<DiscoveryNode>();
        while (nodes.hasNext()) {
            DiscoveryNode node = nodes.next();
            nodeList.add(node);
        }

        assertEquals(vmCount, nodeList.size());

        for (int i = 0; i < nodeList.size(); i++) {
            int ipSuffix = i;

            if (skipIndex != -1 && i >= skipIndex) {
                ipSuffix += 1;
            }

            assertEquals("10.0.5." + ipSuffix, nodeList.get(i).getPrivateAddress().getHost());
            assertEquals(5701, nodeList.get(i).getPrivateAddress().getPort());

            assertEquals("44.18.12." + ipSuffix, nodeList.get(i).getPublicAddress().getHost());
            assertEquals(5701, nodeList.get(i).getPublicAddress().getPort());
        }
    }

    @Test
    public void testDiscoverNodesMocked255() {
        buildFakeVmList(255);
        testDiscoverNodesMocked(255);
    }

    @Test
    public void testDiscoverNodesMetadata() {
        AzureDiscoveryStrategyFactory factory = new AzureDiscoveryStrategyFactory();
        AzureDiscoveryStrategy strategy = (AzureDiscoveryStrategy) factory.newDiscoveryStrategy(null, null, properties);
        strategy.start();
        String localIp = strategy.getLocalHostAddress();
        buildFakeVm(0, localIp);
        strategy.discoverNodes();

        assertEquals(strategy.discoverLocalMetadata().get(PartitionGroupMetaData.PARTITION_GROUP_ZONE),
                Integer.toString(FAULT_DOMAIN_ID));
    }

    @Test
    public void testDiscoverNodesMocked3() {
        buildFakeVmList(3);
        testDiscoverNodesMocked(3);
    }

    @Test
    public void testDiscoverNodesMocked1() {
        buildFakeVmList(1);
        testDiscoverNodesMocked(1);
    }

    @Test
    public void testDiscoverNodesMocked_0() {
        buildFakeVmList(0);
        testDiscoverNodesMocked(0);
    }

    @Test
    public void testDiscoverNodesStoppedVM() {
        buildFakeVmList(4);
        VirtualMachine vmToTurnOff = virtualMachines.remove(2);
        // turn off the vm
        when(vmToTurnOff.powerState()).thenReturn(PowerState.DEALLOCATED);

        // should only recognize 3 hazelcast instances now
        testDiscoverNodesMockedWithSkip(3, 2);
    }

    @Test
    public void testDiscoverNodesUntaggedVM() {
        buildFakeVmList(6);
        VirtualMachine vmToUntag = virtualMachines.get(3);

        // retag vm
        HashMap<String, String> newTags = new HashMap<String, String>();
        newTags.put("INVALID_TAG", "INVALID_PORT");
        when(vmToUntag.tags()).thenReturn(newTags);

        // should only recognize 5 hazelcast instances now
        testDiscoverNodesMockedWithSkip(5, 3);
    }

    @Test
    public void testDiscoverScaleSetNodes() {
        int vmCount = 4;
        buildFakeVmList(0);
        PagedList<VirtualMachineScaleSet> scaleSetsPage = buildScaleSetPage(buildScaleSet(vmCount, PowerState.RUNNING));
        when(scaleSetService.listByResourceGroup(eq("test-value"))).thenReturn(scaleSetsPage);

        AzureDiscoveryStrategyFactory factory = new AzureDiscoveryStrategyFactory();
        AzureDiscoveryStrategy strategy = (AzureDiscoveryStrategy) factory.newDiscoveryStrategy(null, null, properties);

        strategy.start();
        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();

        assertNotNull(nodes);
        assertEquals(vmCount, Iterables.size(nodes));
        for (int i = 0; i < vmCount; i++) {
            DiscoveryNode node = Iterables.get(nodes, i);
            assertEquals("10.0.5." + i, node.getPrivateAddress().getHost());
            assertEquals(5701, node.getPrivateAddress().getPort());
        }
    }

    @Test
    public void testDiscoverScaleSetWithMetadata() {
        buildFakeVmList(0);
        AzureDiscoveryStrategyFactory factory = new AzureDiscoveryStrategyFactory();
        AzureDiscoveryStrategy strategy = (AzureDiscoveryStrategy) factory.newDiscoveryStrategy(null, null, properties);
        String localIp = strategy.getLocalHostAddress();

        PagedList<VirtualMachineScaleSet> scaleSetsPage = buildScaleSetPage(buildScaleSet(1, PowerState.RUNNING, localIp));
        when(scaleSetService.listByResourceGroup(eq("test-value"))).thenReturn(scaleSetsPage);

        strategy.start();
        strategy.discoverNodes();

        assertEquals(Integer.toString(FAULT_DOMAIN_ID),
                strategy.discoverLocalMetadata().get(PartitionGroupMetaData.PARTITION_GROUP_ZONE));
    }

    @Test
    public void testScaleSetShouldIgnoreNonRunningNodes() {
        buildFakeVmList(0);
        PagedList<VirtualMachineScaleSet> scaleSetsPage = buildScaleSetPage(buildScaleSet(4, PowerState.DEALLOCATED));
        when(scaleSetService.listByResourceGroup(eq("test-value"))).thenReturn(scaleSetsPage);

        AzureDiscoveryStrategyFactory factory = new AzureDiscoveryStrategyFactory();
        AzureDiscoveryStrategy strategy = (AzureDiscoveryStrategy) factory.newDiscoveryStrategy(null, null, properties);

        strategy.start();
        Iterable<DiscoveryNode> nodes = strategy.discoverNodes();

        assertNotNull(nodes);
        assertEquals(0, Iterables.size(nodes));
    }

    private PagedList<VirtualMachineScaleSet> buildScaleSetPage(VirtualMachineScaleSet scaleSet) {
        PagedList<VirtualMachineScaleSet> scaleSetsPage = new PagedList<VirtualMachineScaleSet>() {
            @Override
            public Page<VirtualMachineScaleSet> nextPage(String s) throws RestException {
                return null;
            }
        };
        scaleSetsPage.add(scaleSet);
        return scaleSetsPage;
    }

    private VirtualMachineScaleSet buildScaleSet(int vmCount, PowerState powerState) {
        return buildScaleSet(vmCount, powerState, null);
    }

    private VirtualMachineScaleSet buildScaleSet(int vmCount, PowerState powerState, String localIp) {
        VirtualMachineScaleSet scaleSet = mock(VirtualMachineScaleSet.class);
        when(scaleSet.tags()).thenReturn(ImmutableMap.of(properties.get("cluster-id").toString(), "5701"));

        PagedList<VirtualMachineScaleSetVM> vmPagedList = buildScaleSetVMs(vmCount, powerState, localIp);
        VirtualMachineScaleSetVMs scaleSetVMs = mock(VirtualMachineScaleSetVMs.class);
        when(scaleSetVMs.list()).thenReturn(vmPagedList);
        when(scaleSet.virtualMachines()).thenReturn(scaleSetVMs);
        return scaleSet;
    }

    private PagedList<VirtualMachineScaleSetVM> buildScaleSetVMs(int count, PowerState powerState, String localIp) {
        PagedList<VirtualMachineScaleSetVM> vmPage = new PagedList<VirtualMachineScaleSetVM>() {
            @Override
            public Page<VirtualMachineScaleSetVM> nextPage(String s) throws RestException {
                return null;
            }
        };
        ArrayList<VirtualMachineScaleSetVM> vms = new ArrayList<VirtualMachineScaleSetVM>();
        for (int i = 0; i < count; i++) {
            VirtualMachineScaleSetVM vm = mock(VirtualMachineScaleSetVM.class);
            VirtualMachineInstanceView vmInstance = mock(VirtualMachineInstanceView.class);
            when(vm.instanceView()).thenReturn(vmInstance);
            when(vmInstance.platformFaultDomain()).thenReturn(FAULT_DOMAIN_ID);
            when(vm.powerState()).thenReturn(powerState);
            VirtualMachineScaleSetNetworkInterface networkInterface = mock(VirtualMachineScaleSetNetworkInterface.class);
            if (localIp != null) {
                when(networkInterface.primaryPrivateIP()).thenReturn(localIp);
            } else {
                when(networkInterface.primaryPrivateIP()).thenReturn("10.0.5." + i);
            }
            when(vm.primaryNetworkInterfaceId()).thenReturn("primary-net-interface");
            when(vm.getNetworkInterface("primary-net-interface")).thenReturn(networkInterface);
            vms.add(vm);
        }
        vmPage.addAll(vms);
        return vmPage;
    }
}
