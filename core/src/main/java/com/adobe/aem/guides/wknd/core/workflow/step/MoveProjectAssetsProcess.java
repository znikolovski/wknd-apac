/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.adobe.aem.guides.wknd.core.workflow.step;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aarora
 */
@Component(service = WorkflowProcess.class, property = {"process.label=Move Project Assets Workflow Process"})
public class MoveProjectAssetsProcess implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(MoveProjectAssetsProcess.class);
    private static final String TYPE_JCR_PATH = "JCR_PATH";
    private String targetPath;

    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {
        log.info("*****Move Project Assets Workflow Process*****");

        WorkflowData workflowData = item.getWorkflowData();
        if (workflowData.getPayloadType().equals(TYPE_JCR_PATH)) {
            String assetPath = workflowData.getPayload().toString();
            try {
                ResourceResolver resourceResolver = session.adaptTo(ResourceResolver.class);
                Session jcrSession = resourceResolver.adaptTo(Session.class);
                log.info("Session User ID: " + jcrSession.getUserID());

                // Get target path to move assets
                if ((workflowData.getMetaDataMap().get("targetPath", String.class) != null) && !(workflowData.getMetaDataMap().get("targetPath", String.class).isEmpty())) {
                    targetPath = workflowData.getMetaDataMap().get("targetPath", String.class);
                    log.info("workflow metadata for key targetPath and value {}", targetPath);
                } // alternative get target path from project properties
                else {
                    if ((workflowData.getMetaDataMap().get("project.path", String.class) != null)) {
                        String projectPath = workflowData.getMetaDataMap().get("project.path", String.class);
                        log.info("project path {}", projectPath);
                        Node projectNode = jcrSession.getNode(projectPath);
                        Node projContentNode = projectNode.getNode("jcr:content");
                        if (projContentNode.hasProperty("targetPath")) {
                            targetPath = projContentNode.getProperty("targetPath").getString();
                        } else {
                            log.info("**** Error: targetPath property not found in project");
                            return;
                        }
                    } else {
                        log.info("**** Error: project path does not exist");
                        return;
                    }
                }

                com.adobe.granite.asset.api.AssetManager assetManager = resourceResolver.adaptTo(com.adobe.granite.asset.api.AssetManager.class);

                log.info("Current Asset Path: " + assetPath);
                log.info("Target Asset Path: " + targetPath);

                Node assetsNode = jcrSession.getNode(assetPath);
                NodeIterator assetsNodeItr = assetsNode.getNodes();
                while (assetsNodeItr.hasNext()) {
                    Node assetNode = assetsNodeItr.nextNode();
                    if (assetNode.getProperty("jcr:primaryType").getString().equals("dam:Asset")) {
                        String filename = assetNode.getName();
                        log.info("Filename: " + filename);
                        if (filename.equals("cover")) {
                            log.info("skipping file");
                            continue;
                        }

                        Node assetMetadataNode = assetNode.getNode("jcr:content/metadata");
                        if (assetMetadataNode.hasProperty("dam:status")) {
                            String assetStatus = assetMetadataNode.getProperty("dam:status").getString();
                            if (assetStatus.equals("approved")) {
                                String originalPath = assetNode.getPath();
                                log.info("originalPath: " + originalPath);
                                String copyPath = targetPath + "/" + filename;
                                log.info("copyPath: " + copyPath);

                                //Copy the asset
                                assetManager.copyAsset(originalPath, copyPath);
                                //Remove the original asset
                                assetManager.removeAsset(originalPath);
                            } else {
                                log.info("skipping file because status is {}", assetStatus);
                            }
                        } else {
                            log.info("skipping file because status is not set");
                        }
                    }
                }
                
                log.info("*****[Success] Move Project Assets Workflow Process*****");
            } catch (Exception ex) {
                ex.printStackTrace();
                log.info("**** Error: " + ex.getMessage());
            }
        }
        else {
            log.info("**** Error: Payload must be of type {}", TYPE_JCR_PATH);
        }
    }
}
