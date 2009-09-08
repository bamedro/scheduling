/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2009 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version
 * 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.resourcemanager.core;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.Body;
import org.objectweb.proactive.InitActive;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.core.util.wrapper.BooleanWrapper;
import org.objectweb.proactive.core.util.wrapper.IntWrapper;
import org.objectweb.proactive.gcmdeployment.GCMApplication;
import org.ow2.proactive.authentication.RestrictedService;
import org.ow2.proactive.resourcemanager.authentication.RMAuthenticationImpl;
import org.ow2.proactive.resourcemanager.common.RMConstants;
import org.ow2.proactive.resourcemanager.common.event.RMEvent;
import org.ow2.proactive.resourcemanager.common.event.RMEventType;
import org.ow2.proactive.resourcemanager.common.event.RMInitialState;
import org.ow2.proactive.resourcemanager.common.event.RMNodeEvent;
import org.ow2.proactive.resourcemanager.common.event.RMNodeSourceEvent;
import org.ow2.proactive.resourcemanager.core.jmx.JMXMonitoringHelper;
import org.ow2.proactive.resourcemanager.exception.AddingNodesException;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.frontend.RMAdmin;
import org.ow2.proactive.resourcemanager.frontend.RMAdminImpl;
import org.ow2.proactive.resourcemanager.frontend.RMMonitoring;
import org.ow2.proactive.resourcemanager.frontend.RMMonitoringImpl;
import org.ow2.proactive.resourcemanager.frontend.RMUser;
import org.ow2.proactive.resourcemanager.frontend.RMUserImpl;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.manager.GCMInfrastructure;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.manager.InfrastructureManager;
import org.ow2.proactive.resourcemanager.nodesource.infrastructure.manager.InfrastructureManagerFactory;
import org.ow2.proactive.resourcemanager.nodesource.policy.NodeSourcePolicy;
import org.ow2.proactive.resourcemanager.nodesource.policy.NodeSourcePolicyFactory;
import org.ow2.proactive.resourcemanager.nodesource.policy.StaticPolicy;
import org.ow2.proactive.resourcemanager.rmnode.RMNode;
import org.ow2.proactive.resourcemanager.selection.ProbablisticSelectionManager;
import org.ow2.proactive.resourcemanager.selection.SelectionManager;
import org.ow2.proactive.resourcemanager.utils.RMLoggers;
import org.ow2.proactive.scripting.SelectionScript;
import org.ow2.proactive.utils.FileToBytesConverter;
import org.ow2.proactive.utils.NodeSet;


