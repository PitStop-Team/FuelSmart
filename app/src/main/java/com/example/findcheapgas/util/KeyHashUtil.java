package com.example.findcheapgas.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.security.MessageDigest;

/**
 * 앱 서명 키 해시(SHA-1, Base64)를 구한다. 카카오 디벨로퍼스의 Android 플랫폼 등록에 필요하다.
 */
public final class KeyHashUtil {

    private static final String TAG = "KeyHash";

    private KeyHashUtil() {
    }

    public static void logKeyHash(Context context) {
        try {
            for (Signature signature : getSignatures(context)) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d(TAG, Base64.encodeToString(md.digest(), Base64.NO_WRAP));
            }
        } catch (Exception e) {
            Log.e(TAG, "키 해시를 구하지 못했습니다.", e);
        }
    }

    @SuppressWarnings("deprecation")
    private static Signature[] getSignatures(Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) {
                return new Signature[0];
            }
            return info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        }
        PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        return info.signatures;
    }
}
