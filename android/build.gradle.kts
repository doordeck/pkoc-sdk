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
        }
    }
}
