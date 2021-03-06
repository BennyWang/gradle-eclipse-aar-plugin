package com.github.ksoichiro.eclipse.aar

import com.android.build.gradle.AppPlugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class CleanSpec extends BaseSpec {
    @Rule
    TemporaryFolder temporaryFolder

    def "cleaning all directories by default"() {
        setup:
        Project project = ProjectBuilder
                .builder()
                .withProjectDir(temporaryFolder.root)
                .build()
        def libsDirs = [project.file('aarDependencies'), project.file('libs')]
        libsDirs*.mkdirs()

        project.plugins.apply AppPlugin
        project.plugins.apply PLUGIN_ID
        setupRepositories(project)
        project.dependencies {
            compile 'com.android.support:appcompat-v7:21.0.2'
            compile 'com.nineoldandroids:library:2.4.0'
            compile 'com.melnykov:floatingactionbutton:1.0.7'
            compile 'com.github.ksoichiro:android-observablescrollview:1.5.0'
        }

        when:
        project.tasks.cleanEclipseDependencies.execute()

        then:
        !project.file('aarDependencies').exists()
        project.file('libs').exists()
    }

    def "cleaning libs directory is enabled"() {
        setup:
        Project project = ProjectBuilder
                .builder()
                .withProjectDir(temporaryFolder.root)
                .build()
        def libsDirs = [project.file('aarDependencies'), project.file('libs')]
        libsDirs*.mkdirs()

        project.plugins.apply AppPlugin
        project.plugins.apply PLUGIN_ID
        setupRepositories(project)
        project.dependencies {
            compile 'com.android.support:appcompat-v7:21.0.2'
            compile 'com.nineoldandroids:library:2.4.0'
            compile 'com.melnykov:floatingactionbutton:1.0.7'
            compile 'com.github.ksoichiro:android-observablescrollview:1.5.0'
        }
        project.extensions.eclipseAar.cleanLibsDirectoryEnabled = true

        when:
        project.tasks.cleanEclipseDependencies.execute()

        then:
        !project.file('aarDependencies').exists()
        !project.file('libs').exists()
    }
}
