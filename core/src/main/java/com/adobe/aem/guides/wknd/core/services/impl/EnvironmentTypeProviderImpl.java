package com.adobe.aem.guides.wknd.core.services.impl;

import java.util.Map;

import com.adobe.aem.guides.wknd.core.services.EnvironmentTypeProvider;

import org.apache.felix.scr.annotations.Property;
import org.apache.jackrabbit.oak.commons.PropertiesUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

@Component(service = EnvironmentTypeProvider.class, property = {
    "label = WKND Environment Type Provider",
    "description = WKND Environment Type Provider"
  }
  )
public class EnvironmentTypeProviderImpl implements EnvironmentTypeProvider {

    @Property(label = "Environment Type",
        description = "Should be dev/stage/prod/local - default is local")
    private static final String ENVIRONMENT_TYPE = "environment.type";
    private String environmentType;

    private static final String DEFAULT_ENVIRONMENT_TYPE = "author";

    @Activate
    @Modified
    protected void activate(final Map<String, String> config) {
        environmentType = PropertiesUtil.toString(config.get(ENVIRONMENT_TYPE), DEFAULT_ENVIRONMENT_TYPE);
    }

    @Override
    public String getEnvironmentType() {
        return environmentType;
    }
    
}
