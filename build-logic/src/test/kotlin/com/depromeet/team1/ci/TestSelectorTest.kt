package com.depromeet.team1.ci

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 레포의 모듈 의존을 그대로 둔 모델로 테스트 선택을 확인한다.
 *
 * core ← infra, worker ← api (api 는 infra·worker 를 runtimeOnly, infra 의 testFixtures 를 testImplementation 으로 쓴다)
 */
class TestSelectorTest {
    private val selector = TestSelector(
        listOf(
            ModuleModel(":core", "core", emptySet()),
            ModuleModel(":infra", "infra", setOf(":core")),
            ModuleModel(":worker", "worker", setOf(":core")),
            ModuleModel(":api", "api", setOf(":core", ":infra", ":worker")),
        ),
    )

    @Test
    fun `core 가 바뀌면 core 를 쓰는 모든 모듈을 고른다`() {
        val selected = selectedModules("core/src/main/kotlin/com/depromeet/team1/core/item/Item.kt")

        assertEquals(setOf(":core", ":infra", ":worker", ":api"), selected)
    }

    @Test
    fun `infra 가 바뀌면 infra 와 infra 를 쓰는 api 를 고른다`() {
        val selected = selectedModules("infra/src/main/kotlin/com/depromeet/team1/infra/storage/ItemMapper.kt")

        assertEquals(setOf(":infra", ":api"), selected)
    }

    @Test
    fun `아무도 쓰지 않는 모듈이 바뀌면 그 모듈만 고른다`() {
        val selected = selectedModules("api/src/main/kotlin/com/depromeet/team1/api/web/item/ItemController.kt")

        assertEquals(setOf(":api"), selected)
    }

    @Test
    fun `직접 선언하지 않고 다른 모듈을 거쳐 의존하는 모듈도 고른다`() {
        // c 는 a 를 직접 선언하지 않고 b 를 거쳐 의존한다
        val chain = TestSelector(
            listOf(
                ModuleModel(":a", "a", emptySet()),
                ModuleModel(":b", "b", setOf(":a")),
                ModuleModel(":c", "c", setOf(":b")),
            ),
        )

        val selection = chain.select(listOf("a/src/main/kotlin/A.kt"))

        assertIs<Selection.Selected>(selection)
        assertEquals(setOf(":a", ":b", ":c"), selection.modules.keys)
        assertEquals(":a 에 의존", selection.modules[":c"])
    }

    @Test
    fun `테스트 코드, 리소스, 마이그레이션, 모듈 빌드 파일도 그 모듈의 변경으로 본다`() {
        listOf(
            "infra/src/test/kotlin/com/depromeet/team1/infra/SomeTest.kt",
            "infra/src/testFixtures/kotlin/com/depromeet/team1/infra/MySqlContainerConfiguration.kt",
            "infra/src/main/resources/db/migration/V2__item.sql",
            "infra/build.gradle.kts",
        ).forEach { file ->
            assertEquals(setOf(":infra", ":api"), selectedModules(file), file)
        }
    }

    @Test
    fun `고른 이유에 직접 바뀐 모듈과 의존 때문에 고른 모듈을 구분한다`() {
        val selection = selector.select(listOf("worker/src/main/kotlin/com/depromeet/team1/worker/job/JobPoller.kt"))

        assertIs<Selection.Selected>(selection)
        assertEquals(
            mapOf(
                ":api" to ":worker 에 의존",
                ":worker" to "바뀜: worker/src/main/kotlin/com/depromeet/team1/worker/job/JobPoller.kt",
            ),
            selection.modules,
        )
    }

    @Test
    fun `문서와 설정 예시만 바뀌면 아무 모듈도 고르지 않는다`() {
        val selected = selectedModules(
            "docs/ci.md",
            "README.md",
            "api/README.md",
            ".claude/rules/testing.md",
            "compose.yaml",
            "Dockerfile",
            ".github/ISSUE_TEMPLATE/task.md",
        )

        assertEquals(emptySet(), selected)
    }

    @Test
    fun `빌드 전체에 영향을 주는 파일이 바뀌면 전체를 돌린다`() {
        listOf(
            "gradle/libs.versions.toml",
            "build.gradle.kts",
            "settings.gradle.kts",
            "build-logic/src/main/kotlin/com/depromeet/team1/ci/TestSelector.kt",
            ".github/workflows/ci.yml",
            "scripts/ci/select-tests.sh",
        ).forEach { file ->
            assertIs<Selection.Full>(selector.select(listOf(file)), file)
        }
    }

    @Test
    fun `어느 모듈에도 속하지 않는 파일은 판단하지 않고 전체를 돌린다`() {
        val selection = selector.select(listOf("api/src/main/kotlin/A.kt", "tools/seed.sql"))

        assertIs<Selection.Full>(selection)
        assertEquals(listOf("어느 모듈에도 속하지 않음: tools/seed.sql"), selection.reasons)
    }

    @Test
    fun `모듈 디렉터리 이름이 겹치면 가장 깊게 맞는 모듈을 고른다`() {
        val nested = TestSelector(
            listOf(
                ModuleModel(":infra", "infra", emptySet()),
                ModuleModel(":infra:storage", "infra/storage", emptySet()),
                ModuleModel(":infra-legacy", "infra-legacy", emptySet()),
            ),
        )

        val selection = nested.select(listOf("infra/storage/src/main/kotlin/A.kt", "infra-legacy/src/main/kotlin/B.kt"))

        assertIs<Selection.Selected>(selection)
        assertEquals(setOf(":infra:storage", ":infra-legacy"), selection.modules.keys)
    }

    private fun selectedModules(vararg files: String): Set<String> {
        val selection = selector.select(files.toList())
        assertIs<Selection.Selected>(selection)
        return selection.modules.keys
    }
}
