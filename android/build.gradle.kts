buildscript {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion("4.1.132.Final")
                because("Various security fixes")
            }
            if (requested.group == "org.bitbucket.b_c" && requested.name == "jose4j") {
                useVersion("0.9.6")
                because("CVE fix: DoS via compressed JWE content (GHSA-3677-xxcr-wjqv)")
            }
            if (requested.group == "org.jdom" && requested.name == "jdom2") {
                useVersion("2.0.6.1")
                because("CVE fix: XML External Entity (XXE) Injection in JDOM")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion("4.1.132.Final")
                because("Various security fixes")
            }
            if (requested.group == "org.bitbucket.b_c" && requested.name == "jose4j") {
                useVersion("0.9.6")
                because("CVE fix: DoS via compressed JWE content (GHSA-3677-xxcr-wjqv)")
            }
            if (requested.group == "org.jdom" && requested.name == "jdom2") {
                useVersion("2.0.6.1")
                because("CVE fix: XML External Entity (XXE) Injection in JDOM")
            }
        }
    }
}
