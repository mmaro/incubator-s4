package org.apache.s4.tools.helix;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.helix.ConfigScope;
import org.apache.helix.ConfigScopeBuilder;
import org.apache.helix.HelixAdmin;
import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.model.IdealState;
import org.apache.helix.model.IdealState.IdealStateModeProperty;
import org.apache.helix.model.InstanceConfig;
import org.apache.s4.comm.HelixBasedCommModule;
import org.apache.s4.core.HelixBasedCoreModule;
import org.apache.s4.core.util.AppConfig;
import org.apache.s4.tools.Deploy;
import org.apache.s4.tools.Deploy.InlineConfigParameterConverter;
import org.apache.s4.tools.S4ArgsBase;
import org.apache.s4.tools.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class DeployApp extends S4ArgsBase {

    private static Logger logger = LoggerFactory.getLogger(DeployApp.class);

    public static void main(String[] args) {
        DeployAppArgs deployArgs = new DeployAppArgs();

        Tools.parseArgs(deployArgs, args);

        HelixAdmin admin = new ZKHelixAdmin(deployArgs.zkConnectionString);
        ConfigScopeBuilder builder = new ConfigScopeBuilder();
        // ConfigScope scope = builder.forCluster(deployArgs.clusterName).forResource(deployArgs.appName).build();
        ConfigScope scope = builder.forCluster(deployArgs.clusterName).forResource(deployArgs.appName).build();
        Map<String, String> properties = new HashMap<String, String>();

        URI s4rURI = null;
        if (deployArgs.s4rPath != null) {
            try {
                s4rURI = new URI(deployArgs.s4rPath);
            } catch (URISyntaxException e) {
                logger.error("Cannot get URI from s4r parameter: {}", deployArgs.s4rPath);
                return;
            }
            if (Strings.isNullOrEmpty(s4rURI.getScheme())) {
                // default is file
                s4rURI = new File(deployArgs.s4rPath).toURI();
            }
            logger.info(
                    "Using specified S4R [{}], the S4R archive will not be built from source (and corresponding parameters are ignored)",
                    s4rURI.toString());
        } else {
            throw new RuntimeException("Not specifying s4r URI (as -s4r parameter) is not supported yet");
        }

        ImmutableList<String> helixModules = ImmutableList.of(HelixBasedCommModule.class.getName(),
                HelixBasedCoreModule.class.getName());
        // TODO merge with custom modules

        AppConfig appConfig = new AppConfig.Builder().appClassName(deployArgs.appClass).appName(deployArgs.appName)
                .appURI(deployArgs.s4rPath).customModulesNames(helixModules).customModulesURIs(null)
                .namedParameters(Deploy.convertListArgsToMap(deployArgs.extraNamedParameters)).build();
        // properties.put("appConfig", appConfig.asMap());
        // properties.put(DistributedDeploymentManager.S4R_URI, new File(deployArgs.s4rPath).toURI().toString());
        properties.put("type", "App");
        properties.putAll(appConfig.asMap());
        admin.setConfig(scope, properties);

        IdealState is = admin.getResourceIdealState(deployArgs.clusterName, deployArgs.appName);
        if (is == null) {
            is = new IdealState(deployArgs.appName);
        }
        is.setNumPartitions(1);
        is.setIdealStateMode(IdealStateModeProperty.CUSTOMIZED.toString());
        is.setStateModelDefRef("OnlineOffline");
        List<String> instancesInGroup = new ArrayList<String>();
        List<String> instancesInCluster = admin.getInstancesInCluster(deployArgs.clusterName);
        for (String instanceName : instancesInCluster) {
            InstanceConfig instanceConfig = admin.getInstanceConfig(deployArgs.clusterName, instanceName);
            String nodeGroup = instanceConfig.getRecord().getSimpleField("GROUP");
            if (nodeGroup.equals(deployArgs.nodeGroup)) {
                instancesInGroup.add(instanceName);
            }
        }
        for (String instanceName : instancesInGroup) {
            is.setPartitionState(deployArgs.appName, instanceName, "ONLINE");
        }

        admin.setResourceIdealState(deployArgs.clusterName, deployArgs.appName, is);
    }

    @Parameters(commandNames = "newStreamProcessor", separators = "=", commandDescription = "Create a new stream processor")
    static class DeployAppArgs extends S4ArgsBase {

        @Parameter(names = "-zk", description = "ZooKeeper connection string")
        String zkConnectionString = "localhost:2181";

        @Parameter(names = { "-c", "-cluster" }, description = "Logical name of the S4 cluster", required = true)
        String clusterName;

        @Parameter(names = "-s4r", description = "Path to existing s4r file", required = true)
        String s4rPath;

        @Parameter(names = { "-generatedS4R", "-g" }, description = "Location of generated s4r (incompatible with -s4r option). By default, s4r is generated in a temporary directory on the local file system. In a distributed environment, you probably want to specify a location accessible through a distributed file system like NFS. That's the purpose of this option.", required = false)
        String generatedS4R;

        @Parameter(names = { "-appName" }, description = "Name of the App", required = true, arity = 1)
        String appName;

        @Parameter(names = { "-a", "-appClass" }, description = "Full class name of the application class (extending App or AdapterApp)", required = false)
        String appClass = "";

        @Parameter(names = { "-ng", "-nodeGroup" }, description = "Node group name where the App needs to be deployed", required = false, arity = 1)
        String nodeGroup = "default";

        @Parameter(names = { "-namedStringParameters", "-p" }, description = "Comma-separated list of inline configuration parameters, taking precedence over homonymous configuration parameters from configuration files. Syntax: '-p=name1=value1,name2=value2 '", hidden = false, converter = InlineConfigParameterConverter.class)
        List<String> extraNamedParameters = new ArrayList<String>();

    }
}
