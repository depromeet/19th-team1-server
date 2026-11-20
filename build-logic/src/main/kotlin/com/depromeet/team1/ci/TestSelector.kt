package com.depromeet.team1.ci

/**
 * 모듈 하나.
 *
 * @param path Gradle 경로 (`:api`)
 * @param dir 레포 루트 기준 디렉터리 (`api`)
 * @param projectDeps 선언된 프로젝트 의존. 설정 종류(`implementation`, `runtimeOnly`, `testFixtures(project(..))` 등)를 구분하지 않는다
 */
data class ModuleModel(val path: String, val dir: String, val projectDeps: Set<String>)

sealed interface Selection {
    data class Full(val reasons: List<String>) : Selection

    /** @param modules 모든 테스트를 돌릴 모듈 → 고른 이유. 고르지 않은 모듈은 구조 테스트만 돈다 */
    data class Selected(val modules: Map<String, String>) : Selection
}

/**
 * 바뀐 파일로 테스트를 돌릴 모듈을 고른다.
 *
 * 1. 빌드 전체에 영향을 주는 파일이 바뀌면 전체를 돌린다.
 * 2. 테스트에 영향이 없는 파일(문서 등)은 건너뛴다.
 * 3. 모듈 안의 파일이 바뀌면 그 모듈과, 그 모듈에 의존하는 모듈(전이 포함)의 테스트를 모두 돌린다.
 * 4. 어느 모듈에도 속하지 않는 파일은 판단하지 않고 전체를 돌린다. 규칙이 틀려도 더 많이 돌리는 쪽으로만 틀리게 한다.
 */
class TestSelector(private val modules: List<ModuleModel>) {
    /** 모듈 → 그 모듈을 의존 그래프에서 닿을 수 있는 모듈 (자기 자신 포함) */
    private val dependents: Map<String, Set<String>> = run {
        val closures = modules.associate { it.path to closureOf(it.path) }
        modules.associate { module ->
            module.path to closures.filterValues { module.path in it }.keys.toSortedSet()
        }
    }

    fun select(changedFiles: List<String>): Selection {
        val fullReasons = mutableListOf<String>()
        val changedModules = linkedMapOf<String, String>()
        for (file in changedFiles) {
            when {
                affectsWholeBuild(file) -> fullReasons += "빌드 전체에 영향: $file"
                isIgnored(file) -> Unit
                else -> {
                    val owner = ownerOf(file)
                    if (owner == null) {
                        fullReasons += "어느 모듈에도 속하지 않음: $file"
                    } else {
                        changedModules.putIfAbsent(owner.path, file)
                    }
                }
            }
        }
        if (fullReasons.isNotEmpty()) return Selection.Full(fullReasons)

        val selected = sortedMapOf<String, String>()
        changedModules.forEach { (module, file) -> selected[module] = "바뀜: $file" }
        changedModules.keys.forEach { changed ->
            dependents.getValue(changed).forEach { selected.putIfAbsent(it, "$changed 에 의존") }
        }
        return Selection.Selected(selected)
    }

    /** 가장 깊게 맞는 디렉터리의 모듈 */
    private fun ownerOf(file: String): ModuleModel? =
        modules.filter { file.startsWith("${it.dir}/") }.maxByOrNull { it.dir.length }

    private fun closureOf(start: String): Set<String> {
        val byPath = modules.associateBy { it.path }
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (seen.add(path)) byPath[path]?.projectDeps?.let(queue::addAll)
        }
        return seen
    }

    companion object {
        private val WHOLE_BUILD_FILES = setOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
        )
        private val WHOLE_BUILD_DIRS = listOf("gradle/", "build-logic/", ".github/workflows/", "scripts/ci/")

        /** 테스트 결과에 영향이 없는 파일. 포맷 검사(`ktlintCheck`)는 선택과 관계없이 모든 모듈에 돈다 */
        private val IGNORED_FILES = setOf(
            "compose.yaml",
            ".env.example",
            "Dockerfile",
            ".dockerignore",
            ".editorconfig",
            ".gitignore",
            ".gitattributes",
            "scripts/check-convention.sh",
        )
        private val IGNORED_DIRS = listOf("docs/", ".claude/", ".github/ISSUE_TEMPLATE/", "scripts/git-hooks/")

        fun affectsWholeBuild(file: String): Boolean =
            file in WHOLE_BUILD_FILES || WHOLE_BUILD_DIRS.any { file.startsWith(it) }

        fun isIgnored(file: String): Boolean = file.endsWith(".md") ||
            file in IGNORED_FILES ||
            IGNORED_DIRS.any { file.startsWith(it) }
    }
}
