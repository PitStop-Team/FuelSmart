# Pleos Connect 이전 분석 (가는길 주유 / RouteFuel)

FindCheapGas(폰 앱)를 Pleos Connect 차량용 앱으로 개조하기 위한 1단계 분석 문서. 이 단계에서는 코드를 수정하지 않았다.

## 1. 현재 테스트 상태 (기준선)

`./gradlew testDebugUnitTest` 결과: **310개 실행, 실패 0, 오류 0, 건너뜀 1.**

## 2. 클래스 분류 (`app/src/main/java/com/example/findcheapgas`)

`androidx.annotation`(@NonNull 등)만 가져오는 클래스는 "순수"로 본다. 어노테이션은 :domain에서 제거하거나 JSR-305/자체 어노테이션으로 바꾸면 된다.

### 2-1. 순수 Java (Android 의존 없음, :domain 후보)

| 패키지 | 클래스 |
|---|---|
| model | ApiUsage, AroundStations, AverageComparison, AveragePrice, AveragePrices, Brand, BrandFilter, BrandSetting, BrandSettings, CarInfo, DailyAveragePrice, DiscountPolicy, DiscountRule, FavoriteItem, FavoritePrice, FavoriteStation, FuelPrice, FuelType, GasStation, LocationPermissionState, Place, PlaceCompareCard, SavedPlace, SavedPlaces, SavingLevel, SearchError, SearchOrigin, SearchRadius, SortOrder, StationDetail, StationError, StationQuery, StationSaving, StationSearchResult, UserLocation |
| util | AverageCachePolicy, AverageComparisonBuilder, BrandSettingValidator, CarInfoValidator, FavoriteListBuilder, FavoritePriceResolver, FormatUtil, PlaceCompareBuilder, PlaceCompareCachePolicy, PriceTrend, SavingCalculator, SidoMatcher, StationDistances, StationListBuilder, StationSearchResults, TrendChartGeometry |
| util (외부 라이브러리 proj4j만) | CoordinateConverter (순수 Java, 단 proj4j 의존 필요) |
| repository 오류/검색 로직 | SearchErrors, StationErrors, StationNameSearch |
| 예외 | DailyLimitReachedException, OpinetException, OpinetKeyMissingException, StationNotFoundException, KakaoException |
| viewmodel 상태 | SearchOriginState, SectionState |
| 기타 | ui/map/MapGestureGuard, ui/SavingColors |
| widget 순수 로직 | WidgetContent, WidgetContentBuilder, WidgetRefreshMode, WidgetStationLoader (위젯 제거 대상이므로 이전하지 않음) |

`model/ThemeMode`는 android.app/appcompat을 가져오므로 순수가 아니다 (2-2).

### 2-2. Android 의존 (Room, LiveData, Context, UI)

- **data/local**: AppDatabase, 각종 Dao/Entity (Room), ApiCallCounter, AveragePriceCache, BrandPreferences, CarInfoPreferences, StationFilterPreferences, ThemePreferences (SharedPreferences/Context)
- **data/repository**: AveragePriceRepository, BrandSettingsRepository, CarInfoRepository, FavoriteRepository, LocationRepository (FusedLocation), PlaceSearchRepository, RegionNameRepository, SavedPlaceRepository, StationDetailRepository, StationRepository, StationSearchRepository
- **data/remote**: OpinetApi, OpinetRemoteDataSource, kakao/KakaoLocalApi, kakao/KakaoLocalDataSource (Retrofit, android.util.Log). 응답 클래스(Opinet*Response, KakaoLocalResponse)는 Gson 의존
- **data/mock**: MockOpinetDataSource (Context로 assets 읽기)
- **model**: ThemeMode
- **viewmodel**: *ViewModel 전부 (AndroidViewModel/LiveData)
- **ui**: Activity, Fragment, Adapter, Sheet, 커스텀 View (AppFonts, OriginLabels 포함)
- **앱 기반**: FindCheapGasApp, MainActivity, util/KeyHashUtil

### 2-3. 카카오맵 / 위젯 / 길찾기 앱 호출 의존 (4단계 제거 대상)

