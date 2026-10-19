package com.eyeshorts.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import com.eyeshorts.app.core.InputMode;

final class EyeTargetView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc=new RectF();
    private boolean top,bottom,right,practice,mouthPracticeOnly;
    private InputMode mode=InputMode.WINK;
    private double progress;
    EyeTargetView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    void update(boolean top,boolean bottom,boolean right,boolean practice,InputMode mode,double progress,boolean mouthPracticeOnly) {
        this.top=top; this.bottom=bottom; this.right=right; this.practice=practice; this.mode=mode; this.progress=progress;
        this.mouthPracticeOnly=mouthPracticeOnly;
        setContentDescription(mouthPracticeOnly ? "입을 약 0.3초 벌리면 영상 재생/일시정지" : practice ? mode.instruction() : right ? "오른쪽 보정 점" : top ? "위쪽 보정 점" : bottom ? "아래쪽 보정 점" : "중앙 보정 점");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        float cx=getWidth()*0.5f, cy=getHeight()*0.5f, topY=getHeight()*0.15f, bottomY=getHeight()*0.85f;
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
        if (practice && !mouthPracticeOnly && mode!=InputMode.WINK) {
            dot(canvas,cx,cy,6*density,0xFF526C5C);
            label(canvas,"중앙",cx,cy+26*density,12,0xFFA1B2A9);
        }
        float x=cx,y=cy;
        if (practice && !mouthPracticeOnly) {
            switch (mode) {
                case SWIPE_UP -> {
                    dot(canvas,cx,bottomY,8*density,0xFFAFF4CD);
                    label(canvas,"여기서 시작 ↑",cx,bottomY-23*density,12,0xFFAFF4CD); y=topY;
                }
                case DWELL -> y=topY;
                case RIGHT_RETURN -> x=getWidth()*0.85f;
                default -> { }
            }
        } else {
            if (right) x=getWidth()*0.85f;
            else if (top) y=topY;
            else if (bottom) y=bottomY;
        }
        dot(canvas,x,y,42*density,0x152EEDAA);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*density); paint.setColor(0xFF29463A);
        canvas.drawCircle(x,y,27*density,paint);
        paint.setColor(0xFFAFF4CD); paint.setStrokeCap(Paint.Cap.ROUND);
        arc.set(x-27*density,y-27*density,x+27*density,y+27*density);
        canvas.drawArc(arc,-90,(float)progress*360,false,paint);
        paint.setStyle(Paint.Style.FILL);
        if (mouthPracticeOnly) {
            label(canvas,"입",x,y+5*density,16,0xFFAFF4CD);
            label(canvas,"약 0.3초 벌려주세요",cx,cy+58*density,13,0xFFA1B2A9);
            label(canvas,"입을 닫으면 다음 입력 준비",cx,cy+78*density,12,0xFFA1B2A9);
        } else if (practice && mode==InputMode.WINK) {
            label(canvas,"윙크",x,y+5*density,16,0xFFAFF4CD);
            label(canvas,"오른쪽 눈 → 다음",cx,cy+58*density,13,0xFFA1B2A9);
            label(canvas,"왼쪽 눈 → 이전",cx,cy+78*density,13,0xFFA1B2A9);
        } else {
            dot(canvas,x,y,8*density,0xFFAFF4CD);
            if (practice) label(canvas,mode==InputMode.RIGHT_RETURN ? "오른쪽" : mode==InputMode.SWIPE_UP ? "위로 이동" : "다음 영상",
                    x,y+45*density,12,0xFFAFF4CD);
        }
    }
    private void dot(Canvas canvas,float x,float y,float radius,int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawCircle(x,y,radius,paint);
    }
    private void label(Canvas canvas,String text,float x,float y,int sp,int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color);
        paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text,x,y,paint);
    }
}
