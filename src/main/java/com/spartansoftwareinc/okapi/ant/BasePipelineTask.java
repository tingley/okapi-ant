package com.spartansoftwareinc.okapi.ant;

import java.io.File;

import org.apache.tools.ant.Task;

import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.common.filters.DefaultFilters;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginsManager;

public abstract class BasePipelineTask extends Task {
	
	protected FilterConfigurationMapper getFilterMapper(String filterConfigPath, 
						PluginsManager plManager) {
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
	
	protected PipelineWrapper getPipelineWrapper(File baseDir, 
			FilterConfigurationMapper fcMapper, PluginsManager plManager) {
        PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper, baseDir.getPath(),
                plManager, baseDir.getPath(), baseDir.getPath(),
                null, null);
        pipelineWrapper.addFromPlugins(plManager);
        return pipelineWrapper;
    }
}
