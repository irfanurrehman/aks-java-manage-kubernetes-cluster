// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.resourcemanager.kubernetescluster.samples;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.containerservice.models.ContainerServiceVMSizeTypes;
import com.azure.resourcemanager.containerservice.models.KubernetesCluster;
import com.azure.resourcemanager.containerservice.models.KubernetesClusterAgentPool;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.samples.SSHShell;
import com.azure.resourcemanager.samples.Utils;
import com.jcraft.jsch.JSchException;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Azure Container Service (AKS) sample for managing a Kubernetes cluster.
 */
public class ManageKubernetesCluster {

    public static boolean scaleNodeUp(AzureResourceManager azureResourceManager, String clientId, String secret, String nodeName) throws IOException, JSchException {

        try {

            // List eks clusters
            //final list<KubernetesCluster> clusters = azureResourceManager.kubernetesClusters().list();
            for (KubernetesCluster cluster: azureResourceManager.kubernetesClusters().list()) {
                System.out.println("Found EKS cluster: " + cluster.name());
            }


            // Get the agentpool label from the kubernetes node matching the node name we have
            // List the clusters agentpools and match both
            // As of now this looks like the only way to get the agentpool of the corresponding node from name.
            for (KubernetesCluster cluster: azureResourceManager.kubernetesClusters().list()) {

                //=============================================================
                // Instantiate the Kubernetes client using the ".kube/config" file content from the Kubernetes cluster
                //     The Kubernetes client API requires setting an environment variable pointing at a real file;
                //        we will create a temporary file that will be deleted automatically when the sample exits

                System.out.println("Found Kubernetes master at: " + cluster.fqdn());

                byte[] kubeConfigContent = cluster.adminKubeConfigContent();
                File tempKubeConfigFile = File.createTempFile("kube", ".config", new File(System.getProperty("java.io.tmpdir")));
                tempKubeConfigFile.deleteOnExit();
                try (BufferedWriter buffOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempKubeConfigFile), StandardCharsets.UTF_8))) {
                    buffOut.write(new String(kubeConfigContent, StandardCharsets.UTF_8));
                }

                System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, tempKubeConfigFile.getPath());
                Config config = new Config();
                KubernetesClient kubernetesClient = new DefaultKubernetesClient(config);

                // Print the node list
                System.out.println(kubernetesClient.nodes().list());

                for (Node node: kubernetesClient.nodes().list().getItems()){

                    if (node.getMetadata().getName().matches(nodeName)){

                        String nodesAgentPool = node.getMetadata().getLabels().get("agentpool");
                        KubernetesClusterAgentPool pool = cluster.agentPools().get(nodesAgentPool);

                        // scale the agent pool up
                        int count = pool.count();

                        cluster.update()
                        .updateAgentPool(pool.name())
                        .withAgentPoolVirtualMachineCount(count++)
                        .parent()
                        .apply();

                    }

                }

            }


            return true;

        }
        catch (NullPointerException npe) {
            System.out.println("Did not create any resources in Azure. No clean up is necessary");
        }
        catch (Exception g) {
            System.out.println("Found problems: ");
            g.printStackTrace();
        }

        return true;
    }

    /**
     * Main entry point.
     *
     * @param args the parameters
     */
    public static void main(String[] args) {
        try {
            //=============================================================
            // Authenticate

            final AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);
            final TokenCredential credential = new DefaultAzureCredentialBuilder()
                .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
                .build();

            AzureResourceManager azureResourceManager = AzureResourceManager
                .configure()
                .withLogLevel(HttpLogDetailLevel.BASIC)
                .authenticate(credential, profile).withSubscription("6a5d73a4-e446-4c75-8f18-073b2f60d851");
                //.withDefaultSubscription();

            // Print selected subscription
            System.out.println("Selected subscription: " + azureResourceManager.subscriptionId());

            // The node name is hard coded here, this will come from the provision/suspend action
            scaleNodeUp(azureResourceManager, "", "", "aks-nodepool2-46684319-vmss000004");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
