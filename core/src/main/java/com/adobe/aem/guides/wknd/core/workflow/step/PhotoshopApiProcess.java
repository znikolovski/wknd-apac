package com.adobe.aem.guides.wknd.core.workflow.step;


import com.adobe.aem.guides.wknd.core.models.AssetProcessingProfile;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.sling.api.resource.*;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.engine.SlingRequestProcessor;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.day.cq.commons.jcr.JcrConstants.JCR_CONTENT;

@Component(service = WorkflowProcess.class, property = {
        Constants.SERVICE_DESCRIPTION + "=Process Indesign File",
        "process.label" + "=Process Indesign File"
})
@ServiceDescription("Process Indesign File")
public class PhotoshopApiProcess implements WorkflowProcess {

    public static final String INDESIGN_SYSTEM_USER = "indesign-system-user";

    protected static final String WCM_ASYNCCOMMAND_ENDPOINT = "/bin/asynccommand";


    public static String FILE_NAME = "fileName";
    public static String UPLOAD_TOKEN = "uploadToken";
    public static String MIME_TYPE = "mimeType";
    public static String FILE_SIZE = "fileSize";

    //add logger
    private static final Logger LOG = LoggerFactory.getLogger(PhotoshopApiProcess.class);

    private static final String AEMS_HOST_PLACEHOLDER = "(aems:\\/\\/\\$placeholder)(\\/.*?)(\\.tif)";
    private static final String AEMS_HOST_PLACEHOLDER_2 = "(aems:\\/\\/%24placeholder)(\\/.*?)(\\.tif)";

