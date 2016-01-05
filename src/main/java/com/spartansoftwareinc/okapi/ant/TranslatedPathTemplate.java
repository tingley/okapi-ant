package com.spartansoftwareinc.okapi.ant;

import java.io.File;

import net.sf.okapi.common.LocaleId;

public class TranslatedPathTemplate {

	enum Variable {
		TargetBase("@{targetBase}"),
		Directory("@{dir}"),
		File("@{file}"),
		Locale("@{locale}"),
		Extension("@{extension}");
		private String code;
		Variable(String code) {
			this.code = code;
		}
		public String getCode() {
			return code;
		}
		@Override public String toString() {
			return code;
		}
		static Variable byCode(String s) {
			for (Variable v : values()) {
				if (v.code.equals(s)) return v;
			}
			throw new IllegalArgumentException("Unrecognized variable: " + s);
		}
		// Test to see if a String begins with a variable
		static Variable beginsWith(String s) {
			for (Variable v : values()) {
				if (s.startsWith(v.code)) {
					return v;
				}
			}
			throw new IllegalArgumentException("Unrecognized variable: " + s);
		}
	}

	private static final String DEFAULT_TEMPLATE =
			Variable.TargetBase.toString() + Variable.Locale + "/" + Variable.Directory +
			Variable.File + Variable.Extension;

	private String template = DEFAULT_TEMPLATE;

	public static TranslatedPathTemplate defaultTemplate() {
		return new TranslatedPathTemplate(DEFAULT_TEMPLATE);
	}

	private TranslatedPathTemplate(String template) {
		this.template = template;
	}

	public static TranslatedPathTemplate fromString(String s) {
		int start = 0;
		for (int i = s.indexOf('@'); i != -1; i = s.indexOf('@', start)) {
			String sub = s.substring(i);
			Variable v = Variable.beginsWith(sub);
			start = i + v.getCode().length();
		}
		return new TranslatedPathTemplate(s);
	}

	public String resolvePath(String targetBase, String dir, String file, String extension, LocaleId targetLocale) {
		StringBuilder sb = new StringBuilder();

		char[] buf = template.toCharArray();
		for (int i = 0; i < buf.length; i++) {
			char c = buf[i];
			if (c == '@') {
				String sub = template.substring(i);
				Variable v = Variable.beginsWith(sub);
				sb.append(resolveVariable(v, targetBase, dir, file, extension, targetLocale));
				i += v.getCode().length() - 1;
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private String resolveVariable(Variable v, String targetBase, String dir, String file, String extension,
								   LocaleId targetLocale) {
		switch (v) {
		case TargetBase:
			return targetBase.endsWith(File.separator) ? targetBase : targetBase + File.separator;
		case Directory:
			return dir.endsWith(File.separator) ? dir : dir + File.separator;
		case File:
			return file;
		case Extension:
			return extension;
		case Locale:
			return targetLocale.toBCP47();
		default:
			throw new IllegalStateException();
		}
	}
}
