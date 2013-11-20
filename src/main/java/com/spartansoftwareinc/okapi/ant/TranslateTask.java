package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.steps.common.FilterEventsToRawDocumentStep;
import net.sf.okapi.steps.common.RawDocumentToFilterEventsStep;
import net.sf.okapi.steps.leveraging.LeveragingStep;
import net.sf.okapi.steps.rainbowkit.creation.ExtractionStep;
import net.sf.okapi.steps.segmentation.SegmentationStep;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

public class TranslateTask extends BasePipelineTask {
	
	public static final String WORK_DIR_NAME = "work";
	
	private String tmdir = "l10n";
	private String inEnc = Charset.defaultCharset().name();
	private String outEnc = Charset.defaultCharset().name();
	private int matchThreshold = 95;
	private String filterConfigDir = null;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	private String srcLang = null;
	private String srx = null;
	
	private FilterConfigurationMapper fcMapper = null;
	
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
	public void setMatchThreshold(String matchThreshold) {
		this.matchThreshold = Integer.parseInt(matchThreshold);
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
	public void setSrx(String srx) {
		this.srx = srx;
	}

	@Override
	void checkConfiguration() throws BuildException {
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		if (!tmDir.isDirectory()) {
			throw new BuildException("TM dir not present.");
		}
		if (filesets.isEmpty()) {
			throw new BuildException("No files specified to translate.");
		}
		if (srcLang == null) {
			throw new BuildException("Source language must be set.");
		}
		if (srx != null) {
			File srxFile = new File(getProject().getBaseDir(), srx);
			if (!srxFile.isFile()) {
				throw new BuildException("Could not locate specified SRX file.");
			}
		}
	}
	
	private FilterConfigurationMapper getFilterMapper() {
		if (fcMapper == null) {
			File tmDir = new File(getProject().getBaseDir(), tmdir);
			String filterConfigDirPath = filterConfigDir == null ? tmDir.getAbsolutePath()
					: new File(getProject().getBaseDir(), filterConfigDir).getAbsolutePath();
			fcMapper = super.getFilterMapper(filterConfigDirPath, null);
		}
		return fcMapper;
	}

	@Override
	void executeWithOkapiClassloader() {
		
		// Make pipeline.
		PipelineDriver driver = new PipelineDriver();
		driver.setFilterConfigurationMapper(getFilterMapper());
		
		// Step 1: Raw Docs to Filter Events
		driver.addStep(new RawDocumentToFilterEventsStep());
		
		if (srx != null) {
			// Step 2: Segmentation
			SegmentationStep segmentation = new SegmentationStep();
			driver.addStep(segmentation);
			
			net.sf.okapi.steps.segmentation.Parameters sp =
					(net.sf.okapi.steps.segmentation.Parameters) segmentation.getParameters();
			sp.setSegmentSource(true);
			sp.setSourceSrxPath(new File(getProject().getBaseDir(), srx).getAbsolutePath());
			sp.setCopySource(false);
		}
		
		// Step 3: Leverage
		LeveragingStep leverage = new LeveragingStep();
		driver.addStep(leverage);
		
		net.sf.okapi.steps.leveraging.Parameters lp =
				(net.sf.okapi.steps.leveraging.Parameters) leverage.getParameters();
		lp.setResourceClassName("net.sf.okapi.connectors.bifile.BilingualFileConnector");
		lp.setFillTarget(true);
		lp.setFillTargetThreshold(matchThreshold);
		
		// Step 4: Leveraging Result Sniffer
		LeveragingResultSniffer sniffer = new LeveragingResultSniffer();
		driver.addStep(sniffer);
		
		// Step 5: Filter Events to Raw Docs (write output files)
		driver.addStep(new FilterEventsToRawDocumentStep());
		
		// Run pipeline once for each TMX
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		for (File tmx : tmDir.listFiles(TaskUtil.TMX_FILE_FILTER)) {
			
			LocaleId trgLocale = TaskUtil.guessLocale(tmx, srcLang);

			lp.setResourceParameters("biFile=" + tmx.getAbsolutePath());
			sniffer.setTargetLocale(trgLocale);
			
			Set<RawDocument> rawDocs = new HashSet<RawDocument>();
			
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
						FilterConfiguration fc = getFilterMapper().getDefaultConfigurationFromExtension(ext);
						if (fc != null) {
							filterId = fc.configId;
						}
					}
					RawDocument rawDoc = new RawDocument(inUri, inEnc, new LocaleId(srcLang), trgLocale, filterId);
					URI outUri = getOutUri(f, trgLocale);
					driver.addBatchItem(rawDoc, outUri, outEnc);
					rawDocs.add(rawDoc);
				}
			}
			
			driver.processBatch();
			
			driver.clearItems();
			
			if (sniffer.getTotal() != sniffer.getLeveraged()) {
				System.out.println("There are some untranslated strings for " + trgLocale);
				if (!"false".equals(getProject().getProperty("okapi.generate"))) {
					generateOmegaTKit(trgLocale, leverage, rawDocs, tmx);
				}
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
	
	private void generateOmegaTKit(LocaleId locale, LeveragingStep lStep, Set<RawDocument> docs, File tmx) {
		
		// Make pipeline.
		PipelineDriver driver = new PipelineDriver();
		driver.setFilterConfigurationMapper(getFilterMapper());
		
		driver.setRootDirectories("", getProject().getBaseDir().getAbsolutePath());
		driver.setOutputDirectory(getProject().getBaseDir().getAbsolutePath());
		
		// Step 1: Raw Docs to Filter Events
		driver.addStep(new RawDocumentToFilterEventsStep());
		
		if (srx != null) {
			// Step 2: Segmentation
			SegmentationStep segmentation = new SegmentationStep();
			driver.addStep(segmentation);
			
			net.sf.okapi.steps.segmentation.Parameters sp =
					(net.sf.okapi.steps.segmentation.Parameters) segmentation.getParameters();
			sp.setSegmentSource(true);
			sp.setSourceSrxPath(new File(getProject().getBaseDir(), srx).getAbsolutePath());
			sp.setCopySource(false);
		}
		
		// Step 3: Leverage
		driver.addStep(lStep);
		
		// Step 4: Approve ALL the TUs!
		ApproverStep approver = new ApproverStep();
		driver.addStep(approver);
		
		// Step 5: Extraction
		ExtractionStep extraction = new ExtractionStep();
		driver.addStep(extraction);
		
		net.sf.okapi.steps.rainbowkit.creation.Parameters ep =
				(net.sf.okapi.steps.rainbowkit.creation.Parameters) extraction.getParameters();
		ep.setWriterClass("net.sf.okapi.steps.rainbowkit.omegat.OmegaTPackageWriter");
		ep.setWriterOptions("#v1\n"
                + "placeholderMode.b=false\n"
                + "allowSegmentation.b=false\n"
                + "includePostProcessingHook.b=true");
		ep.setPackageName("Translate_" + locale.toString());
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		File outDir = new File(tmDir, WORK_DIR_NAME);
		ep.setPackageDirectory(outDir.getAbsolutePath());
		
		for (RawDocument doc : docs) {
			driver.addBatchItem(doc, doc.getInputURI(), outEnc);
		}
		
		driver.processBatch();
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
