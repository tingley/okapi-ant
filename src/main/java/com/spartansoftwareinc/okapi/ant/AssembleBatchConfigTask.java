package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import net.sf.okapi.applications.rainbow.Input;
import net.sf.okapi.applications.rainbow.Project;
import net.sf.okapi.applications.rainbow.batchconfig.BatchConfiguration;
import net.sf.okapi.applications.rainbow.lib.LanguageManager;
import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.common.filters.DefaultFilters;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginItem;
import net.sf.okapi.common.plugins.PluginsManager;
//import net.sf.okapi.common.plugins.PManager;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

// XXX Would be nice to allow loading from .pln or .rnb
public class AssembleBatchConfigTask extends Task {
	private String okapiLib;
	private String plnPath;
	private String rnbPath;
	private String bconfPath;
	private String filterConfigPath;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	
	private File tempPluginsDir = null;
	
	public void setOkapiLib(String okapiLib) {
		this.okapiLib = okapiLib;
	}
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
	
	/**
	 * Initialize a plugin manager, using the specified temporary working
	 * directory.
	 * @param tempPluginsDir
	 * @return
	 * @throws IOException
	 */
	PluginsManager createPluginsManager(File tempPluginsDir) throws IOException {
		// FileSet handling
		for (FileSet fs : filesets) {
			DirectoryScanner ds = fs.getDirectoryScanner();
			ds.scan();
			File baseDir = ds.getBasedir();
			for (String filename : ds.getIncludedFiles()) {
				File f = new File(baseDir, filename);
				Util.copyJarToDirectory(tempPluginsDir, f, false);
			}
		}
		PluginsManager plManager = new PluginsManager();
		plManager.discover(tempPluginsDir, false);
		return plManager;
	}
	
