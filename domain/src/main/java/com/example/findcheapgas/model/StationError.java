package com.example.findcheapgas.model;

/** 주유소 조회 실패 원인 */
public enum StationError {
    NETWORK,      // 인터넷 연결 없음, 시간 초과 등
    DAILY_LIMIT,  // 오늘 실제 API 호출이 앱에서 정한 하루 한도(300회)에 도달해서 호출하지 않음
    NOT_FOUND,    // 상세정보에 그 주유소가 없음 (폐업 등)
    API_KEY,      // local.properties에 오피넷 API 키가 없어서 호출하지 않음 (다시 시도해도 같음)
    OPINET        // 오피넷이 오류나 알 수 없는 응답을 보냄 (오피넷 쪽 한도 초과 응답 포함)
}
