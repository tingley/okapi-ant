package com.spartansoftwareinc.okapi.ant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.sf.okapi.applications.rainbow.Input;
import net.sf.okapi.applications.rainbow.Project;
import net.sf.okapi.applications.rainbow.batchconfig.BatchConfiguration;
import net.sf.okapi.applications.rainbow.lib.LanguageManager;
import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginItem;
import net.sf.okapi.common.plugins.PluginsManager;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;

public class AssembleBatchConfigTask extends BasePipelineTask {
	private String plnPath;
	private String rnbPath;
	private String bconfPath;
	private String filterConfigPath;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	
	private Path tempPluginsDir = null;
	
	public void setSettings(String settingsPath) {
		this.rnbPath = settingsPath;
	}
	public void setPipeline(String settingsPath) {
		this.plnPath = settingsPath;
	}
	public void setBconfPath(String bconfPath) {
		this.bconfPath = bconfPath;
	}
	public void setFilterConfigDir(String filterConfigPath) {
		this.filterConfigPath = filterConfigPath;
	}
	public void addFileset(FileSet fileset) {
		this.filesets.add(fileset);
	}
	public FilterMapping createFilterMapping() {
		FilterMapping fm = new FilterMapping();
		filterMappings.add(fm);
		return fm;
	}
	
	@Override
	void executeWithOkapiClassloader() {
	    try {
	        assembleBatchConfig();
	    }
	    finally {
    	    if (tempPluginsDir != null) {
                TaskUtil.deleteDirectory(tempPluginsDir);
            }
	    }
	}
	
	void assembleBatchConfig() {
		// PManager.discover() doesn't work on individual
		// files, so I need to copy the contents of the task's
		// fileset to a temporary directory.
		PluginsManager plManager = null;
		try {
			tempPluginsDir = Files.createTempDirectory("plugins");
			plManager = TaskUtil.createPluginsManager(tempPluginsDir, filesets);
		}
		catch (IOException e) {
			throw new BuildException("Failed to initialize plugins", e);
		}
		
		for (PluginItem plugin : plManager.getList()) {
			System.out.println("Plugin: " + plugin.getClassName());
		}

		if (rnbPath != null) {
			loadFromSettings(rnbPath, plManager);
		}
		else {
			loadFromPipeline(plnPath, plManager);
		}
	}
	
	/**
	 * Load from an rnb file.
	 * @throws Exception 
	 */
	void loadFromSettings(String rnbPath, PluginsManager plManager) {
		LanguageManager lm = new LanguageManager(); // ???
		Project project = new Project(lm);
		try {
			project.load(rnbPath);
		}
		catch (Exception e) {
			throw new BuildException("Couldn't load project from " + rnbPath, e);
		}
		
		// Pipeline
		FilterConfigurationMapper fcMapper = getFilterMapper(filterConfigPath, plManager);
		PipelineWrapper pipelineWrapper = getPipelineWrapper(getProject().getBaseDir(), fcMapper, plManager);
		String pln = project.getUtilityParameters("currentProjectPipeline");
		pipelineWrapper.loadFromStringStorageOrReset(pln);
		
		// Allow <filterMapping> elements to override the contents of the settings.
		// BatchConfiguration.exportConfiguration() always takes the first configuration
		// it finds for a given extension, so we just list the overrides first.
		List<Input> inputFiles = processFilterMappings(fcMapper);
		inputFiles.addAll(project.getList(0));
		BatchConfiguration bconfig = new BatchConfiguration();
		System.out.println("Writing batch configuration to " + bconfPath);
		bconfig.exportConfiguration(bconfPath, pipelineWrapper, fcMapper, inputFiles);
	}
	
	void loadFromPipeline(String plnPath, PluginsManager plManager) {
		// Initialize things and load the pipeline.  This will produce
		// a warning if the pipeline references unavailable steps.
		// However, it will not break the build.  (Okapi gives me no 
		// easy way to intercept this problem.)
		FilterConfigurationMapper fcMapper = getFilterMapper(filterConfigPath, plManager);
		PipelineWrapper pipelineWrapper = getPipelineWrapper(getProject().getBaseDir(), 
												fcMapper, plManager);
        pipelineWrapper.load(plnPath);
        List<Input> inputFiles = processFilterMappings(fcMapper);	
        
		BatchConfiguration bconfig = new BatchConfiguration();
		System.out.println("Writing batch configuration to " + bconfPath);
		bconfig.exportConfiguration(bconfPath, pipelineWrapper, fcMapper, inputFiles);
	}
	
	List<Input> processFilterMappings(FilterConfigurationMapper fcMapper) {
		// Convert filter mappings into dummy input files so the extension
        // map is generated.  Also add any custom configurations while we go.
		List<Input> inputFiles = new ArrayList<Input>();
        for (FilterMapping fm : filterMappings) {
        	Input input = TaskUtil.createInput(fm.extension, fm.filterConfig);
        	inputFiles.add(input);
        	System.out.println("Added filter mapping " + fm.extension + " --> " + fm.filterConfig);
        	if (fcMapper.getConfiguration(input.filterConfigId) == null) {
        		System.out.println("Loading " + input.filterConfigId);
        		fcMapper.addCustomConfiguration(input.filterConfigId);
        		if (fcMapper.getConfiguration(input.filterConfigId) == null) {
        			throw new BuildException("Could not load filter configuration '" 
        									 + input.filterConfigId + "'");
        		}
        	}
        }
        return inputFiles;
	}
	
	private static final String RNB_ATTR = "settings";
	private static final String PLN_ATTR = "pipeline";
	private static final String BCONFPATH_ATTR = "bconfPath";
	private static final String FM_EXTENSION_ATTR = "extension";
	private static final String FM_FILTER_ATTR = "filterConfig";
	
	@Override
	void checkConfiguration() {
		if (plnPath == null && rnbPath == null) {
			throw new BuildException("One of " + PLN_ATTR + " and " +
									 RNB_ATTR + " must be set.");
		}
		if (plnPath != null && rnbPath != null) {
			throw new BuildException("Only one of " + PLN_ATTR + " or " +
					 RNB_ATTR + " may be set.");
		}
		TaskUtil.checkExists(BCONFPATH_ATTR, bconfPath);
		for (FilterMapping fm : filterMappings) {
			checkFilterMapping(fm);
		}
	}

	private void checkFilterMapping(FilterMapping fm) {
		TaskUtil.checkExists(FM_EXTENSION_ATTR, fm.extension);
		TaskUtil.checkExists(FM_FILTER_ATTR, fm.filterConfig);
	}

}
