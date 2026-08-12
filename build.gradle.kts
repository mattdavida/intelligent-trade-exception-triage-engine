plugins {
    java
}

allprojects {
    group = "com.itee"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_22
        targetCompatibility = JavaVersion.VERSION_22
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(22)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
