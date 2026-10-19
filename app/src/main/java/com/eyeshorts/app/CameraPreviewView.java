package com.eyeshorts.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class CameraPreviewView extends View {
    private PreviewBus.Frame frame;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF imageRect=new RectF();
    CameraPreviewView(Context context) {
        super(context); setBackgroundColor(0xFF0B100E);
        setContentDescription("전면 카메라 미리보기. 인식한 홍채에 점이 표시됩니다.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void setFrame(PreviewBus.Frame value) { if (frame!=null) frame.recycle(); frame=value; invalidate(); }
    void clearFrame() { if (frame!=null) frame.recycle(); frame=null; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        if (frame==null) {
            paint.setColor(0xFFA1B2A9); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(12*density);
            canvas.drawText("카메라 미리보기",getWidth()/2f,getHeight()/2f,paint); return;
        }
        float scale=Math.min((float)getWidth()/frame.bitmap().getWidth(),(float)getHeight()/frame.bitmap().getHeight());
        float width=frame.bitmap().getWidth()*scale, height=frame.bitmap().getHeight()*scale;
        float left=(getWidth()-width)/2, top=(getHeight()-height)/2;
        canvas.save(); canvas.translate(left+width,top); canvas.scale(-1,1);
        imageRect.set(0,0,width,height); canvas.drawBitmap(frame.bitmap(),null,imageRect,paint);
        if (frame.face()) {
            paint.setColor(frame.eyesOpen() ? 0xFFAFF4CD : 0xFFFFC47C);
            canvas.drawCircle(frame.leftX()*width,frame.leftY()*height,3*density,paint);
            canvas.drawCircle(frame.rightX()*width,frame.rightY()*height,3*density,paint);
        }
        canvas.restore();
    }
}
