package com.spartansoftwareinc.okapi.ant;

import java.io.File;

import net.sf.okapi.applications.rainbow.batchconfig.BatchConfiguration;
import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.common.ExecutionContext;
import net.sf.okapi.common.filters.DefaultFilters;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginsManager;

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
	
	private String bconfPath;
	
	public void setBconf(String val) {
		this.bconfPath = val;
	}
	
	// TODO - need to fiddle with shade config to exclude osx/swt stuff?
	public void execute() {
		checkConfiguration();
		
		// Let's start by specifying a pipeline
		// Alternately, should I take a whole settings file?  That would give me the mappings.
		// Or, I could just take a bconf.  That would give me the plugins as well.

		log("bconf: " + bconfPath);
		
		File bconfFile = getBconfFile(bconfPath);
		// Should probably create a temp working space, since this needs a lot of file paths

		File baseDir = new File("/tmp/bconf"); // XXX
		PluginsManager plManager = new PluginsManager();
		plManager.discover(new File(getConfigDirPath(baseDir)), true);
		PipelineWrapper pipelineWrapper = preparePipelineWrapper(baseDir, plManager);
        BatchConfiguration bconf = new BatchConfiguration();
        bconf.installConfiguration(bconfFile.getAbsolutePath(),
                getConfigDirPath(baseDir), pipelineWrapper);
	}
	
	// XXX This probably needs to expand the bconf somewhere temporary so that it can install 
	// the plugins, etc?
	private PipelineWrapper preparePipelineWrapper(File baseDir, PluginsManager plManager) {
        // Load local plug-ins                                           
		//plManager.discover(new File(WorkspaceUtils.getConfigDirPath(projId)), true);
        
		// Initialize filter configurations
        FilterConfigurationMapper fcMapper = new FilterConfigurationMapper();
        DefaultFilters.setMappings(fcMapper, false, true);
        //fcMapper.addFromPlugins(plManager);
        //fcMapper.setCustomConfigurationsDirectory(WorkspaceUtils.getConfigDirPath(projId));
        fcMapper.updateCustomConfigurations();

        // Load pipeline
        ExecutionContext context = new ExecutionContext();
        context.setApplicationName("okapi-ant");
        context.setIsNoPrompt(true);
        PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper, getConfigDirPath(baseDir),
                plManager, getInputDirPath(baseDir), getInputDirPath(baseDir),
                null, context);
        pipelineWrapper.addFromPlugins(plManager);
        return pipelineWrapper;
    }
	
	private String getConfigDirPath(File base) {
		return getPath(new File(base, "config"));
	}
	
	private String getInputDirPath(File base) {
		return getPath(new File(base, "input"));
	}
	
	private String getPath(File dir) {
		dir.mkdirs();
		return dir.getPath();
	}
	
	private File getBconfFile(String path) {
		File f = new File(path);
		if (!f.exists() || !f.isFile()) {
			throw new BuildException("Not a bconf file: " + path);
		}
		return f;
	}
	
	private void checkConfiguration() {
		if (bconfPath == null) {
			throw new BuildException(BCONF_ATTR + " was not set");
		}
	}
	
	// XXX remove
	public static void main(String[] args) {
		System.out.println("Hello world");
	}
}