    private Clock clock = Clock.systemDefaultZone();

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private RequestResponseFactory requestResponseFactory;
    @Reference
    private SlingRequestProcessor requestProcessor;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
        try {
            String srcFilePath = workItem.getContentPath();
            String folderPath = srcFilePath.substring(0, srcFilePath.lastIndexOf("/"));

            String profilePath = metaDataMap.get("PROCESS_ARGS", String.class);
            if (StringUtils.isEmpty(profilePath)) {
                LOG.error("Profile path is empty");
                return;
            }

            LOG.info("Source file path : {}, Profile Path {}", srcFilePath, profilePath);
            Map<String, Object> param = new HashMap<>();
            param.put(ResourceResolverFactory.SUBSERVICE, INDESIGN_SYSTEM_USER);
            ResourceResolver resourceResolver = resolverFactory.getServiceResourceResolver(param);


            Map<String, Object> params = new HashMap<>();
            params.put("operation", "PROCESS");
            params.put("runAssetCompute", "true");
            params.put("processType", "advanced");
            params.put("runPostProcess", Boolean.FALSE.toString());
            Resource profileCategoryResource = resourceResolver.getResource(profilePath);
            if (profileCategoryResource == null) {
                LOG.error("Profile path is invalid");
                return;
            }
            AssetProcessingProfile processingProfile = getAssetProcessingProfile(profileCategoryResource);

            if (processingProfile != null) {
                String value = new ObjectMapper().writeValueAsString(processingProfile.getParams());
                LOG.info("profile {}", value);
                params.put("profile", value);
            } else {
                LOG.error("Profile path is invalid");
                return;
            }

            Asset asset = resourceResolver.getResource(srcFilePath).adaptTo(Asset.class);
            Rendition rendition = asset.getOriginal();
            InputStream is = null;
            Set<String> linkSet = Sets.newHashSet();
            if (rendition != null) {
                String assetName = asset.getName();
                String newAssetName = assetName.substring(0, assetName.lastIndexOf(".")) + "-processed"+assetName.substring(assetName.lastIndexOf("."));
                is = rendition.getStream();
                String encoding = rendition.getProperties().get(JcrConstants.JCR_ENCODING, String.class);
                if (encoding == null) {
                    encoding = "utf-8";
                }
                InputStreamReader r = new InputStreamReader(is, encoding);
                String nodeContent = IOUtils.toString(r);
                LOG.info("found the asset size {}", nodeContent.length());

                Pattern pattern = Pattern.compile(AEMS_HOST_PLACEHOLDER);
                Matcher matcher = pattern.matcher(nodeContent);
                while (matcher.find()) {
                    linkSet.add(matcher.group(2) + matcher.group(3));
                }
                String psdContent = matcher.replaceAll("$1$2.psd");

                pattern = Pattern.compile(AEMS_HOST_PLACEHOLDER_2);
                matcher = pattern.matcher(psdContent);
                while (matcher.find()) {
                    linkSet.add(matcher.group(2) + matcher.group(3));
                }

                psdContent = matcher.replaceAll("$1$2.psd");

                // Resource processedResource = resourceResolver.getResource(folderPath + "/" + newAssetName);
                // if (processedResource != null) {
                //     LOG.info("processed file already exist {}", processedResource.getPath());
                // } else {
                //     generateProcessedFile(folderPath, resourceResolver, newAssetName, psdContent);
                // }
            }
            for (String _tifFile : linkSet) {
                String tifFile = _tifFile.replace("%20", " ");
                Resource tifResource = resourceResolver.getResource(tifFile);
                String psdFile = tifFile.substring(0, tifFile.length() - 4) + ".psd";
                Resource psdResource = resourceResolver.getResource(psdFile);
                if (tifResource != null && psdResource == null) {
                    processTifFile(profilePath, resourceResolver, params, tifFile, tifResource);
                } else if (tifResource == null) {
                    LOG.error("cannot find the tif file {}", tifFile);
                } else if (tifResource != null && psdResource != null) {
                    LOG.info("skip {} because psd file {} already exists", tifFile, psdFile);
                }
            }
        } catch (Exception exception) {
            LOG.error("Error while getting the asset", exception);
        }
    }

    private AssetProcessingProfile getAssetProcessingProfile(Resource profileCategoryResource) {
        for (Resource renditionResource : profileCategoryResource.getChildren()) {
            Resource renditionContentResource = renditionResource.getChild(JCR_CONTENT);
            if (renditionContentResource == null) {
                continue;
            }
            ValueMap vm = renditionContentResource.getValueMap();
            String resourceType = vm.get("sling:resourceType", String.class);
            AssetProcessingProfile processingProfile = new AssetProcessingProfile(renditionResource.getName(),
                    profileCategoryResource.getName(), resourceType);
            //get workerParam
            Resource workerParameterResource = renditionResource.getChild(AssetProcessingProfile.WORKER_PARAMETERS);
            ValueMap workervm = null;
            if (workerParameterResource != null) {
                workervm = workerParameterResource.getValueMap();
            }
            processingProfile.addParameters(vm, workervm);
            return processingProfile;
        }
        return null;
    }

    private void processTifFile(String profilePath, ResourceResolver resourceResolver, Map<String, Object> params, String tifFile, Resource tifResource) throws ServletException, IOException {
        LOG.info("process {} using {}", tifFile, profilePath);
        params.put("asset", tifResource.getPath());
        params.put("description", "Process-" + tifResource.getName() + " by-workflow-" + clock.millis());
        HttpServletRequest request = requestResponseFactory.createRequest("POST", WCM_ASYNCCOMMAND_ENDPOINT, params);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletResponse response = requestResponseFactory.createResponse(out);
        requestProcessor.processRequest(request, response, resourceResolver);
    }

    private void generateProcessedFile(String folderPath, ResourceResolver resourceResolver, String newAssetName, String psdContent) throws ServletException, IOException, JSONException {
        Map<String, Object> initParams = new HashMap<>();
        initParams.put(FILE_NAME, newAssetName);
        initParams.put(FILE_SIZE, psdContent.length() + "");
        HttpServletRequest initRequest = requestResponseFactory.createRequest("POST", folderPath + ".initiateUpload.json", initParams);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpServletResponse initResponse = requestResponseFactory.createResponse(out);
        requestProcessor.processRequest(initRequest, initResponse, resourceResolver);

        JSONObject uploadResponse = new JSONObject(new String(out.toByteArray()));
        JSONArray filesJSON = uploadResponse.getJSONArray("files");
        JSONObject fileJSON = (JSONObject) filesJSON.get(0);

        String binaryPUTUrl = fileJSON.getJSONArray("uploadURIs").getString(0);
        LOG.info("found put url {}", binaryPUTUrl);
        Request.Put(binaryPUTUrl)
                .bodyByteArray(psdContent.getBytes()).execute().returnResponse();
        LOG.info("upload file to blob");

        String uploadToken = fileJSON.getString("uploadToken");
        String mimeType = fileJSON.getString("mimeType");
        Map<String, Object> completeParams = new HashMap<>();
        completeParams.put(FILE_NAME, newAssetName);
        completeParams.put(UPLOAD_TOKEN, uploadToken);
        completeParams.put(MIME_TYPE, mimeType);

        HttpServletRequest completeRequest = requestResponseFactory.createRequest("POST", uploadResponse.getString("completeURI"), completeParams);
        HttpServletResponse completeResponse = requestResponseFactory.createResponse(new ByteArrayOutputStream());
        requestProcessor.processRequest(completeRequest, completeResponse, resourceResolver);
    }

    private boolean readArgument(MetaDataMap args) {
        String argument = args.get("PROCESS_ARGS", "false");
        return argument.equalsIgnoreCase("true");
    }

}
