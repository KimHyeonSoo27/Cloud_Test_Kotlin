plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.cloudtest_kotlin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cloudtest_kotlin"
        minSdk = 24
        targetSdk = 35
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

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    // Google Vision API 및 gRPC 관련 의존성
    implementation("io.grpc:grpc-okhttp:1.54.0")
    implementation("com.google.api:gax-grpc:2.0.0")

    implementation("com.google.auth:google-auth-library-oauth2-http:1.4.0")
    implementation("io.grpc:grpc-auth:1.42.0")
    implementation("com.google.cloud:google-cloud-vision:1.100.0")

    // 추가된 필요 의존성
    implementation("io.grpc:grpc-core:1.42.0")
    implementation("io.grpc:grpc-stub:1.42.0")
    implementation("com.google.protobuf:protobuf-java:3.17.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}