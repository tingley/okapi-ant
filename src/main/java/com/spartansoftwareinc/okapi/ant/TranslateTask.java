package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.okapi.common.LocaleId;
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
import org.apache.tools.ant.types.FileSet;

public class TranslateTask extends BasePipelineTask {

	public static final String DEFAULT_WORK_DIR = "work";
	public static final String DEFAULT_RESOURCES_DIR = "l10n";

	// Parameters
	private Path tmDir;
	private Path workDir;
	private String inEnc = Charset.defaultCharset().name();
	private String outEnc = Charset.defaultCharset().name();
	private int matchThreshold = 95;
	private String filterConfigDir = null;
	private List<FileSet> filesets = new ArrayList<FileSet>();
	private List<FilterMapping> filterMappings = new ArrayList<FilterMapping>();
	private String srcLang = null;
	private String srx = null;
	private Path targetPath = null;
	private TargetPattern targetPattern = null;
	private TranslatedPathTemplate pathTemplate = TranslatedPathTemplate.defaultTemplate();

	// State
	private FileSystem fs = FileSystems.getDefault();

	private FilterConfigurationMapper fcMapper = null;
	
	public void setSrcLang(String srcLang) {
		this.srcLang = srcLang;
	}
	public void setTmDir(String tmdir) {
		this.tmDir = fs.getPath(tmdir);
	}
	public void setWorkDir(String dir) {
		this.workDir = fs.getPath(dir);
	}
	public void setInEnc(String inEnc) {
		this.inEnc = inEnc;
	}
	public void setOutEnc(String outEnc) {
		this.outEnc = outEnc;
	}
	/**
	 * Set the path to the directory where target files should be generated.
	 * If this is not set, files will be generated in the same locations that
	 * their corresponding source files were found.
	 * @param targetDir Path to the directory, which is expected to exist
	 */
	public void setTargetDir(String targetDir) {
	    this.targetPath = fs.getPath(targetDir);
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
	public void addTarget(TargetPattern targetPattern) {
		if (this.targetPattern != null) {
			throw new BuildException("Only one <target> element is allowed.");
		}
		this.targetPattern = targetPattern;
	}

	@Override
	void checkConfiguration() throws BuildException {
		if (tmDir == null) {
			tmDir = getProject().getBaseDir().toPath().resolve(DEFAULT_RESOURCES_DIR);
		}
		if (!Files.isDirectory(tmDir)) {
			throw new BuildException("TM dir not present.");
		}
		if (workDir == null) {
			workDir = tmDir.resolve(DEFAULT_WORK_DIR);
		}
		if (filesets.isEmpty()) {
			throw new BuildException("No files specified to translate.");
		}
		if (!Files.exists(targetPath)) {
			throw new BuildException("Target directory " + targetPath + " does not exist");
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
		if (targetPattern != null) {
			pathTemplate = targetPattern.getPathTemplate();
		}
	}
	
	private FilterConfigurationMapper getFilterMapper() {
		if (fcMapper == null) {
			String filterConfigDirPath = filterConfigDir == null ? DEFAULT_RESOURCES_DIR
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
		for (File tmx : tmDir.toFile().listFiles(TaskUtil.TMX_FILE_FILTER)) {
			
			LocaleId trgLocale = TaskUtil.guessLocale(tmx, srcLang);
			File tkit = getTkits(workDir).get(trgLocale);
			if (tkit != null && tkitWasModified(tkit, tmx.lastModified())) {
				System.out.println("Translation kit is newer than existing data. Post-processing...");
				PostProcessTranslationTask pp = new PostProcessTranslationTask();
				pp.doPostProcess(tkit, tmx, new LocaleId(srcLang), trgLocale);
			}

			lp.setResourceParameters("biFile=" + tmx.getAbsolutePath());
			sniffer.setTargetLocale(trgLocale);

			Set<RawDocument> rawDocs = new HashSet<RawDocument>();

			for (TaskUtil.FileSetEntry entry : TaskUtil.filesetToFiles(filesets)) {
				String ext = getExtension(entry.getFilename());
				String filterId = getMappedFilter(ext);
				if (filterId == null) {
					FilterConfiguration fc = getFilterMapper().getDefaultConfigurationFromExtension(ext);
					if (fc != null) {
						filterId = fc.configId;
					}
				}
				RawDocument rawDoc = new RawDocument(entry.getURI(), inEnc, new LocaleId(srcLang), trgLocale, filterId);
				URI outUri = getOutputUri(entry, ext, trgLocale);
				driver.addBatchItem(rawDoc, outUri, outEnc);
				rawDocs.add(rawDoc);
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

	private boolean tkitWasModified(File tkit, final long timestamp) {
		File target = new File(tkit, "target");
		final WrappedBool b = new WrappedBool();
		try {
			Files.walkFileTree(target.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path,
						BasicFileAttributes attrs) throws IOException {
					if (path.toFile().lastModified() > timestamp) {
						b.value= true;
						return FileVisitResult.TERMINATE;
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return b.value;
	}

	class WrappedBool {
		public boolean value = false;
	}

	@SuppressWarnings("unchecked")
	public static Map<LocaleId, File> getTkits(Path workDir) {

		if (!Files.isDirectory(workDir)) {
			//System.out.println("Translation work directory " + workDir + " doesn't exist.");
			return Collections.EMPTY_MAP;
		}

		Map<LocaleId, File> tkits = new HashMap<LocaleId, File>();

		for (File f : workDir.toFile().listFiles()) {
			if (f.isDirectory() && new File(f, "omegat.project").exists()) {
				tkits.put(TaskUtil.guessLocale(f, null), f);
			}
		}

		return tkits;
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
		ep.setPackageDirectory(workDir.toString());
		
		for (RawDocument doc : docs) {
			driver.addBatchItem(doc, doc.getInputURI(), outEnc);
		}

		driver.processBatch();
	}

	private String getExtension(String name) {
		int lastDot = name.lastIndexOf('.');
		if (lastDot == -1) {
			return "";
		}
		return name.substring(lastDot);
	}

	private URI getOutputUri(TaskUtil.FileSetEntry entry, String extension, LocaleId locale) {
		try {
			String dirName = entry.getBaseDir().getName();
			String fileName = entry.getFilename();
			if (fileName.endsWith(extension)) {
				fileName = fileName.substring(0, fileName.length() - extension.length());
			}
			String targetBase = targetPath != null ? targetPath.toString() : "";
			String tgtPathStr = pathTemplate.resolvePath(targetBase, dirName, fileName, extension, locale);
			Path targetPath = Paths.get(tgtPathStr);
			ensureDirectory(targetPath.getParent());
			System.out.println("Resolved dir=" + dirName + ", file=" + entry.getFilename() +
							   ", locale=" + locale + " ==> " + targetPath);
			return targetPath.toUri();
		}
		catch (IOException e) {
			throw new BuildException("Failed to create target for " + entry.getPath(), e);
		}
	}

	private Path ensureDirectory(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			dir = Files.createDirectories(dir);
		}
		return dir;
	}
}
