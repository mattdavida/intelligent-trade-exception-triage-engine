plugins {
    application
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:3.8.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

application {
    mainClass.set("com.itee.producer.ExceptionFeedProducer")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
