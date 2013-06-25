package com.spartansoftwareinc.okapi.ant;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.tools.ant.BuildException;

public class Util {

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
}
