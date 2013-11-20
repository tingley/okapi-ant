package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
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
		
		Map<LocaleId, File> tkits = getTkits();
		if (tkits.isEmpty()) {
			System.out.println("No translation kits present.");
			return;
		}
		
		Map<LocaleId, File> tmxs = getTmxs();
		if (tmxs.isEmpty()) {
			System.out.println("No TMXs present.");
			return;
		}
		
		// Make pipeline.
		final PipelineDriver driver = new PipelineDriver();
		driver.setFilterConfigurationMapper(getFilterMapper(null, null));
		
		// Step 1: Raw Docs to Filter Events
		driver.addStep(new RawDocumentToFilterEventsStep());
		
		// Step 2: Format Conversion
		FormatConversionStep conversion = new FormatConversionStep();
		driver.addStep(conversion);
		net.sf.okapi.steps.formatconversion.Parameters cp =
				(net.sf.okapi.steps.formatconversion.Parameters) conversion.getParameters();
		cp.setOutputFormat(net.sf.okapi.steps.formatconversion.Parameters.FORMAT_TMX);
		
		for (LocaleId trgLocale : tkits.keySet()) {
			final LocaleId thisTrgLocale = trgLocale;
			File xlfDir = new File(tkits.get(trgLocale), "target");
			
			File outputTmx = tmxs.get(trgLocale);
			if (outputTmx == null) {
				System.out.println("No suitable TMX found for language " + trgLocale + ". Skipping.");
				continue;
			}
			
			System.out.println("Post-processing tkit " + tkits.get(trgLocale).getName()
					+ " to " + outputTmx.getName());
			
			cp.setOutputPath(outputTmx.getAbsolutePath());
			
			// Walk all XLIFFs and add them to pipeline.
			try {
				Files.walkFileTree(xlfDir.toPath(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file,
							BasicFileAttributes attrs) throws IOException {
						if (!attrs.isRegularFile() || !file.toFile().getName().endsWith(".xlf")) {
							return FileVisitResult.CONTINUE;
						}
						RawDocument rawDoc = new RawDocument(file.toFile().toURI(), "utf-8",
								new LocaleId(srcLang),
								thisTrgLocale,
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
	}
	
	@SuppressWarnings("unchecked")
	private Map<LocaleId, File> getTkits() {
		
		File tmDir = new File(getProject().getBaseDir(), tmdir);
		File workDir = new File(tmDir, TranslateTask.WORK_DIR_NAME);
		
		if (!workDir.isDirectory()) {
			System.out.println("Translation work directory doesn't exist.");
			return Collections.EMPTY_MAP;
		}
		
		Map<LocaleId, File> tkits = new HashMap<LocaleId, File>();
		
		for (File f : workDir.listFiles()) {
			if (f.isDirectory() && new File(f, "omegat.project").exists()) {
				tkits.put(TaskUtil.guessLocale(f, srcLang), f);
			}
		}
		
		return tkits;
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
