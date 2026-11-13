package live.ptiv.watch;

import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.Locale;

/**
 * Battery-conscious procedural space art. It is NOT an audio spectrum analyzer.
 * Captions come only from user-supplied LRC, never generated or timed by animation.
 */
public final class CosmicView extends View {
    public interface Source {
        String title();
        String lyric();
        String nextLyric();
        boolean hasTimedLyrics();
        boolean isPlaying();
        int positionMs();
        int durationMs();
    }

    private final Source source;
    private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String volumeToast = "";
    private long volumeVisibleUntil = 0L;
    private boolean visible = true;

    public CosmicView(Context context, Source source) {
        super(context);
        this.source = source;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("P.T. Architech Cosmic Karaoke. Triple tap to return. Two fingers sliding horizontally controls volume.");
    }

    public void showVolume(String message) {
        volumeToast = message;
        volumeVisibleUntil = android.os.SystemClock.uptimeMillis() + 1300L;
        invalidate();
    }

    public void setScreenVisible(boolean value) {
        visible = value;
        if(value)invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w=getWidth(), h=getHeight();
        if(w<=0||h<=0)return;
        float cx=w/2f,cy=h/2f;
        long now=android.os.SystemClock.uptimeMillis();
        float t=source.isPlaying() ? source.positionMs()/1000f : 0f;
        float min=Math.min(w,h);

        canvas.drawColor(Color.rgb(5,8,22));
        // Diffuse radial nebula, discrete short-lived gradients. No image downloads.
        brush.setStyle(Paint.Style.FILL);
        brush.setShader(new RadialGradient(w*.49f,h*.44f,min*.6f,
            new int[]{Color.rgb(41,18,86),Color.rgb(16,29,73),Color.rgb(5,8,22)},
            null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,brush);
        brush.setShader(null);

        // Three slow orbiting iridescent halos, decorative, never falsely audio-reactive.
        for(int k=0;k<4;k++){
            float orbit=.21f+k*.085f;
            float spin=t*(3.4f+k*.85f)+k*48f;
            brush.setStyle(Paint.Style.STROKE);
            brush.setStrokeWidth(Math.max(1f,min*.004f));
            brush.setColor(Color.argb(105-k*17,95+k*24,185-k*19,240-k*12));
            canvas.drawArc(cx-min*orbit,cy-min*orbit,cx+min*orbit,cy+min*orbit,
                spin,180f+k*27f,false,brush);
        }
        brush.setStyle(Paint.Style.FILL);
        for(int i=0;i<76;i++){
            // Deterministic stars: no per-frame random allocations.
            float fx=(float)((i*0.61803398875d+0.31d)%1d);
            float fy=(float)((i*0.75487766625d+0.17d)%1d);
            float twinkle=(float)(0.65+0.35*Math.sin(t*.9+i*.91));
            int alpha=Math.max(40,Math.min(220,(int)(twinkle*(90+(i%5)*24))));
            brush.setColor(Color.argb(alpha,193,244,242));
            canvas.drawCircle(w*fx,h*fy,Math.max(.7f,min*(.002f+(i%4)*.001f)),brush);
        }
        brush.setStyle(Paint.Style.FILL);
        brush.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        drawCenter(canvas,"P.T. ARCHITECH",cx,h*.135f,
            Math.max(11f,min*.045f),Color.rgb(193,247,113));
        String title=source.title();
        if(title==null||title.isEmpty())title="Select a PTIV track";
        title=TextUtils.ellipsize(title,paintText(Math.max(12f,min*.045f),Color.WHITE,false),
            w*.80f,TextUtils.TruncateAt.END).toString();
        drawCenter(canvas,title,cx,h*.235f,Math.max(12f,min*.046f),Color.WHITE);

        final boolean timed=source.hasTimedLyrics();
        String line=timed?source.lyric():"NO VERIFIED LRC YET";
        if(line==null||line.trim().isEmpty())line=timed?"♪":"NO VERIFIED LRC YET";
        drawWrapped(canvas,line,w*.78f,h*.52f,Math.max(17f,min*.071f),
            timed?Color.rgb(227,255,236):Color.rgb(223,207,232),3);
        String next=timed?source.nextLyric():"Visuals are artistic; no words or timings invented";
        if(next==null||next.trim().isEmpty())next="♪";
        drawWrapped(canvas,next,w*.80f,h*.705f,Math.max(10f,min*.033f),
            Color.rgb(164,194,203),2);

        int duration=source.durationMs();
        float progress=duration>0?Math.max(0,Math.min(1f,source.positionMs()/(float)duration)):0f;
        brush.setColor(Color.rgb(55,80,83));canvas.drawRoundRect(w*.16f,h*.81f,w*.84f,h*.82f,4,4,brush);
        brush.setColor(Color.rgb(193,247,113));canvas.drawRoundRect(w*.16f,h*.81f,
            w*(.16f+.68f*progress),h*.82f,4,4,brush);
        drawCenter(canvas,format(source.positionMs())+"  /  "+format(duration),
            cx,h*.865f,Math.max(11f,min*.033f),Color.rgb(206,235,228));
        drawCenter(canvas,"3 TAPS: PLAYER    ·    2 FINGERS: VOLUME",
            cx,h*.937f,Math.max(8f,min*.027f),Color.rgb(177,206,194));
        if(now<volumeVisibleUntil){
            brush.setColor(Color.argb(236,19,44,54));
            canvas.drawRoundRect(w*.17f,h*.02f,w*.83f,h*.11f,18,18,brush);
            drawCenter(canvas,volumeToast,cx,h*.078f,
                Math.max(12f,min*.048f),Color.rgb(193,247,113));
        }
        // 10 FPS only during playback; hold a static picture when paused/background.
        if(visible && source.isPlaying())postInvalidateDelayed(100);
    }

    private String format(int millis) {
        long seconds=Math.max(0,millis)/1000;
        return String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);
    }
    private TextPaint paintText(float size,int color,boolean bold) {
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        textPaint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return textPaint;
    }
    private void drawCenter(Canvas canvas,String value,float x,float baseline,float size,int color) {
        Paint p=brush;
        p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);p.setColor(color);p.clearShadowLayer();
        canvas.drawText(value,x,baseline,p);
    }
    private void drawWrapped(Canvas canvas,String value,float width,float centerY,float size,int color,int lines) {
        String safe=value==null?"":value;
        TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        StaticLayout layout=StaticLayout.Builder.obtain(safe,0,safe.length(),tp,(int)width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
        canvas.save();
        canvas.translate((getWidth()-width)/2f,centerY-layout.getHeight()/2f);
        layout.draw(canvas);
        canvas.restore();
    }
}
