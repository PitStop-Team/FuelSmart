package com.example.findcheapgas.model;

/** 장소 검색, 주유소 이름 검색 실패 원인 */
public enum SearchError {
    KAKAO_AUTH,   // 카카오 REST API 키가 없거나 틀림 (HTTP 401, 403)
    KAKAO,        // 카카오가 그 밖의 오류를 보냄
    NETWORK,      // 인터넷 연결 없음, 시간 초과 등
    DAILY_LIMIT,  // 오늘 오피넷 실제 호출이 하루 한도(300회)에 도달해서 호출하지 않음
    OPINET_KEY,   // local.properties에 오피넷 API 키가 없어서 주유소 이름 검색을 부르지 않음
    OPINET        // 오피넷이 오류나 알 수 없는 응답을 보냄
}
