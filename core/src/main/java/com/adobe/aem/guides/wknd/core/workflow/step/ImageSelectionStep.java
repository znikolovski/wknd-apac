package com.adobe.aem.guides.wknd.core.workflow.step;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.resource.collection.ResourceCollection;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.ContentVariation;
import com.adobe.cq.dam.cfm.VariationDef;
import com.adobe.cq.xf.ExperienceFragment;
import com.adobe.cq.xf.ExperienceFragmentVariation;
import com.adobe.granite.taskmanagement.Task;
import com.adobe.granite.taskmanagement.TaskManager;
import com.adobe.granite.taskmanagement.TaskManagerException;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.Status;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.commons.util.DamUtil;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;

@Component(service=WorkflowProcess.class,
	property={
				Constants.SERVICE_DESCRIPTION + "=Image Selection Workflow Step",
				Constants.SERVICE_VENDOR + "=Adobe",
				"process.label=WKND: Image Selection Workflow Step"
})
@ServiceDescription("Image Selection Workflow Step")
public class ImageSelectionStep implements WorkflowProcess {
	
	private static final Logger LOG = LoggerFactory.getLogger(ImageSelectionStep.class);
	
	@Reference
	private ResourceResolverFactory resourceResolverFactory;
	
	@Reference
	private Replicator replicator;
	
	@Override
	public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {
		try {
			ResourceResolver resourceResolver = getAdminResourceResolver();
			Resource projectResource = resourceResolver.getResource(WKNDConstants.WKND_NBC_PROJECT);
			Resource projectContent = projectResource.getChild(JcrConstants.JCR_CONTENT);
	        Resource tasksNode = projectContent.getChild("tasks");

			TaskManager taskManager = tasksNode.adaptTo(TaskManager.class);
			String taskId = item.getWorkflowData().getMetaDataMap().get("task", String.class);
			Task task = taskManager.getTask(taskId);
			while(task.getStatus().name().equals(Status.ACTIVE.name())) {
				resourceResolver.commit();
				resourceResolver.close();
				Thread.sleep(30000l);
				resourceResolver = getAdminResourceResolver();
				projectResource = resourceResolver.getResource(WKNDConstants.WKND_NBC_PROJECT);
				projectContent = projectResource.getChild(JcrConstants.JCR_CONTENT);
		        tasksNode = projectContent.getChild("tasks");

				taskManager = tasksNode.adaptTo(TaskManager.class);
				taskId = item.getWorkflowData().getMetaDataMap().get("task", String.class);
				task = taskManager.getTask(taskId);
			}
			String cfPath = item.getWorkflowData().getMetaDataMap().get("cf", String.class);
			String xfPath = item.getWorkflowData().getMetaDataMap().get("xf", String.class);
			Resource cfRes = resourceResolver.getResource(cfPath);
		    Resource xfRes = resourceResolver.getResource(xfPath);
		    
		    ContentFragment cf = cfRes.adaptTo(ContentFragment.class);
		    
		    List<String> xfPaths = new ArrayList<String>();
		    xfPaths.add(xfPath);
		    
			if(StringUtils.isNotEmpty(cfPath) && StringUtils.isNotEmpty(xfPath)) {
				createCFVariationsForImages(resourceResolver, cf);
				associateCFVariationswithXFs(resourceResolver, cf, xfRes, xfPaths);
			}
			
		} catch (LoginException e) {
			LOG.error("Authentication error processing request", e);
		} catch (ContentFragmentException e) {
			LOG.error("Content Fragment error processing request", e);
		} catch (IOException e) {
			LOG.error("IO error processing request", e);
		} catch (TaskManagerException e) {
			LOG.error("Error retrieving the task", e);
		} catch (InterruptedException e) {
			LOG.error("Thread was interrupted", e);
		} catch (WCMException e) {
			LOG.error("Error creating the XF copies", e);
		}
	}
	
