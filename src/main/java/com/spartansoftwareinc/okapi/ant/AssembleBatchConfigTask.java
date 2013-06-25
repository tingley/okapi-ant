package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.applications.rainbow.pipeline.StepInfo;
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
	private static final String RNB_ATTR = "settings";
	private static final String OKAPI_ATTR = "okapiLib";
	
	private String okapiLib;
	private String rnbPath;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	
	public void setOkapiLib(String okapiLib) {
		this.okapiLib = okapiLib;
	}
	public void setSettings(String settingsPath) {
		this.rnbPath = settingsPath;
	}
	
	public void addFileset(FileSet fileset) {
		this.filesets.add(fileset);
	}
	
	PluginsManager createPManager(File tempPluginsDir) throws IOException {
		// FileSet handling
		for (FileSet fs : filesets) {
			DirectoryScanner ds = fs.getDirectoryScanner();
			ds.scan();
			File baseDir = ds.getBasedir();
			for (String filename : ds.getIncludedFiles()) {
				File f = new File(baseDir, filename);
				File out = Util.copyJarToDirectory(tempPluginsDir, f);
				System.out.println("Including: " + f + " --> " + out);
			}
		}
		System.out.println("Discovering from " + tempPluginsDir);
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
			runPlugin();
		}
		finally {
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
	
	public void runPlugin() {
		checkConfiguration();

		// PManager.discover() doesn't work on individual
		// files, so I need to copy the contents of the task's
		// fileset to a temporary directory.
		File tempPluginsDir = null;
		PluginsManager plManager = null;
		try {
			tempPluginsDir = Util.createTempDir("plugins");
			plManager = createPManager(tempPluginsDir);
		}
		catch (IOException e) {
			throw new BuildException("Failed to initialize plugins", e);
		}
		
		for (PluginItem plugin : plManager.getList()) {
			System.out.println("Plugin: " + plugin.getClassName());
		}
		
		File baseDir = getProject().getBaseDir();
		// XXX Need to discover elsewhere as well -- instead of baseDir,
		// pass plugins directory location
		// should I handle it as a file?  as a dir?  Need to explore ant task
		// interface to fileset info.
		/*
		PipelineWrapper pipelineWrapper = preparePipelineWrapper(baseDir, plManager);
		
		// XXX Hack - expect a raw pln file for now
        pipelineWrapper.load(rnbPath);
		for (StepInfo stepInfo : pipelineWrapper.getSteps()) {
			System.out.println(stepInfo.name);
		}
		*/
		Util.deleteDirectory(tempPluginsDir);

	}
	
	// TODO: refactor with PipelineTask
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

        // Load pipeline -- TODO: as of M21 we can use execution context here
        /*
        ExecutionContext context = new ExecutionContext();
        context.setApplicationName("okapi-ant");
        context.setIsNoPrompt(true);
        // TODO: fix the paths
        PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper, baseDir.getPath(),
                plManager, baseDir.getPath(), baseDir.getPath(),
                null, context);
                */
        /*
        PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper, getConfigDirPath(baseDir),
                plManager, getInputDirPath(baseDir), getInputDirPath(baseDir),
                null, context);
                */
        //pipelineWrapper.addFromPlugins(plManager);
        //return pipelineWrapper;
        return null;
    }
	

	private void checkConfiguration() {
		checkPath(RNB_ATTR, rnbPath);
		checkPath(OKAPI_ATTR, okapiLib);
	}
	
	private void checkPath(String name, String value) {
		if (value == null) {
			throw new BuildException(name + " was not set");
		}
		File f = new File(value);
		if (!f.exists()) {
			throw new BuildException("Invalid " + name + " value: " + 
					value);
		}
	}

}
