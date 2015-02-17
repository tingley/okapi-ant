package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.sf.okapi.applications.rainbow.Project;
import net.sf.okapi.applications.rainbow.lib.LanguageManager;
import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.common.ExecutionContext;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginsManager;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;

public class PipelineTask extends BasePipelineTask {
	/**
	 * <okapi:exec-pipeline pipeline="foo.pln" srcLang="en" tgtLang="fr"
	 * 			srcEncoding="UTF-8" tgtEncoding="UTF-8" filterConfigDir="filterConfigs">
	 * 		<filterMapping extension=".xml" filterConfig="okf_xmlstream@myConfig" />
	 * 		<fileset dir="files" includes"..." />
	 * 		<!-- How to handle plugins? I will need to figure it out. 
	 *           I think I need a <plugins> element with embedded filesets. -->
	 * </okapi:exec-pipeline>
	 *
	 * Things to do: - Handle plugins - Specify output - I might need to specify
	 * the baseDir somehow?
	 */

	private String srcLang, tgtLang, srcEncoding, tgtEncoding;
	private String plnPath;
	private String filterConfigPath;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();

	public void setSrcLang(String value) {
		this.srcLang = value;
	}

	public void setTgtLang(String value) {
		this.tgtLang = value;
	}

	public void setSrcEncoding(String value) {
		this.srcEncoding = value;
	}

	public void setTgtEncoding(String value) {
		this.tgtEncoding = value;
	}

	public void setPipeline(String settingsPath) {
		this.plnPath = settingsPath;
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
	void checkConfiguration() throws BuildException {
		TaskUtil.checkExists("srcLang", srcLang);
		TaskUtil.checkExists("tgtLang", tgtLang);
		TaskUtil.checkExists("srcEncoding", srcEncoding);
		TaskUtil.checkExists("tgtEncoding", tgtEncoding);
		TaskUtil.checkExists(plnPath, "plnPath");
		if (filterConfigPath != null) {
			TaskUtil.checkPath("filterConfigPath", filterConfigPath);
		}
		normalizeFilterMappings();
	}

	@Override
	void executeWithOkapiClassloader() {
		FilterConfigurationMapper fcMapper = getFilterMapper(filterConfigPath,
				null);
		PluginsManager plManager = new PluginsManager();
		PipelineWrapper pipelineWrapper = getPipelineWrapper(getProject()
				.getBaseDir(), fcMapper, plManager);
		pipelineWrapper.load(plnPath);
		Project prj = createProject(pipelineWrapper);
		for (FileSet fs : filesets) {
			for (String s : fs.getDirectoryScanner().getIncludedFiles()) {
				addFileToProject(prj, new File(s));
			}
		}
		pipelineWrapper.execute(prj);
	}

	@Override
	protected PipelineWrapper getPipelineWrapper(File baseDir,
			FilterConfigurationMapper fcMapper, PluginsManager plManager) {
		ExecutionContext context = new ExecutionContext();
		context.setApplicationName("Longhorn");
		context.setIsNoPrompt(true);
		PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper,
				baseDir.getPath(), plManager, baseDir.getPath(),
				baseDir.getPath(), baseDir.getPath(), null, context);
		pipelineWrapper.addFromPlugins(plManager);
		return pipelineWrapper;
	}

	// Based on code from ProjectUtils in longhorn
	private Project createProject(PipelineWrapper pipelineWrapper) {
		Project project = new Project(new LanguageManager());
		String projectPath = getProject().getBaseDir().getAbsolutePath();
		project.setSourceLanguage(new LocaleId(srcLang, true));
		project.setTargetLanguage(new LocaleId(tgtLang, true));
		project.setInputRoot(0, projectPath, true);
		project.setOutputRoot(projectPath);
		project.setUseOutputRoot(true);
		return project;
	}

	private void addFileToProject(Project project, File file) {
		String extension = getExtension(file.getName());
		String filterConfigId = getFilterConfigMapping(extension);
		if (filterConfigId == null) {
			throw new BuildException(
					"Unable to find a filter configuration for "
							+ file.getName());
		}
		if (project.addDocument(0, file.getAbsolutePath(), null, null,
				filterConfigId, false) == 1) {
			throw new BuildException("Adding document " + file.getName()
					+ " to list of input files failed");
		}
	}

	private String getFilterConfigMapping(String fileExtension) {
		for (FilterMapping fm : filterMappings) {
			if (fm.extension.equals(fileExtension)) {
				return fm.filterConfig;
			}
		}
		return null;
	}

	private void normalizeFilterMappings() {
		for (FilterMapping fm : filterMappings) {
			if (!fm.extension.startsWith(".")) {
				fm.extension = "." + fm.extension;
			}
		}
	}

	private String getExtension(String filename) {
		int i = filename.lastIndexOf('.');
		if (i == -1) {
			return "";
		}
		return filename.substring(i);
	}
}
