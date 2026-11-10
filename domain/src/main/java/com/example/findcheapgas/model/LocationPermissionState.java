package com.example.findcheapgas.model;

public enum LocationPermissionState {
    UNKNOWN,            // 아직 묻지 않았거나 응답을 기다리는 중
    GRANTED,            // 정확한 위치 또는 대략적 위치 허용
    DENIED,             // 거부. 다시 요청할 수 있다.
    PERMANENTLY_DENIED  // "다시 묻지 않음". 앱 설정 화면에서만 바꿀 수 있다.
}
