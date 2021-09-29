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
import com.adobe.granite.workflow.model.WorkflowModel;
import java.util.HashMap;
import java.util.Map;
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
@Component(service = WorkflowProcess.class, property = {"process.label=Asset Status Change To Approved Workflow Process"})
public class AssetsStatusChangeToApprovedProcess implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(AssetsStatusChangeToApprovedProcess.class);
    private static final String TYPE_JCR_PATH = "JCR_PATH";

    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {
        log.info("*****Asset Status Change To Approved Workflow Process*****");

        WorkflowData workflowData = item.getWorkflowData();
        if (workflowData.getPayloadType().equals(TYPE_JCR_PATH)) {
            String assetPath = workflowData.getPayload().toString();
            try {
                ResourceResolver resourceResolver = session.adaptTo(ResourceResolver.class);
                Session jcrSession = resourceResolver.adaptTo(Session.class);
                log.info("Session User ID: " + jcrSession.getUserID());

                log.info("Current Asset Path: " + assetPath);

                Node assetNode = jcrSession.getNode(assetPath);
                Node assetMetadataNode = assetNode.getNode("jcr:content/metadata");

                if (assetMetadataNode.hasProperty("dam:status")) {
                    String assetStatus = assetMetadataNode.getProperty("dam:status").getString();
                    if (assetStatus.equals("changesRequested")) {
                        String originalPath = assetNode.getPath();
                        log.info("originalPath: " + originalPath);

                        assetMetadataNode.setProperty("dam:status", "approved");
                    } else {
                        log.info("skipping file because status is {}", assetStatus);
                    }
                } else {
                    log.info("skipping file because status is not set");
                }

                jcrSession.save();
                log.info("*****[Success] Asset Status Change To Approved Workflow Process*****");
            } catch (Exception ex) {
                ex.printStackTrace();
                log.info("**** Error: " + ex.getMessage());
            }
        }
    }
}
