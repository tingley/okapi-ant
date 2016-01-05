package com.spartansoftwareinc.okapi.ant;

import org.apache.tools.ant.BuildException;

public class TargetPattern {
	private String pattern;
	public TargetPattern() {}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public TranslatedPathTemplate getPathTemplate() {
		if (pattern == null) {
			throw new BuildException("<target> element without pattern attribute.");
		}
		return TranslatedPathTemplate.fromString(pattern);
	}
}
