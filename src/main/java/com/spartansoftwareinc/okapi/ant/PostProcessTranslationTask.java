package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.steps.common.RawDocumentToFilterEventsStep;
import net.sf.okapi.steps.formatconversion.FormatConversionStep;

import org.apache.tools.ant.BuildException;

public class PostProcessTranslationTask extends BasePipelineTask {

	private String tmdir = "l10n";
	private String srcLang = null;
	
	public void setTmdir(String tmdir) {
		this.tmdir = tmdir;
	}
	public void setSrcLang(String srcLang) {
		this.srcLang = srcLang;
	}
	
	@Override
	void checkConfiguration() throws BuildException {
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		if (!tmDir.isDirectory()) {
			throw new BuildException("TM dir not present.");
		}
		if (srcLang == null) {
			throw new BuildException("Source language must be set.");
		}
	}

	@Override
	void executeWithOkapiClassloader() {
		
		Map<LocaleId, File> tkits = TranslateTask.getTkits(new File(getProject().getBaseDir(), tmdir));
		if (tkits.isEmpty()) {
			System.out.println("No translation kits present.");
			return;
		}
		
		Map<LocaleId, File> tmxs = getTmxs();
		if (tmxs.isEmpty()) {
			System.out.println("No TMXs present.");
			return;
		}
		
		for (LocaleId trgLocale : tkits.keySet()) {
			File outputTmx = tmxs.get(trgLocale);
			if (outputTmx == null) {
				System.out.println("No suitable TMX found for language " + trgLocale + ". Skipping.");
				continue;
			}
			doPostProcess(tkits.get(trgLocale), outputTmx, new LocaleId(srcLang), trgLocale);
		}
	}
	
	
	void doPostProcess(File tkit, File outputTmx, final LocaleId srcLocale, final LocaleId trgLocale) {
		File xlfDir = new File(tkit, "target");
		
		System.out.println("Post-processing tkit " + tkit.getName()
				+ " to " + outputTmx.getName());
		
		final PipelineDriver driver = getDriver();
		
		getParams().setOutputPath(outputTmx.getAbsolutePath());
		
		// Walk all XLIFFs and add them to pipeline.
		try {
			Files.walkFileTree(xlfDir.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path,
						BasicFileAttributes attrs) throws IOException {
					if (!attrs.isRegularFile()) {
						return FileVisitResult.CONTINUE;
					}
					File file = path.toFile();
					if (!file.getName().endsWith(".xlf")) {
						return FileVisitResult.CONTINUE;
					}
					RawDocument rawDoc = new RawDocument(file.toURI(), "utf-8",
							srcLocale,
							trgLocale,
							"okf_xliff");
					driver.addBatchItem(rawDoc, null, "utf-8");
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new BuildException(e);
		}
		driver.processBatch();
		
		driver.clearItems();
	}
	
	private net.sf.okapi.steps.formatconversion.Parameters cp;
	private PipelineDriver driver;
	
	private net.sf.okapi.steps.formatconversion.Parameters getParams() {
		if (cp == null) {
			initPipeline();
		}
		return cp;
	}
	
	private PipelineDriver getDriver() {
		if (driver == null) {
			initPipeline();
		}
		return driver;
	}
	
	private void initPipeline() {
		// Make pipeline.
		driver = new PipelineDriver();
		driver.setFilterConfigurationMapper(getFilterMapper(null, null));
		
		// Step 1: Raw Docs to Filter Events
		driver.addStep(new RawDocumentToFilterEventsStep());
		
		// Step 2: Format Conversion
		FormatConversionStep conversion = new FormatConversionStep();
		driver.addStep(conversion);
		cp = (net.sf.okapi.steps.formatconversion.Parameters) conversion.getParameters();
		cp.setOutputFormat(net.sf.okapi.steps.formatconversion.Parameters.FORMAT_TMX);
	}
	
	private Map<LocaleId, File> getTmxs() {
		
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		
		Map<LocaleId, File> tmxs = new HashMap<LocaleId, File>();
		
		for (File f : tmDir.listFiles(TaskUtil.TMX_FILE_FILTER)) {
			tmxs.put(TaskUtil.guessLocale(f, srcLang), f);
		}
		
		return tmxs;
	}
}
