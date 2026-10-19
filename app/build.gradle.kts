plugins { id("com.android.application") }
android {
    namespace = "io.github.jahn2007.edifierx"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "io.github.jahn2007.edifierx"
        minSdk = 35
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    buildTypes {
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    buildFeatures { buildConfig = true }
    lint { abortOnError = true; warningsAsErrors = false }
    packaging { resources.merges += "META-INF/xposed/*" }
}
dependencies {
    implementation(project(":core"))
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
