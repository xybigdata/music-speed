package com.speedvolume;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * 速度仪表盘自定义视图
 * 显示圆形表盘、速度刻度、指针动画和当前速度数值
 * 支持夜间模式
 */
public class SpeedDashboardView extends View {

    // 默认尺寸
    private static final int DEFAULT_SIZE = 300;
    private static final int MAX_SPEED = 120;
    private static final int SPEED_INTERVAL = 10; // 刻度间隔

    // 速度范围
    private float currentSpeed = 0;
    private float targetSpeed = 0;
    private ValueAnimator speedAnimator;

    // 绘制工具
    private Paint backgroundPaint;
    private Paint ringPaint;
    private Paint tickPaint;
    private Paint tickTextPaint;
    private Paint needlePaint;
    private Paint centerPaint;
    private Paint speedTextPaint;
    private Paint unitTextPaint;

    // 颜色方案 - 日间模式
    private int dayBackgroundColor = 0xFFFFFFFF;
    private int dayRingColor = 0xFFE0E0E0;
    private int dayTickColor = 0xFF9E9E9E;
    private int dayTickTextColor = 0xFF616161;
    private int dayNeedleColor = 0xFF2196F3;
    private int dayCenterColor = 0xFF1976D2;
    private int daySpeedTextColor = 0xFF212121;
    private int dayUnitTextColor = 0xFF757575;
    private int dayProgressRingColor = 0xFF2196F3;

    // 颜色方案 - 夜间模式
    private int nightBackgroundColor = 0xFF1A1A1A;
    private int nightRingColor = 0xFF333333;
    private int nightTickColor = 0xFF666666;
    private int nightTickTextColor = 0xFFAAAAAA;
    private int nightNeedleColor = 0xFF64B5F6;
    private int nightCenterColor = 0xFF42A5F5;
    private int nightSpeedTextColor = 0xFFE0E0E0;
    private int nightUnitTextColor = 0xFF9E9E9E;
    private int nightProgressRingColor = 0xFF64B5F6;

    // 当前使用的颜色
    private int backgroundColor;
    private int ringColor;
    private int tickColor;
    private int tickTextColor;
    private int needleColor;
    private int centerColor;
    private int speedTextColor;
    private int unitTextColor;
    private int progressRingColor;

    // 绘制区域
    private RectF arcRect;
    private float centerX;
    private float centerY;
    private float radius;

    public SpeedDashboardView(Context context) {
        super(context);
        init();
    }

    public SpeedDashboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedDashboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化绘制工具和颜色
     */
    private void init() {
        // 根据当前主题设置颜色
        updateColorsForTheme();

        // 背景画笔
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);

        // 表盘圆环画笔
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(ringColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dpToPx(12));