/**
 * The main active object of the Resource Manager (RM), the RMCore has to
 * provide nodes to a scheduler.
 * 
 * The RMCore functions are :<BR>
 * - Create Resource Manager's active objects at its initialization ;
 * {@link RMAdmin}, {@link RMUser}, {@link RMMonitoring}.<BR>
 * - keep an up-to-date list of nodes able to perform scheduler's tasks.<BR>
 * - give nodes to the Scheduler asked by {@link RMUser} object, with a node
 * selection mechanism performed by {@link SelectionScript}.<BR>
 * - dialog with node sources which add and remove nodes to the Core. - perform
 * creation and removal of NodeSource objects. <BR>
 * - treat removing nodes and adding nodes request coming from {@link RMAdmin}.
 * - create and launch RMEvents concerning nodes and nodes Sources To
 * RMMonitoring active object.<BR>
 * <BR>
 * 
 * Nodes in Resource Manager are represented by {@link RMNode objects}. RMcore
 * has to manage different states of nodes : -free : node is ready to perform a
 * task.<BR>
 * -busy : node is executing a task.<BR>
 * -to be released : node is busy and have to be removed at the end of the its
 * current task.<BR>
 * -down : node is broken, and not anymore able to perform tasks.<BR>
 * <BR>
 * 
 * RMCore is not responsible of creation, acquisition and monitoring of nodes,
 * these points are performed by {@link NodeSource} objects.<BR>
 * <BR>
 * RMCore got at least one node Source created at its startup (named
 * {@link RMConstants#DEFAULT_STATIC_SOURCE_NAME}), which is a Static node
 * source ({@link GCMNodeSource}), able to receive a {@link GCMApplication}
 * objects and deploy them.<BR>
 * <BR>
 * 
 * WARNING : you must instantiate this class as an Active Object !
 * 
 * RmCore should be non-blocking which means <BR>
 * - no direct access to nodes <BR>
 * - all method calls to other active objects should be either asynchronous or non-blocking immediate services <BR>
 * - methods which have to return something depending on another active objects should use an automatic continuation <BR>
 *
 * @see RMCoreInterface
 * @see RMCoreSourceInterface
 * 
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
public class RMCore extends RestrictedService implements RMCoreInterface, InitActive, Serializable {

    /** Log4J logger name for RMCore */
    private final static Logger logger = ProActiveLogger.getLogger(RMLoggers.CORE);

    /** If RMCore Active object */
    private String id;

    /** ProActive Node containing the RMCore */
    private Node nodeRM;

    /** stub of RMAdmin active object of the RM */
    private RMAdminImpl admin;

    /** stub of RMMonitoring active object of the RM */
    private RMMonitoringImpl monitoring;

    /** stub of RMuser active object of the RM */
    private RMUserImpl user;

    /** authentication active object */
    private RMAuthenticationImpl authentication;

    /** HashMap of NodeSource active objects */
    private HashMap<String, NodeSource> nodeSources;

    /** HashMaps of nodes known by the RMCore */
    private HashMap<String, RMNode> allNodes;

    /** list of all free nodes */
    private ArrayList<RMNode> freeNodes;

    private SelectionManager selectionManager;

    /** indicates that RMCore must shutdown */
    private boolean toShutDown = false;

    /** nodes to deploy during startup of resource manager */
    private Collection<String> localGCMDeploymentFiles = null;

    JMXMonitoringHelper jmxHelper = new JMXMonitoringHelper();

    /**
     * Normalize the given URL into an URL that only contains protocol://host:port/
     *
     * @param url the url to transform
     * @return an URL that only contains protocol://host:port/
     */
    public static String getHostURL(String url) {
        URI uri = URI.create(url);
        int port = uri.getPort();
        if (port == -1) {
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } else {
            return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/";
        }
    }

    /**
     * ProActive Empty constructor
     */
    public RMCore() {
    }

    /**
     * Creates the RMCore object.
     * 
     * @param id
     *            Name for RMCOre.
     * @param nodeRM
     *            Name of the ProActive Node object containing RM active
     *            objects.
     * @throws ActiveObjectCreationException
     *             if creation of the active object failed.
     * @throws NodeException
     *             if a problem occurs on the target node.
     */
    public RMCore(String id, Node nodeRM) throws ActiveObjectCreationException, NodeException {
        this.id = id;
        this.nodeRM = nodeRM;

        nodeSources = new HashMap<String, NodeSource>();
        allNodes = new HashMap<String, RMNode>();
        freeNodes = new ArrayList<RMNode>();
    }

    /**
     * Creates the RMCore object with further deployment of given data.
     * 
     * @param id
     *            Name for RMCOre.
     * @param nodeRM
     *            Name of the ProActive Node object containing RM active
     *            objects.
     * @param gcmDeploymentData
     *            data to deploy.
     * @throws ActiveObjectCreationException
     *             if creation of the active object failed.
     * @throws NodeException
     *             if a problem occurs on the target node.
     */
    public RMCore(String id, Node nodeRM, Collection<String> localGCMDeploymentFiles)
            throws ActiveObjectCreationException, NodeException {
        this(id, nodeRM);
        this.localGCMDeploymentFiles = localGCMDeploymentFiles;
    }

    /**
     * Initialization part of the RMCore active object. <BR>
     * Create RM's active objects :<BR>
     * -{@link RMAdmin},<BR>
     * -{@link RMUser},<BR>
     * -{@link RMMonitoring},<BR>
     * and creates the default static Node Source named
     * {@link RMConstants#DEFAULT_STATIC_SOURCE_NAME}. Finally throws the RM
     * started event.
     * 
     * @param body
     *            the active object's body.
     * 
     */
    public void initActivity(Body body) {

        if (logger.isDebugEnabled()) {
            logger.debug("RMCore start : initActivity");
        }
        try {
            PAActiveObject.registerByName(PAActiveObject.getStubOnThis(),
                    RMConstants.NAME_ACTIVE_OBJECT_RMCORE);

            if (logger.isDebugEnabled()) {
                logger.debug("active object RMAuthentication");
            }

            authentication = (RMAuthenticationImpl) PAActiveObject.newActive(RMAuthenticationImpl.class
                    .getName(), new Object[] { PAActiveObject.getStubOnThis() }, nodeRM);

            if (logger.isDebugEnabled()) {
                logger.debug("active object RMAdmin");
            }

            admin = (RMAdminImpl) PAActiveObject.newActive(RMAdminImpl.class.getName(), new Object[] {
                    PAActiveObject.getStubOnThis(), authentication }, nodeRM);

            if (logger.isDebugEnabled()) {
                logger.debug("active object RMUser");
            }

            user = (RMUserImpl) PAActiveObject.newActive(RMUserImpl.class.getName(), new Object[] {
                    PAActiveObject.getStubOnThis(), authentication }, nodeRM);

            if (logger.isDebugEnabled()) {
                logger.debug("active object RMMonitoring");
            }
            // Create the MBeanServers and the Connectors before creating the monitoring Object
            jmxHelper.createMBeanServers();
            jmxHelper.createConnectors(authentication);
            monitoring = (RMMonitoringImpl) PAActiveObject.newActive(RMMonitoringImpl.class.getName(),
                    new Object[] { PAActiveObject.getStubOnThis() }, nodeRM);

            if (logger.isDebugEnabled()) {
                logger.debug("active object SelectionManager");
            }
            selectionManager = (SelectionManager) PAActiveObject.newActive(ProbablisticSelectionManager.class
                    .getName(), new Object[] { PAActiveObject.getStubOnThis() }, nodeRM);

            // register objects which are allowed to call methods of RMCore
            registerTrustedService(authentication);
            registerTrustedService(admin);
            registerTrustedService(user);
            registerTrustedService(monitoring);
            registerTrustedService(selectionManager);

            // callback from started nodes
            setPublicMethod("addNode");

            if (logger.isDebugEnabled()) {
                logger.debug("instantiation of the node source " + NodeSource.DEFAULT_NAME);
            }

            NodeSource ns = createNodesource(NodeSource.DEFAULT_NAME, GCMInfrastructure.class.getName(),
                    null, StaticPolicy.class.getName(), null);

            // TODO remove GCM reference from RMCore
            // deployment of required nodes 
            if (localGCMDeploymentFiles != null) {
                for (String gcmDeploymentFile : localGCMDeploymentFiles) {
                    File gcmDeployFile = new File(gcmDeploymentFile);
                    ns.addNodes(FileToBytesConverter.convertFileToByteArray(gcmDeployFile));
                }
                localGCMDeploymentFiles = null; // don't need it anymore
            }

            // adding shutdown hook
            final RMCore rmcoreStub = (RMCore) PAActiveObject.getStubOnThis();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    RMCore.this.registerTrustedService(PAActiveObject.getBodyOnThis().getID());
                    if (!RMCore.this.toShutDown) {
                        PAFuture.waitFor(rmcoreStub.shutdown(true));
                    }
                }
            });

            // Creating RM started event
            this.monitoring.rmEvent(new RMEvent(RMEventType.STARTED));

            ProActiveLogger.getLogger(RMLoggers.CONSOLE).info(
                    "Resource Manager successfully created on " +
                        getHostURL(PAActiveObject.getActiveObjectNodeUrl(PAActiveObject.getStubOnThis())));

            authentication.setActivated(true);

        } catch (IOException e) {
            logger.error("", e);
        } catch (ActiveObjectCreationException e) {
            logger.error("", e);
        } catch (NodeException e) {
            logger.error("", e);
        } catch (RMException e) {
            logger.error("", e);
        } catch (ProActiveException e) {
            logger.error("", e);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("RMCore end : initActivity");
        }
    }

    /**
     * Returns a node object to a corresponding URL.
     * 
     * @param url
     *            url of the node asked.
     * @return RMNode object containing the node.
     */
    private RMNode getNodebyUrl(String url) {
        assert allNodes.containsKey(url);
        return allNodes.get(url);
    }

    /**
     * Set a node's state to free, after a completed task by it. Set the to free
     * and move the node to the internal free nodes list. An event informing the
     * node state's change is thrown to RMMonitoring.
     * 
     * @param rmnode
     *            node to set free.
     */
    private void internalSetFree(RMNode rmnode) {
        // the node can only come from a busy state or down state
        assert rmnode.isBusy();
        try {
            rmnode.setFree();
            this.freeNodes.add(rmnode);

            // create the event
            this.monitoring.nodeEvent(new RMNodeEvent(rmnode, RMEventType.NODE_STATE_CHANGED));
        } catch (NodeException e) {
            // Exception on the node, we assume the node is down
            internalSetDown(rmnode);
            logger.debug("", e);
        }
    }

    /**
     * Set a node state to 'to be released'. mark the node toRelease, and move
     * the node to the internal 'to be released' nodes list. An event informing
     * the node state's change is thrown to RMMonitoring.
     * 
     * @param rmnode
     *            node to set.
     */
    private void internalSetToRelease(RMNode rmnode) {
        if (logger.isDebugEnabled()) {
            logger.debug("Prepare to release node " + rmnode.getNodeURL());
        }
        // the node can only come from a busy state
        assert rmnode.isBusy();
        try {
            rmnode.setToRelease();
        } catch (NodeException e1) {
            // A down node shouldn't be busied...
            logger.debug("", e1);
        }
        // create the event
        this.monitoring.nodeEvent(new RMNodeEvent(rmnode, RMEventType.NODE_STATE_CHANGED));
    }

    /**
     * Set a node state to down, and move the node to the internal down nodes
     * list. An event informing the node state's change is thrown to
     * RMMonitoring
     */
    private void internalSetDown(RMNode rmnode) {
        logger.info("Down node : " + rmnode.getNodeURL() + ", from Source : " + rmnode.getNodeSourceId());
        assert (!rmnode.isDown());

        if (rmnode.isFree()) {
            freeNodes.remove(rmnode);
        }
        rmnode.setDown();
        // create the event
        this.monitoring.nodeEvent(new RMNodeEvent(rmnode, RMEventType.NODE_STATE_CHANGED));
    }

    /**
     * Performs an RMNode release from the Core At this point the node is at
     * free or 'to be released' state. do the release, and confirm to NodeSource
     * the removal.
     * 
     * @param rmnode
     *            the node to release
     */
    private void internalDoRelease(RMNode rmnode) {
        if (logger.isInfoEnabled()) {
            logger.info("Releasing node " + rmnode.getNodeURL());
        }
        internalRemoveNodeFromCore(rmnode);
        rmnode.getNodeSource().removeNode(rmnode.getNodeURL(), false);
    }

    /**
     * Internal operations to remove the node from Core. RMNode object is
     * removed from {@link RMCore#allNodes}, removal Node event is thrown to
     * RMMonitoring Active object.
     * 
     * @param rmnode
     *            the node to remove.
     */
    private void internalRemoveNodeFromCore(RMNode rmnode) {
        // removing the node from the HM list
        if (rmnode.isFree()) {
            freeNodes.remove(rmnode);
        }
        this.allNodes.remove(rmnode.getNodeURL());
        // create the event
        this.monitoring.nodeEvent(new RMNodeEvent(rmnode, RMEventType.NODE_REMOVED));
    }

    /**
     * Internal operation of registering a new node in the Core ; adding the
     * node to the all nodes list Creating the RMNode object related to the
     * node, and put the node in free state.
     * 
     * @param rmnode
     *            node object to add
     */
    public void internalAddNodeToCore(RMNode rmnode) {
        this.freeNodes.add(rmnode);
        this.allNodes.put(rmnode.getNodeURL(), rmnode);
        // create the event
        this.monitoring.nodeEvent(new RMNodeEvent(rmnode, RMEventType.NODE_ADDED));
        if (logger.isInfoEnabled()) {
            logger.info("New node added, node ID is : " + rmnode.getNodeURL() + ", node Source : " +
                rmnode.getNodeSourceId());
        }
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getId()
     */
    public String getId() {
        return this.id;
    }

    // ----------------------------------------------------------------------
    // Methods called by RMAdmin, override RMCoreInterface
    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public BooleanWrapper addNode(String nodeUrl) {
        return addNode(nodeUrl, NodeSource.DEFAULT_NAME);
    }

    /**
     * {@inheritDoc}
     */
    public BooleanWrapper addNode(String nodeUrl, String sourceName) {
        if (nodeSources.containsKey(sourceName)) {
            NodeSource nodeSource = this.nodeSources.get(sourceName);

            // Known URL, so do some cleanup before replacing it
            if (allNodes.containsKey(nodeUrl)) {

                if (!allNodes.get(nodeUrl).getNodeSourceId().equals(sourceName)) {
                    // trying to already registered node to another node source
                    // do nothing in this case
                    throw new AddingNodesException("An attempt to add a node " + nodeUrl +
                        " registered in one node source to another one");
                }
            }
            return nodeSource.acquireNode(nodeUrl);
        } else {
            throw new AddingNodesException("Unknown node source " + sourceName);
        }
    }

    /**
     * {@inheritDoc}
     */
    public BooleanWrapper addNodes(String sourceName, Object[] parameters) {
        NodeSource ns = nodeSources.get(sourceName);
        if (ns == null) {
            throw new AddingNodesException("Unknown node source " + sourceName);
        }
        return ns.addNodes(parameters);
    }

    /**
     * Removes a node from the RM.
     *
     * @param nodeUrl URL of the node to remove.
     * @param preempt if true remove the node immediately without waiting while it will be freed.
     * @param forever if true remove the from a dynamic node source forever. Otherwise node source
     * is able to add this node to the RM again once it is needed. See {@link NodeSourcePolicy}.
     */
    public void removeNode(String nodeUrl, boolean preempt, boolean forever) {
        if (this.allNodes.containsKey(nodeUrl)) {
            RMNode rmnode = this.allNodes.get(nodeUrl);

            if (rmnode.isDown() || preempt || rmnode.isFree()) {
                internalRemoveNodeFromCore(rmnode);
                rmnode.getNodeSource().removeNode(rmnode.getNodeURL(), forever);
            } else if (rmnode.isBusy()) {
                internalSetToRelease(rmnode);
            }
        } else {
            logger.warn("An attempt to remove non existing node " + nodeUrl);
        }
    }

    /**
     * Removes "number" of nodes from the node source.
     *
     * @param number amount of nodes to be released
     * @param name a node source name
     * @param preemptive if true remove nodes immediately without waiting while they will be freed
     */
    public void removeNodes(int number, String nodeSourceName, boolean preemptive) {
        int numberOfRemovedNodes = 0;

        // temporary list to avoid concurrent modification
        List<RMNode> nodelList = new LinkedList<RMNode>();
        nodelList.addAll(freeNodes);

        logger.debug("Free nodes size " + nodelList.size());
        for (RMNode node : nodelList) {

            if (numberOfRemovedNodes == number) {
                break;
            }

            if (node.getNodeSource().getName().equals(nodeSourceName)) {
                removeNode(node.getNodeURL(), preemptive, false);
                numberOfRemovedNodes++;
            }
        }

        nodelList.clear();
        nodelList.addAll(allNodes.values());
        logger.debug("All nodes size " + nodelList.size());
        if (numberOfRemovedNodes < number) {
            for (RMNode node : nodelList) {

                if (numberOfRemovedNodes == number) {
                    break;
                }

                if (node.getNodeSource().getName().equals(nodeSourceName)) {
                    removeNode(node.getNodeURL(), preemptive, false);
                    numberOfRemovedNodes++;
                }
            }
        }

        if (numberOfRemovedNodes < number) {
            logger.warn("Cannot remove " + number + " nodes from node source " + nodeSourceName);
        }
    }

    /**
     * Removes all nodes from the specified node source.
     *
     * @param nodeSourceName a name of the node source
     * @param preemptive if true remove nodes immediately without waiting while they will be freed
     */
    public void removeAllNodes(String nodeSourceName, boolean preemptive) {
        for (Node node : nodeSources.get(nodeSourceName).getAliveNodes()) {
            removeNode(node.getNodeInformation().getURL(), preemptive, false);
        }
        for (Node node : nodeSources.get(nodeSourceName).getDownNodes()) {
            removeNode(node.getNodeInformation().getURL(), preemptive, false);
        }
    }

    /**
     * Creates a new node source with specified name, infrastructure manages {@link InfrastructureManager}
     * and acquisition policy {@link NodeSourcePolicy}.
     *
     * @param nodeSourceName the name of the node source
     * @param infrastructureType type of the underlying infrastructure {@link InfrastructureType}
     * @param infrastructureParameters parameters for infrastructure creation
     * @param policyType name of the policy type. It passed as a string due to pluggable approach {@link NodeSourcePolicyFactory}
     * @param policyParameters parameters for policy creation
     * @return constructed NodeSource
     * @throws RMException if any problems occurred
     */
    public NodeSource createNodesource(String nodeSourceName, String infrastructureType,
            Object[] infrastructureParameters, String policyType, Object[] policyParameters)
            throws RMException {

        if (this.nodeSources.containsKey(nodeSourceName)) {
            throw new RMException("Node Source name " + nodeSourceName + " is already exist");
        }

        logger.info("Creating a Node source : " + nodeSourceName);

        InfrastructureManager im = InfrastructureManagerFactory.create(infrastructureType,
                infrastructureParameters);
        NodeSourcePolicy policy = NodeSourcePolicyFactory.create(policyType, infrastructureType,
                policyParameters);

        NodeSource nodeSource;
        try {
            nodeSource = (NodeSource) PAActiveObject.newActive(NodeSource.class.getName(), new Object[] {
                    nodeSourceName, im, policy, PAActiveObject.getStubOnThis() }, nodeRM);
        } catch (Exception e) {
            throw new RMException(e);
        }

        registerTrustedService(policy);
        nodeSourceRegister(nodeSource, nodeSourceName);
        nodeSource.activate();

        logger.info("Node source : " + nodeSourceName + " has been successfully created");
        return nodeSource;
    }

    /**
     * Remove a node source from the RM.
     * All nodes handled by the node source are removed.
     *
     * @param sourceName name (id) of the source to remove.
     * @param preempt if true remove the node immediately without waiting while it will be freed.
     * @throws RMException if the node source doesn't exists
     */
    public void removeNodeSource(String sourceName, boolean preempt) throws RMException {
        if (sourceName.equals(NodeSource.DEFAULT_NAME)) {
            throw new RMException("Default static node source cannot be removed");
        } else if (nodeSources.containsKey(sourceName)) {
            //remove down nodes handled by the source
            //because node source doesn't know anymore its down nodes

            removeAllNodes(sourceName, preempt);
            nodeSources.get(sourceName).shutdown();
        } else {
            throw new RMException("unknown node source : " + sourceName);
        }
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#shutdown(boolean)
     */
    public BooleanWrapper shutdown(boolean preempt) {
        logger.info("RMCore shutdown request");
        this.monitoring.rmEvent(new RMEvent(RMEventType.SHUTTING_DOWN));
        this.toShutDown = true;

        for (Entry<String, NodeSource> entry : this.nodeSources.entrySet()) {
            removeAllNodes(entry.getKey(), preempt);
            entry.getValue().shutdown();
        }
        return new BooleanWrapper(true);
    }

    // ----------------------------------------------------------------------
    // Methods called by RMUser, override RMCoreInterface
    // ----------------------------------------------------------------------

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getNbAllRMNodes()
     */
    public IntWrapper getNbAllRMNodes() {
        return new IntWrapper(this.allNodes.size());
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getSizeListFreeRMNodes()
     */
    public IntWrapper getSizeListFreeRMNodes() {
        return new IntWrapper(this.freeNodes.size());
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#freeNode(org.objectweb.proactive.core.node.Node)
     */
    public void freeNode(Node node) {
        String nodeURL = null;
        try {
            nodeURL = node.getNodeInformation().getURL();
        } catch (RuntimeException e) {
            logger.debug("A Runtime exception occured " + "while obtaining information on the node,"
                + "the node must be down (it will be detected later)", e);
            // node is down,
            // will be detected later
            return;
        }

        // verify whether the node has not been removed from the RM
        if (this.allNodes.containsKey(nodeURL)) {
            RMNode rmnode = this.getNodebyUrl(nodeURL);

            assert (rmnode.isBusy() || rmnode.isToRelease() || rmnode.isDown());
            // prevent Scheduler Error : Scheduler try to render anode already
            // free
            if (rmnode.isFree()) {
                logger.warn("[RMCORE] scheduler tried to free a node already free ! Node URL : " + nodeURL);
            } else {
                // verify that scheduler don't try to render a node detected
                // down
                if (!rmnode.isDown()) {
                    if (rmnode.isToRelease()) {
                        internalDoRelease(rmnode);
                    } else {
                        internalSetFree(rmnode);
                    }
                }
            }
        } else {
            logger.warn("[RMCORE] scheduler asked to free an unknown node ! Node URL : " + nodeURL);
        }
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#freeNodes(org.ow2.proactive.utils.NodeSet)
     */
    public void freeNodes(NodeSet nodes) {
        for (Node node : nodes)
            freeNode(node);
    }

    /**
     * {@inheritDoc}
     */
    public NodeSet getAtMostNodes(int nb, SelectionScript selectionScript, NodeSet exclusion) {
        ArrayList<SelectionScript> list = null;
        if (selectionScript != null) {
            list = new ArrayList<SelectionScript>();
            list.add(selectionScript);
        }
        return getAtMostNodes(nb, list, exclusion);
    }

    /**
     * {@inheritDoc}
     */
    public NodeSet getAtMostNodes(int nb, List<SelectionScript> selectionScriptList, NodeSet exclusion) {

        // if RM is in shutdown state, it doesn't give nodes
        if (this.toShutDown) {
            return new NodeSet();
        } else {

            logger.info("Number of requested nodes is " + nb);
            return selectionManager.findAppropriateNodes(nb, selectionScriptList, exclusion);
        }
    }

    /**
     * {@inheritDoc}
     */
    public NodeSet getExactlyNodes(int nb, SelectionScript selectionScript) {
        // not implemented
        return null;
    }

    // ----------------------------------------------------------------------
    // Methods called by RMMonitoring, override RMCoreInterface
    // ----------------------------------------------------------------------

    /**
     * Builds and returns a snapshot of RMCore's current state. Initial state
     * must be understood as a new Monitor point of view. A new monitor start to
     * receive RMCore events, so must be informed of the current state of the
     * Core at the beginning of monitoring.
     * 
     * @return RMInitialState containing nodes and nodeSources of the RMCore.
     */
    public RMInitialState getRMInitialState() {
        ArrayList<RMNodeEvent> nodesList = new ArrayList<RMNodeEvent>();
        for (RMNode rmnode : this.allNodes.values()) {
            nodesList.add(new RMNodeEvent(rmnode, RMEventType.NODE_ADDED)); //TODO change event type
        }

        ArrayList<RMNodeSourceEvent> nodeSourcesList = new ArrayList<RMNodeSourceEvent>();
        for (NodeSource s : this.nodeSources.values()) {
            nodeSourcesList.add(new RMNodeSourceEvent(s, RMEventType.NODESOURCE_CREATED)); //TODO change event type
        }

        return new RMInitialState(nodesList, nodeSourcesList);
    }

    // ----------------------------------------------------------------------
    // Methods called by RMFactory, override RMCoreInterface
    // ----------------------------------------------------------------------

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getAdmin()
     */
    public RMAdminImpl getAdmin() {
        return this.admin;
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getMonitoring()
     */
    public RMMonitoringImpl getMonitoring() {
        return this.monitoring;
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getUser()
     */
    public RMUserImpl getUser() {
        return this.user;
    }

    // ----------------------------------------------------------------------
    // Methods called by NodeSource objects, override RMNodeManagerSourceInt
    // ----------------------------------------------------------------------

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreSourceInterface#nodeSourceRegister(org.ow2.proactive.resourcemanager.nodesource.deprecated.NodeSource,
     *      java.lang.String)
     */
    public void nodeSourceRegister(NodeSource source, String sourceId) {
        this.nodeSources.put(sourceId, source);
        // create the event
        this.monitoring.nodeSourceEvent(new RMNodeSourceEvent(source, RMEventType.NODESOURCE_CREATED));
        registerTrustedService(source);
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreSourceInterface#nodeSourceUnregister(java.lang.String,
     *      org.ow2.proactive.resourcemanager.common.event.RMNodeSourceEvent)
     */
    public BooleanWrapper nodeSourceUnregister(String sourceId, RMNodeSourceEvent evt) {
        NodeSource nodeSource = this.nodeSources.remove(sourceId);

        if (nodeSource == null) {
            logger.warn("Attempt to remove non-existing node source " + sourceId);
            new BooleanWrapper(false);
        }

        logger.info("Node Source removed : " + sourceId);
        // create the event
        this.monitoring.nodeSourceEvent(evt);
        unregisterTrustedService(nodeSource);

        if ((this.nodeSources.size() == 0) && this.toShutDown) {
            // all nodes sources has been removed and RMCore in shutdown state,
            // finish the shutdown
            this.user.shutdown();
            this.monitoring.shutdown();
            PAActiveObject.terminateActiveObject(admin, false);
            PAActiveObject.terminateActiveObject(true);
            try {
                Thread.sleep(2000);
                this.nodeRM.getProActiveRuntime().killRT(true);
            } catch (Exception e) {
                logger.debug("", e);
            }
        }

        return new BooleanWrapper(true);
    }

    /**
     * Set a node state to busy. Set the node to busy, and move the node to the
     * internal busy nodes list. An event informing the node state's change is
     * thrown to RMMonitoring.
     *
     * @param rmnode
     *            node to set
     * @throws NodeException
     */
    public void setBusyNode(String nodeUrl) throws NodeException {

        RMNode rmnode = allNodes.get(nodeUrl);
        if (rmnode == null) {
            logger.error("Unknown node " + nodeUrl);
            return;
        }

        assert rmnode.isFree();
        assert this.freeNodes.contains(rmnode);

        try {
            rmnode.setBusy();
        } catch (NodeException e1) {
            // A down node shouldn't be busied...
            logger.debug("", e1);
        }
        this.freeNodes.remove(rmnode);
        // create the event
        this.monitoring.nodeEvent(new RMNodeEvent(rmnode, RMEventType.NODE_STATE_CHANGED));
    }

    /**
     * Sets a node state to down and updates all internal structures of rm core
     * accordingly. Sends an event indicating that the node is down.
     */
    public void setDownNode(String nodeUrl) {
        RMNode rmnode = getNodebyUrl(nodeUrl);
        if (rmnode != null) {
            this.internalSetDown(rmnode);
        } else {
            // the nodes has been removed from core asynchronously
            // when pinger of selection manager tried to access it
            // do nothing in this case
        }
    }

    /**
     * Removed a node with given url from the internal structures of the core.
     *
     * @param nodeUrl down node to be removed
     * @return true if the nodes was successfully removed, false otherwise
     */
    public BooleanWrapper internalRemoveNodeFromCore(String nodeUrl) {
        RMNode rmnode = getNodebyUrl(nodeUrl);
        if (rmnode != null) {
            internalRemoveNodeFromCore(rmnode);
            return new BooleanWrapper(true);
        } else {
            return new BooleanWrapper(false);
        }
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#getPingFrequency(java.lang.String)
     */
    public IntWrapper getPingFrequency(String sourceName) throws RMException {
        if (this.nodeSources.containsKey(sourceName)) {
            return this.nodeSources.get(sourceName).getPingFrequency();
        } else {
            throw new RMException("unknown node source : " + sourceName);
        }
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#setAllPingFrequency(int)
     */
    public void setAllPingFrequency(int frequency) {
        for (Entry<String, NodeSource> entry : this.nodeSources.entrySet()) {
            entry.getValue().setPingFrequency(frequency);
        }
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#setPingFrequency(int)
     */
    public void setPingFrequency(int frequency) {
        this.nodeSources.get(NodeSource.DEFAULT_NAME).setPingFrequency(frequency);
    }

    /**
     * @see org.ow2.proactive.resourcemanager.core.RMCoreInterface#setPingFrequency(int,
     *      java.lang.String)
     */
    public void setPingFrequency(int frequency, String sourceName) throws RMException {
        if (this.nodeSources.containsKey(sourceName)) {
            this.nodeSources.get(sourceName).setPingFrequency(frequency);
        } else {
            throw new RMException("unknown node source : " + sourceName);
        }
    }

    /**
     * Logger used in RestrictedService
     */
    public Logger getLogger() {
        return logger;
    }

    public ArrayList<RMNode> getFreeNodes() {
        return freeNodes;
    }

}
