package com.github.ksoichiro.eclipse.aar

import com.android.build.gradle.AppPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.testfixtures.ProjectBuilder

class TargetConfigurationsSpec extends BaseSpec {
    def "default configurations"() {
        setup:
        Project project = ProjectBuilder.builder().withProjectDir(new File("src/test/projects/normal")).build()
        project.plugins.apply AppPlugin
        project.plugins.apply PLUGIN_ID

        when:
        GenerateTask task = project.tasks['generateEclipseDependencies'] as GenerateTask
        task.extension = project.eclipseAar
        List<Configuration> result = task.androidConfigurations(project)

        then:
        result.size() == 2
        !result.any { !(it in [project.configurations.compile, project.configurations.debugCompile]) }
    }

    def "changed configurations"() {
        setup:
        Project project = ProjectBuilder.builder().withProjectDir(new File("src/test/projects/normal")).build()
        project.plugins.apply AppPlugin
        project.plugins.apply PLUGIN_ID
        project.eclipseAar.targetConfigurations << 'releaseCompile'

        when:
        GenerateTask task = project.tasks['generateEclipseDependencies'] as GenerateTask
        task.extension = project.eclipseAar
        List<Configuration> result = task.androidConfigurations(project)

        then:
        result.size() == 3
        !result.any { !(it in [project.configurations.compile, project.configurations.debugCompile, project.configurations.releaseCompile ]) }
    }
}
