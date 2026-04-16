package com.kimiclaw.pet;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Canvas绘制的小龙虾View
 */
public class LobsterView extends View {

    private Paint bodyPaint;
    private Paint darkPaint;
    private Paint lightPaint;
    private Paint eyePaint;
    private Paint clawPaint;
    private Paint strokePaint;

    private Path bodyPath;
    private Path leftClawPath;
    private Path rightClawPath;
    private Path tailPath;
    private Path leftEyePath;
    private Path rightEyePath;

    // 动画状态
    private float animationProgress = 0f;
    private ValueAnimator animator;

    // 龙虾状态
    public enum State { NORMAL, EATING, HUNGRY, SAD }
    private State currentState = State.NORMAL;

    // 身体部件位置
    private float centerX, centerY;
    private float bodyWidth = 45f;
    private float bodyHeight = 65f;

    public LobsterView(Context context) {
        super(context);
        init();
    }

    public LobsterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LobsterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 身体主色 - 龙虾红色
        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.parseColor("#E74C3C"));
        bodyPaint.setStyle(Paint.Style.FILL);

        // 深色部分
        darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setColor(Color.parseColor("#C0392B"));
        darkPaint.setStyle(Paint.Style.FILL);

        // 浅色高光
        lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lightPaint.setColor(Color.parseColor("#F1948A"));
        lightPaint.setStyle(Paint.Style.FILL);

        // 眼睛
        eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setColor(Color.BLACK);
        eyePaint.setStyle(Paint.Style.FILL);

        // 钳子
        clawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clawPaint.setColor(Color.parseColor("#D35400"));
        clawPaint.setStyle(Paint.Style.FILL);

        // 描边
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.parseColor("#922B21"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);

        bodyPath = new Path();
        leftClawPath = new Path();
        rightClawPath = new Path();
        tailPath = new Path();
        leftEyePath = new Path();
        rightEyePath = new Path();

        startAnimation();
    }

    private void startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setState(State state) {
        if (currentState != state) {
            currentState = state;
            invalidate();
        }
    }

    public State getState() {
        return currentState;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        // 根据状态调整绘制
        switch (currentState) {
            case EATING:
                drawEatingState(canvas);
                break;
            case HUNGRY:
                drawHungryState(canvas);
                break;
            case SAD:
                drawSadState(canvas);
                break;
            default:
                drawNormalState(canvas);
                break;
        }

        canvas.restore();
    }

    /**
     * 正常状态绘制
     */
    private void drawNormalState(Canvas canvas) {
        float breatheOffset = (float) Math.sin(animationProgress * Math.PI * 2) * 2f;

        // 绘制尾巴（在身体后面）
        drawTail(canvas, breatheOffset);

        // 绘制钳子
        drawClaws(canvas, breatheOffset);

        // 绘制身体
        drawBody(canvas, breatheOffset);

        // 绘制眼睛
        drawEyes(canvas, breatheOffset);

        // 绘制触须
        drawAntennas(canvas, breatheOffset);
    }

    /**
     * 进食状态 - 钳子动作更大
     */
    private void drawEatingState(Canvas canvas) {
        float eatOffset = (float) Math.sin(animationProgress * Math.PI * 4) * 5f;

        drawTail(canvas, 0);
        drawClawsEating(canvas, eatOffset);
        drawBody(canvas, 0);
        drawEyes(canvas, 0);
        drawAntennas(canvas, 0);
    }

    /**
     * 饥饿状态 - 身体下垂，眼睛无神
     */
    private void drawHungryState(Canvas canvas) {
        float hungerOffset = (float) Math.sin(animationProgress * Math.PI) * 1f;

        drawTail(canvas, -3f);
        drawClawsHungry(canvas, hungerOffset);
        drawBody(canvas, -3f);
        drawEyesHungry(canvas, hungerOffset);
        drawAntennas(canvas, -2f);
    }

    /**
     * 悲伤状态 - 身体蜷缩
     */
    private void drawSadState(Canvas canvas) {
        float sadOffset = (float) Math.sin(animationProgress * Math.PI * 0.5) * 1f;

        drawTailSad(canvas, sadOffset);
        drawClawsSad(canvas, sadOffset);
        drawBodySad(canvas, sadOffset);
        drawEyesSad(canvas, sadOffset);
        drawAntennasSad(canvas, sadOffset);
    }

    private void drawBody(Canvas canvas, float offset) {
        bodyPath.reset();

        float topY = centerY - bodyHeight / 2 + offset;
        float bottomY = centerY + bodyHeight / 2 + offset;
        float leftX = centerX - bodyWidth / 2;
        float rightX = centerX + bodyWidth / 2;

        // 头部（椭圆形）
        RectF headRect = new RectF(leftX, topY, rightX, topY + bodyHeight * 0.45f);
        canvas.drawOval(headRect, bodyPaint);
        canvas.drawOval(headRect, strokePaint);

        // 身体中段（稍小）
        RectF bodyRect = new RectF(leftX + 5, topY + bodyHeight * 0.35f, rightX - 5, topY + bodyHeight * 0.75f);
        canvas.drawOval(bodyRect, bodyPaint);
        canvas.drawOval(bodyRect, strokePaint);

        // 尾部（扇形）
        RectF tailRect = new RectF(leftX + 8, topY + bodyHeight * 0.65f, rightX - 8, bottomY);
        canvas.drawOval(tailRect, darkPaint);
        canvas.drawOval(tailRect, strokePaint);

        // 身体纹理线条
        Paint linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#922B21"));
        linePaint.setStrokeWidth(1.5f);
        linePaint.setStyle(Paint.Style.STROKE);

        for (int i = 1; i <= 3; i++) {
            float y = topY + bodyHeight * 0.2f * i;
            canvas.drawLine(leftX + 8, y, rightX - 8, y, linePaint);
        }
    }

    private void drawBodySad(Canvas canvas, float offset) {
        float topY = centerY - bodyHeight / 2 + offset + 5f;
        float leftX = centerX - bodyWidth / 2 + 5;
        float rightX = centerX + bodyWidth / 2 - 5;

        // 蜷缩的身体 - 更圆更小
        RectF bodyRect = new RectF(leftX, topY, rightX, topY + bodyHeight * 0.6f);
        canvas.drawOval(bodyRect, darkPaint);
        canvas.drawOval(bodyRect, strokePaint);
    }

    private void drawClaws(Canvas canvas, float offset) {
        float clawY = centerY - bodyHeight * 0.05f + offset;

        // 左钳子 - 更靠近身体，不伸出边界
        leftClawPath.reset();
        float leftClawX = centerX - bodyWidth / 2 + 2;
        leftClawPath.moveTo(leftClawX, clawY);
        leftClawPath.quadTo(leftClawX - 12, clawY - 12, leftClawX - 18, clawY - 5);
        leftClawPath.quadTo(leftClawX - 16, clawY + 3, leftClawX - 10, clawY + 6);
        leftClawPath.quadTo(leftClawX - 6, clawY + 2, leftClawX, clawY + 3);
        leftClawPath.close();
        canvas.drawPath(leftClawPath, clawPaint);
        canvas.drawPath(leftClawPath, strokePaint);

        // 右钳子
        rightClawPath.reset();
        float rightClawX = centerX + bodyWidth / 2 - 2;
        rightClawPath.moveTo(rightClawX, clawY);
        rightClawPath.quadTo(rightClawX + 12, clawY - 12, rightClawX + 18, clawY - 5);
        rightClawPath.quadTo(rightClawX + 16, clawY + 3, rightClawX + 10, clawY + 6);
        rightClawPath.quadTo(rightClawX + 6, clawY + 2, rightClawX, clawY + 3);
        rightClawPath.close();
        canvas.drawPath(rightClawPath, clawPaint);
        canvas.drawPath(rightClawPath, strokePaint);

        // 钳子高光
        Paint highlightPaint = new Paint();
        highlightPaint.setColor(Color.parseColor("#F5B7B1"));
        highlightPaint.setStyle(Paint.Style.FILL);

        canvas.drawCircle(leftClawX - 12, clawY - 4, 2.5f, highlightPaint);
        canvas.drawCircle(rightClawX + 12, clawY - 4, 2.5f, highlightPaint);
    }

    private void drawClawsEating(Canvas canvas, float offset) {
        float clawY = centerY - bodyHeight * 0.1f;

        // 进食时钳子张开更大
        float openAmount = 10 + offset;

        // 左钳子（张开）
        leftClawPath.reset();
        float leftClawX = centerX - bodyWidth / 2 - 5;
        leftClawPath.moveTo(leftClawX, clawY);
        leftClawPath.quadTo(leftClawX - 20, clawY - 15 - openAmount, leftClawX - 28, clawY - 8 - openAmount);
        leftClawPath.quadTo(leftClawX - 25, clawY + 5, leftClawX - 15, clawY + 8);
        leftClawPath.quadTo(leftClawX - 10, clawY + 3, leftClawX, clawY + 5);
        leftClawPath.close();
        canvas.drawPath(leftClawPath, clawPaint);
        canvas.drawPath(leftClawPath, strokePaint);

        // 右钳子（张开）
        rightClawPath.reset();
        float rightClawX = centerX + bodyWidth / 2 + 5;
        rightClawPath.moveTo(rightClawX, clawY);
        rightClawPath.quadTo(rightClawX + 20, clawY - 15 - openAmount, rightClawX + 28, clawY - 8 - openAmount);
        rightClawPath.quadTo(rightClawX + 25, clawY + 5, rightClawX + 15, clawY + 8);
        rightClawPath.quadTo(rightClawX + 10, clawY + 3, rightClawX, clawY + 5);
        rightClawPath.close();
        canvas.drawPath(rightClawPath, clawPaint);
        canvas.drawPath(rightClawPath, strokePaint);
    }

    private void drawClawsHungry(Canvas canvas, float offset) {
        float clawY = centerY - bodyHeight * 0.1f + offset;

        // 饥饿时钳子下垂
        float leftClawX = centerX - bodyWidth / 2 - 5;
        leftClawPath.reset();
        leftClawPath.moveTo(leftClawX, clawY);
        leftClawPath.quadTo(leftClawX - 15, clawY + 10, leftClawX - 20, clawY + 20);
        leftClawPath.quadTo(leftClawX - 12, clawY + 18, leftClawX, clawY + 8);
        leftClawPath.close();
        canvas.drawPath(leftClawPath, darkPaint);
        canvas.drawPath(leftClawPath, strokePaint);

        float rightClawX = centerX + bodyWidth / 2 + 5;
        rightClawPath.reset();
        rightClawPath.moveTo(rightClawX, clawY);
        rightClawPath.quadTo(rightClawX + 15, clawY + 10, rightClawX + 20, clawY + 20);
        rightClawPath.quadTo(rightClawX + 12, clawY + 18, rightClawX, clawY + 8);
        rightClawPath.close();
        canvas.drawPath(rightClawPath, darkPaint);
        canvas.drawPath(rightClawPath, strokePaint);
    }

    private void drawClawsSad(Canvas canvas, float offset) {
        // 悲伤时钳子收拢
        float clawY = centerY + offset + 5;
        float leftClawX = centerX - bodyWidth / 2 + 5;
        float rightClawX = centerX + bodyWidth / 2 - 5;

        leftClawPath.reset();
        leftClawPath.moveTo(leftClawX, clawY);
        leftClawPath.quadTo(leftClawX - 10, clawY + 5, leftClawX - 8, clawY + 12);
        leftClawPath.quadTo(leftClawX - 5, clawY + 8, leftClawX, clawY + 5);
        leftClawPath.close();
        canvas.drawPath(leftClawPath, darkPaint);

        rightClawPath.reset();
        rightClawPath.moveTo(rightClawX, clawY);
        rightClawPath.quadTo(rightClawX + 10, clawY + 5, rightClawX + 8, clawY + 12);
        rightClawPath.quadTo(rightClawX + 5, clawY + 8, rightClawX, clawY + 5);
        rightClawPath.close();
        canvas.drawPath(rightClawPath, darkPaint);
    }

    private void drawEyes(Canvas canvas, float offset) {
        float eyeY = centerY - bodyHeight * 0.25f + offset;
        float eyeRadius = 5f;

        // 左眼
        canvas.drawCircle(centerX - 10, eyeY, eyeRadius, eyePaint);
        // 左眼高光
        canvas.drawCircle(centerX - 11, eyeY - 1, 2, lightPaint);

        // 右眼
        canvas.drawCircle(centerX + 10, eyeY, eyeRadius, eyePaint);
        // 右眼高光
        canvas.drawCircle(centerX + 9, eyeY - 1, 2, lightPaint);
    }

    private void drawEyesHungry(Canvas canvas, float offset) {
        float eyeY = centerY - bodyHeight * 0.25f + offset - 2;

        // 饥饿时眼睛半闭
        Paint halfClosedPaint = new Paint();
        halfClosedPaint.setColor(Color.BLACK);
        halfClosedPaint.setStyle(Paint.Style.STROKE);
        halfClosedPaint.setStrokeWidth(3f);

        // 左眼（半闭）
        canvas.drawLine(centerX - 14, eyeY, centerX - 6, eyeY, halfClosedPaint);
        // 右眼（半闭）
        canvas.drawLine(centerX + 6, eyeY, centerX + 14, eyeY, halfClosedPaint);
    }

    private void drawEyesSad(Canvas canvas, float offset) {
        float eyeY = centerY - bodyHeight * 0.15f + offset;

        // 悲伤时眼睛变成> <形状
        Paint sadEyePaint = new Paint();
        sadEyePaint.setColor(Color.BLACK);
        sadEyePaint.setStyle(Paint.Style.STROKE);
        sadEyePaint.setStrokeWidth(2f);

        // 左眼（>）
        canvas.drawLine(centerX - 12, eyeY - 3, centerX - 8, eyeY, sadEyePaint);
        canvas.drawLine(centerX - 8, eyeY, centerX - 12, eyeY + 3, sadEyePaint);

        // 右眼（<）
        canvas.drawLine(centerX + 12, eyeY - 3, centerX + 8, eyeY, sadEyePaint);
        canvas.drawLine(centerX + 8, eyeY, centerX + 12, eyeY + 3, sadEyePaint);
    }

    private void drawTail(Canvas canvas, float offset) {
        float tailY = centerY + bodyHeight * 0.3f + offset;
        float tailWidth = bodyWidth * 0.6f;

        tailPath.reset();
        tailPath.moveTo(centerX - tailWidth / 2, tailY);
        tailPath.lineTo(centerX + tailWidth / 2, tailY);
        tailPath.lineTo(centerX, tailY + 25);
        tailPath.close();

        canvas.drawPath(tailPath, darkPaint);
        canvas.drawPath(tailPath, strokePaint);

        // 尾扇纹理
        Paint linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#922B21"));
        linePaint.setStrokeWidth(1f);
        canvas.drawLine(centerX, tailY, centerX, tailY + 20, linePaint);
    }

    private void drawTailSad(Canvas canvas, float offset) {
        float tailY = centerY + bodyHeight * 0.2f + offset;

        // 悲伤时尾巴蜷缩
        RectF tailRect = new RectF(centerX - 15, tailY, centerX + 15, tailY + 15);
        canvas.drawOval(tailRect, darkPaint);
    }

    private void drawAntennas(Canvas canvas, float offset) {
        float antennaY = centerY - bodyHeight * 0.3f + offset;

        Paint antennaPaint = new Paint();
        antennaPaint.setColor(Color.parseColor("#C0392B"));
        antennaPaint.setStrokeWidth(2f);
        antennaPaint.setStyle(Paint.Style.STROKE);
        antennaPaint.setAntiAlias(true);

        // 左触须 - 缩短长度，不伸出边界
        float wave = (float) Math.sin(animationProgress * Math.PI * 2) * 4f;
        Path leftAntenna = new Path();
        leftAntenna.moveTo(centerX - 6, antennaY);
        leftAntenna.quadTo(centerX - 10 + wave, antennaY - 15, centerX - 8 + wave, antennaY - 25);
        canvas.drawPath(leftAntenna, antennaPaint);

        // 右触须
        Path rightAntenna = new Path();
        rightAntenna.moveTo(centerX + 6, antennaY);
        rightAntenna.quadTo(centerX + 10 - wave, antennaY - 15, centerX + 8 - wave, antennaY - 25);
        canvas.drawPath(rightAntenna, antennaPaint);

        // 触须末端小球
        canvas.drawCircle(centerX - 8 + wave, antennaY - 25, 2, bodyPaint);
        canvas.drawCircle(centerX + 8 - wave, antennaY - 25, 2, bodyPaint);
    }

    private void drawAntennasSad(Canvas canvas, float offset) {
        float antennaY = centerY - bodyHeight * 0.15f + offset;

        Paint antennaPaint = new Paint();
        antennaPaint.setColor(Color.parseColor("#922B21"));
        antennaPaint.setStrokeWidth(1.5f);
        antennaPaint.setStyle(Paint.Style.STROKE);

        // 悲伤时触须下垂
        canvas.drawLine(centerX - 5, antennaY, centerX - 10, antennaY + 15, antennaPaint);
        canvas.drawLine(centerX + 5, antennaY, centerX + 10, antennaY + 15, antennaPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