	private void associateCFVariationswithXFs(ResourceResolver resourceResolver, ContentFragment cf, Resource xfRes, List<String> xfPaths) throws WCMException, PersistenceException {
		PageManager pgMgr = resourceResolver.adaptTo(PageManager.class);
		
		Page seedXf = pgMgr.getPage(xfRes.getPath());
		
		Iterator<VariationDef> variations = cf.listAllVariations();
		while(variations.hasNext()) {
			VariationDef variation = variations.next();
			Page newXf = pgMgr.copy(seedXf, WKNDConstants.XF_LOCATION + variation.getName(), null, false, true);
			xfPaths.add(newXf.getPath());
			
			ExperienceFragment xf = newXf.adaptTo(ExperienceFragment.class);
			
			if(resourceResolver.hasChanges()) {
				resourceResolver.commit();
			}
			
			List<ExperienceFragmentVariation> xfVariations = xf.getVariations();
			for(ExperienceFragmentVariation xfVariation: xfVariations) {
				Resource xfResource = resourceResolver.getResource(xfVariation.getPath() + "/jcr:content/root/cf_offer");
				ModifiableValueMap properties = xfResource.adaptTo(ModifiableValueMap.class);
				properties.replace(JcrConstants.JCR_TITLE, variation.getTitle());
				properties.replace("fragmentPath", cf.adaptTo(Resource.class).getPath());
				properties.put("variationName", variation.getName());
			}
			
			if(resourceResolver.hasChanges()) {
				resourceResolver.commit();
			}
		}
		
	}

	private void createCFVariationsForImages(final ResourceResolver resourceResolver, ContentFragment cf) throws ContentFragmentException, PersistenceException {
		Iterator<Resource> associatedContent = cf.getAssociatedContent();
		String letter = "A";
		String prefix = "";
		while(associatedContent.hasNext()) {
			Resource collectionRes = associatedContent.next();
			ResourceCollection collection = collectionRes.adaptTo(ResourceCollection.class);
			boolean first = true;
			Iterator<Resource> collectionItems = collection.getResources();
			
			while(collectionItems.hasNext()) {
				Resource collectionItem = collectionItems.next();
				if(DamUtil.isAsset(collectionItem) && isAssetImage(collectionItem, cf)) {
					Asset image = collectionItem.adaptTo(Asset.class);
					if(first) {
						ContentElement imageElement = cf.getElement("image");
						imageElement.setContent(image.getPath(), null);
						first = false;
					} else {
						
						String variationName = cf.getName() + "-" + letter;
						String variationTitle = cf.getName() + " " + letter;
						cf.createVariation(variationName, variationTitle, cf.getDescription());
						ContentElement imageElement = cf.getElement("image");
						ContentVariation imageVariation = imageElement.getVariation(variationName);
						imageVariation.setContent(image.getPath(), null);
						int charValue = letter.charAt(0);
						letter = String.valueOf( (char) (charValue + 1));
						if(letter.equals("Z")) {
							letter = "A";
							if(prefix.isEmpty()) {
								prefix = "A";
							} else {
								int prefixChar = prefix.charAt(0);
								prefix = String.valueOf( (char) (prefixChar + 1));
							}
						}
					}
				}
			}
		}
		
	}

	private boolean isAssetImage(Resource collectionItem, ContentFragment cf) {
		boolean isCF = collectionItem.getName().equals(cf.getName());
		String mimeType = collectionItem.adaptTo(Asset.class).getMimeType();
		boolean isImage = StringUtils.isNotEmpty(mimeType) && (mimeType.equals("image/png") || mimeType.equals("image/jpeg"));
		return !isCF && isImage;
	}

	private ResourceResolver getAdminResourceResolver() throws LoginException {
		Map<String, Object> param = new HashMap<String, Object>();
        param.put(ResourceResolverFactory.SUBSERVICE, "wkndService");
        
        ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
        
		return resourceResolver;
	}
	
}
