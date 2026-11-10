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
