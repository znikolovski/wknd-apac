package com.adobe.aem.guides.wknd.core.workflow.step;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
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

import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.adobe.cq.xf.ExperienceFragment;
import com.adobe.cq.xf.ExperienceFragmentVariation;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;

@Component(service=WorkflowProcess.class,
	property={
				Constants.SERVICE_DESCRIPTION + "=Next Best Conversation Workflows Step",
				Constants.SERVICE_VENDOR + "=Adobe",
				"process.label=WKND: Next Best Conversation Workflows Step"
})
@ServiceDescription("Image Selection for Next Best Conversation")
public class NextBestConversationWorkflowStep implements WorkflowProcess {
	
	private static final Logger LOG = LoggerFactory.getLogger(NextBestConversationWorkflowStep.class);
	
	private static final String PROPERTY_NAME = "cf-name";
	
	private static final String PROPERTY_TITLE = "cf-title";
	
	private static final String PROPERTY_CONVERSATION = "conversation";
	
	private static final String PROPERTY_INTENT = "intent";
	
	private static final String TAGS = "tags";
	
	@Reference
	private ResourceResolverFactory resourceResolverFactory;
	
	@Reference
    private QueryBuilder queryBuilder;
	
	private ResourceCollectionManager resourceCollectionManager;

	@Override
	public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {
		try {
			ResourceResolver resourceResolver = getAdminResourceResolver();
			WorkflowData wfData = item.getWorkflow().getWorkflowData();
			
			ContentFragment cf = createContentFragment(wfData, resourceResolver);
			addCFCollection(cf, wfData, resourceResolver);
			addCFMetaData(cf, wfData, resourceResolver);
			ExperienceFragment xf = createXPFragment(wfData, resourceResolver);
			associateCFandXF(cf, xf, resourceResolver);
			
			item.getWorkflowData().getMetaDataMap().put("xf", xf.getPath());
			item.getWorkflowData().getMetaDataMap().put("cf", cf.adaptTo(Resource.class).getPath());
			
			resourceResolver.commit();
			resourceResolver.close();
			
//			session.complete(item, session.getRoutes(item, false).get(0));
			
		} catch (LoginException e) {
			LOG.error("Authentication error processing request", e);
		} catch (ContentFragmentException e) {
			LOG.error("Content Fragment error processing request", e);
		} catch (RepositoryException e) {
			LOG.error("Repository error processing request", e);
		} catch (WCMException e) {
			LOG.error("WCM error processing request", e);
		} catch (IOException e) {
			LOG.error("IO error processing request", e);
		}
	}
	
	private ContentFragment createContentFragment(WorkflowData workflowData, ResourceResolver resourceResolver) 
			throws LoginException, IOException, ContentFragmentException, RepositoryException {
		Resource parent = resourceResolver.getResource(WKNDConstants.OFFER_CF_PARRENT_PATH);
		Resource template = resourceResolver.getResource(WKNDConstants.OFFER_CF_TEMPLATE_PATH);
		
		String cfTitle = getParameter(workflowData, PROPERTY_TITLE) + " " + getParameter(workflowData, PROPERTY_CONVERSATION);
		String cfName = getParameter(workflowData, PROPERTY_NAME);
		
		if(cfName.isEmpty() || cfTitle.isEmpty()) {
			throw new IOException("Required parameters are missing.");
		}
		
		ContentFragment fragment = template.adaptTo(FragmentTemplate.class).createFragment(parent, cfName, cfTitle);
		fragment.setDescription(getParameter(workflowData, PROPERTY_INTENT));
		Session jcrSession = resourceResolver.adaptTo(Session.class);
		jcrSession.save();
		
		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}
		
		return fragment;
	}

	private ExperienceFragment createXPFragment(WorkflowData workflowData, ResourceResolver resourceResolver) 
			throws LoginException, WCMException, IOException, RepositoryException {
		PageManager pgMgr = resourceResolver.adaptTo(PageManager.class);
		
		Page seedXf = pgMgr.getPage(WKNDConstants.OFFER_XF_SEED_PAGE_PATH);
		
		String xfTitle = getParameter(workflowData, PROPERTY_TITLE) + " " + getParameter(workflowData, PROPERTY_CONVERSATION);
		String xfName = getParameter(workflowData, PROPERTY_NAME);
		
		if(xfName.isEmpty() || xfTitle.isEmpty()) {
			throw new IOException("Required parameters are missing.");
		}
		
		Page newXf = pgMgr.copy(seedXf, WKNDConstants.XF_LOCATION + xfName, null, false, true);
		
		ExperienceFragment xf = newXf.adaptTo(ExperienceFragment.class);
		ModifiableValueMap properties = newXf.getContentResource().adaptTo(ModifiableValueMap.class);
		properties.replace(JcrConstants.JCR_TITLE, xfTitle);
		properties.replace(JcrConstants.JCR_DESCRIPTION, getParameter(workflowData, PROPERTY_INTENT));
		
		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}
		
		return xf;
	}

	private void addCFCollection(ContentFragment cf, WorkflowData workflowData, ResourceResolver resourceResolver) 
			throws LoginException, PersistenceException, ContentFragmentException, RepositoryException {
		
		String tagsStr = getParameter(workflowData, TAGS);
		
		String[] tags = {};
		if(!tagsStr.isEmpty()) {
			tags = tagsStr.split(",");
		}
		
		Map<String, String> map = new HashMap<String, String>();
		map.put("path", WKNDConstants.WKND_DAM_PATH);
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
    		resourceCollectionManager = resourceResolver.adaptTo(ResourceCollectionManager.class);
    		Resource wkndCollection = resourceResolver.getResource(WKNDConstants.WKND_COLLECTIONS_ROOT_PATH);
    		Map<String, Object> properties = getCollectionProperties(resourceResolver, workflowData);
    		ResourceCollection collection = resourceCollectionManager.createCollection(wkndCollection, cf.getName(), properties);
    		
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
    		
    		collection.add(cf.adaptTo(Resource.class));
    		
    		Session jcrSession = resourceResolver.adaptTo(Session.class);
    		jcrSession.save();
    		if(resourceResolver.hasChanges()) {
    			resourceResolver.commit();
    		}
    		
    		cf.addAssociatedContent(resourceResolver.getResource(collection.getPath()));
    		
    		if(resourceResolver.hasChanges()) {
    			resourceResolver.commit();
    		}
    	}
	}

	private void addCFMetaData(ContentFragment cf, WorkflowData workflowData, ResourceResolver resourceResolver) {
		// TODO Auto-generated method stub
		
	}
	
	private void associateCFandXF(ContentFragment cf, ExperienceFragment xf, ResourceResolver resourceResolver) throws LoginException, PersistenceException {
		List<ExperienceFragmentVariation> variations = xf.getVariations();
		
		for(ExperienceFragmentVariation variation: variations) {
			Resource xfResource = resourceResolver.getResource(variation.getPath() + "/jcr:content/root/cf_offer");
			ModifiableValueMap properties = xfResource.adaptTo(ModifiableValueMap.class);
			properties.replace("fragmentPath", cf.adaptTo(Resource.class).getPath());
		}
		
		if(resourceResolver.hasChanges()) {
			resourceResolver.commit();
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
	
	private Map<String, Object> getCollectionProperties(ResourceResolver resourceResolver, WorkflowData workflowData) throws RepositoryException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(JcrConstants.JCR_TITLE, getParameter(workflowData, PROPERTY_TITLE) + " " + getParameter(workflowData, PROPERTY_CONVERSATION));
        String description = getParameter(workflowData, PROPERTY_INTENT);
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
