package com.github.ksoichiro.eclipse.aar

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction

class GenerateTask extends BaseTask {
    Set<AndroidDependency> jarDependencies
    Set<AndroidDependency> aarDependencies

    static {
        String.metaClass.isNewerThan = { String v2 ->
            String v1 = delegate
            def versions1 = v1.tokenize('.')
            def versions2 = v2.tokenize('.')
            for (int i = 0; i < Math.min(versions1.size(), versions2.size()); i++) {
                int n1 = versions1[i].toInteger()
                int n2 = versions2[i].toInteger()
                if (n2 < n1) {
                    return true
                }
            }
            versions2.size() < versions1.size()
        }
    }

    GenerateTask() {
        description = 'Used for Eclipse. Copies all AAR dependencies for library directory.'
    }

    @TaskAction
    def exec() {
        extension = project.eclipseAar
        jarDependencies = [] as Set<AndroidDependency>
        aarDependencies = [] as Set<AndroidDependency>

        findTargetProjects()

        projects.each { Project p ->
            androidConfigurations().each { configuration ->
                println "Aggregating JAR dependencies for project ${p.name} from ${configuration.name} configuration"
                configuration.filter {
                    it.name.endsWith 'jar'
                }.each { File jar ->
                    def d = new AndroidDependency()
                    d.with {
                        group = "" // TODO
                        name = "" // TODO
                        version = "" // TODO
                        file = jar
                        artifactType = AndroidArtifactType.JAR
                    }
                    jarDependencies << d
                }
                jarDependencies = getLatestDependencies(jarDependencies)

                println "Aggregating AAR dependencies for project ${p.name} from ${configuration.name} configuration"
                configuration.filter { File aar ->
                    aar.name.endsWith('aar')
                }.each { File aar ->
                    def convertedPath = aar.path.tr(System.getProperty('file.separator'), '.')
                    def convertedPathExtStripped = convertedPath.lastIndexOf('.').with {
                        it != -1 ? convertedPath[0..<it] : convertedPath
                    }
                    def matchedDependency = configuration.dependencies.find { d -> convertedPathExtStripped.endsWith("${d.name}-release") }
                    if (matchedDependency instanceof ProjectDependency) {
                        // ProjectDependency should be not be exploded, just include in project.properties with relative path
                        println "  Skip ProjectDependency: ${matchedDependency} for file ${aar}"
                    } else {
                        def d = new AndroidDependency()
                        d.with {
                            group = "" // TODO
                            name = "" // TODO
                            version = "" // TODO
                            file = aar
                            artifactType = AndroidArtifactType.AAR
                        }
                        aarDependencies << d
                    }
                }
                aarDependencies = getLatestDependencies(aarDependencies)
            }
        }

        def extractDependenciesFrom = { Project p ->
            jarDependencies.each { AndroidDependency d -> moveJarIntoLibs(p, d) }
            aarDependencies.each { AndroidDependency d -> moveAndRenameAar(p, d) }
        }

        projects.each {
            extractDependenciesFrom it
        }

        projects.each { Project p ->
            List<File> aars = p?.file(extension.aarDependenciesDir)?.listFiles()?.findAll {
                it.isDirectory()
            }
            aars?.each { aar ->
                generateProjectPropertiesFile(p, aar)
                generateEclipseClasspathFile(p, aar)
                generateEclipseProjectFile(p, aar)
            }
            generateEclipseClasspathFileForParent(p)
        }
    }

    def androidConfigurations() {
        def result = []
        projects.each {
            result.addAll([it.configurations.compile, it.configurations.debugCompile])
        }
        result
    }

    static String getAarJarFilename(File file) {
        "${getBaseName(file.name)}.jar"
    }

    static String getDependencyProjectName(File file) {
        file.name.lastIndexOf('.').with { it != -1 ? file.name[0..<it] : file.name }
    }

    static String getBaseName(String filename) {
        filename.lastIndexOf('.').with { it != -1 ? filename[0..<it] : filename }
    }

    static String getDependencyName(String jarFilename) {
        def baseFilename = getBaseName(jarFilename)
        baseFilename.lastIndexOf('-').with { it != -1 ? baseFilename[0..<it] : baseFilename }
    }

