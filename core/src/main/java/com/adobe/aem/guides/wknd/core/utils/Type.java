package com.adobe.aem.guides.wknd.core.utils;

public enum Type {
	
	OFFER("OFF", "offers"), MESSAGE("MSG", "messages");
	
	private String type;
	private String destination;
	
	private Type(String type, String destination) {
		this.type = type;
		this.destination = destination;
	}
	
	public String getType() {
		return type;
	}
	
	public String getDestination() {
		return destination;
	}

}
