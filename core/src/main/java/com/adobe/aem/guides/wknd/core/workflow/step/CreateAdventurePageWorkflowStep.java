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
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;

@Component(service = WorkflowProcess.class, property = {
		Constants.SERVICE_DESCRIPTION + "=Create Adventure Page and Fragment Step", Constants.SERVICE_VENDOR + "=Adobe",
		"process.label=WKND: Create Adventure Page and Fragment" })
@ServiceDescription("Create Adventure Page and Content Fragment Workflows Process")
public class CreateAdventurePageWorkflowStep implements WorkflowProcess {

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
			Page adventurePage = createPage(wfData, cf, resourceResolver);

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
		} catch (WCMException e) {
			LOG.error("Error creating page", e);
		}

	}

	private Page createPage(WorkflowData workflowData, ContentFragment cf, ResourceResolver resourceResolver) throws WCMException, PersistenceException {
		PageManager pgMgr = resourceResolver.adaptTo(PageManager.class);

		Page seedPage = pgMgr.getPage(WKNDConstants.ADVENTURE_SEED_PAGE_PATH);

		String pageTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String pageName = workflowData.getMetaDataMap().get("pageLabel", String.class);
		String pageDescription = workflowData.getMetaDataMap().get("description", String.class);
		if (StringUtils.isEmpty(pageName)) {
			pageName = pageTitle.toLowerCase().replaceAll(" ", "-");
		}

		// Page newPage = pgMgr.copy(seedPage, WKNDConstants.ADVENTURE_LOCATION + pageName, null, false, true);
		Page newPage = pgMgr.create(WKNDConstants.ADVENTURE_LOCATION, pageName, "/conf/wknd/settings/wcm/templates/adventure-page-template", pageTitle);
		if (resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}

		ModifiableValueMap properties = newPage.getContentResource().adaptTo(ModifiableValueMap.class);
		properties.replace(JcrConstants.JCR_TITLE, pageTitle);
		properties.replace(JcrConstants.JCR_DESCRIPTION, pageDescription);

		Resource pageCFResource = resourceResolver.getResource(newPage.getPath() + "/jcr:content/root/container/container_fixed/container/contentfragment");
		ModifiableValueMap cfProperties = pageCFResource.adaptTo(ModifiableValueMap.class);
		cfProperties.put("fragmentPath", cf.adaptTo(Resource.class).getPath());

		Resource pageTab1Resource = resourceResolver.getResource(newPage.getPath() + "/jcr:content/root/container/container_fixed/tabs/item_1594238312974");
		ModifiableValueMap tab1Properties = pageTab1Resource.adaptTo(ModifiableValueMap.class);
		tab1Properties.put("fragmentPath", cf.adaptTo(Resource.class).getPath());

		Resource pageTab2Resource = resourceResolver.getResource(newPage.getPath() + "/jcr:content/root/container/container_fixed/tabs/item_1570890140330");
		ModifiableValueMap tab2Properties = pageTab2Resource.adaptTo(ModifiableValueMap.class);
		tab2Properties.put("fragmentPath", cf.adaptTo(Resource.class).getPath());

		Resource pageTab3Resource = resourceResolver.getResource(newPage.getPath() + "/jcr:content/root/container/container_fixed/tabs/item_1570890147607");
		ModifiableValueMap tab3Properties = pageTab3Resource.adaptTo(ModifiableValueMap.class);
		tab3Properties.put("fragmentPath", cf.adaptTo(Resource.class).getPath());

		if (resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}

		return newPage;
	}

	private ContentFragment createContentFragment(WorkflowData workflowData, ResourceResolver resourceResolver)
			throws LoginException, IOException, ContentFragmentException, RepositoryException {

		String contentFragmentPath = workflowData.getPayload().toString();
		String contentFragmentTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String contentFragmentName = workflowData.getMetaDataMap().get("pageLabel", String.class);
		String contentFragmentDescription = workflowData.getMetaDataMap().get("description", String.class);
		contentFragmentTitle = contentFragmentName + " - " + contentFragmentTitle;
		if (StringUtils.isEmpty(contentFragmentName)) {
			contentFragmentName = contentFragmentTitle.toLowerCase().replaceAll(" ", "-");
		}
		
		contentFragmentPath = "/content/dam/wknd/en/adventures";

		Session jcrSession = resourceResolver.adaptTo(Session.class);
		JcrUtil.createPath(contentFragmentPath + "/" + contentFragmentName, true, JcrResourceConstants.NT_SLING_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, jcrSession, true);

		Resource parent = resourceResolver.getResource(contentFragmentPath + "/" + contentFragmentName);
		ModifiableValueMap properties = parent.adaptTo(ModifiableValueMap.class);
		properties.replace(JcrConstants.JCR_TITLE, contentFragmentTitle);
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

}
