package com.adobe.aem.guides.wknd.core.models;

import org.apache.sling.api.resource.ValueMap;

import java.util.HashMap;
import java.util.Map;

public class AssetProcessingProfile {

    public static final String WORKER_PARAMETERS = "workerParameters";
    private final String title;
    private final String category;
    private final String resourceType;
    private Map<String, Object> params = new HashMap<>();

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public AssetProcessingProfile(String title, String category, String resourceType) {
        this.title = title;
        this.category = category;
        this.resourceType = resourceType;
    }

    /**
     * Copy rendition parameters from ValueMap to internal parameter map
     *
     * @param vm
     */
    public void addParameters(ValueMap vm, ValueMap workerParams) {
        //For backward compatibility: if workerParams is null, it is an old format. Put name in params and move other fields to workerParams
        for (String paramName : workerParams.keySet()) {
            if (!paramName.contains(":")) {
                paramName = paramName.trim();
                if (workerParams.get(paramName) instanceof String) {
                    String paramValue = ((String) workerParams.get(paramName)).trim();
                    addToWorkerParameter(paramName, paramValue);
                } else {
                    addToWorkerParameter(paramName, workerParams.get(paramName));
                }
            }
        }
        for (String paramName : vm.keySet()) {
            if (!paramName.contains(":")) {
                addParameter(paramName, vm.get(paramName));
            }
        }
    }

    public void addParameters(ValueMap vm) {
        for (String paramName : vm.keySet()) {
            if (!paramName.contains(":")) {
                addParameter(paramName, vm.get(paramName));
            }
        }
    }

    public void addToWorkerParameter(String key, Object value) {
        Object workerParameters = params.get(WORKER_PARAMETERS);
        if (workerParameters != null && workerParameters instanceof Map) {
            ((Map) workerParameters).put(key, value);
        } else {
            Map<String, Object> workerParams = new HashMap<>();
            workerParams.put(key, value);
            params.put(WORKER_PARAMETERS, workerParams);
        }
    }

    public void addParameter(String key, Object value) {
        params.put(key, value);
    }

}
