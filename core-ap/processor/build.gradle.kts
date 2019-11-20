import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.31"
    kotlin("kapt") version "1.3.31"
}

applyCoreApConfig()

kapt.includeCompileClasspath = false

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":core-ap:annotations"))
    "implementation"(project(":core-ap:runtime"))
    "implementation"(Libs.guava)
    "implementation"(Libs.javapoet)
    "implementation"(Libs.autoCommon)
    "compileOnly"(Libs.autoValueAnnotations)
    "kapt"(Libs.autoValueProcessor)
    "compileOnly"(Libs.autoService)
    "kapt"(Libs.autoService)

    "testImplementation"(kotlin("stdlib-jdk8"))
    "testRuntime"(Libs.junitVintageEngine)
    "testImplementation"(Libs.compileTesting) {
        exclude("junit", "junit")
    }
    // Hack - we need the tools jar
    "testRuntime"(files(Jvm.current().toolsJar ?: throw IllegalStateException("No tools.jar is present. Please ensure you are using JDK 8.")))
    "testImplementation"(Libs.mockito)
    "testImplementation"(Libs.logbackCore)
    "testImplementation"(Libs.logbackClassic)
    "testImplementation"(project(":default-impl"))
    "testCompileOnly"(Libs.autoService)
    "kaptTest"(Libs.autoService)
    "kaptTest"(project(":core-ap:processor"))
}
