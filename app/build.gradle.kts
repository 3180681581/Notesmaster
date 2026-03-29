plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.micode.notes"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.micode.notes"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES");
        resources.excludes.add("META-INF/NOTICE");
        resources.excludes.add("META-INF/LICENSE");
        resources.excludes.add("META-INF/LICENSE.txt");
        resources.excludes.add("META-INF/NOTICE.txt");
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
//    implementation(fileTree(mapOf(
//        "dir" to "D:\\git\\Notesmaster\\httpcomponents-client-4.5.14-bin",
//        "include" to listOf("*.aar", "*.jar"),
//        "exclude" to listOf("")
//    )))
//    implementation(files("D:\\git\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpclient-osgi-4.5.14.jar"))
//    implementation(files("D:\\git\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpclient-win-4.5.14.jar"))
//    implementation(files("D:\\git\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpcore-4.4.16.jar"))
    implementation(files("../httpcomponents-client-4.5.14-bin/lib/httpclient-osgi-4.5.14.jar"))
    implementation(files("../httpcomponents-client-4.5.14-bin/lib/httpclient-win-4.5.14.jar"))
    implementation(files("../httpcomponents-client-4.5.14-bin/lib/httpcore-4.4.16.jar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}