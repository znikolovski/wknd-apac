package com.adobe.aem.guides.wknd.core.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

public class ExporterUtil {
	
	public static final boolean filterNode(String node) {
		for (ReservedNode reserveNode: ReservedNode.values()) {
			if(node.contains(reserveNode.getNode())) {
				return true;
			}
		}
		
		return false;
	}
	
	public static final ValueMap filterValueMap(ValueMap valueMap) {
		Map<String, Object> filteredMap = new HashMap<>();
		
		for(String key: valueMap.keySet()) {
			if(!filterNode(key)) {
				filteredMap.put(key, valueMap.get(key));
			}
		}
		
		return new ValueMapDecorator(filteredMap);
	}
	
	private static enum ReservedNode {
		CQ("cq:"),
		SLING("sling"),
		JCR_CONTENT("jcr:content"),
		JCR_MIXIN_TYPES("jcr:mixinTypes"),
		JCR_LAST_MODIFIED_BY("jcr:lastModifiedBy"),
		JCR_CREATED("jcr:created"),
		JCR_CREATED_BY("jcr:createdBy"),
		JCR_LAST_MODIFIED("jcr:lastModified"),
		JCR_PRIMARY_TYPE("jcr:primaryType");
		
		private String node;
		
		ReservedNode(String node) {
			this.node = node;
		}
		
		public String getNode() {
			return node;
		}
	}

}
