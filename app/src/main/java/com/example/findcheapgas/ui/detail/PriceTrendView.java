package com.example.findcheapgas.ui.detail;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.ui.AppFonts;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.TrendChartGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 최근 7일 전국 평균가 꺾은선 그래프 (라이브러리 없이 Canvas로 그림).
 * - 아래쪽에 날짜(11/1), 점마다 동그라미, 가장 높은 값·가장 낮은 값·마지막 날 값을 숫자로 적는다.
 * - 좌표 계산은 TrendChartGeometry가 한다. 앞으로의 값(추세선 연장)은 그리지 않는다.
 * - 좌표와 숫자 위치는 데이터나 크기가 바뀔 때만 계산하고, onDraw에서는 그리기만 한다.
 * - 색은 colors.xml의 trend_* (다크 모드는 values-night)에서 읽는다.
 * 접근성 설명(contentDescription)은 화면이 setPoints와 함께 넣는다.
 */
public class PriceTrendView extends View {

    /** 그래프 위에 적을 숫자 하나 */
    private static final class ValueLabel {
        final String text;
        final float x;
        final float baseline;

        ValueLabel(String text, float x, float baseline) {
            this.text = text;
            this.x = x;
            this.baseline = baseline;
        }
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointHolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();

    private final float pointRadius;
    private final float lastPointRadius;
    private final float labelGap;
    private final float sidePadding;

    private List<DailyAveragePrice> points = Collections.emptyList();
    // 데이터나 크기가 바뀔 때 다시 계산한다 (layout). 그릴 수 없으면 geometry가 null
    @Nullable
    private TrendChartGeometry geometry;
    private final List<String> dateTexts = new ArrayList<>();
    private final List<ValueLabel> valueLabels = new ArrayList<>();
    private float baselineY;
    private float dateBaseline;

    public PriceTrendView(Context context) {
        this(context, null);
    }

    public PriceTrendView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        linePaint.setColor(ContextCompat.getColor(context, R.color.trend_line));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(res.getDimension(R.dimen.trend_line_width));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        pointPaint.setColor(ContextCompat.getColor(context, R.color.trend_point));
        pointHolePaint.setColor(ContextCompat.getColor(context, R.color.trend_point_hole));

        baselinePaint.setColor(ContextCompat.getColor(context, R.color.trend_baseline));
        baselinePaint.setStrokeWidth(res.getDimension(R.dimen.trend_baseline_width));

        valuePaint.setColor(ContextCompat.getColor(context, R.color.trend_value_text));
        valuePaint.setTextSize(res.getDimension(R.dimen.trend_value_text));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(AppFonts.of(context, Typeface.BOLD));

        datePaint.setColor(ContextCompat.getColor(context, R.color.trend_axis_text));
        datePaint.setTextSize(res.getDimension(R.dimen.trend_axis_text));
        datePaint.setTypeface(AppFonts.of(context, Typeface.NORMAL));
        datePaint.setTextAlign(Paint.Align.CENTER);

        pointRadius = res.getDimension(R.dimen.trend_point_radius);
        lastPointRadius = res.getDimension(R.dimen.trend_last_point_radius);
        labelGap = res.getDimension(R.dimen.trend_label_gap);
        // 양 끝 점의 숫자("1,668원")가 잘리지 않을 만큼 좌우를 비운다.
        sidePadding = valuePaint.measureText("0,000원") / 2 + res.getDimension(R.dimen.trend_side_padding);
    }

    /** 날짜순 평균가. 2개 미만이면 화면이 이 View 대신 안내 문구를 보여준다. */
    public void setPoints(List<DailyAveragePrice> newPoints) {
        points = new ArrayList<>(newPoints);
        layoutChart();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        layoutChart();
    }

