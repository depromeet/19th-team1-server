plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("ci") {
            id = "team1.ci"
            implementationClass = "com.depromeet.team1.ci.CiPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
