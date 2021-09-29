package com.adobe.aem.guides.wknd.core.workflow.step;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.adapter.AdapterManager;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.resource.collection.ResourceCollection;
import org.apache.sling.resource.collection.ResourceCollectionManager;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service=WorkflowProcess.class,
	property={
				Constants.SERVICE_DESCRIPTION + "=Create Creative Job workflow step",
				Constants.SERVICE_VENDOR + "=Adobe",
				"process.label=WKND: Create Creative Job"
})
@ServiceDescription("Create Creative Job Workflows Process")
public class CreateJobWorkflowStep implements WorkflowProcess {
	
	private static final Logger LOG = LoggerFactory.getLogger(CreateJobWorkflowStep.class);
	
	@Reference
	private ResourceResolverFactory resourceResolverFactory;
	
	@Reference
  private QueryBuilder queryBuilder;
	
	private ResourceCollectionManager resourceCollectionManager;

	@Reference
	private AdapterManager adapterManager;
	
	@Override
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
		Map<String, Object> param = new HashMap<String, Object>();
        param.put(ResourceResolverFactory.SUBSERVICE, "wkndService");
        
        try {
			ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
			
			WorkflowData wfData = workItem.getWorkflow().getWorkflowData();
			createJobStructure(wfData, resourceResolver);
			createCollection(wfData, resourceResolver);
			
			Session jcrSession = resourceResolver.adaptTo(Session.class);
			jcrSession.save();
			
			resourceResolver.commit();
			resourceResolver.close();
			
		} catch (LoginException e) {
			LOG.error("Authentication error processing request", e);
		} catch (ContentFragmentException e) {
			LOG.error("Content Fragment error processing request", e);
		} catch (RepositoryException e) {
			LOG.error("Repository error processing request", e);
		} catch (IOException e) {
			LOG.error("IO error processing request", e);
		}
	
	}
	
	private void createJobStructure(WorkflowData workflowData, ResourceResolver resourceResolver)
			throws RepositoryException, PersistenceException {
		String jobPath = workflowData.getPayload().toString();
		String jobTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String jobName = workflowData.getMetaDataMap().get("pageLabel", String.class);
		String jobDescription = workflowData.getMetaDataMap().get("description", String.class);
		String asignee = workflowData.getMetaDataMap().get("assignee", String.class);

		if(StringUtils.isEmpty(jobName)) {
			jobName = jobTitle.toLowerCase().replaceAll(" ", "-");
		} else {
			jobTitle = jobName + " - " + jobTitle;
		}

		Resource seedRes = resourceResolver.getResource(WKNDConstants.WKND_JOB_SEED_FOLDER);
		Resource jobDestinationRes = resourceResolver.getResource(jobPath);
		JcrUtil.copy(seedRes.adaptTo(Node.class), jobDestinationRes.adaptTo(Node.class), jobName, true);
		
		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}
		Resource destinationRes = resourceResolver.getResource(jobPath + "/" + jobName + "/jcr:content");

		ModifiableValueMap properties = destinationRes.adaptTo(ModifiableValueMap.class);
		properties.replace(JcrConstants.JCR_TITLE, jobTitle);
		properties.replace(JcrConstants.JCR_DESCRIPTION, jobDescription);
		properties.put("assignee", asignee);
		
		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}
	}
	
	private void createCollection(WorkflowData workflowData, ResourceResolver resourceResolver) 
			throws LoginException, PersistenceException, ContentFragmentException, RepositoryException {
		
		String tagsStr = workflowData.getMetaDataMap().get("cq:tags", String.class);
		String jobPath = workflowData.getPayload().toString();
		String jobTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String jobName = workflowData.getMetaDataMap().get("pageLabel", String.class);

		if(StringUtils.isEmpty(jobName)) {
			jobName = jobTitle.toLowerCase().replaceAll(" ", "-");
		}

		String[] tags = {};
		resourceCollectionManager = resourceResolver.adaptTo(ResourceCollectionManager.class);
		Resource wkndCollection = resourceResolver.getResource(WKNDConstants.WKND_COLLECTIONS_ROOT_PATH);
		Map<String, Object> properties = getCollectionProperties(resourceResolver, workflowData);
		ResourceCollection collection = resourceCollectionManager.createCollection(wkndCollection, jobName, properties);
		ResourceCollection rootCollection = resourceCollectionManager.getCollection(resourceResolver.getResource(WKNDConstants.WKND_COLLECTIONS_ROOT_PATH + "/wknd_jobs"));
		if(StringUtils.isNotBlank(tagsStr)) {
			tags = tagsStr.split(",");
			
			Map<String, String> map = new HashMap<String, String>();
			map.put("path", "/content/dam");
			map.put("type", "dam:Asset");
			map.put("1_property.property", "jcr:content/metadata/dam:MIMEtype");
	        map.put("1_property.1_value", "image/jpeg");
	        map.put("1_property.2_value", "image/png");
	        map.put("1_property.p.or", "true");
	        if(tags.length > 0) {
		        map.put("2_property.property", "jcr:content/metadata/predictedTags/*/name");
		        int index = 1;
		        for(String tag: tags) {
		        	map.put("2_property." + index + "_value", tag);
		        	index = index + 1;
		        }
		        map.put("2_property.p.and", "true");
	        }
			map.put("orderby","@jcr:content/jcr:lastModified"); //<--here
			map.put("orderby.sort", "desc");
			map.put("p.limit", "50");
			
			Query query = queryBuilder.createQuery(PredicateGroup.create(map), resourceResolver.adaptTo(Session.class));
			SearchResult result = query.getResult();

			if(!result.getHits().isEmpty()) {
	    		for(final Hit hit: result.getHits()) {
						Resource resource = hit.getResource();
						Asset asset = resource.adaptTo(Asset.class);
						if(asset.getMimeType() != null && 
						(asset.getMimeType().equals("image/png") || 
						asset.getMimeType().equals("image/jpeg") || 
						asset.getMimeType().equals("image/jpg"))) {
							collection.add(resource);
						}
					}
	    }
		}
		Session jcrSession = resourceResolver.adaptTo(Session.class);
		jcrSession.save();
		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}

		collection.add(resourceResolver.getResource(jobPath + "/" + jobName));
		rootCollection.add(resourceResolver.getResource(collection.getPath()));

		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}
	}
	
	private Map<String, Object> getCollectionProperties(ResourceResolver resourceResolver, WorkflowData workflowData) throws RepositoryException {
        Map<String, Object> properties = new HashMap<String, Object>();
        String contentFragmentTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
				String contentFragmentDescription = workflowData.getMetaDataMap().get("description", String.class);
        properties.put(JcrConstants.JCR_TITLE, contentFragmentTitle + " " + contentFragmentDescription);
        String description = contentFragmentDescription;
        if (description != null) {
            properties.put(JcrConstants.JCR_DESCRIPTION, description);
        }
        properties.put(
            SlingConstants.NAMESPACE_PREFIX + ':'
                + SlingConstants.PROPERTY_RESOURCE_TYPE,
            DamConstants.COLLECTION_SLING_RES_TYPE);
        String userId = resourceResolver.getUserID();
        properties.put(JcrConstants.JCR_CREATED_BY, userId);
        properties.put(JcrConstants.JCR_CREATED, Calendar.getInstance());
        properties.put(JcrConstants.JCR_LAST_MODIFIED_BY, userId);
        properties.put(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
        return properties;
    }

}
