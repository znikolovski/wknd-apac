package com.adobe.aem.guides.wknd.core.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.HttpConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.guides.wknd.core.utils.WKNDConstants;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.Workflow;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.model.WorkflowModel;

@Component(
	service = Servlet.class,
	property = {
			"sling.servlet.extensions=html",
			"sling.servlet.extensions=htm",
			"sling.servlet.paths=/services/wknd/conversation",
			"sling.servlet.methods=get",
			"sling.servlet.methods=post",
	}
)
public class NextBestConversationHandler extends SlingAllMethodsServlet {

	private static final long serialVersionUID = 461810817936705087L;
	
	private static final Logger LOG = LoggerFactory.getLogger(NextBestConversationHandler.class);
	
	@Reference
	private ResourceResolverFactory resourceResolverFactory;
	
	@Override
	protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		try {
			ResourceResolver resourceResolver = getAdminResourceResolver();
			
			WorkflowSession workflowSession = resourceResolver.adaptTo(WorkflowSession.class);
			
			WorkflowModel xfModel = workflowSession.getModel(WKNDConstants.WORKFLOW_MODEL_NAME);
			WorkflowData wfData = workflowSession.newWorkflowData("JCR_PATH", WKNDConstants.OFFER_CF_PARRENT_PATH + "/" + request.getParameter("cf-name"));
			Map<String, String[]> requestMap = request.getParameterMap();
			wfData.getMetaDataMap().putAll(requestMap);
			
			Workflow wf = workflowSession.startWorkflow(xfModel, wfData);
			
			LOG.info(" state {} , is active {} metadata {} id {}", wf.getState(), wf.isActive(), wf.getMetaDataMap(), wf.getId() );

		} catch (WorkflowException e) {
			LOG.error("IO error processing request", e);
		} catch (LoginException e) {
			LOG.error("IO error processing request", e);
		}
		
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		// Assuming your json object is **jsonObject**, perform the following, it will return your json object  
		out.print("{status: 'ok'}");
		out.flush();
		
	}

	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		super.doGet(request, response);
	}
	
	private ResourceResolver getAdminResourceResolver() throws LoginException {
		Map<String, Object> param = new HashMap<String, Object>();
        param.put(ResourceResolverFactory.SUBSERVICE, "wkndService");
        
        ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
        
		return resourceResolver;
	}

}
