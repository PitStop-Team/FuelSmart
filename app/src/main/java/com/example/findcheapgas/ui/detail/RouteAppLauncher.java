package com.example.findcheapgas.ui.detail;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.findcheapgas.R;
import com.example.findcheapgas.util.RouteLinks;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 상세 화면과 지도 탭 이득 1위 카드의 길찾기 버튼. 카카오맵 / 티맵 중 고르는 선택창을 보여주고, 고른 앱을 연다.
 * - 설치 여부는 선택창을 열 때마다 확인한다 (AndroidManifest.xml의 queries에 두 패키지를 선언해 둠).
 * - 설치됨: 길찾기 주소(URL 스킴)로 바로 연다. 열 수 없으면(ActivityNotFoundException) 플레이스토어로 보낸다.
 * - 설치 필요: 플레이스토어 앱(market://)으로 보내고, 그것도 없으면 웹 설치 페이지를 연다.
 * 주소와 순서는 RouteLinks가 만든다. 이 앱은 외부 앱을 여는 것까지만 하고 경로를 그리지 않는다.
 */
public final class RouteAppLauncher {

    private static final String TAG = "RouteAppLauncher";

    private RouteAppLauncher() {
    }

    /**
     * @param name      주유소 이름 (티맵 목적지 이름)
     * @param latitude  WGS84 위도
     * @param longitude WGS84 경도
     */
    public static void showChooser(Activity activity, String name, double latitude, double longitude) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_route_app, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.route_app_title)
                .setView(content)
                .setNegativeButton(R.string.route_app_cancel, null)
                .create();
        bindRow(activity, dialog, content, R.id.route_app_kakao, R.id.route_app_kakao_status,
                R.string.route_app_kakao_map, RouteLinks.App.KAKAO_MAP, name, latitude, longitude);
        bindRow(activity, dialog, content, R.id.route_app_tmap, R.id.route_app_tmap_status,
                R.string.route_app_tmap, RouteLinks.App.TMAP, name, latitude, longitude);
        dialog.show();
    }

    private static void bindRow(Activity activity, AlertDialog dialog, View content, int rowId, int statusId,
                                int labelRes, RouteLinks.App app, String name, double latitude, double longitude) {
        boolean installed = isInstalled(activity, app);
        TextView status = content.findViewById(statusId);
        status.setText(installed ? R.string.route_app_installed : R.string.route_app_not_installed);
        status.setTextColor(MaterialColors.getColor(status, installed
                ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant));
        View row = content.findViewById(rowId);
        row.setContentDescription(activity.getString(R.string.route_app_row_description,
                activity.getString(labelRes), status.getText()));
        row.setOnClickListener(v -> {
            dialog.dismiss();
            // 선택창을 띄운 뒤 설치하거나 지웠을 수 있으므로 누를 때 다시 확인한다.
            launch(activity, app, isInstalled(activity, app), name, latitude, longitude);
        });
    }

    /** 실행할 수 있는 앱이 기기에 있는지. queries 선언이 있어야 Android 11 이상에서 보인다. */
    static boolean isInstalled(Context context, RouteLinks.App app) {
        PackageManager pm = context.getPackageManager();
        return pm.getLaunchIntentForPackage(app.getPackageName()) != null;
    }

    /** RouteLinks가 정한 순서대로 열어 보고, 처음 열리는 주소에서 멈춘다. */
    private static void launch(Activity activity, RouteLinks.App app, boolean installed, String name,
                               double latitude, double longitude) {
        for (String uri : RouteLinks.launchOrder(app, installed, name, latitude, longitude)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            try {
                activity.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                // 주소에는 주유소 이름과 좌표만 있고 키는 없다. 스킴만 남긴다.
                Log.w(TAG, "열 수 없음: " + intent.getScheme() + " (" + app + "), 다음 방법으로 엽니다.");
            }
        }
        Toast.makeText(activity, R.string.route_app_open_failed, Toast.LENGTH_LONG).show();
    }
}
