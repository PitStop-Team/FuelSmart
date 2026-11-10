package com.example.findcheapgas.ui.map;

/**
 * 아래 시트를 올린 동안 시트 밖 지도에서 시작한 손짓을 어떻게 처리할지 정한다.
 * - 시트를 내린 상태(또는 키보드 때문에 숨긴 상태)에서는 지금처럼 지도가 그대로 받는다.
 * - 시트를 올린 상태(또는 움직이는 중)에서 누르기만 했으면 시트만 내리고, 지도에는 누름을 취소로 보내서
 *   마커가 선택되거나 지도가 바뀌지 않게 한다.
 * - 시트를 올린 상태에서 지도를 끌기 시작하면 시트를 내리고 지도는 손가락을 따라 움직이게 둔다.
 * 한 손짓(누름부터 뗌까지)마다 처음 누른 순간의 시트 상태로 판단한다. Android에 의존하지 않아서 단위 테스트로 확인한다.
 */
public final class MapGestureGuard {

    /** 손짓을 시작한 순간의 아래 시트 상태 */
    public enum SheetPosition {
        /** 내린 상태 (이득 1위 카드만 보임) */
        COLLAPSED,
        /** 올린 상태 (목록까지 보임) */
        EXPANDED,
        /** 끌거나 자리를 찾아 움직이는 중 */
        MOVING,
        /** 키보드가 보이는 동안 숨김 */
        HIDDEN
    }

    /** 지금 들어온 움직임을 처리하는 방법 */
    public enum Result {
        /** 지도에 그대로 넘긴다. */
        PASS,
        /** 시트를 내리고 지도에도 그대로 넘긴다 (끌기). */
        COLLAPSE_AND_PASS,
        /** 시트를 내리고 지도에는 누름을 취소로 보낸다 (누르기). */
        COLLAPSE_AND_CANCEL
    }

    // 이번 손짓을 아직 누르기인지 끌기인지 정하지 않았고, 시트를 올린 상태에서 시작했으면 true
    private boolean undecided;

    /** 이 시트 상태에서 시작한 지도 손짓을 가로챌지. 올린 상태와 움직이는 중만 가로챈다. */
    public static boolean guards(SheetPosition position) {
        return position == SheetPosition.EXPANDED || position == SheetPosition.MOVING;
    }

    /** 첫 손가락을 댔을 때 */
    public void onDown(SheetPosition position) {
        undecided = guards(position);
    }

    /** 손가락이 처음 댄 곳에서 (dx, dy)만큼 움직였을 때. slop보다 멀리 가면 끌기로 본다. */
    public Result onMove(float dx, float dy, float slop) {
        if (!undecided || dx * dx + dy * dy <= slop * slop) {
            return Result.PASS;
        }
        undecided = false;
        return Result.COLLAPSE_AND_PASS;
    }

    /** 두 번째 손가락을 댔을 때 (확대·축소는 끌기와 같이 본다) */
    public Result onSecondPointer() {
        if (!undecided) {
            return Result.PASS;
        }
        undecided = false;
        return Result.COLLAPSE_AND_PASS;
    }

    /** 마지막 손가락을 뗐을 때. 끌지 않고 뗐으면 누르기다. */
    public Result onUp() {
        if (!undecided) {
            return Result.PASS;
        }
        undecided = false;
        return Result.COLLAPSE_AND_CANCEL;
    }

    /** 손짓이 취소됐을 때 */
    public void onCancel() {
        undecided = false;
    }
}
