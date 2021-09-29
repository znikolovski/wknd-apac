/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.adobe.aem.guides.wknd.core.servlets;

import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.model.WorkflowModel;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import javax.servlet.Servlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.Session;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Servlet.class,
        property = {
            Constants.SERVICE_DESCRIPTION + "=Retouch Asset Servlet",
            "sling.servlet.methods=" + HttpConstants.METHOD_POST,
            "sling.servlet.paths=" + "/bin/retouchasset"
        })
public class RetouchAssetServlet extends SlingAllMethodsServlet {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    protected void doPost(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain");
        PrintWriter out = resp.getWriter();
        try {
            // Get resource resolver from the request object
            ResourceResolver resourceResolver = req.getResourceResolver();
            // Get jcr session from the resource resolver
            Session session = resourceResolver.adaptTo(Session.class);
            // Get workflow session from the resource resolver
            WorkflowSession workflowSession = resourceResolver.adaptTo(WorkflowSession.class);

            log.info("*****Retouch Assets Servlet*****");
            out.println("*****Retouch Assets Servlet*****");

            //String sourceCollectionPath = request.getParameter("sourceCollectionPath");
            String[] paths = req.getParameterValues("path");
            log.info("paths:-");
            out.println("paths:-");
            for (String path : paths) {
                log.info("starting workflow on payload: " + path);
                out.println("starting workflow on payload: " + path);

                // Get the workflow payload
                String payloadPath = path;

                // Workflow model path - This is the already created workflow
                String model = "/var/workflow/models/conf/wknd/settings/workflow/models/retouch-assets/jcr:content/model";

                // Get the workflow model object
                WorkflowModel workflowModel = workflowSession.getModel(model);

                // Create a workflow Data (or Payload) object pointing to a resource via JCR
                // Path (alternatively, a JCR_UUID can be used)
                WorkflowData workflowData = workflowSession.newWorkflowData("JCR_PATH", payloadPath);

                // Optionally pass in workflow metadata via a Map
                final Map<String, Object> workflowMetadata = new HashMap<>();

                // Start the workflow!
                workflowSession.startWorkflow(workflowModel, workflowData, workflowMetadata);

                log.info("Workflow:" + model + " started");
                out.println("Workflow:" + model + " started");

                log.info("");
                out.println();
            }

            log.info("*****[SUCCESS] Retouch Assets Servlet*****");
            out.println("*****[SUCCESS] Retouch Assets Servlet*****");
        } catch (Exception ex) {
            out.println(ex.toString());
            log.info(ex.toString());
        }
    }
}
