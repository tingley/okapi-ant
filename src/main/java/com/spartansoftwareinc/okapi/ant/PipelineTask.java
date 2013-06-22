package com.spartansoftwareinc.okapi.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class PipelineTask extends Task {
	
	public static final String BCONF_ATTR = "bconf";

	// Sample rainbow command line:
	// rainbow -np -pln pseudo.pln -sl en -se utf-8 -tl fr -te utf-8 simple.dfxp.xml -fc okf_dfxpstream -o out
	//
	// More reference: 
	//   http://www.opentag.com/okapi/wiki/index.php?title=Rainbow_-_Command_Line

	// What's the structure?
	// <pipeline bconf="...">
	//    <!-- file information -->
	// </pipeline>
	
	private String bconf;
	
	public void setBconf(String val) {
		this.bconf = val;
	}
	
	public void execute() {
		checkConfiguration();
		Project project = getProject();
		
		// Let's start by specifying a pipeline
		// Alternately, should I take a whole settings file?  That would give me the mappings.
		// Or, I could just take a bconf.  That would give me the plugins as well.

		log("bconf: " + bconf);
	}
	
	private void checkConfiguration() {
		if (bconf == null) {
			throw new BuildException(BCONF_ATTR + " was not set");
		}
	}
}
