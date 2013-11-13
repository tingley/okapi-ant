okapi-ant
=========

This is a collection of ant tasks that provide functionality based on the 
[Okapi Framework](http://okapi.opentag.com/).

Currently, it supports 3 tasks, two of which are related to the batch 
configuration (bconf) files used by the [Longhorn](http://www.opentag.com/okapi/wiki/index.php?title=longhorn) server.

- `okapi:translate` - leverage TMX to produce target assets from source, as well as generating tkits for additional translation
- `okapi:mkbconf` - generate a batch configuration from components
- `okapi:installbconf` - unpack a batch configuration into components

Defining the Tasks
------------------

The tasks depend on both the okapi-ant jar and also on the presence of the
Okapi framework itself.  Task definition then typically looks something 
like this:

    <property environment="env"/>
    <condition property="okapi.lib" value="${env.OKAPI_HOME}/lib"
        else="/path/to/your/okapi/installation">
        <isset property="env.OKAPI_HOME"/>
    </condition>
    <!-- The task needs both its own code and an okapi installation to
       load. -->
    <path id="okapi.classpath">
      <fileset dir="${okapi.lib}" includes="**/*.jar" />
      <fileset dir="${basedir}" includes="okapi-ant-1.1-SNAPSHOT.jar" />
    </path>
    
    <!-- Load all the tasks in the okapi namespace.  -->
    <taskdef uri="antlib:com.spartansoftwareinc.okapi.ant"
        resource="com/spartansoftwareinc/okapi/ant/antlib.xml"
        classpathref="okapi.classpath" />

The antlib.xml file will define all tasks within the `okapi:` namespace.

Creating bconfs with `<okapi:mkbconf>`
--------------------------------------

This task provides similar functionality to the "Export Batch Configuration"
feature in Rainbow.  `<okapi:mkbconf>` can either generate a bconf based
on a Rainbow settings (`.rnb`) file, or a combination of component files
such as a pipeline (`.pln`) file and filter configuration (`.fprm`) files.

### Generating from a settings file
    <okapi:mkbconf settings="config/settings.rnb"
            bconfPath="output.bconf" 
            filterConfigDir="config">
        <!-- Add any additional plugins -->
        <fileset dir="plugins" includes="**/*.jar"/>
        <!-- filter configs are imported from the rnb file; these are
           - then added, overriding previous mappings. The value of
           - 'filterConfigDir' is searched for custom fprm files. -->
        <filterMapping extension=".xml" filterConfig="okf_xmlstream@cdata" />
    </okapi:mkbconf>

### Generating from a pipeline file
    <okapi:mkbconf pipeline="config/pipeline.pln"
            bconfPath="output.bconf" 
            filterConfigDir="config">
        <!-- Add any additional plugins -->
        <fileset dir="plugins" includes="**/*.jar"/>
        <!-- filter configs are imported from the rnb file; these are
           - then added, overriding previous mappings. The value of
           - 'filterConfigDir' is searched for custom fprm files. -->
        <filterMapping extension=".xml" filterConfig="okf_xmlstream@cdata" />
    </okapi:mkbconf>

Exploding bconfs with `<okapi:installbconf>`
--------------------------------------------

This task provides similar functionality to the "Install Batch Configuration"
feature in Rainbow.

    <!-- Very simple: write the contents of output.bconf to 
       - the installed_bconf directory, which much exist and be empty. -->
    <okapi:installbconf bconf="output.bconf" dir="installed_bconf" />

Translating localizable assets
------------------------------

This task allows you to generate localized versions of translatable assets
as part of the build process, as well as easily generate translation kits
to facilitate localization.

    <okapi:translate srcLang="en-us" srx="l10n/Segmentation.srx">
        <filterMapping extension=".properties" filterConfig="okf_properties" />
        <fileset dir="src" includes="**/Strings.properties" />
    </okapi:translate>

Usage:
- Place TMX files (one for each target language) in a top-level directory
  such as `l10n` (configurable via the `tmdir` parameter).
- Specify all files to be translated via `<fileset>`s. The translated
  files will be output to `source/file/path/${basename}_${targetLocale}.${ext}`.
- Specify `<filterMapping>`s if using custom filters or if Okapi cannot
  auto-detect an appropriate filter.
- OmegaT translation kits will be automatically generated in `tmdir/work` for
  any languages that are detected to be incomplete. Suppress this by setting
  e.g. `-Dokapi.generate=false`.
  
Additional supported parameters (specified as attributes on `<okapi:translate>`):
- `tmdir`: The location of the TMX files and tkit `work` directory
- `inEnc`: The encoding of the input files. The default value depends on your system.
  This is only used if the filter cannot detect the proper encoding.
- `outEnc`: The encoding of the output files. The default value depends on your system.
- `matchThreshold`: The minimum required matching percentage for leveraging
- `filterConfigDir`: The location of any custom filter configurations
- `srx`: The path of an SRX file to be used for segmenting. Specify this relative
  to the repository root.


Issues
------

When working with bconfs, the paths for any referenced files (for example, a
SRX file used as a parameter for a segmentation step) should be specified
relative to the location of the ant buildfile.  In some cases, this may mean
you need to modify your .pln or .rnb file to update the paths of these
referenced files.

In the translate step, due to differences in the way inline codes are handled
by OmegaT and Okapi, it is not possible at the moment to correctly leverage
text units containing inline codes.


Okapi Version Support
---------------------

Okapi M24-SNAPSHOT (at least 2013-11-13) or later is required.