	public void execute() {
		// We need to save and restore the context class loader, because
		// Okapi's PluginsManager uses the CCL to load plugins.  Therefore
		// we need to temporarily replace the CCL with the same classloader 
		// as the one used to load Okapi as part of instantiating this task,
		// so that we don't end up with odd situations where plugins can't be
		// detected.
		ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			installNewClassLoader(getClass().getClassLoader());
			assembleBatchConfig();
		}
		finally {
			if (tempPluginsDir != null) {
				Util.deleteDirectory(tempPluginsDir);
			}
			Thread.currentThread().setContextClassLoader(oldClassLoader);
		}
	}

	void installNewClassLoader(ClassLoader parentClassLoader) {
		File dir = new File(okapiLib);
		File[] jars = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".jar");
			}
		});
		List<URL> urls = new ArrayList<URL>();
		for (File j : jars) {
			try {
				urls.add(j.toURI().toURL());
			}
			catch (MalformedURLException e) {
				throw new BuildException(e);
			}
		}
		URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]),
													    parentClassLoader);
		Thread.currentThread().setContextClassLoader(classLoader);
	}
	
	public void assembleBatchConfig() {
		checkConfiguration();

		// PManager.discover() doesn't work on individual
		// files, so I need to copy the contents of the task's
		// fileset to a temporary directory.
		PluginsManager plManager = null;
		try {
			tempPluginsDir = Util.createTempDir("plugins");
			plManager = createPluginsManager(tempPluginsDir);
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
		File baseDir = getProject().getBaseDir();
		FilterConfigurationMapper fcMapper = getFilterMapper(plManager);
		PipelineWrapper pipelineWrapper = getPipelineWrapper(baseDir, fcMapper, plManager);
		String pln = project.getUtilityParameters("currentProjectPipeline");
		pipelineWrapper.loadFromStringStorageOrReset(pln);
		
		BatchConfiguration bconfig = new BatchConfiguration();
		System.out.println("Writing batch configuration to " + bconfPath);
		bconfig.exportConfiguration(bconfPath, pipelineWrapper, fcMapper, project.getList(0));
	}
	
	void loadFromPipeline(String plnPath, PluginsManager plManager) {
		// Initialize things and load the pipeline.  This will produce
		// a warning if the pipeline references unavailable steps.
		// However, it will not break the build.  (Okapi gives me no 
		// easy way to intercept this problem.)
		File baseDir = getProject().getBaseDir();
		FilterConfigurationMapper fcMapper = getFilterMapper(plManager);
		PipelineWrapper pipelineWrapper = getPipelineWrapper(baseDir, 
												fcMapper, plManager);
        pipelineWrapper.load(plnPath);

        // Convert filter mappings into dummy input files so the extension
        // map is generated.  Also add any custom configurations while we go.
		List<Input> inputFiles = new ArrayList<Input>();
        for (FilterMapping fm : filterMappings) {
        	Input input = new Input();
        	input.filterConfigId = fm.filterConfig;
        	input.relativePath = "dummy" + fm.extension;
        	// Other fields are unused by BatchConfiguration.exportConfiguration()
        	inputFiles.add(input);
        	if (fcMapper.getConfiguration(input.filterConfigId) == null) {
        		System.out.println("Loading " + input.filterConfigId);
        		fcMapper.addCustomConfiguration(input.filterConfigId);
        		if (fcMapper.getConfiguration(input.filterConfigId) == null) {
        			throw new BuildException("Could not load filter configuration '" 
        									 + input.filterConfigId + "'");
        		}
        	}
        }
        
		BatchConfiguration bconfig = new BatchConfiguration();
		System.out.println("Writing batch configuration to " + bconfPath);
		bconfig.exportConfiguration(bconfPath, pipelineWrapper, fcMapper, inputFiles);
	}
	
	private FilterConfigurationMapper getFilterMapper(PluginsManager plManager) {
		// Initialize filter configurations
        FilterConfigurationMapper fcMapper = new FilterConfigurationMapper();
        DefaultFilters.setMappings(fcMapper, false, true);
        fcMapper.addFromPlugins(plManager);
        if (filterConfigPath != null) {
        	System.out.println("Loading custom filter configurations from " + 
        				       filterConfigPath);
            fcMapper.setCustomConfigurationsDirectory(filterConfigPath);
        }
        return fcMapper;
    }
	
	private PipelineWrapper getPipelineWrapper(File baseDir, 
			FilterConfigurationMapper fcMapper, PluginsManager plManager) {
        PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper, baseDir.getPath(),
                plManager, baseDir.getPath(), baseDir.getPath(),
                null, null);
        pipelineWrapper.addFromPlugins(plManager);
        return pipelineWrapper;
    }
	
	public static class FilterMapping {
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
	
	private static final String RNB_ATTR = "settings";
	private static final String PLN_ATTR = "pipeline";
	private static final String OKAPI_ATTR = "okapiLib";
	private static final String BCONFPATH_ATTR = "bconfPath";
	private static final String FM_EXTENSION_ATTR = "extension";
	private static final String FM_FILTER_ATTR = "filterConfig";
	
	private void checkConfiguration() {
		if (plnPath == null && rnbPath == null) {
			throw new BuildException("One of " + PLN_ATTR + " and " +
									 RNB_ATTR + " must be set.");
		}
		if (plnPath != null && rnbPath != null) {
			throw new BuildException("Only one of " + PLN_ATTR + " or " +
					 RNB_ATTR + " may be set.");
		}
		checkPath(OKAPI_ATTR, okapiLib);
		checkExists(BCONFPATH_ATTR, bconfPath);
		for (FilterMapping fm : filterMappings) {
			checkFilterMapping(fm);
		}
	}

	private void checkFilterMapping(FilterMapping fm) {
		checkExists(FM_EXTENSION_ATTR, fm.extension);
		checkExists(FM_FILTER_ATTR, fm.filterConfig);
	}
	
	private void checkExists(String name, String value) {
		if (value == null) {
			throw new BuildException("Required attribute '" + name + "' was not set");
		}
	}
	private void checkPath(String name, String value) {
		checkExists(name, value);
		File f = new File(value);
		if (!f.exists()) {
			throw new BuildException("Invalid " + name + " value: " + 
					value);
		}
	}

}
