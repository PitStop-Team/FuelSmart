package com.example.findcheapgas;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.ui.favorite.FavoriteFragment;
import com.example.findcheapgas.ui.map.MapFragment;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.ui.settings.SettingsFragment;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_MAP = "tab_map";
    private static final String TAG_FAVORITE = "tab_favorite";
    private static final String TAG_SETTINGS = "tab_settings";
    private static final String[] TABS = {TAG_MAP, TAG_FAVORITE, TAG_SETTINGS};

    private static final String KEY_CURRENT_TAB = "current_tab";
    // 홈 화면 위젯을 눌러서 열었을 때 그 위젯이 고른 위치 종류 (HOME / WORK). 좌표는 넣지 않고 DB에서 읽는다.
    private static final String EXTRA_WIDGET_PLACE_KIND = "widget_place_kind";

    private String currentTab = TAG_MAP;
    private BottomNavigationView bottomNav;

    /**
     * 홈 화면 위젯을 눌렀을 때 여는 Intent. 앱이 이미 열려 있으면 위에 쌓인 화면을 닫고 onNewIntent로 받는다.
     * 지도 탭에서 그 위치를 조회 위치("집 기준")로 주변을 조회하고 이득 1위 주유소를 선택한다.
     */
    public static Intent createWidgetIntent(Context context, SavedPlace.Kind kind) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_WIDGET_PLACE_KIND, kind.name())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // super.onCreate보다 먼저 호출해야 로딩 화면이 보인다.
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // 처음 실행이면 로딩 화면 다음에 내 차 정보 입력 화면을 먼저 보여준다.
        // 탭(지도 탭의 위치 권한 요청 포함)은 만들지 않고 넘어가고, 입력 화면을 마치면 이 화면이 새로 열린다.
        if (!CarInfoRepository.getInstance(this).isOnboardingCompleted()) {
            startActivity(OnboardingActivity.createFirstRunIntent(this));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // 아래쪽 시스템 바 여백은 BottomNavigationView가 직접 처리한다.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // 탭 Fragment 3개를 처음에 모두 붙여 두고 show/hide로 바꾼다.
        // 그래서 탭을 바꿔도 지도 화면이 새로 만들어지지 않는다.
        if (savedInstanceState == null) {
            Fragment favorite = new FavoriteFragment();
            Fragment settings = new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container, new MapFragment(), TAG_MAP)
                    .add(R.id.fragment_container, favorite, TAG_FAVORITE)
                    .add(R.id.fragment_container, settings, TAG_SETTINGS)
                    .hide(favorite)
                    .hide(settings)
                    .commitNow();
        } else {
            // 화면 회전 등으로 다시 만들어지면 FragmentManager가 Fragment와 숨김 상태를 복원한다.
            currentTab = savedInstanceState.getString(KEY_CURRENT_TAB, TAG_MAP);
        }

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_favorite) {
                showTab(TAG_FAVORITE);
            } else if (id == R.id.nav_settings) {
                showTab(TAG_SETTINGS);
            } else {
                showTab(TAG_MAP);
            }
            return true;
        });

        // 화면 회전으로 다시 만들어질 때는 이미 처리했으므로 다시 조회하지 않는다.
        if (savedInstanceState == null) {
            handleWidgetIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    /**
     * 위젯을 눌러서 열었으면 집·회사 기준 비교 카드를 누를 때와 같은 흐름으로 지도 탭에서 그 위치의 이득 1위를 선택한다.
     * 위치는 DB에서 읽은 뒤에 넘기고, 그 사이 위치를 지웠으면 지도 탭만 보여준다.
     */
    private void handleWidgetIntent(@Nullable Intent intent) {
        String kindName = intent != null ? intent.getStringExtra(EXTRA_WIDGET_PLACE_KIND) : null;
        if (kindName == null || bottomNav == null) {
            return;
        }
        intent.removeExtra(EXTRA_WIDGET_PLACE_KIND);
        SavedPlace.Kind kind;
        try {
            kind = SavedPlace.Kind.valueOf(kindName);
        } catch (IllegalArgumentException unknown) {
            return;
        }
        showMapTab();
        MapViewModel mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);
        LiveData<SavedPlaces> places = SavedPlaceRepository.getInstance(this).getPlaces();
        places.observe(this, new Observer<SavedPlaces>() {
            @Override
            public void onChanged(SavedPlaces loaded) {
                if (loaded == null) {
                    return;
                }
                places.removeObserver(this);
                SavedPlace place = loaded.get(kind);
                if (place != null) {
                    mapViewModel.showSavedPlaceOnMap(getString(kind == SavedPlace.Kind.HOME
                                    ? R.string.place_kind_home : R.string.place_kind_work),
                            place.getLatitude(), place.getLongitude());
                }
            }
        });
    }

    /** 지도 탭으로 바꾼다 (즐겨찾기 탭의 "지도에서 보기"). 하단 탭 선택도 함께 바뀐다. */
    public void showMapTab() {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_map);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_TAB, currentTab);
    }

    private void showTab(String tag) {
        if (tag.equals(currentTab)) {
            return;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction().setReorderingAllowed(true);
        for (String tab : TABS) {
            Fragment fragment = fragmentManager.findFragmentByTag(tab);
            if (fragment == null) {
                continue;
            }
            if (tab.equals(tag)) {
                transaction.show(fragment);
            } else {
                transaction.hide(fragment);
            }
        }
        transaction.commit();
        currentTab = tag;
    }
}
