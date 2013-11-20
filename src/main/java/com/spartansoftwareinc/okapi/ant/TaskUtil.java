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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.okapi.applications.rainbow.Input;
import net.sf.okapi.common.FileUtil;
import net.sf.okapi.common.LocaleId;

import org.apache.tools.ant.BuildException;

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
	
	public static File createTempDir(String prefix) throws IOException {
		File f = File.createTempFile(prefix, "");
		
		if (!f.delete() || !f.mkdir()) {
			throw new BuildException("Failed to create temporary directory");
		}
		return f;
	}
	
	public static void deleteDirectory(File dir) {
		if (dir == null) {
			return;
		}
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				deleteDirectory(f);
			}
			else {
				f.delete();
			}
		}
		dir.delete();
	}
	
	public static File copyJarToDirectory(File dir, File f, boolean useTempName) throws IOException {
		File dest = new File(dir, f.getName());
		if (useTempName) {
			// Use temp file functionality to avoid name collisions when we 
			// flatten the directory structure
			dest = File.createTempFile("plugin", ".jar", dir);
		}
		InputStream is = new BufferedInputStream(new FileInputStream(f));
		OutputStream os = new BufferedOutputStream(new FileOutputStream(dest));
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
	static void checkPath(String name, String value) {
		checkExists(name, value);
		File f = new File(value);
		if (!f.exists()) {
			throw new BuildException("Invalid " + name + " value: " + 
					value);
		}
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
}
	
