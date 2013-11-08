package com.spartansoftwareinc.okapi.ant;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.Property;
import net.sf.okapi.common.resource.TextContainer;

public class ApproverStep extends BasePipelineStep {

	@Override
	public String getDescription() {
		return "Step that approves all TU targets.";
	}

	@Override
	public String getName() {
		return "Approver Step";
	}
	
	@Override
	public Event handleTextUnit(Event event) {
		ITextUnit tu = event.getTextUnit();
		for (LocaleId locale : tu.getTargetLocales()) {
			TextContainer tc = tu.getTarget(locale);
			if (!tc.isEmpty()) {
				tu.setTargetProperty(locale, new Property(Property.APPROVED, "yes"));
			}
		}
		return event;
	}

}
