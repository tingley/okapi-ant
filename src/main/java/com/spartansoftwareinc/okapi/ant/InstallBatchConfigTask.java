package com.spartansoftwareinc.okapi.ant;

import net.sf.okapi.applications.rainbow.batchconfig.BatchConfiguration;
import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginsManager;

public class InstallBatchConfigTask extends BasePipelineTask {
	private String bconfPath, directoryPath;
	
	public void setBconf(String bconfPath) {
		this.bconfPath = bconfPath;
	}
	public void setDir(String dirPath) {
		this.directoryPath = dirPath;
	}
	
	@Override
	void executeWithOkapiClassloader() {
		PluginsManager plManager = new PluginsManager();
		// HACK: We have to initialize the PluginsManager -somewhere-, because otherwise 
		// the PipelineWrapper constructor will crash.  This is because the PluginsManager
		// lazily initializes its internal lists, rather than allowing for empty lists.
		plManager.discover(getProject().getBaseDir(), true);
		FilterConfigurationMapper fcMapper = new FilterConfigurationMapper();
		PipelineWrapper pipelineWrapper = getPipelineWrapper(getProject().getBaseDir(), 
				fcMapper, plManager);
		System.out.println("Writing " + bconfPath + " to " + directoryPath);
		new BatchConfiguration().installConfiguration(bconfPath, directoryPath,
													  pipelineWrapper);
	}
	
	private static final String BCONF_ATTR = "bconf";
	private static final String DIR_ATTR = "dir";
	
	@Override
	void checkConfiguration() {
		TaskUtil.checkExists(BCONF_ATTR, bconfPath);
		TaskUtil.checkExists(DIR_ATTR, directoryPath);
		TaskUtil.checkEmptyDirectory(DIR_ATTR, directoryPath, true);
	}
}
