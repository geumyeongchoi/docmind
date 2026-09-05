plugins {
    // JDK 21 toolchain 자동 프로비저닝 (로컬에 21이 없을 때 다운로드)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "docmind"
