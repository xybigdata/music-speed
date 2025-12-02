package com.speedvolume;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class FunctionGraphView extends View {
    private Paint axisPaint, gridPaint, curvePaint, textPaint;
    private int mode = 0;
    private int volumeCrit = 30;
    private int speedCrit = 20;
    private float easeCoef = 0.1f;
    private float powerIndex = 1.0f;

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
        
        // 绘制坐标轴
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint);
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint);
        
        // 绘制函数曲线
        Path path = new Path();
        boolean started = false;
        
        for (int i = 0; i <= 150; i++) {
            float speed = i;
            float volume;
            
            if (mode == 0) {
                if (speed < speedCrit) {
                    volume = volumeCrit;
                } else {
                    float k = (float) volumeCrit / speedCrit;
                    volume = Math.min(100, k * speed);
                }
            } else {
                volume = Math.min(100, 0.1f * easeCoef * (float) Math.pow(speed, powerIndex));
            }
            
            float x = padding + (speed / 150f) * graphWidth;
            float y = height - padding - (volume / 100f) * graphHeight;
            
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        
        canvas.drawPath(path, curvePaint);
    }
}
