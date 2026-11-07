plugins {
    id("com.android.application")
}

android {
    namespace = "dev.hengen.uiskipper"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.hengen.uiskipper"
        minSdk = 26
        targetSdk = 34
        versionCode = 108
        versionName = "1.0.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "stubs")
            // Entry implements the legacy IXposedHookLoadPackage API. Do not
            // declare it in META-INF/xposed: API 102 hides the legacy classes.
        }
    }

    // Crucial: Strip compile-time Xposed stubs from classes before dexing,
    // ensuring the runtime Xposed framework (Vector/LSPosed) provides the real implementation.
    applicationVariants.all {
        javaCompileProvider.configure {
            doLast {
                delete(fileTree(destinationDirectory).matching {
                    include("de/robv/**")
                })
            }
        }
    }
}

dependencies {
    // Compile-time stubs are provided in xposed-module/stubs and stripped prior to dexing
}