    /** 점 좌표, 선, 날짜와 숫자 위치를 계산해 둔다. */
    private void layoutChart() {
        geometry = null;
        dateTexts.clear();
        valueLabels.clear();
        linePath.reset();
        if (points.isEmpty() || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float valueHeight = valuePaint.getFontSpacing();
        float dateHeight = datePaint.getFontSpacing();
        float left = getPaddingLeft() + sidePadding;
        float right = getWidth() - getPaddingRight() - sidePadding;
        // 위: 최고값 숫자, 아래: 최저값 숫자와 날짜
        float top = getPaddingTop() + valueHeight + labelGap;
        float dateTop = getHeight() - getPaddingBottom() - dateHeight;
        float bottom = dateTop - valueHeight - labelGap * 2;
        if (right <= left || bottom <= top) {
            return;
        }

        double[] values = new double[points.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = points.get(i).getPrice();
            dateTexts.add(FormatUtil.formatShortDate(points.get(i).getDate()));
        }
        TrendChartGeometry computed = TrendChartGeometry.compute(values, left, top, right, bottom);
        for (int i = 0; i < computed.size(); i++) {
            if (i == 0) {
                linePath.moveTo(computed.x(i), computed.y(i));
            } else {
                linePath.lineTo(computed.x(i), computed.y(i));
            }
        }
        baselineY = dateTop - labelGap / 2;
        dateBaseline = dateTop - datePaint.ascent();
        placeValueLabels(computed);
        geometry = computed;
    }

    /**
     * 가장 높은 값, 가장 낮은 값, 마지막 날 값의 위치. 마지막 날을 먼저 놓고,
     * 이미 놓은 숫자와 겹치면 점의 반대쪽(위 ↔ 아래)에 놓는다.
     */
    private void placeValueLabels(TrendChartGeometry chart) {
        List<RectF> placed = new ArrayList<>();
        Set<Integer> done = new HashSet<>();
        int[] order = {chart.getLastIndex(), chart.getMaxIndex(), chart.getMinIndex()};
        for (int index : order) {
            // 같은 점(예: 마지막 날이 최고값)은 한 번만 적는다.
            if (!chart.isLabeled(index) || !done.add(index)) {
                continue;
            }
            String text = FormatUtil.formatWon(points.get(index).getPrice());
            boolean below = chart.isLabelBelow(index);
            RectF rect = labelRect(text, chart, index, below);
            if (overlaps(rect, placed)) {
                rect = labelRect(text, chart, index, !below);
            }
            placed.add(rect);
            valueLabels.add(new ValueLabel(text, rect.centerX(), rect.top - valuePaint.ascent()));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TrendChartGeometry chart = geometry;
        if (chart == null) {
            return;
        }
        // 날짜 줄 위의 기준선
        canvas.drawLine(getPaddingLeft(), baselineY, getWidth() - getPaddingRight(), baselineY, baselinePaint);
        canvas.drawPath(linePath, linePaint);
        for (int i = 0; i < chart.size(); i++) {
            boolean last = i == chart.getLastIndex();
            float radius = last ? lastPointRadius : pointRadius;
            canvas.drawCircle(chart.x(i), chart.y(i), radius, pointPaint);
            if (last) {
                canvas.drawCircle(chart.x(i), chart.y(i), radius / 2, pointHolePaint);
            }
            canvas.drawText(dateTexts.get(i), chart.x(i), dateBaseline, datePaint);
        }
        for (ValueLabel label : valueLabels) {
            canvas.drawText(label.text, label.x, label.baseline, valuePaint);
        }
    }

    private RectF labelRect(String text, TrendChartGeometry chart, int index, boolean below) {
        float width = valuePaint.measureText(text);
        float height = valuePaint.getFontSpacing();
        // 좌우 끝에서 잘리지 않게 View 안으로 당긴다.
        float minCenter = getPaddingLeft() + width / 2;
        float maxCenter = getWidth() - getPaddingRight() - width / 2;
        float centerX = Math.max(minCenter, Math.min(maxCenter, chart.x(index)));
        float pointY = chart.y(index);
        float radius = index == chart.getLastIndex() ? lastPointRadius : pointRadius;
        float top = below ? pointY + radius + labelGap / 2 : pointY - radius - labelGap / 2 - height;
        return new RectF(centerX - width / 2, top, centerX + width / 2, top + height);
    }

    private static boolean overlaps(RectF rect, List<RectF> placed) {
        for (RectF other : placed) {
            if (RectF.intersects(rect, other)) {
                return true;
            }
        }
        return false;
    }
}
