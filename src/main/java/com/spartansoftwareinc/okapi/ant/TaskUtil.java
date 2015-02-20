package com.spartansoftwareinc.okapi.ant;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.okapi.applications.rainbow.Input;
import net.sf.okapi.common.FileUtil;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.plugins.PluginsManager;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

public class TaskUtil {
	
	private static final String REGEX_LANG_CODE = "(?<![a-z])[a-z]{2}[-_][a-z]{2}(?![a-z])";
	private static final Pattern PATTERN_LANG_CODE = Pattern.compile(REGEX_LANG_CODE, Pattern.CASE_INSENSITIVE);

	public static final FilenameFilter TMX_FILE_FILTER = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			return name.endsWith(".tmx");
		}
	};

	public static Input createInput(String extension, String filterConfigId) {
    	// Other fields are unused by BatchConfiguration.exportConfiguration()
    	Input input = new Input();
    	input.filterConfigId = filterConfigId;
    	input.relativePath = "dummy" + extension;
    	return input;
	}
	
	/**
	 * Initialize a plugin manager, using the specified temporary working
	 * directory.
	 * @param tempPluginsDir
	 * @return
	 * @throws IOException
	 */
	public static PluginsManager createPluginsManager(Path tempPluginsDir, List<FileSet> filesets) throws IOException {
		// FileSet handling
		for (FileSet fs : filesets) {
			DirectoryScanner ds = fs.getDirectoryScanner();
			ds.scan();
			File baseDir = ds.getBasedir();
			for (String filename : ds.getIncludedFiles()) {
				File f = new File(baseDir, filename);
				TaskUtil.copyJarToDirectory(tempPluginsDir, f, false);
			}
		}
		PluginsManager plManager = new PluginsManager();
		System.out.println("Discovering from " + tempPluginsDir.toFile());
		plManager.discover(tempPluginsDir.toFile(), false);
		return plManager;
	}

	public static void deleteDirectory(Path dir) {
		if (dir == null) {
			return;
		}
		try {
			for (File f : dir.toFile().listFiles()) {
				if (f.isDirectory()) {
					deleteDirectory(f.toPath());
				}
				else {
					f.delete();
				}
			}
			Files.delete(dir);
		}
		catch (IOException e) {
			throw new BuildException("Failed to delete " + dir, e);
		}
	}
	
	public static Path copyJarToDirectory(Path dir, File f, boolean useTempName) throws IOException {
		Path dest = dir.resolve(f.getName());
		if (useTempName) {
			// Use temp file functionality to avoid name collisions when we 
			// flatten the directory structure
			dest = Files.createTempFile(dir, "plugin", ".jar");
		}
		InputStream is = new BufferedInputStream(new FileInputStream(f));
		OutputStream os = new BufferedOutputStream(Files.newOutputStream(dest));
		byte[] buf = new byte[8192];
		for (int r = is.read(buf); r != -1; r = is.read(buf)) {
			os.write(buf, 0, r);
		}
		os.close();
		is.close();
		return dest;
	}
	
	static void checkExists(String name, String value) {
		if (value == null) {
			throw new BuildException("Required attribute '" + name + "' was not set");
		}
	}
	static void checkDir(String name, String value) {
		File f = checkPath(name, value);
		if (!f.isDirectory()) {
			throw new BuildException("Value of " + name + " is not a directory: " + value);
		}
	}
	static File checkPath(String name, String value) {
		checkExists(name, value);
		File f = new File(value);
		if (!f.exists()) {
			throw new BuildException("Invalid " + name + " value: " + 
					value);
		}
		return f;
	}
	static void checkEmptyDirectory(String name, String value, boolean createIfMissing) {
		File f = new File(value);
		if (!f.exists() && createIfMissing) {
		    if (!f.mkdirs()) {
		        throw new BuildException("Couldn't create directory " + name);
		    }
		}
		if (!f.isDirectory() || f.list().length > 0) {
			throw new BuildException(name + " must refer to an empty directory");
		}
	}
	
	public static LocaleId guessLocale(File file, String srcLang) {
		if (file.isFile()) {
			List<String> langs = FileUtil.guessLanguages(file.getAbsolutePath());
			List<String> trgLangCandidates = new ArrayList<String>();
			for (String s : langs) {
				if (!s.equalsIgnoreCase(srcLang)) {
					trgLangCandidates.add(s);
				}
			}
			
			// If we have unique result, return now.
			if (trgLangCandidates.size() == 1) {
				return new LocaleId(trgLangCandidates.get(0));
			}
		}
		
		// Detect locale from filename.
		Matcher m = PATTERN_LANG_CODE.matcher(file.getName());
		if (m.find() && !m.group().equalsIgnoreCase(srcLang)) {
			return new LocaleId(m.group());
		}
		
		throw new BuildException("Could not determine target language of file " + file.getAbsolutePath());
	}
	
	public static List<File> filesetToFiles(List<FileSet> filesets) {
		List<File> result = new ArrayList<File>();
		for (FileSet set : filesets) {
			DirectoryScanner ds = set.getDirectoryScanner();
			ds.scan();
			File baseDir = ds.getBasedir();
			for (String filename : ds.getIncludedFiles()) {
				result.add(new File(baseDir, filename));
			}
		}
		return result;
	}
}
	
