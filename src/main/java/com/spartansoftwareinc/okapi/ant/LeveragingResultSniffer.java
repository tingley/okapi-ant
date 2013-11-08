package com.spartansoftwareinc.okapi.ant;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.resource.TextContainer;

public class LeveragingResultSniffer extends BasePipelineStep {
	
	private LocaleId targetLocale = null;
	private int total = 0;
	private int leveraged = 0;

	@Override
	public String getDescription() {
		return "Examines filter events to determine how much of a pipeline was leveraged.";
	}

	@Override
	public String getName() {
		return "Leveraging Result Sniffer Step";
	}

	@Override
	public Event handleStartBatch(Event event) {
		total = 0;
		leveraged = 0;
		return event;
	}
	
	@Override
	public Event handleTextUnit(Event event) {
		total++;
		TextContainer tc = event.getTextUnit().getTarget(targetLocale);
		if (tc != null && !tc.isEmpty()) {
			leveraged++;
		}
		return event;
	}
	
	public void setTargetLocale(LocaleId targetLocale) {
		this.targetLocale = targetLocale;
	}
	
	public int getTotal() {
		return total;
	}
	
	public int getLeveraged() {
		return leveraged;
	}
	
	public static abstract class LeveragingSnifferCallback {
		public abstract void sniffedLeveragingResults(int total, int translated);
	}
}
