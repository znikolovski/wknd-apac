package com.adobe.aem.guides.wknd.core.models;

import org.osgi.annotation.versioning.ProviderType;

import com.adobe.cq.wcm.core.components.models.Container;

@ProviderType
public interface TargetContainer extends Container {

    public String getMboxName();
    
}