- **카카오맵 SDK**: FindCheapGasApp (SDK 초기화), ui/map/MapFragment, ui/map/StationMarkers, util/KeyHashUtil (키 해시 확인용)
- **홈 위젯**: widget 패키지 전체 (GasWidgetProvider, WidgetConfigActivity, WidgetContent, WidgetContentBuilder, WidgetPreferences, WidgetRefreshMode, WidgetRenderer, WidgetScheduler, WidgetSettingsWatcher, WidgetStationLoader, WidgetUpdateWorker) 와 work-runtime 의존
- **카카오맵/티맵 앱 호출**: util/RouteLinks (순수 Java이지만 앱 호출 URI 생성), ui/detail/RouteAppLauncher, 이를 쓰는 ui/detail/StationDetailActivity, ui/map/MapFragment
- 참고: data/remote/kakao 쪽 Kakao Local REST(장소 검색)는 지도 SDK와 별개다. 4단계에서 장소 검색 기능 유지 여부는 별도로 판단한다.

## 3. 거리 / 할인 / 예상 이득 계산 로직 위치

| 로직 | 클래스 | 비고 |
|---|---|---|
| 좌표 변환(WGS84 ↔ KATEC), 두 점 직선거리(m) | `util/CoordinateConverter` (`toKatec`, `toWgs84`, `distanceMeters`) | proj4j 사용, 순수 Java |
| 조회 위치 기준 주유소 직선거리 재계산 | `util/StationDistances.fromOrigin` | `GasStation.withDistance` 사용 |
| 도로 거리 추정 | `util/SavingCalculator.ROAD_DISTANCE_FACTOR = 1.4`, `roadDistance()` | 직선거리 × 1.4 고정. 구간별 계수/실제 도로 거리 조회는 아직 없음 (3단계에서 추가) |
| 할인 규칙 | `model/DiscountRule` (리터당 할인, 1회 한도, `amountFor`), `model/DiscountPolicy` (주유소 → 규칙), `model/BrandSettings` (DiscountPolicy 구현, 브랜드별 설정) | |
| 할인액 / 기름값 / 이동비용 / 총비용 / 예상 이득 | `util/SavingCalculator` (`calculate`, `estimateAgainst`, `findBaseline`) | 결과는 `model/StationSaving`. 이득 1위 판정 포함 |
| 목록 구성(반경, 브랜드 필터, 정렬) | `util/StationListBuilder` | SavingCalculator 호출 |
| 즐겨찾기 비교 | `util/FavoriteListBuilder`, `util/PlaceCompareBuilder` | `SavingCalculator.estimateAgainst` 호출 |
| 차량 정보(연료량, 연비) | `model/CarInfo`, `util/CarInfoValidator`, `data/local/CarInfoPreferences`, `data/repository/CarInfoRepository` | 입력값 기반. 차량 API 연동 지점이 될 곳 |

`SavingCalculator` 계산식: 할인액 = min(리터당 할인 × 주유량, 한도) / 기름값 = 가격 × 주유량 − 할인액 / 이동 거리 = 직선거리 × 1.4 / 이동 비용 = 이동 거리 ÷ 연비 × 가격 / 예상 이득 = 기준 주유소 총비용 − 해당 주유소 총비용. 기준 주유소는 가장 가까운 곳(동률이면 싼 곳).

## 4. 이후 단계 메모

- `docs/pleos/` 폴더(Pleos SDK 문서)가 없다. 5단계(NaviHelper), 6단계(Vehicle/STT/TTS)는 문서가 추가되기 전까지 구현하지 않는다.
- 빌드 환경: JDK 17 + Android SDK 34 필요. 프로젝트 경로에 한글이 있으면 테스트 워커가 클래스를 못 찾으므로 ASCII 경로(예: `subst X:`)에서 빌드해야 한다.

## 5. 진행 상태

