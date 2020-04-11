plugins {
    kotlin("jvm").version("1.3.61")
    java
    application
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
    }
}

repositories {
  mavenCentral()
  flatDir {
      dirs("lib")
  }
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(kotlin("reflect"))

  compile(files("lib/gvgai-master.jar"))
  compile("com.google.code.gson:gson:2.8.6")
  compile("org.tensorflow:tensorflow:1.15.0")
  compile("jdom:jdom:1.0")
  compile("com.google.protobuf:protobuf-java:3.11.0")
}

application {
    mainClassName="spinbattle.actuator.SourceTargetActuatorTest"
}

tasks.register<JavaExec>("serve") {
    group = "Runner"
    description = "Runs spinbattle server"
    main = "spinbattle.network.SpinBattleServer"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("serveproto") {
    group = "Runner"
    description = "Runs spinbattle server"
    main = "spinbattle.network.SpinBattleServerProto"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("proto") {
    group = "Runner"
    description = "Runs spinbattle test"
    main = "spinbattle.core.TestGameStateProto"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("evalann") {
    group = "Runner"
    description = "Evaluates ann player"
    main = "spinbattle.players.EvaluateFlatANNPlayer"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("evalrheaann") {
    group = "Runner"
    description = "Evaluates RHEA integrated with ANN Policy"
    main = "spinbattle.players.EvaluatePolicyRHEAANN"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.withType<Jar>() {
    configurations["compileClasspath"].forEach {file: File ->
        from(zipTree(file.absoluteFile))
    }
}
