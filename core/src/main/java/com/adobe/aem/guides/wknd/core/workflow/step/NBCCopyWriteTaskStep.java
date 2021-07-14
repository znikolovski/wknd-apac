package com.adobe.aem.guides.wknd.core.workflow.step;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.granite.taskmanagement.Task;
import com.adobe.granite.taskmanagement.TaskManager;
import com.adobe.granite.taskmanagement.TaskManagerException;
import com.adobe.granite.taskmanagement.TaskManagerFactory;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.exec.InboxItem.Priority;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrConstants;

@Component(service=WorkflowProcess.class,
	property={
				Constants.SERVICE_DESCRIPTION + "=Copywrite Workflow Step",
				Constants.SERVICE_VENDOR + "=Adobe",
				"process.label=WKND: Copywrite Workflow Step"
})
@ServiceDescription("Copywrite Workflow Step")
public class NBCCopyWriteTaskStep implements WorkflowProcess {
	
	private static final Logger LOG = LoggerFactory.getLogger(NBCCopyWriteTaskStep.class);
	
	private static final String PROPERTY_TITLE = "cf-title";
	
	@Reference
	private ResourceResolverFactory resourceResolverFactory;
	
	@Override
	public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {
		try {
			ResourceResolver resourceResolver = getAdminResourceResolver();
			Resource projectResource = resourceResolver.getResource(WKNDConstants.WKND_NBC_PROJECT);
			Resource projectContent = projectResource.getChild(JcrConstants.JCR_CONTENT);
	        Resource tasksNode = projectContent.getChild("tasks");
	        WorkflowData workflowData = item.getWorkflow().getWorkflowData();

	        if (tasksNode == null) {
	            // create
	            tasksNode = resourceResolver.create(projectContent, "tasks", null);

	            // set the name of the tasks node for this project on the project
	            ModifiableValueMap valueMap = projectContent.adaptTo(ModifiableValueMap.class);
	            valueMap.put("tasks.folder", tasksNode.getName());
	        }
			
			TaskManager taskManager = tasksNode.adaptTo(TaskManager.class);
			TaskManagerFactory taskManagerFactory = taskManager.getTaskManagerFactory();
			try {
				Task task = taskManagerFactory.newTask(Task.DEFAULT_TASK_TYPE);
				String offerName = getParameter(workflowData, PROPERTY_TITLE);
				task.setName("Update Conversation " + offerName);
				task.setDescription("A new conversation template has been assigned to you.");
				task.setInstructions("Update the content in the Conversation Content Fragment");
				task.setCurrentAssignee("admin");
				task.setContentPath(WKNDConstants.OFFER_CF_PARRENT_PATH);

				task.setPriority(Priority.MEDIUM); // or

				// Finally create the task; note this call will commit to the JCR.
				// The provided user context will be used to attempt the save, so user-permissions do come into play.
				// If no user context was provided, then a Task manager service user will be used.
				task = taskManager.createTask(task);
				
				item.getWorkflowData().getMetaDataMap().put("task", task.getId());
			} catch (TaskManagerException e) {
				LOG.error("Error creating the copywrite task", e);
			} catch (RepositoryException e) {
				// TODO Auto-generated catch block
				LOG.error("Repository exception while processing workflow step", e);
			}
			
			resourceResolver.commit();
			resourceResolver.close();
			
//			session.complete(item, session.getRoutes(item, false).get(0));
			
		} catch (LoginException e) {
			LOG.error("Authentication error processing request", e);
		} catch (IOException e) {
			LOG.error("IO error processing request", e);
		}
	}

	private ResourceResolver getAdminResourceResolver() throws LoginException {
		Map<String, Object> param = new HashMap<String, Object>();
        param.put(ResourceResolverFactory.SUBSERVICE, "wkndService");
        
        ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
        
		return resourceResolver;
	}
	
	private String getParameter(WorkflowData workflowData, String key) throws RepositoryException {
		if(workflowData.getMetaDataMap().get(key) != null && workflowData.getMetaDataMap().get(key) instanceof Value[]) {
			Value[] val = (Value[])workflowData.getMetaDataMap().get(key);
			if(val.length > 0) {
				return val[0].getString();
			}
		}
		
		return StringUtils.EMPTY;
		
	}
	
}
