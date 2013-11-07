package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.steps.common.FilterEventsToRawDocumentStep;
import net.sf.okapi.steps.common.RawDocumentToFilterEventsStep;
import net.sf.okapi.steps.generatesimpletm.GenerateSimpleTmStep;
import net.sf.okapi.steps.leveraging.LeveragingStep;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

public class TranslateTask extends BasePipelineTask {
	
	private String tmdir = "l10n";
	private String inEnc = "utf-8";
	private String outEnc = "utf-8";
	private String filterConfigDir = null;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	private String srcLang = null;
	
	public void setSrcLang(String srcLang) {
		this.srcLang = srcLang;
	}
	public void setTmdir(String tmdir) {
		this.tmdir = tmdir;
	}
	public void setInEnc(String inEnc) {
		this.inEnc = inEnc;
	}
	public void setOutEnc(String outEnc) {
		this.outEnc = outEnc;
	}
	public void addFileset(FileSet fileset) {
		this.filesets.add(fileset);
	}
	public FilterMapping createFilterMapping() {
		FilterMapping fm = new FilterMapping();
		filterMappings.add(fm);
		return fm;
	}
	public void setFilterConfigDir(String filterConfigDir) {
		this.filterConfigDir = filterConfigDir;
	}

	@Override
	void checkConfiguration() throws BuildException {
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		if (!tmDir.isDirectory()) {
			throw new BuildException("tm dir not present");
		}
	}

	@Override
	void executeWithOkapiClassloader() {
		
		Map<LocaleId, File> tmpDbs = createTempDbs();
		
		try {
			FilterConfigurationMapper fcMapper = getFilterMapper(filterConfigDir, null);
			PipelineDriver driver = new PipelineDriver();
			driver.setFilterConfigurationMapper(fcMapper);
			driver.addStep(new RawDocumentToFilterEventsStep());
			
			LeveragingStep leverage = new LeveragingStep();
			driver.addStep(leverage);
			
			net.sf.okapi.steps.leveraging.Parameters p =
					(net.sf.okapi.steps.leveraging.Parameters) leverage.getParameters();
			p.setResourceClassName("net.sf.okapi.connectors.simpletm.SimpleTMConnector");
			p.setFillTarget(true);
			p.setFillTargetThreshold(100);
			
			driver.addStep(new FilterEventsToRawDocumentStep());
			
			for (Entry<LocaleId, File> e : tmpDbs.entrySet()) {
	
				p.setResourceParameters("dbPath=" + e.getValue().getAbsolutePath());
				
				for (FileSet set : filesets) {
					DirectoryScanner ds = set.getDirectoryScanner();
					ds.scan();
					File baseDir = ds.getBasedir();
					for (String filename : ds.getIncludedFiles()) {
						File f = new File(baseDir, filename);
						URI inUri = Util.toURI(f.getAbsolutePath());
						String ext = splitExt(f)[1];
						String filterId = getMappedFilter(ext);
						if (filterId == null) {
							FilterConfiguration fc = fcMapper.getDefaultConfigurationFromExtension(ext);
							if (fc != null) {
								filterId = fc.configId;
							}
						}
						RawDocument rawDoc = new RawDocument(inUri, inEnc, new LocaleId(srcLang), e.getKey(), filterId);
						URI outUri = getOutUri(f, e.getKey());
						driver.addBatchItem(rawDoc, outUri, outEnc);
					}
				}
				
				driver.processBatch();
				
				driver.clearItems();
			}
		} finally {
			for (Entry<LocaleId, File> e : tmpDbs.entrySet()) {
				e.getValue().delete();
			}
		}
	}
	
	private String getMappedFilter(String ext) {
		for (FilterMapping fm : filterMappings) {
			if (fm.extension.equalsIgnoreCase(ext)) {
				return fm.filterConfig;
			}
		}
		return null;
	}
	
	private Map<LocaleId, File> createTempDbs() {
		
		Map<LocaleId, File> tmpDbs = new HashMap<LocaleId, File>();
		
		FilterConfigurationMapper fcMapper = getFilterMapper(filterConfigDir, null);
		PipelineDriver driver = new PipelineDriver();
		driver.setFilterConfigurationMapper(fcMapper);
		driver.addStep(new RawDocumentToFilterEventsStep());
		
		GenerateSimpleTmStep genTmStep = new GenerateSimpleTmStep();
		driver.addStep(genTmStep);
		
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".tmx");
			}
		};
		
		for (File f : tmDir.listFiles(filter)) {
			
			String trgLang = f.getName().substring(0, f.getName().indexOf("."));
			LocaleId trgLocale = new LocaleId(trgLang);
			
			File tmpDb = null;
			try {
				tmpDb = File.createTempFile("okapi-ant", ".h2.db");
				tmpDbs.put(trgLocale, tmpDb);
			} catch (IOException e) {
				throw new RuntimeException("Could not create temp file for SimpleTM");
			}
			
			net.sf.okapi.steps.generatesimpletm.Parameters p =
					(net.sf.okapi.steps.generatesimpletm.Parameters) genTmStep.getParameters();
			p.setTmPath(tmpDb.getAbsolutePath());
			
			RawDocument rawDoc = new RawDocument(f.toURI(), inEnc, new LocaleId(srcLang),
					trgLocale, "okf_tmx");
			driver.addBatchItem(rawDoc, f.toURI(), outEnc);
			
			driver.processBatch();
			
			driver.clearItems();
		}
		
		return tmpDbs;
	}

	private String[] splitExt(File file) {
		String name = file.getName();
		int lastDot = name.lastIndexOf('.');
		if (lastDot == -1) {
			return new String[] { name, "" };
		}
		String basename = name.substring(0, lastDot);
		String ext = name.substring(lastDot);
		return new String[] { basename, ext };
	}
	
	private URI getOutUri(File file, LocaleId locale) {
		String[] split = splitExt(file);
		File outFile = new File(file.getParentFile(),
				split[0] + "_" + locale.toPOSIXLocaleId() + split[1]);
		return outFile.toURI();
	}
}
