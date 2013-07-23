package com.spartansoftwareinc.okapi.ant;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.sf.okapi.applications.rainbow.Input;

import org.apache.tools.ant.BuildException;

public class TaskUtil {

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
}
	
