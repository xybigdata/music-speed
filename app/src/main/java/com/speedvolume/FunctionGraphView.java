package com.speedvolume;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * 函数曲线图视图
 * 支持线性函数、指数函数和自定义范围曲线的实时预览
 */
public class FunctionGraphView extends View {
    private Paint axisPaint, gridPaint, curvePaint, textPaint, fillPaint;
    
    // 曲线模式
    private int mode = 0; // 0: 线性, 1: 指数, 2: 自定义范围
    
    // 线性函数参数
    private int volumeCrit = 30;
    private int speedCrit = 20;
    
    // 指数函数参数
    private float easeCoef = 0.1f;
    private float powerIndex = 1.0f;
    
    // 自定义范围
    private List<SpeedVolumeConfig.SpeedRange> customRanges = new ArrayList<>();

    public FunctionGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        axisPaint = new Paint();
        axisPaint.setColor(Color.BLACK);
        axisPaint.setStrokeWidth(3);
        axisPaint.setAntiAlias(true);

        gridPaint = new Paint();
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1);
        gridPaint.setAntiAlias(true);

        curvePaint = new Paint();
        curvePaint.setColor(Color.parseColor("#2196F3"));
        curvePaint.setStrokeWidth(5);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setAntiAlias(true);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        
        fillPaint = new Paint();
        fillPaint.setColor(Color.parseColor("#332196F3")); // 半透明蓝色
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(24);
        textPaint.setAntiAlias(true);
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    public void setLinearParams(int volumeCrit, int speedCrit) {
        this.volumeCrit = volumeCrit;
        this.speedCrit = speedCrit;
        invalidate();
    }

    public void setPowerParams(float easeCoef, float powerIndex) {
        this.easeCoef = easeCoef;
        this.powerIndex = powerIndex;
        invalidate();
    }
    
    /**
     * 设置自定义范围用于曲线预览
     */
    public void setCustomRanges(List<SpeedVolumeConfig.SpeedRange> ranges) {
        this.customRanges.clear();
        if (ranges != null) {
            this.customRanges.addAll(ranges);
        }
        invalidate();
    }

    // 音量限制（用于预览）
    private int minVolumeLimit = 0;
    private int maxVolumeLimit = 100;
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        int padding = 50;
        
        int graphWidth = width - 2 * padding;
        int graphHeight = height - 2 * padding;
        
        // 绘制网格和坐标值
        for (int i = 0; i <= 10; i++) {
            float x = padding + i * graphWidth / 10f;
            canvas.drawLine(x, padding, x, height - padding, gridPaint);
            // X轴标签 (速度 km/h)
            if (i % 2 == 0) {
                String label = String.valueOf(i * 15);
                canvas.drawText(label, x - textPaint.measureText(label) / 2, height - padding + 30, textPaint);
            }
            
            float y = padding + i * graphHeight / 10f;
            canvas.drawLine(padding, y, width - padding, y, gridPaint);
            // Y轴标签 (音量 %)
            if (i % 2 == 0) {
                String label = String.valueOf(100 - i * 10);
                canvas.drawText(label, padding - 35, y + 8, textPaint);
            }
        }
        
        // 绘制音量限制区域（半透明背景）
        if (minVolumeLimit > 0 || maxVolumeLimit < 100) {
            Paint limitPaint = new Paint();
            limitPaint.setColor(Color.parseColor("#33FF9800")); // 半透明橙色
            limitPaint.setStyle(Paint.Style.FILL);
            
            // 绘制最小音量限制区域（底部）
            if (minVolumeLimit > 0) {
                float minY = height - padding - (minVolumeLimit / 100f) * graphHeight;
                canvas.drawRect(padding, height - padding, width - padding, minY, limitPaint);
            }
            
            // 绘制最大音量限制区域（顶部）
            if (maxVolumeLimit < 100) {
                float maxY = height - padding - (maxVolumeLimit / 100f) * graphHeight;
                canvas.drawRect(padding, padding, width - padding, maxY, limitPaint);
            }
        }
        
        // 绘制坐标轴
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint);
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint);
        
        // 绘制X轴标签
        String xLabel = "速度 (km/h)";
        canvas.drawText(xLabel, width - padding - textPaint.measureText(xLabel), height - 10, textPaint);
        
        // 绘制Y轴标签
        canvas.save();
        canvas.rotate(-90, 20, height / 2f);
        String yLabel = "音量 (%)";
        canvas.drawText(yLabel, 20 - textPaint.measureText(yLabel) / 2, height / 2f, textPaint);
        canvas.restore();
        
        // 绘制函数曲线
        Path path = new Path();
        Path fillPath = new Path();
        boolean started = false;
        
        for (int i = 0; i <= 150; i++) {
            float speed = i;
            float volume = calculateVolume(speed);
            
            // 应用音量限制
            volume = Math.max(minVolumeLimit, Math.min(maxVolumeLimit, volume));
            
            float x = padding + (speed / 150f) * graphWidth;
            float y = height - padding - (volume / 100f) * graphHeight;
            
            if (!started) {
                path.moveTo(x, y);
                fillPath.moveTo(x, height - padding);
                fillPath.lineTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        
        // 完成填充路径
        fillPath.lineTo(width - padding, height - padding);
        fillPath.close();
        
        // 绘制填充区域
        canvas.drawPath(fillPath, fillPaint);
        
        // 绘制曲线
        canvas.drawPath(path, curvePaint);
        
        // 绘制范围标记点（仅在自定义模式下）
        if (mode == 2 && !customRanges.isEmpty()) {
            drawRangeMarkers(canvas, padding, graphWidth, graphHeight, height);
        }
        
        // 绘制音量限制线
        drawVolumeLimitLines(canvas, padding, graphWidth, graphHeight, height);
    }
    
    /**
     * 绘制音量限制参考线
     */
    private void drawVolumeLimitLines(Canvas canvas, int padding, int graphWidth, int graphHeight, int height) {
        Paint limitLinePaint = new Paint();
        limitLinePaint.setColor(Color.parseColor("#FF9800"));
        limitLinePaint.setStrokeWidth(2);
        limitLinePaint.setAntiAlias(true);
        limitLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{5, 5}, 0));
        
        // 最小音量限制线
        if (minVolumeLimit > 0) {
            float minY = height - padding - (minVolumeLimit / 100f) * graphHeight;
            canvas.drawLine(padding, minY, padding + graphWidth, minY, limitLinePaint);
            
            // 标签
            Paint labelPaint = new Paint();
            labelPaint.setColor(Color.parseColor("#FF9800"));
            labelPaint.setTextSize(20);
            labelPaint.setAntiAlias(true);
            canvas.drawText("最小:" + minVolumeLimit + "%", padding + graphWidth + 5, minY + 5, labelPaint);
        }
        
        // 最大音量限制线
        if (maxVolumeLimit < 100) {
            float maxY = height - padding - (maxVolumeLimit / 100f) * graphHeight;
            canvas.drawLine(padding, maxY, padding + graphWidth, maxY, limitLinePaint);
            
            // 标签
            Paint labelPaint = new Paint();
            labelPaint.setColor(Color.parseColor("#FF9800"));
            labelPaint.setTextSize(20);
            labelPaint.setAntiAlias(true);
            canvas.drawText("最大:" + maxVolumeLimit + "%", padding + graphWidth + 5, maxY + 5, labelPaint);
        }
    }
    
    /**
     * 设置音量限制用于预览
     */
    public void setVolumeLimits(int min, int max) {
        this.minVolumeLimit = min;
        this.maxVolumeLimit = max;
        invalidate();
    }
    
    /**
     * 绘制范围标记点
     */
    private void drawRangeMarkers(Canvas canvas, int padding, int graphWidth, int graphHeight, int height) {
        Paint markerPaint = new Paint();
        markerPaint.setColor(Color.parseColor("#FF5722"));
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setAntiAlias(true);
        
        for (SpeedVolumeConfig.SpeedRange range : customRanges) {
            // 在范围边界绘制标记点
            float x1 = padding + (Math.min(range.minSpeed, 150) / 150f) * graphWidth;
            float y = height - padding - (range.volume / 100f) * graphHeight;
            canvas.drawCircle(x1, y, 6, markerPaint);
        }
    }
    
    /**
     * 根据当前模式和参数计算音量
     */
    private float calculateVolume(float speed) {
        switch (mode) {
            case 0: // 线性函数
                if (speed < speedCrit) {
                    return volumeCrit;
                } else {
                    float k = (float) volumeCrit / speedCrit;
                    return Math.min(100, k * speed);
                }
            case 1: // 指数函数
                return Math.min(100, 0.1f * easeCoef * (float) Math.pow(speed, powerIndex));
            case 2: // 自定义范围
                return calculateVolumeFromRanges(speed);
            default:
                return 50;
        }
    }
    
    /**
     * 根据自定义范围计算音量
     */
    private float calculateVolumeFromRanges(float speed) {
        if (customRanges.isEmpty()) {
            return 50;
        }
        
        // 查找匹配的范围
        for (SpeedVolumeConfig.SpeedRange range : customRanges) {
            if (speed >= range.minSpeed && speed < range.maxSpeed) {
                return range.volume;
            }
        }
        
        // 如果没有匹配的范围，返回最后一个范围的音量
        return customRanges.get(customRanges.size() - 1).volume;
    }
}
