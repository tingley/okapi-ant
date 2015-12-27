package com.spartansoftwareinc.okapi.ant;

import org.junit.Test;

import net.sf.okapi.common.LocaleId;

import static org.junit.Assert.*;

public class TestTranslatedPathTemplate {

	@Test
	public void testFactory() {
		assertNotNull(TranslatedPathTemplate.fromString("@{targetBase}@{dir}@{file}_@{locale}@{extension}"));
		assertNotNull(TranslatedPathTemplate.fromString("@{targetBase}@{locale}/@{dir}@{file}@{extension}"));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testInvalidTemplate() {
		TranslatedPathTemplate.fromString("@{foo}");
	}

	@Test
	public void testResolveDefaultTemplate() {
		// "@locale/@dir@file@extension"
		assertEquals("foo/fr-fr/bar/quux.html",
				TranslatedPathTemplate.defaultTemplate().resolvePath("foo", "bar", "quux", ".html",
									new LocaleId("fr", "fr")));
	}

	@Test
	public void testResolveCustomTemplate() {
		assertEquals("foo/bar/quux_fr-fr.html",
					 TranslatedPathTemplate.fromString("@{targetBase}@{dir}@{file}_@{locale}@{extension}")
					 	.resolvePath("foo", "bar", "quux", ".html", new LocaleId("fr", "fr")));
	}
}