    static String getVersionName(String jarFilename) {
        def baseFilename = getBaseName(jarFilename)
        baseFilename.lastIndexOf('-').with { it != -1 ? baseFilename.substring(it + 1) : baseFilename }
    }

    static Set<AndroidDependency> getLatestDependencies(Set<AndroidDependency> dependencies) {
        Set<AndroidDependency> latestDependencies = []
        dependencies.each { dependency ->
            def dependencyName = getDependencyName(dependency.file.name)
            String latestJarVersion = "0"
            def duplicateDependencies = dependencies.findAll { it.file.name.startsWith(dependencyName) }
            if (1 < duplicateDependencies.size()) {
                duplicateDependencies.each {
                    if (getVersionName(it.file.name).isNewerThan(latestJarVersion)) {
                        latestJarVersion = getVersionName(it.file.name)
                    }
                }
                latestDependencies << dependencies.find { getVersionName(it.file.name) == latestJarVersion }
            } else {
                latestDependencies << dependency
            }
        }
        latestDependencies
    }

    void moveJarIntoLibs(Project p, AndroidDependency dependency) {
        println "Added jar ${dependency.file}"
        copyJarIfNewer(p, 'libs', dependency.file, false)
    }

    void moveAndRenameAar(Project p, AndroidDependency dependency) {
        println "Added aar ${dependency.file}"
        def dependencyProjectName = getDependencyProjectName(dependency.file)

        // directory excluding the classes.jar
        p.copy {
            from p.zipTree(dependency.file)
            exclude 'classes.jar'
            into "${extension.aarDependenciesDir}/${dependencyProjectName}"
        }

        // Copies the classes.jar into the libs directory of the exploded AAR.
        // In Eclipse you can then import this exploded ar as an Android project
        // and then reference not only the classes but also the android resources :D
        ["${extension.aarDependenciesDir}/${dependencyProjectName}/libs", "libs"].each { dest ->
            copyJarIfNewer(p, dest, dependency.file, true)
        }
    }

    void copyJarIfNewer(Project p, String libsDir, File dependency, boolean isAarDependency) {
        def dependencyFilename = dependency.name
        def dependencyProjectName = getDependencyProjectName(dependency)
        def dependencyName = getDependencyName(dependencyFilename)
        def versionName = getVersionName(dependencyFilename)
        boolean isNewer = false
        boolean sameDependencyExists = false
        def dependencies = isAarDependency ? aarDependencies : jarDependencies
        def copyClosure = isAarDependency ? { destDir ->
            p.copy {
                from p.zipTree(dependency)
                exclude 'classes.jar'
                into "${extension.aarDependenciesDir}/${dependencyProjectName}"
            }
            p.copy {
                from p.zipTree(dependency)
                include 'classes.jar'
                into destDir
                rename { String fileName ->
                    fileName.replace('classes.jar', "${dependencyProjectName}.jar")
                }
            }
        } : { destDir ->
            p.copy {
                from dependency
                into destDir
            }
        }
        dependencies.findAll { AndroidDependency it ->
            // Check if there are any dependencies with the same name but different version
            getDependencyName(it.file.name) == dependencyName && getVersionName(it.file.name) != versionName
        }.each { AndroidDependency androidDependency ->
            println "  Same dependency exists: ${dependencyFilename}, ${androidDependency.file.name}"
            sameDependencyExists = true
            def v1 = getVersionName(dependencyFilename)
            def v2 = getVersionName(androidDependency.file.name)
            // 'androidDependency.file' may be removed in previous loop
            if (androidDependency.file.exists() && v1.isNewerThan(v2)) {
                println "  Found older dependency. Copy ${dependencyFilename} to all subprojects"
                isNewer = true
                // Should be replaced to jarFilename jar
                projects.each { Project pp ->
                    def projectLibDir = pp.file('libs')
                    if (isAarDependency) {
                        projectLibDir.listFiles().findAll {
                            it.isDirectory() && getDependencyName(it.name) == dependencyName
                        }.each { File lib ->
                            println "  REMOVED ${lib}"
                            pp.delete(lib)
                            pp.copy {
                                from pp.zipTree(dependency)
                                exclude 'classes.jar'
                                into "${extension.aarDependenciesDir}/${dependencyProjectName}"
                            }
                            copyClosure(projectLibDir)
                        }
                    } else {
                        projectLibDir.listFiles().findAll {
                            !it.isDirectory() && getDependencyName(it.name) == dependencyName
                        }.each { File lib ->
                            println "  REMOVED ${lib}"
                            pp.delete(lib)
                            copyClosure(projectLibDir)
                        }
                    }
                }
            }
        }
        if (!sameDependencyExists || isNewer) {
            println "  Copy new dependency: ${dependencyFilename}"
            copyClosure(libsDir)
        }
    }

