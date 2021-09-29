package com.adobe.aem.guides.wknd.core.workflow.step;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.resource.collection.ResourceCollection;
import org.apache.sling.resource.collection.ResourceCollectionManager;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.guides.wknd.core.utils.Type;
import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.FragmentTemplate;
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

@Component(service = WorkflowProcess.class, property = {
		Constants.SERVICE_DESCRIPTION + "=Create Content Fragment workflow step", Constants.SERVICE_VENDOR + "=Adobe",
		"process.label=WKND: Create Content Fragment" })
@ServiceDescription("Create Content Fragment Workflows Process")
public class CreateContentFragmentWorkflowStep implements WorkflowProcess {

	private static final Logger LOG = LoggerFactory.getLogger(CreateContentFragmentWorkflowStep.class);

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Reference
	private QueryBuilder queryBuilder;

	private ResourceCollectionManager resourceCollectionManager;

	@Override
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
			throws WorkflowException {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put(ResourceResolverFactory.SUBSERVICE, "wkndService");

		try {
			ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);

			WorkflowData wfData = workItem.getWorkflow().getWorkflowData();
			ContentFragment cf = createContentFragment(wfData, resourceResolver);
			addCFCollection(cf, wfData, resourceResolver);

			Resource metaData = resourceResolver.getResource(workItem.getWorkflow().getId() + "/data/metaData");
			ModifiableValueMap editableMetaData = metaData.adaptTo(ModifiableValueMap.class);

			editableMetaData.put("cf", cf.adaptTo(Resource.class).getPath());

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

	private ContentFragment createContentFragment(WorkflowData workflowData, ResourceResolver resourceResolver)
			throws LoginException, IOException, ContentFragmentException, RepositoryException {

		String contentFragmentPath = workflowData.getPayload().toString();
		String contentFragmentTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String contentFragmentName = workflowData.getMetaDataMap().get("pageLabel", String.class);
		String contentFragmentDescription = workflowData.getMetaDataMap().get("description", String.class);
		contentFragmentTitle = contentFragmentName + " - " + contentFragmentTitle;
		String contentFragmentType = workflowData.getMetaDataMap().get("type", String.class);
		if (StringUtils.isEmpty(contentFragmentName)) {
			contentFragmentName = contentFragmentTitle.toLowerCase().replaceAll(" ", "-");
		}

		if (Type.OFFER.getType().equals(contentFragmentType)) {
			contentFragmentPath = contentFragmentPath + "/" + Type.OFFER.getDestination();
		} else {
			contentFragmentPath = contentFragmentPath + "/" + Type.MESSAGE.getDestination();
		}

		Session jcrSession = resourceResolver.adaptTo(Session.class);
		JcrUtil.createPath(contentFragmentPath + "/" + contentFragmentName, true, JcrResourceConstants.NT_SLING_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, jcrSession, true);

		Resource parent = resourceResolver.getResource(contentFragmentPath + "/" + contentFragmentName);
		String templatePath = workflowData.getMetaDataMap().get("template", String.class) + "/jcr:content";
		Resource template = resourceResolver.getResource(templatePath);

		ContentFragment fragment = template.adaptTo(FragmentTemplate.class).createFragment(parent, contentFragmentName,
				contentFragmentTitle);
		fragment.setDescription(contentFragmentDescription);
		
		jcrSession.save();

		if (resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}

		return fragment;
	}

	private void addCFCollection(ContentFragment cf, WorkflowData workflowData, ResourceResolver resourceResolver)
			throws LoginException, PersistenceException, ContentFragmentException, RepositoryException {

		String tagsStr = workflowData.getMetaDataMap().get("cq:tags", String.class);

		String[] tags = {};
		if (StringUtils.isNotBlank(tagsStr)) {
			tags = tagsStr.split(",");

			Map<String, String> map = new HashMap<String, String>();
			map.put("path", "/content/dam");
			map.put("type", "dam:Asset");
			map.put("1_property.property", "jcr:content/metadata/dam:MIMEtype");
			map.put("1_property.1_value", "image/jpeg");
			map.put("1_property.2_value", "image/png");
			map.put("1_property.p.or", "true");
			if (tags.length > 0) {
				map.put("2_property.property", "jcr:content/metadata/predictedTags/*/name");
				int index = 1;
				for (String tag : tags) {
					map.put("2_property." + index + "_value", tag);
					index = index + 1;
				}
				map.put("2_property.p.and", "true");
			}
			map.put("orderby", "@jcr:content/jcr:lastModified"); // <--here
			map.put("orderby.sort", "desc");
			map.put("p.limit", "10");

			Query query = queryBuilder.createQuery(PredicateGroup.create(map), resourceResolver.adaptTo(Session.class));
			SearchResult result = query.getResult();
			resourceCollectionManager = resourceResolver.adaptTo(ResourceCollectionManager.class);
			Resource wkndCollection = resourceResolver.getResource(WKNDConstants.WKND_COLLECTIONS_ROOT_PATH);
			Map<String, Object> properties = getCollectionProperties(resourceResolver, workflowData);
			ResourceCollection collection = resourceCollectionManager.createCollection(wkndCollection, cf.getName(),
					properties);

			if (!result.getHits().isEmpty()) {

				for (final Hit hit : result.getHits()) {
					Resource resource = hit.getResource();
					Asset asset = resource.adaptTo(Asset.class);
					if (asset.getMimeType() != null && (asset.getMimeType().equals("image/png")
							|| asset.getMimeType().equals("image/jpeg") || asset.getMimeType().equals("image/jpg"))) {
						collection.add(resource);
					}
				}

			}

			collection.add(cf.adaptTo(Resource.class));

			Session jcrSession = resourceResolver.adaptTo(Session.class);
			jcrSession.save();
			if (resourceResolver.hasChanges()) {
				resourceResolver.commit();
			}

			cf.addAssociatedContent(resourceResolver.getResource(collection.getPath()));

			if (resourceResolver.hasChanges()) {
				resourceResolver.commit();
			}
		}
	}

	private Map<String, Object> getCollectionProperties(ResourceResolver resourceResolver, WorkflowData workflowData)
			throws RepositoryException {
		Map<String, Object> properties = new HashMap<String, Object>();
		String contentFragmentTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String contentFragmentDescription = workflowData.getMetaDataMap().get("description", String.class);
		properties.put(JcrConstants.JCR_TITLE, contentFragmentTitle + " " + contentFragmentDescription);
		String description = contentFragmentDescription;
		if (description != null) {
			properties.put(JcrConstants.JCR_DESCRIPTION, description);
		}
		properties.put(SlingConstants.NAMESPACE_PREFIX + ':' + SlingConstants.PROPERTY_RESOURCE_TYPE,
				DamConstants.COLLECTION_SLING_RES_TYPE);
		String userId = resourceResolver.getUserID();
		properties.put(JcrConstants.JCR_CREATED_BY, userId);
		properties.put(JcrConstants.JCR_CREATED, Calendar.getInstance());
		properties.put(JcrConstants.JCR_LAST_MODIFIED_BY, userId);
		properties.put(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
		return properties;
	}

}
