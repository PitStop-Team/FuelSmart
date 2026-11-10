plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // @NonNull 등 어노테이션만 쓴다. 안드로이드 런타임에는 의존하지 않는다.
    api(libs.annotation)
    api(libs.proj4j)

    testImplementation(libs.junit)
}
