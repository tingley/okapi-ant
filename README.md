okapi-ant
=========

This is a collection of ant tasks that provide functionality based on the 
[Okapi Framework](http://okapi.opentag.com/).

Currently, it supports 5 tasks:

- `okapi:translate` and `okapi:post-translate` - tasks for implementing a 
   translation workflow
- `okapi:mkbconf` and `okapi:installbconf - tasks to work with batch 
   configuration (bconf) files for use with 
   [Longhorn](http://www.opentag.com/okapi/wiki/index.php?title=longhorn).
- `okapi:exec-pipeline` - execute a pre-defined okapi pipeline


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
      <fileset dir="${basedir}" includes="okapi-ant-1.2.jar" />
    </path>
    
    <!-- Load all the tasks in the okapi namespace.  -->
    <taskdef uri="antlib:com.spartansoftwareinc.okapi.ant"
        resource="com/spartansoftwareinc/okapi/ant/antlib.xml"
        classpathref="okapi.classpath" />

The antlib.xml file will define all tasks within the `okapi:` namespace.

Documentation
-------------

Task documentation is available on the [wiki](https://github.com/tingley/okapi-ant/wiki).

Examples
--------

Sample ant files that demonstrate the various tasks can be found in the
`examples` directory.


Okapi Version Support
---------------------

Okapi M26 or later is required.


Java Version Support
--------------------

As Okapi M26 and later requires Java 7, so too does this project.


License
-------

okapi-ant is licensed under the [LGPLv3](https://www.gnu.org/licenses/lgpl-3.0.txt).
