plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// Force minimum versions for vulnerable transitive build dependencies.
buildscript {
    configurations.all {
        resolutionStrategy {
            force(libs.jdom)
            force(libs.jose4j)
            force(libs.commons.lang3)
            force(libs.httpclient)
            force(libs.bcprov)
            force(libs.bcpkix)

            eachDependency {
                if (requested.group == "io.netty") {
                    useVersion(libs.versions.netty.get())
                    because("Various security fixes")
                }
            }
        }
    }
}

allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion(rootProject.libs.versions.netty.get())
                because("Various security fixes")
            }
        }
    }
}
