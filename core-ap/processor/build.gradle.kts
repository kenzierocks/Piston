applyCoreApConfig()

dependencies {
    "implementation"(project(":core"))
    "implementation"(project(":core-ap:annotations"))
    "implementation"(project(":core-ap:runtime"))
    "implementation"(Libs.guava)
    "implementation"(Libs.javapoet)
    "implementation"(Libs.autoCommon)
    "compileOnly"(Libs.autoValueAnnotations)
    "annotationProcessor"(Libs.autoValueProcessor)
    "compileOnly"(Libs.autoService)
    "annotationProcessor"(Libs.autoService)

    "testImplementation"(Libs.compileTesting)
    "testImplementation"(Libs.mockito)
    "testImplementation"(Libs.logbackCore)
    "testImplementation"(Libs.logbackClassic)
    "testImplementation"(project(":default-impl"))
    "testAnnotationProcessor"(project(":core-ap:processor"))
}

configurations.getByName("testAnnotationProcessor")
        .extendsFrom(configurations.getByName("runtimeClasspath"))