        // 刻度线画笔
        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(tickColor);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dpToPx(2));

        // 刻度文字画笔
        tickTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickTextPaint.setColor(tickTextColor);
        tickTextPaint.setTextSize(spToPx(12));
        tickTextPaint.setTextAlign(Paint.Align.CENTER);

        // 指针画笔
        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setColor(needleColor);
        needlePaint.setStyle(Paint.Style.FILL);

        // 中心圆画笔
        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(centerColor);
        centerPaint.setStyle(Paint.Style.FILL);

        // 速度文字画笔
        speedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        speedTextPaint.setColor(speedTextColor);
        speedTextPaint.setTextSize(spToPx(36));
        speedTextPaint.setTextAlign(Paint.Align.CENTER);
        speedTextPaint.setFakeBoldText(true);

        // 单位文字画笔
        unitTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitTextPaint.setColor(unitTextColor);
        unitTextPaint.setTextSize(spToPx(14));
        unitTextPaint.setTextAlign(Paint.Align.CENTER);

        arcRect = new RectF();
    }

    /**
     * 根据系统主题更新颜色
     */
    private void updateColorsForTheme() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightMode = nightMode == Configuration.UI_MODE_NIGHT_YES;

        if (isNightMode) {
            backgroundColor = nightBackgroundColor;
            ringColor = nightRingColor;
            tickColor = nightTickColor;
            tickTextColor = nightTickTextColor;
            needleColor = nightNeedleColor;
            centerColor = nightCenterColor;
            speedTextColor = nightSpeedTextColor;
            unitTextColor = nightUnitTextColor;
            progressRingColor = nightProgressRingColor;
        } else {
            backgroundColor = dayBackgroundColor;
            ringColor = dayRingColor;
            tickColor = dayTickColor;
            tickTextColor = dayTickTextColor;
            needleColor = dayNeedleColor;
            centerColor = dayCenterColor;
            speedTextColor = daySpeedTextColor;
            unitTextColor = dayUnitTextColor;
            progressRingColor = dayProgressRingColor;
        }
    }

    /**
     * 设置当前速度（带动画）
     */
    public void setSpeed(float speed) {
        targetSpeed = Math.max(0, Math.min(MAX_SPEED, speed));

        if (speedAnimator != null && speedAnimator.isRunning()) {
            speedAnimator.cancel();
        }

        speedAnimator = ValueAnimator.ofFloat(currentSpeed, targetSpeed);
        speedAnimator.setDuration(300);
        speedAnimator.setInterpolator(new OvershootInterpolator(0.5f));
        speedAnimator.addUpdateListener(animation -> {
            currentSpeed = (float) animation.getAnimatedValue();
            invalidate();
        });
        speedAnimator.start();
    }

    /**
     * 设置当前速度（无动画）
     */
    public void setSpeedImmediate(float speed) {
        currentSpeed = targetSpeed = Math.max(0, Math.min(MAX_SPEED, speed));
        invalidate();
    }

    /**
     * 获取当前速度
     */
    public float getSpeed() {
        return currentSpeed;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(DEFAULT_SIZE, widthMeasureSpec);
        int height = resolveSize(DEFAULT_SIZE, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - dpToPx(20);

        arcRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制背景
        canvas.drawCircle(centerX, centerY, radius + dpToPx(15), backgroundPaint);

        // 绘制表盘外环（背景环）
        drawRing(canvas);

        // 绘制进度环
        drawProgressRing(canvas);

        // 绘制刻度和刻度文字
        drawTicks(canvas);

        // 绘制指针
        drawNeedle(canvas);

        // 绘制中心圆
        canvas.drawCircle(centerX, centerY, dpToPx(12), centerPaint);

        // 绘制速度文字
        drawSpeedText(canvas);
    }

    /**
     * 绘制表盘外环
     */
    private void drawRing(Canvas canvas) {
        ringPaint.setColor(ringColor);
        canvas.drawCircle(centerX, centerY, radius, ringPaint);
    }

    /**
     * 绘制进度环
     */
    private void drawProgressRing(Canvas canvas) {
        if (currentSpeed <= 0) return;

        float sweepAngle = (currentSpeed / MAX_SPEED) * 270; // 270度弧度

        Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressRingColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dpToPx(12));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // 从 -135° 开始（左下方），到 135°（右下方）
        canvas.drawArc(arcRect, 135, sweepAngle, false, progressPaint);
    }

    /**
     * 绘制刻度和刻度文字
     */
    private void drawTicks(Canvas canvas) {
        float tickRadius = radius - dpToPx(25);
        float textRadius = radius - dpToPx(45);

        // 270度弧度，从 -135° 到 135°
        for (int speed = 0; speed <= MAX_SPEED; speed += SPEED_INTERVAL) {
            // 计算角度：从 -135° 开始，均匀分布
            float angle = -135 + (speed / (float) MAX_SPEED) * 270;
            double radians = Math.toRadians(angle + 90); // +90 因为 0° 在 12 点钟方向

            // 主刻度线
            float startX = centerX + (float) (tickRadius * Math.cos(radians));
            float startY = centerY + (float) (tickRadius * Math.sin(radians));
            float endX = centerX + (float) ((tickRadius - dpToPx(15)) * Math.cos(radians));
            float endY = centerY + (float) ((tickRadius - dpToPx(15)) * Math.sin(radians));

            tickPaint.setStrokeWidth(dpToPx(3));
            tickPaint.setColor(tickColor);
            canvas.drawLine(startX, startY, endX, endY, tickPaint);

            // 刻度文字
            float textX = centerX + (float) (textRadius * Math.cos(radians));
            float textY = centerY + (float) (textRadius * Math.sin(radians)) + dpToPx(5);

            tickTextPaint.setColor(tickTextColor);
            canvas.drawText(String.valueOf(speed), textX, textY, tickTextPaint);
        }

        // 绘制小刻度
        tickPaint.setStrokeWidth(dpToPx(1));
        for (int speed = 0; speed <= MAX_SPEED; speed += 5) {
            if (speed % SPEED_INTERVAL == 0) continue; // 跳过主刻度位置

            float angle = -135 + (speed / (float) MAX_SPEED) * 270;
            double radians = Math.toRadians(angle + 90);

            float startX = centerX + (float) (tickRadius * Math.cos(radians));
            float startY = centerY + (float) (tickRadius * Math.sin(radians));
            float endX = centerX + (float) ((tickRadius - dpToPx(8)) * Math.cos(radians));
            float endY = centerY + (float) ((tickRadius - dpToPx(8)) * Math.sin(radians));

            canvas.drawLine(startX, startY, endX, endY, tickPaint);
        }
    }

    /**
     * 绘制指针
     */
    private void drawNeedle(Canvas canvas) {
        // 计算指针角度：从 -135° 开始，根据速度旋转
        float angle = -135 + (currentSpeed / MAX_SPEED) * 270;
        double radians = Math.toRadians(angle + 90);

        float needleLength = radius - dpToPx(50);

        // 指针顶端位置
        float needleX = centerX + (float) (needleLength * Math.cos(radians));
        float needleY = centerY + (float) (needleLength * Math.sin(radians));

        // 绘制指针阴影
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x40000000);
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(dpToPx(2), dpToPx(2));
        drawNeedleShape(canvas, needleX, needleY, shadowPaint);
        canvas.restore();

        // 绘制指针
        needlePaint.setColor(needleColor);
        drawNeedleShape(canvas, needleX, needleY, needlePaint);
    }

    /**
     * 绘制指针形状
     */
    private void drawNeedleShape(Canvas canvas, float needleX, float needleY, Paint paint) {
        // 指针尾部宽度
        float tailWidth = dpToPx(6);
        // 指针末端宽度（尖端）
        float tipWidth = dpToPx(2);
        // 指针长度
        float needleLength = radius - dpToPx(30);

        float angle = -135 + (currentSpeed / MAX_SPEED) * 270;

        // 计算指针四个角的坐标
        double radians = Math.toRadians(angle + 90);
        double perpRadians = radians + Math.PI / 2;

        // 指针末端（尖端）
        float tipX = needleX;
        float tipY = needleY;

        // 指针基部的两个角
        float base1X = centerX + (float) (tailWidth * Math.cos(perpRadians));
        float base1Y = centerY + (float) (tailWidth * Math.sin(perpRadians));
        float base2X = centerX - (float) (tailWidth * Math.cos(perpRadians));
        float base2Y = centerY - (float) (tailWidth * Math.sin(perpRadians));

        // 绘制指针为三角形
        canvas.save();
        canvas.rotate(angle + 90, centerX, centerY);
        canvas.drawPath(
            new android.graphics.Path() {{
                moveTo(centerX, centerY - tailWidth);
                lineTo(centerX, centerY + tailWidth);
                lineTo(centerX + needleLength - dpToPx(20), centerY);
                close();
            }},
            paint
        );
        canvas.restore();
    }

    /**
     * 绘制速度文字
     */
    private void drawSpeedText(Canvas canvas) {
        // 速度数值
        String speedStr = String.format("%.1f", currentSpeed);
        float textY = centerY + dpToPx(60);

        speedTextPaint.setColor(speedTextColor);
        canvas.drawText(speedStr, centerX, textY, speedTextPaint);

        // 单位文字
        textY += dpToPx(30);
        unitTextPaint.setColor(unitTextColor);
        canvas.drawText("km/h", centerX, textY, unitTextPaint);
    }

    /**
     * dp 转 px
     */
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    /**
     * sp 转 px
     */
    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * 当配置改变时（如夜间模式切换），更新颜色
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateColorsForTheme();
        updatePaintColors();
        invalidate();
    }

    /**
     * 更新画笔颜色
     */
    private void updatePaintColors() {
        if (backgroundPaint != null) backgroundPaint.setColor(backgroundColor);
        if (ringPaint != null) ringPaint.setColor(ringColor);
        if (tickPaint != null) tickPaint.setColor(tickColor);
        if (tickTextPaint != null) tickTextPaint.setColor(tickTextColor);
        if (needlePaint != null) needlePaint.setColor(needleColor);
        if (centerPaint != null) centerPaint.setColor(centerColor);
        if (speedTextPaint != null) speedTextPaint.setColor(speedTextColor);
        if (unitTextPaint != null) unitTextPaint.setColor(unitTextColor);
    }
}
