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
import java.util.Calendar;
import java.util.TimeZone;
import javax.jcr.Node;
import javax.jcr.Session;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aarora
 */
@Component(service = WorkflowProcess.class, property = {"process.label=Create Review Task Workflow Process"})
public class CreateReviewTaskProcess implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(CreateReviewTaskProcess.class);
    private static final String TYPE_JCR_PATH = "JCR_PATH";

    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {
        log.info("*****Create Review Task Workflow Process*****");

        WorkflowData workflowData = item.getWorkflowData();

        String taskName = "Review Uploaded Assets";
        String taskAssignTo;
        String taskDescription = "Review the uploaded assets";
        String taskInitiator = item.getWorkflow().getInitiator();

        if (workflowData.getPayloadType().equals(TYPE_JCR_PATH)) {
            String projectAssetsPath = workflowData.getPayload().toString();
            if (projectAssetsPath.startsWith("/content/dam")) {
                try {
                    //ResourceResolver resourceResolver = session.adaptTo(ResourceResolver.class);
                    Session jcrSession = session.adaptTo(Session.class);
                    log.info("Session User ID: " + jcrSession.getUserID());

                    // Get Task Name
                    if ( (workflowData.getMetaDataMap().get("taskName", String.class) != null) && !(workflowData.getMetaDataMap().get("taskName", String.class).isEmpty()) ) {
                        taskName = workflowData.getMetaDataMap().get("taskName", String.class);
                        log.info("workflow metadata for key taskName and value {}", taskName);
                    } else {
                        log.info("using default task name: {}", taskName);
                    }

                    // Get Task AssignTo
                    if ( (workflowData.getMetaDataMap().get("taskAssignee", String.class) != null) && !(workflowData.getMetaDataMap().get("taskAssignee", String.class).isEmpty()) )  {
                        taskAssignTo = workflowData.getMetaDataMap().get("taskAssignee", String.class);
                        log.info("workflow metadata for key taskAssignTo and value {}", taskAssignTo);
                    } else {
                        taskAssignTo = item.getWorkflow().getInitiator();
                        log.info("assigning task to initiator: {}", taskAssignTo);
                    }

                    // Get Task Description
                    if ( (workflowData.getMetaDataMap().get("description", String.class) != null) && !(workflowData.getMetaDataMap().get("description", String.class).isEmpty()) ) {
                        taskDescription = workflowData.getMetaDataMap().get("description", String.class);
                        log.info("workflow metadata for key taskDescription and value {}", taskDescription);
                    } else {
                        log.info("using default task description: {}", taskDescription);
                    }

                    Node projectAssetsNode = jcrSession.getNode(projectAssetsPath);
                    Node projectAssetsJCRContentNode = projectAssetsNode.getNode("jcr:content");

                    // Find/Create the main tasks node
                    Node tasksNode;
                    if (projectAssetsJCRContentNode.hasNode("tasks")) {
                        log.info("tasks node already exists");
                        tasksNode = projectAssetsJCRContentNode.getNode("tasks");
                    } else {
                        log.info("creating a new tasks node");
                        tasksNode = projectAssetsJCRContentNode.addNode("tasks");
                    }

                    // Find/Create child folder node
                    String date = java.time.LocalDate.now().toString();
                    log.info("date: {}", date);
                    Node tasksFolderNode;
                    if (tasksNode.hasNode(date)) {
                        log.info("tasks child folder node already exists");
                        tasksFolderNode = tasksNode.getNode(date);
                    } else {
                        log.info("creating a new tasks child folder node");
                        tasksFolderNode = tasksNode.addNode(date, "sling:Folder");
                    }

                    // Find/Create the child task node
                    String newTaskName = taskName;
                    int i = 0;
                    while (tasksFolderNode.hasNode(newTaskName)) {
                        log.info("task node already exists {}", newTaskName);
                        newTaskName = taskName + i;
                        log.info("new task name {}", newTaskName);
                        i++;
                    }
                    log.info("creating a new task node {}", newTaskName);
                    Node taskNode = tasksFolderNode.addNode(newTaskName, "granite:Task");

                    // Get current datetime
                    Calendar nowCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

                    // Add properties to this task node
                    taskNode.setProperty("_charset_", "UTF-8");
                    taskNode.setProperty("assignee", taskAssignTo);
                    taskNode.setProperty("contentPath", projectAssetsPath);
                    taskNode.setProperty("createdBy", taskInitiator); // change
                    taskNode.setProperty("description", taskDescription);
                    taskNode.setProperty("lastModified", nowCalendar);
                    taskNode.setProperty("lastModifiedBy", taskInitiator);
                    taskNode.setProperty("name", taskName);
                    taskNode.setProperty("nameHierarchy", taskName);
                    taskNode.setProperty("priority", 0);
                    taskNode.setProperty("progressBeginTime", nowCalendar);
                    taskNode.setProperty("projectPath", "/content/dam");
                    taskNode.setProperty("startTime", nowCalendar);
                    taskNode.setProperty("status", "ACTIVE");
                    taskNode.setProperty("taskDueDate@TypeHint", "Date");
                    taskNode.setProperty("taskPriority", "Medium");
                    taskNode.setProperty("taskTypeName", "dam:review");

                    // Save JCR Session
                    jcrSession.save();
                    log.info("*****[Success] Create Review Task Workflow Process*****");

                } catch (Exception ex) {
                    ex.printStackTrace();
                    log.info("**** Error: " + ex.getMessage());
                }
            } else {
                log.info("**** Error: Payload must start with /content/dam");
            }
        }
        else {
            log.info("**** Error: Payload must be of type {}", TYPE_JCR_PATH);
        }
    }
}
