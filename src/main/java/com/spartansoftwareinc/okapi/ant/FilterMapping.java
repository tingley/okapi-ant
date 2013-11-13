package com.spartansoftwareinc.okapi.ant;

public class FilterMapping {
	public FilterMapping() {}
	String extension;
	String filterConfig;
	public void setExtension(String extension) {
		this.extension = extension;
	}
	public void setFilterConfig(String filterConfig) {
		this.filterConfig = filterConfig;
	}
	public String toString() {
		return "FilterMapping('" + extension + "' --> " + filterConfig + ")";
	}
}