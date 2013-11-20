package com.spartansoftwareinc.okapi.ant;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.pipelinedriver.PipelineDriver;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.steps.common.RawDocumentToFilterEventsStep;
import net.sf.okapi.steps.formatconversion.FormatConversionStep;

import org.apache.tools.ant.BuildException;

public class PostProcessTranslationTask extends BasePipelineTask {

	@Override
	void checkConfiguration() throws BuildException {
		// Nothing
	}

	@Override
	void executeWithOkapiClassloader() {
		
		// Make pipeline.
		final PipelineDriver driver = new PipelineDriver();
		driver.setFilterConfigurationMapper(getFilterMapper(null, null));
		
		// Step 1: Raw Docs to Filter Events
		driver.addStep(new RawDocumentToFilterEventsStep());
		
		// Step 2: Format Conversion
		FormatConversionStep conversion = new FormatConversionStep();
		net.sf.okapi.steps.formatconversion.Parameters cp =
				(net.sf.okapi.steps.formatconversion.Parameters) conversion.getParameters();
		cp.setOutputFormat("tmx");
		cp.setOutputPath(getProject().getProperty("okapi.target.tmx"));
		
		// Walk all XLIFFs and add them to pipeline
		File xlfDir = new File(getProject().getBaseDir(), getProject().getProperty("okapi.tkit.target"));
		try {
			Files.walkFileTree(xlfDir.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {
					if (attrs.isRegularFile() || !file.endsWith(".xlf")) {
						return FileVisitResult.CONTINUE;
					}
					RawDocument rawDoc = new RawDocument(file.toFile().toURI(), "utf-8",
							new LocaleId(getProject().getProperty("okapi.src.lang")),
							new LocaleId(getProject().getProperty("okapi.src.lang")),
							"okf_xliff");
					driver.addBatchItem(rawDoc, rawDoc.getInputURI(), "utf-8");
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new BuildException(e);
		}
		driver.processBatch();
	}
}
