package com.adobe.aem.guides.wknd.core.models.impl;

import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Via;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.ExporterOption;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.via.ResourceSuperType;

import com.adobe.aem.guides.wknd.core.models.TargetContainer;
import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ContainerExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.adobe.cq.wcm.core.components.models.LayoutContainer;
import com.adobe.cq.wcm.core.components.models.ListItem;
import com.adobe.cq.wcm.core.components.models.datalayer.ComponentData;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

@Model(adaptables = SlingHttpServletRequest.class, adapters = {TargetContainer.class, ComponentExporter.class, ContainerExporter.class}, resourceType = TargetContainerImpl.RESOURCE_TYPE)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME,
        extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class TargetContainerImpl implements TargetContainer {

    protected static final String RESOURCE_TYPE = "wknd/components/targetcontainer";
    private static final String MBOX_NAME_PROPERTY = "mboxName";

    @Self
    @Via(type = ResourceSuperType.class)
    private LayoutContainer container;

    @ScriptVariable
    private ValueMap properties;

    private String mBoxName;

    @PostConstruct
    public void initModel() {
        mBoxName = properties.get(MBOX_NAME_PROPERTY, String.class);
    }

    @Override
    @JsonInclude
    public @Nullable String getMboxName() {
        return mBoxName != null ? mBoxName : "";
    }

    @Override
    public String getAccessibilityLabel() {
        return container.getAccessibilityLabel();
    }

    @Override
    public @NotNull LayoutType getLayout() {
        return container.getLayout();
    }

    @Override
    public String getRoleAttribute() {
        return container.getRoleAttribute();
    }

    @Override
    public @Nullable String getBackgroundStyle() {
        return container.getBackgroundStyle();
    }

    @Override
    public @NotNull Map<String, ? extends ComponentExporter> getExportedItems() {
        return container.getExportedItems();
    }

    @Override
    public @NotNull String[] getExportedItemsOrder() {
        return container.getExportedItemsOrder();
    }

    @Override
    public @NotNull List<ListItem> getItems() {
        return container.getItems();
    }

    @Override
    public @Nullable String getAppliedCssClasses() {
        return container.getAppliedCssClasses();
    }

    @Override
    public @Nullable ComponentData getData() {
        return container.getData();
    }

    @Override
    public @NotNull String getExportedType() {
        return RESOURCE_TYPE;
    }

    @Override
    public @Nullable String getId() {
        return container.getId();
    }

    
    
}