| 단계 | 상태 | 비고 |
|---|---|---|
| 1 분석 | 완료 | 이 문서 |
| 2 :domain 분리 | 완료 | 순수 모델/계산/예외와 해당 테스트 이동. 동작 변경 없음 |
| 3 인터페이스, 거리 계산 | 완료 | VehicleDataSource, NavigationController, RouteDistanceProvider 와 사용자 입력/모의 구현. 구간별 계수 대체 경로와 테스트. 지도 목록(MapViewModel)과 즐겨찾기 목록이 이 경로를 사용. PlaceCompareBuilder는 아직 고정 계수 1.4 |
| 4 앱 껍데기 | 완료 (빌드까지) | minSdk 28, 앱 이름 "가는길 주유", applicationId com.pitstop.routefuel, 위젯/카카오맵 SDK/길찾기 앱 호출 제거, 목록 + 이득 1위 카드 화면. 에뮬레이터 실행 확인은 사람이 해야 함. 자바 패키지명은 com.example.findcheapgas 그대로 |
| 5 NaviHelper | 건너뜀 | docs/pleos/ 없음 ("문서 필요") |
| 6 주행/정차 UI, 음성 | 건너뜀 | docs/pleos/ 없음 ("문서 필요"). Vehicle 정차 판단 API, STT/TTS 모두 "문서에 없음" |

4단계에서 기능 제거와 함께 지운 테스트: 위젯 로직(WidgetContentBuilderTest, WidgetStationLoaderTest), 지도 터치 가드(MapGestureGuardTest), 길찾기 앱 URI(RouteLinksTest).

## 6. Pleos SDK 연동

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (settings.gradle.kts), 인증 없음
- 의존성: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`
- 권한: `CAR_ENERGY`(Fuel), `CAR_DRIVE`(Brake), `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`. Pleos App Market이 승인한 범위에서만 쓸 수 있다.
- 연료: `getFuelPercent`(%, 리터가 아님), `getCurrentFuelEconomy`(L/100km, 문서 기준). 읽기 전에 `checkFuelPercentCapabilities`, `checkCurrentFuelEconomyCapabilities`로 확인한다.
- 정차: Brake `getCurrentBrakePedalStatus`/`registerBrakePedalStatus`. DrivingMode(NORMAL/ECO/SPORT)에는 정차 정보가 없다. Gear 도메인은 SDK에는 있지만 API Reference에 문서가 없어 쓰지 않았다.
- 내비: `requestRoute`, `addWaypoint`, `removeWaypoint`, `getCurrentLocationInfo`, `getRouteStateInfo`, `getDestinationInfo`, `getWaypointInfo`
- 음성(TextToSpeech, SpeechToText), FusedLocation: SDK 신청 안내에 "준비 중"으로 표시되어 구현하지 않았다.
- Client ID/Secret은 TextToSpeech, SpeechToText에만 필요하다고 문서에 적혀 있어 이번 구현에서는 쓰지 않는다.

## 7. 연료 부족 자동 제안

- 기준: 연료 잔량(%) 25% 미만에서 제안, 35% 이상으로 회복하면 재무장 (`LowFuelTrigger.SUGGEST_BELOW_PERCENT`, `REARM_AT_PERCENT`). 정확히 25%는 제안하지 않는다.
- 연료통 용량을 모르므로 리터로 바꾸지 않고 %로만 판단한다.
- 한 번 제안하면 회복 전까지 다시 제안하지 않는다. "나중에"를 눌러도 같은 하락 구간에서는 다시 묻지 않는다. 앱을 다시 시작하면 상태가 초기화된다.
- 연료 값이 없거나 capability가 미지원이면 값이 비어 있으므로 제안은 일어나지 않는다. 사용자 입력 연비 경로와는 무관하다.
- 후보: 화면 목록의 예상 이득 1위 1곳 (기존 예상 총비용 계산 재사용). 경유 모드(경로 위 주유소만 비교)는 아직 없어 주변 목록에서만 고른다.
- 승인: 안내 중인 경로가 있으면 "경유지로 추가"(경유지 승인 화면을 거침), 없으면 "여기로 안내"(`requestRoute`). 이 버튼 누름이 승인이며, 승인 없이 경로를 바꾸지 않는다.
- 화면: 주행 중에는 제안 카드와 큰 버튼 두 개만, 정차 중에는 목록 위 맨 위 카드로 보인다. 다른 화면을 덮지 않는다.
- 시연: 디버그 빌드의 설정 탭 맨 아래에서 연료 %를 정할 수 있다(`PleosVehicleSession.setDebugFuelPercent`). 릴리스 빌드에서는 화면이 만들어지지 않고 호출해도 아무 일도 하지 않는다.
