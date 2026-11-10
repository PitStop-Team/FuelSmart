package com.example.findcheapgas.vehicle;

/** 화면을 나누는 기준. UNKNOWN은 차량에서 브레이크 정보를 받지 못한 상태다. */
public enum DrivingState {
    /** 주행 중이거나 정차했다고 보기 어려운 상태: 카드 1장과 큰 승인 버튼 */
    DRIVING,
    /** 정차: 목록과 계산 내역 */
    STOPPED,
    /** 차량 데이터 없음 (폰, 에뮬레이터, 미지원, 읽기 실패) */
    UNKNOWN
}
