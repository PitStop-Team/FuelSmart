package com.example.findcheapgas.util;

/**
 * 7일 추이 그래프의 좌표 계산 (Android에 의존하지 않음). 그리는 것은 PriceTrendView가 한다.
 * - x: 날짜 순서대로 그림 영역 왼쪽부터 오른쪽까지 같은 간격. 점이 하나면 가운데
 * - y: 그 기간 최솟값~최댓값에 위아래 여유를 둔다. 여유는 범위의 25%이고, 범위가 작아도 위아래 최소 5원씩 둔다
 *   (값이 거의 같을 때 작은 차이가 크게 부풀려 보이지 않게).
 * - 숫자를 적을 점: 가장 높은 값, 가장 낮은 값, 마지막 날. 같은 점이면 한 번만 적고,
 *   모든 값이 같으면 마지막 날만 적는다.
 */
public final class TrendChartGeometry {

    /** 위아래 여유의 최소값 (원) */
    static final double MIN_PADDING_WON = 5;
    /** 범위에 대한 위아래 여유 비율 */
    static final double PADDING_RATIO = 0.25;

    private final float[] xs;
    private final float[] ys;
    private final double axisMin;
    private final double axisMax;
    private final int maxIndex;
    private final int minIndex;
    private final int lastIndex;

    private TrendChartGeometry(float[] xs, float[] ys, double axisMin, double axisMax,
                               int maxIndex, int minIndex, int lastIndex) {
        this.xs = xs;
        this.ys = ys;
        this.axisMin = axisMin;
        this.axisMax = axisMax;
        this.maxIndex = maxIndex;
        this.minIndex = minIndex;
        this.lastIndex = lastIndex;
    }

    /**
     * @param values 날짜 순서의 값 (원). 비어 있으면 안 된다.
     * @param left   그림 영역 왼쪽 (px). right, top, bottom도 같다. y는 아래로 갈수록 커진다.
     */
    public static TrendChartGeometry compute(double[] values, float left, float top, float right, float bottom) {
        if (values.length == 0) {
            throw new IllegalArgumentException("값이 없습니다.");
        }
        int maxIndex = 0;
        int minIndex = 0;
        for (int i = 1; i < values.length; i++) {
            // 같은 값이 여러 번이면 나중 날짜에 적는다 (최근 값이 더 의미 있음).
            if (values[i] >= values[maxIndex]) {
                maxIndex = i;
            }
            if (values[i] <= values[minIndex]) {
                minIndex = i;
            }
        }
        double min = values[minIndex];
        double max = values[maxIndex];
        double padding = Math.max((max - min) * PADDING_RATIO, MIN_PADDING_WON);
        double axisMin = min - padding;
        double axisMax = max + padding;

        float[] xs = new float[values.length];
        float[] ys = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            xs[i] = values.length == 1 ? (left + right) / 2f
                    : left + (right - left) * i / (values.length - 1);
            ys[i] = (float) (top + (axisMax - values[i]) / (axisMax - axisMin) * (bottom - top));
        }
        return new TrendChartGeometry(xs, ys, axisMin, axisMax, maxIndex, minIndex, values.length - 1);
    }

    public int size() {
        return xs.length;
    }

    public float x(int index) {
        return xs[index];
    }

    public float y(int index) {
        return ys[index];
    }

    /** y축 아래 끝 값 (원) */
    public double getAxisMin() {
        return axisMin;
    }

    /** y축 위 끝 값 (원) */
    public double getAxisMax() {
        return axisMax;
    }

    public int getMaxIndex() {
        return maxIndex;
    }

    public int getMinIndex() {
        return minIndex;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    /** 숫자를 적을지. 가장 높은 값, 가장 낮은 값, 마지막 날 (모든 값이 같으면 마지막 날만) */
    public boolean isLabeled(int index) {
        if (index == lastIndex) {
            return true;
        }
        if (ys[maxIndex] == ys[minIndex]) {
            return false; // 모두 같은 값
        }
        return index == maxIndex || index == minIndex;
    }

    /** 숫자를 점 아래에 적을지. 가장 낮은 값은 아래, 나머지는 위 */
    public boolean isLabelBelow(int index) {
        return index == minIndex && index != maxIndex;
    }
}
