import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// API 키는 local.properties에서 읽어 BuildConfig로 넘긴다. 값이 없으면 기본값을 쓴다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name)?.trim() ?: defaultValue

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.findcheapgas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pitstop.routefuel"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room 스키마를 app/schemas/{DB 클래스}/{버전}.json으로 내보낸다. 커밋해 두고 다음 마이그레이션 테스트의 기준으로 쓴다.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }

        buildConfigField("String", "OPINET_API_KEY", quoted(localProperty("OPINET_API_KEY")))
        buildConfigField("String", "KAKAO_REST_API_KEY", quoted(localProperty("KAKAO_REST_API_KEY")))
        // "false"라고 적은 경우에만 실제 API를 사용한다.
        val useMockData = !localProperty("USE_MOCK_DATA", "true").equals("false", ignoreCase = true)
        buildConfigField("boolean", "USE_MOCK_DATA", useMockData.toString())
    }

    // 마이그레이션 테스트(MigrationTestHelper)가 내보낸 스키마 JSON을 읽을 수 있게 androidTest assets에 넣는다.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // 도로 거리 계수 측정(RoadDistanceCheckTest)은 -ProadDistanceCheck=true를 줄 때만 실행한다.
    // 켰을 때는 외부 API 결과를 다시 받아야 하므로 이전 테스트 결과를 재사용하지 않는다.
    val roadDistanceCheck = providers.gradleProperty("roadDistanceCheck").getOrElse("false")
    testOptions {
        unitTests.all {
            it.systemProperty("roadDistanceCheck", roadDistanceCheck)
            it.systemProperty(
                "roadDistanceCheckOutput",
                rootProject.file("docs/road-distance-check.csv").absolutePath
            )
            if (roadDistanceCheck == "true") {
                it.outputs.upToDateWhen { false }
            }
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    // 요청 URL에 오피넷 API 키가 들어가므로 HTTP 로깅 인터셉터는 넣지 않는다.
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.play.services.location)
    implementation(project(":domain"))
    implementation(libs.proj4j)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.core.splashscreen)
    implementation(libs.swiperefreshlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
}