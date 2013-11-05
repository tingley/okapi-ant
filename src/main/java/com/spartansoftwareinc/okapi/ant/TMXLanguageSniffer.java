package com.spartansoftwareinc.okapi.ant;

import java.util.Set;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.resource.Property;

public class TMXLanguageSniffer extends BasePipelineStep {
	
	private LanguageSnifferCallback callback;
	
	public TMXLanguageSniffer(LanguageSnifferCallback callback) {
		this.callback = callback;
	}

	@Override
	public String getDescription() {
		return "Examines filter events to determine what "
				+ "languages are represented.";
	}

	@Override
	public String getName() {
		return "TMX Language Sniffer Step";
	}

	public Event handleTextUnit(Event event) {
		Property sourceLang = event.getTextUnit().getSource().getProperty("lang");
		if (sourceLang != null) {
			callback.sniffedSourceLanguage(sourceLang.getValue());
		}
		callback.sniffedTargetLanguages(event.getTextUnit().getTargetLocales());
		return event;
	}
	
	public static abstract class LanguageSnifferCallback {
		public abstract void sniffedSourceLanguage(String lang);
		public abstract void sniffedTargetLanguages(Set<LocaleId> locales);
	}
}
