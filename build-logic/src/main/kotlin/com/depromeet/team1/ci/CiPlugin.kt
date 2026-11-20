package com.depromeet.team1.ci

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * CI 에서 쓰는 태스크를 루트 프로젝트에 등록한다.
 *
 * - `ciSelectTests -Pci.changed=<바뀐 파일 목록>`: 테스트를 돌릴 모듈을 골라 `build/ci/selection.txt` 에 쓴다.
 * - `-Pci.selection=build/ci/selection.txt`: 이 값을 주면 고른 모듈은 테스트를 모두, 나머지 모듈은 구조 테스트만 실행한다.
 */
class CiPlugin : Plugin<Project> {
    private companion object {
        const val ARCHITECTURE_TEST = "ArchitectureTest"
    }

    override fun apply(root: Project) {
        require(root == root.rootProject) { "team1.ci 는 루트 프로젝트에 적용한다" }
        val outputDir = root.layout.buildDirectory.dir("ci")

        val selectTests = root.tasks.register("ciSelectTests", SelectTestsTask::class.java) {
            group = "ci"
            description = "바뀐 파일에 영향을 받는 모듈을 고른다"
            changedFiles.set(root.providers.gradleProperty("ci.changed").map { root.file(it).absolutePath })
            selectionFile.set(outputDir.map { it.file("selection.txt") })
            summaryFile.set(outputDir.map { it.file("selection.md") })
        }

        root.gradle.projectsEvaluated {
            val modules = root.subprojects.map { project ->
                val deps = project.configurations
                    .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                    .map { it.path }
                    .toSet()
                ModuleModel(
                    project.path,
                    project.projectDir.relativeTo(root.projectDir).invariantSeparatorsPath,
                    deps - project.path,
                )
            }
            selectTests.configure { this.modules.set(modules) }
        }

        root.providers.gradleProperty("ci.selection").orNull?.let { selectionPath ->
            val selected = SelectionFile.read(root.file(selectionPath)) ?: return@let
            root.subprojects {
                if (this.path in selected) return@subprojects
                // 고르지 않은 모듈은 구조 테스트(이름이 ArchitectureTest 로 끝나는 클래스)만 돌린다. 없으면 아무것도 돌지 않는다.
                tasks.withType(Test::class.java).matching { it.name == "test" }.configureEach {
                    filter {
                        includeTestsMatching("*$ARCHITECTURE_TEST")
                        includeTestsMatching("*$ARCHITECTURE_TEST\$*")
                        isFailOnNoMatchingTests = false
                    }
                }
            }
        }
    }
}

@UntrackedTask(because = "git 변경 목록을 읽는다")
abstract class SelectTestsTask : DefaultTask() {
    @get:Internal
    abstract val changedFiles: Property<String>

    @get:Internal
    abstract val modules: ListProperty<ModuleModel>

    @get:Internal
    abstract val selectionFile: RegularFileProperty

    @get:Internal
    abstract val summaryFile: RegularFileProperty

    @TaskAction
    fun select() {
        val changed = File(changedFiles.get()).readLines().filter { it.isNotBlank() }
        val selection = TestSelector(modules.get()).select(changed)
        SelectionFile.write(selection, selectionFile.get().asFile)
        val summary = SelectionFile.summary(selection, changed)
        summaryFile.get().asFile.writeText(summary)
        logger.lifecycle(summary)
    }
}

/**
 * 테스트 선택 결과 파일. 줄 단위 텍스트다.
 * ```
 * mode=selected
 * module=:infra
 * module=:api
 * ```
 */
object SelectionFile {
    fun write(selection: Selection, file: File) {
        file.parentFile.mkdirs()
        val lines = when (selection) {
            is Selection.Full -> listOf("mode=full")
            is Selection.Selected -> listOf("mode=selected") + selection.modules.keys.map { "module=$it" }
        }
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    /** 모든 테스트를 돌릴 모듈. 전체 실행이면 null */
    fun read(file: File): Set<String>? {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.firstOrNull() != "mode=selected") return null
        return lines.drop(1).map { it.removePrefix("module=") }.toSet()
    }

    fun summary(selection: Selection, changedFiles: List<String>): String = buildString {
        appendLine("## 테스트 선택")
        appendLine()
        appendLine("바뀐 파일 ${changedFiles.size}개")
        appendLine()
        when (selection) {
            is Selection.Full -> {
                appendLine("**전체 실행**")
                selection.reasons.forEach { appendLine("- $it") }
            }
            is Selection.Selected -> {
                appendLine("**선택 실행**: 모듈 ${selection.modules.size}개의 테스트 전부. 나머지 모듈은 구조 테스트만 돈다")
                if (selection.modules.isNotEmpty()) {
                    appendLine()
                    appendLine("| 모듈 | 이유 |")
                    appendLine("| --- | --- |")
                    selection.modules.forEach { (module, reason) -> appendLine("| `$module` | $reason |") }
                }
            }
        }
    }
}
