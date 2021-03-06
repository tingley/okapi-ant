package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

public class ExecutePipelineTask extends BasePipelineTask {
	/**
	 * - Okapi PipelineStorage load code warns & removes missing plugins from
	 *   pipelines.  Can I make this be a hard error? 
	 */
	private String srcLang, tgtLang;
	private String srcEncoding = Charset.defaultCharset().name(),
				   tgtEncoding = Charset.defaultCharset().name();
	private String plnPath;
	private String filterConfigPath = TranslateTask.DEFAULT_RESOURCES_DIR;
	private String outputDirPath;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	private Plugins plugins;
	private Path tempPluginsDir;

	public Plugins createPlugins() {
		if (plugins != null) {
			throw new BuildException("Only one <plugins> element is allowed");
		}
		plugins = new Plugins();
		return plugins;
	}

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
	public void setOutputDir(String outputDirPath) {
		System.out.println("Setting output dir to " + outputDirPath);
		this.outputDirPath = outputDirPath;
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
		TaskUtil.checkExists(plnPath, "plnPath");
		if (filterConfigPath != null) {
			TaskUtil.checkDir("filterConfigPath", filterConfigPath);
		}
		if (outputDirPath != null) {
			TaskUtil.checkDir("outputDirPath", outputDirPath);
		}
		normalizeFilterMappings();
		if (plugins != null) {
			System.out.println("Got plugins element");
		}
	}

	@Override
	void executeWithOkapiClassloader() {
		PluginsManager plManager = null;
		try {
			FilterConfigurationMapper fcMapper = getFilterMapper(filterConfigPath,
					null);
			tempPluginsDir = Files.createTempDirectory("plugins");
			if (plugins != null) {
				plManager = TaskUtil.createPluginsManager(tempPluginsDir, plugins.filesets);
			}
			else {
				plManager = new PluginsManager();
			}
			PipelineWrapper pipelineWrapper = getPipelineWrapper(getProject()
					.getBaseDir(), fcMapper, plManager);
			pipelineWrapper.load(plnPath);
			Project prj = createProject(pipelineWrapper);
			for (FileSet fs : filesets) {
				DirectoryScanner ds = fs.getDirectoryScanner(getProject());
				for (String s : ds.getIncludedFiles()) {
					addFileToProject(prj, new File(ds.getBasedir(), s));
				}
			}
			pipelineWrapper.execute(prj);
		}
		catch (IOException e) {
			throw new BuildException("Failed to initialize plugins", e);
		}
		finally {
			TaskUtil.deleteDirectory(tempPluginsDir);
		}
	}

	private String getProjectOutputPath(File baseDir) {
		// XXX Doesn't work for absolute path
		return outputDirPath != null ?
				new File(baseDir, outputDirPath).getAbsolutePath() : baseDir.getPath();
	}

	@Override
	protected PipelineWrapper getPipelineWrapper(File baseDir,
			FilterConfigurationMapper fcMapper, PluginsManager plManager) {
		ExecutionContext context = new ExecutionContext();
		context.setApplicationName("Longhorn");
		context.setIsNoPrompt(true);
		String outputRoot = getProjectOutputPath(baseDir);
		PipelineWrapper pipelineWrapper = new PipelineWrapper(fcMapper,
				baseDir.getPath(), plManager, outputRoot,
				baseDir.getPath(), outputRoot, null, context);
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
		String outputRoot = getProjectOutputPath(getProject().getBaseDir());
		project.setOutputRoot(outputRoot);
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
		if (project.addDocument(0, file.getAbsolutePath(), srcEncoding, tgtEncoding,
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

	public class Plugins {
		List<FileSet> filesets = new ArrayList<FileSet>();
		public Plugins() { }
		public void addFileset(FileSet fileset) {
			this.filesets.add(fileset);
		}
		public List<FileSet> getFilesets() {
			return filesets;
		}
	}

}