    void generateProjectPropertiesFile(Project p, File aar) {
        p.file("${extension.aarDependenciesDir}/${aar.name}/project.properties").text = """\
target=${extension.androidTarget}
android.library=true
"""
    }

    void generateEclipseClasspathFile(Project p, File aar) {
        p.file("${extension.aarDependenciesDir}/${aar.name}/.classpath").text = """\
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="src" path="gen"/>
	<classpathentry kind="con" path="com.android.ide.eclipse.adt.ANDROID_FRAMEWORK"/>
	<classpathentry exported="true" kind="con" path="com.android.ide.eclipse.adt.LIBRARIES"/>
	<classpathentry exported="true" kind="con" path="com.android.ide.eclipse.adt.DEPENDENCIES"/>
	<classpathentry exported="true" kind="con" path="org.springsource.ide.eclipse.gradle.classpathcontainer"/>
	<classpathentry kind="output" path="bin/classes"/>
</classpath>
"""
    }

    void generateEclipseProjectFile(Project p, File aar) {
        def projectName = extension.projectName ?: p.name
        def name = aar.name
        p.file("${extension.aarDependenciesDir}/${name}/.project").text = """\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>${extension.projectNamePrefix}${projectName}-${name}</name>
	<comment></comment>
	<projects>
	</projects>
	<buildSpec>
		<buildCommand>
			<name>com.android.ide.eclipse.adt.ResourceManagerBuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
		<buildCommand>
			<name>com.android.ide.eclipse.adt.PreCompilerBuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
		<buildCommand>
			<name>com.android.ide.eclipse.adt.ApkBuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
	</buildSpec>
	<natures>
		<nature>org.springsource.ide.eclipse.gradle.core.nature</nature>
		<nature>org.eclipse.jdt.core.javanature</nature>
		<nature>com.android.ide.eclipse.adt.AndroidNature</nature>
	</natures>
</projectDescription>
"""
    }

    void generateEclipseClasspathFileForParent(Project p) {
        def classpathFile = p.file('.classpath')
        def libNames = []
        if (classpathFile.exists()) {
            // Aggregate dependencies
            def classPaths = new XmlSlurper().parseText(classpathFile.text)
            def libClassPathEntries = classPaths.classpathentry?.findAll { it.@kind?.text() == 'lib' }
            libNames = libClassPathEntries.collect { it.@path.text().replaceFirst('^libs/', '') }
        } else {
            // Create minimum classpath file
            classpathFile.text = """\
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
\t<classpathentry kind="src" path="src"/>
\t<classpathentry kind="src" path="gen"/>
\t<classpathentry kind="con" path="com.android.ide.eclipse.adt.ANDROID_FRAMEWORK"/>
\t<classpathentry exported="true" kind="con" path="com.android.ide.eclipse.adt.LIBRARIES"/>
\t<classpathentry exported="true" kind="con" path="com.android.ide.eclipse.adt.DEPENDENCIES"/>
\t<classpathentry kind="output" path="bin/classes"/>
</classpath>
"""
        }
        def jars = jarDependencies.collect { it.file.name } + aarDependencies.collect { getAarJarFilename(it.file) }
        jars = jars.findAll { !(it in libNames) }
        if (jars) {
            def entriesToAdd = jars.collect { it -> "\t<classpathentry kind=\"lib\" path=\"libs/${it}\"/>" }
            def lines = classpathFile.readLines()?.findAll { it != '</classpath>' }
            lines += entriesToAdd
            lines += "</classpath>${System.getProperty('line.separator')}"
            classpathFile.text = lines.join(System.getProperty('line.separator'))
        }
    }
}
