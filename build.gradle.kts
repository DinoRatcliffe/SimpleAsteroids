plugins {
    kotlin("jvm").version("1.3.61")
    java
    application
}

sourceSets["main"].java.srcDir("src")

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
  compile("org.openjfx:javafx-base:13")
  compile("com.google.code.gson:gson:2.2.4")
  compile("jdom:jdom:1.0")
}

application {
    mainClassName="spinbattle.actuator.SourceTargetActuatorTest"
}
