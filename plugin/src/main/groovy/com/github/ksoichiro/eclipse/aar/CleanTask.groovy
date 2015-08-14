package com.github.ksoichiro.eclipse.aar

import org.gradle.api.tasks.TaskAction

class CleanTask extends BaseTask {
    CleanTask() {
        description = 'Used for Eclipse. Cleans AAR dependencies directory.'
    }

    @TaskAction
    def exec() {
        extension = project.eclipseAar

        findTargetProjects()
        projects.each { AndroidProject p ->
            def targets = [ p.project.file(extension.aarDependenciesDir) ]
            println p.project.name+".cleanLibsDirectoryEnabled="+p.project.eclipseAar.cleanLibsDirectoryEnabled
            if (p.project.eclipseAar.cleanLibsDirectoryEnabled) {
                targets << p.project.file('libs')
            }
            targets.each {
                if (it.exists()) {
                    p.project.delete(it)
                    println "Deleted ${it}"
                }
            }
        }
    }
}
