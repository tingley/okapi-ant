okapi-ant
=========

This is a collection of ant tasks that provide functionality based on the 
[Okapi Framework](http://okapi.opentag.com/).

Currently, all the tasks are related to the batch configuration (bconf)
files used by the [Longhorn](http://www.opentag.com/okapi/wiki/index.php?title=longhorn) server.

Defining the Tasks
------------------

The tasks depend on both the okapi-ant jar and also on the presence of the
Okapi framework itself.  Task definition then typically looks something 
like this:

    <property name="okapi.lib" 
        value="/Applications/okapi-apps_cocoa-macosx-x86_64_0.21/lib/" />
    <!-- The task needs both its own code and an okapi installation to
       load. -->
    <path id="okapi.classpath">
      <fileset dir="${okapi.lib}" includes="**/*.jar" />
      <fileset dir="${basedir}" includes="okapi-ant-1.0-SNAPSHOT.jar" />
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

Issues
------

The paths for any referenced files (for example, a SRX file used as a
parameter for a segmentation step) should be specified relative to the 
location of the ant buildfile.  In some cases, this may mean you need to
modify your .pln or .rnb file to update the paths of these referenced files.

Okapi Version Support
---------------------

This code was tested with Okapi M21 and should work with newer versions as
well.  It may work with older versions, but I don't have plans to support
them.
