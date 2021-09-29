package com.adobe.aem.guides.wknd.core.workflow.step;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.day.cq.commons.Externalizer;

import javax.jcr.RepositoryException;

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

import com.adobe.aem.guides.wknd.core.services.EnvironmentTypeProvider;
import com.adobe.aem.guides.wknd.core.utils.Type;
import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.ContentVariation;
import com.adobe.cq.dam.cfm.VariationDef;
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
import com.day.cq.dam.commons.util.DamUtil;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;

@Component(service = WorkflowProcess.class, property = {
		Constants.SERVICE_DESCRIPTION + "=Create Experience Fragment workflow step", Constants.SERVICE_VENDOR + "=Adobe",
		"process.label=WKND: Create Experience Fragment" })
@ServiceDescription("Create Experience Fragment Workflows Process")
public class CreateExperienceFragmentWorkflowStep implements WorkflowProcess {

	private static final Logger LOG = LoggerFactory.getLogger(CreateExperienceFragmentWorkflowStep.class);

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Reference
	private Replicator replicator;

	@Reference
	EnvironmentTypeProvider environmentTypeProvider;

	@Override
	public void execute(WorkItem workItem, WorkflowSession session, MetaDataMap metaDataMap) throws WorkflowException {
		if (environmentTypeProvider.getEnvironmentType().equals(Externalizer.AUTHOR)) {
			Map<String, Object> param = new HashMap<String, Object>();
			param.put(ResourceResolverFactory.SUBSERVICE, "wkndService");

			try {
				ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);

				WorkflowData wfData = workItem.getWorkflow().getWorkflowData();
				String cfPath = workItem.getWorkflowData().getMetaDataMap().get("cf", String.class);
				Resource cfRes = resourceResolver.getResource(cfPath);
				ContentFragment cf = cfRes.adaptTo(ContentFragment.class);

				ExperienceFragment xf = createXPFragment(wfData, resourceResolver);
				associateCFandXF(cf, xf, resourceResolver);

				Resource metaData = resourceResolver.getResource(workItem.getWorkflow().getId() + "/data/metaData");
				ModifiableValueMap editableMetaData = metaData.adaptTo(ModifiableValueMap.class);

				editableMetaData.put("xf", xf.getPath());

				Resource xfRes = resourceResolver.getResource(xf.getPath());

				List<String> xfPaths = new ArrayList<String>();
				xfPaths.add(xf.getPath());

				if (StringUtils.isNotEmpty(cfPath) && StringUtils.isNotEmpty(xf.getPath())) {
					createCFVariationsForImages(resourceResolver, cf);
					associateCFVariationswithXFs(wfData, resourceResolver, cf, xfRes, xfPaths);
				}

				resourceResolver.commit();

				ReplicationStatus cfStatus = cfRes.adaptTo(ReplicationStatus.class);
				ReplicationStatus xfStatus = xfRes.adaptTo(ReplicationStatus.class);
				LOG.info("Delivery status of CF: " + cfStatus.isDelivered());
				LOG.info("Delivery status of XF: " + xfStatus.isDelivered());
				resourceResolver.close();

			} catch (LoginException e) {
				LOG.error("Authentication error processing request", e);
			} catch (RepositoryException e) {
				LOG.error("Repository error processing request", e);
			} catch (WCMException e) {
				LOG.error("WCM error processing request", e);
			} catch (IOException e) {
				LOG.error("IO error processing request", e);
			} catch (ContentFragmentException e) {
				LOG.error("Content Fragment error processing request", e);
			}

		}
	}

	private ExperienceFragment createXPFragment(WorkflowData workflowData, ResourceResolver resourceResolver)
			throws LoginException, WCMException, IOException, RepositoryException {
		PageManager pgMgr = resourceResolver.adaptTo(PageManager.class);

		Page seedXf = pgMgr.getPage(WKNDConstants.OFFER_XF_SEED_PAGE_PATH);

		String xfTitle = workflowData.getMetaDataMap().get("workflowTitle", String.class);
		String xfName = workflowData.getMetaDataMap().get("pageLabel", String.class);
		String xfDescription = workflowData.getMetaDataMap().get("description", String.class);
		if (StringUtils.isEmpty(xfName)) {
			xfName = xfTitle.toLowerCase().replaceAll(" ", "-");
		}
		String xfOrigName = xfName;
		xfName = "xf-" + xfName;

		if (xfName.isEmpty() || xfTitle.isEmpty()) {
			throw new IOException("Required parameters are missing.");
		}

		Page newXf = pgMgr.copy(seedXf, WKNDConstants.XF_LOCATION + xfName, null, false, true);

		ExperienceFragment xf = newXf.adaptTo(ExperienceFragment.class);
		ModifiableValueMap properties = newXf.getContentResource().adaptTo(ModifiableValueMap.class);
		properties.replace(JcrConstants.JCR_TITLE, xfOrigName + " - " + xfTitle);
		properties.replace(JcrConstants.JCR_DESCRIPTION, xfDescription);

		if (resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}

		return xf;
	}

	private void associateCFandXF(ContentFragment cf, ExperienceFragment xf, ResourceResolver resourceResolver)
			throws LoginException, PersistenceException {
		List<ExperienceFragmentVariation> variations = xf.getVariations();

		for (ExperienceFragmentVariation variation : variations) {
			Resource xfResource = resourceResolver.getResource(variation.getPath() + "/jcr:content/root/container/contentfragment");
			ModifiableValueMap properties = xfResource.adaptTo(ModifiableValueMap.class);
			properties.replace("fragmentPath", cf.adaptTo(Resource.class).getPath());
		}

		if (resourceResolver.hasChanges()) {
			resourceResolver.commit();
		}

	}

	private void associateCFVariationswithXFs(WorkflowData workflowData, ResourceResolver resourceResolver,
			ContentFragment cf, Resource xfRes, List<String> xfPaths) throws WCMException, PersistenceException {
		PageManager pgMgr = resourceResolver.adaptTo(PageManager.class);

		Page seedXf = pgMgr.getPage(xfRes.getPath());

		Iterator<VariationDef> variations = cf.listAllVariations();
		while (variations.hasNext()) {
			VariationDef variation = variations.next();
			String xfFragmentType = workflowData.getMetaDataMap().get("type", String.class);
			String xfLocation = WKNDConstants.XF_LOCATION;
			if (Type.MESSAGE.getType().equals(xfFragmentType)) {
				xfLocation = xfLocation.replace(Type.OFFER.getDestination(), Type.MESSAGE.getDestination());
			}

			Page newXf = pgMgr.copy(seedXf, xfLocation + "/xf-" + variation.getName(), null, false, true);
			xfPaths.add(newXf.getPath());

			ExperienceFragment xf = newXf.adaptTo(ExperienceFragment.class);
			ModifiableValueMap xfProperties = resourceResolver.getResource(newXf.getPath()).adaptTo(ModifiableValueMap.class);
			xfProperties.replace(JcrConstants.JCR_TITLE, variation.getName() + " - " + variation.getTitle());

			if (resourceResolver.hasChanges()) {
				resourceResolver.commit();
			}

			List<ExperienceFragmentVariation> xfVariations = xf.getVariations();
			for (ExperienceFragmentVariation xfVariation : xfVariations) {
				Resource xfResource = resourceResolver.getResource(xfVariation.getPath() + "/jcr:content/root/container/contentfragment");
				ModifiableValueMap properties = xfResource.adaptTo(ModifiableValueMap.class);
				properties.replace(JcrConstants.JCR_TITLE, variation.getTitle());
				properties.replace("fragmentPath", cf.adaptTo(Resource.class).getPath());
				properties.put("variationName", variation.getName());
			}

			if (resourceResolver.hasChanges()) {
				resourceResolver.commit();
			}
		}

	}

	private void createCFVariationsForImages(final ResourceResolver resourceResolver, ContentFragment cf)
			throws ContentFragmentException, PersistenceException {
		Iterator<Resource> associatedContent = cf.getAssociatedContent();
		String letter = "A";
		String prefix = "";
		while (associatedContent.hasNext()) {
			Resource collectionRes = associatedContent.next();
			ResourceCollection collection = collectionRes.adaptTo(ResourceCollection.class);
			boolean first = true;
			Iterator<Resource> collectionItems = collection.getResources();

			while (collectionItems.hasNext()) {
				Resource collectionItem = collectionItems.next();
				if (DamUtil.isAsset(collectionItem) && isAssetImage(collectionItem, cf)) {
					Asset image = collectionItem.adaptTo(Asset.class);
					if (first) {
						ContentElement imageElement = cf.getElement("heroImage");
						imageElement.setContent(image.getPath(), null);
						first = false;
					} else {

						String variationName = cf.getName() + "-" + letter;
						String variationTitle = cf.getName() + " " + letter;
						cf.createVariation(variationName, variationTitle, cf.getDescription());
						ContentElement imageElement = cf.getElement("heroImage");
						ContentVariation imageVariation = imageElement.getVariation(variationName);
						imageVariation.setContent(image.getPath(), null);
						int charValue = letter.charAt(0);
						letter = String.valueOf((char) (charValue + 1));
						if (letter.equals("Z")) {
							letter = "A";
							if (prefix.isEmpty()) {
								prefix = "A";
							} else {
								int prefixChar = prefix.charAt(0);
								prefix = String.valueOf((char) (prefixChar + 1));
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
		boolean isImage = StringUtils.isNotEmpty(mimeType)
				&& (mimeType.equals("image/png") || mimeType.equals("image/jpeg"));
		return !isCF && isImage;
	}

}
