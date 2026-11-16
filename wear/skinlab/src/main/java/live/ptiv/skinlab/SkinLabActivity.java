package live.ptiv.skinlab;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/**
 * Isolated, permission-free, original visual prototypes.
 * Does not read, play, write or modify music, saved lyrics, or the main Walkman.
 */
public final class SkinLabActivity extends Activity {
    private PreviewSurface preview;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        preview = new PreviewSurface();
        preview.setContentDescription(getString(R.string.app_name)
                + ", visual interface prototype. Bottom left toggles animation. Bottom right opens original PTIV Walkman.");
        setContentView(preview);
    }
    @Override protected void onResume() {super.onResume(); if(preview!=null)preview.setActive(true);}
    @Override protected void onPause() {if(preview!=null)preview.setActive(false);super.onPause();}

    private void openOriginalPlayer() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("live.ptiv.watch");
        if (launch == null) {
            Toast.makeText(this, "Original PTIV Walkman is not installed", Toast.LENGTH_LONG).show();
            return;
        }
        try {startActivity(launch);}
        catch (Exception e) { Toast.makeText(this, "Unable to open original Walkman", Toast.LENGTH_SHORT).show();}
    }

    private final class PreviewSurface extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int skin=BuildConfig.SKIN;
        private boolean running=true,active=false;
        private long origin=android.os.SystemClock.uptimeMillis();
        private float scale,ox,oy;
        private float downX,downY;
        private boolean powerSave;
        private final int mint = Color.rgb(191,246,126);
        private final int pearl = Color.rgb(130,245,239);
        private final int orange = Color.rgb(248,157,79);
        PreviewSurface(){super(SkinLabActivity.this);PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);powerSave=pm!=null&&pm.isPowerSaveMode();setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setActive(boolean state){active=state;if(active)invalidate();}
        private void set(int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);p.setAlpha(Color.alpha(color));}
        private void rect(float x,float y,float w,float h,float r,int color){set(color);getCanvas().drawRoundRect(new RectF(x,y,x+w,y+h),r,r,p);}
        private Canvas target;
        private Canvas getCanvas(){return target;}
        private void line(float x1,float y1,float x2,float y2,float thick,int color){set(color);p.setStrokeWidth(thick);getCanvas().drawLine(x1,y1,x2,y2,p);}
        private void ring(float x,float y,float rad,float thick,int color){set(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thick);getCanvas().drawCircle(x,y,rad,p);p.setStyle(Paint.Style.FILL);}
        private void circle(float x,float y,float radius,int color){set(color);getCanvas().drawCircle(x,y,radius,p);}
        private void text(String text,float x,float y,float size,int color,boolean bold,boolean centered){
            set(color);p.setTextSize(size);p.setTypeface(bold?android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD):android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
            p.setTextAlign(centered?Paint.Align.CENTER:Paint.Align.LEFT);getCanvas().drawText(text,x,y,p);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            target=canvas;float w=getWidth(),h=getHeight();
            float side=Math.min(w,h);
            scale=side/360f;
            ox=(w-360f*scale)/2f;oy=(h-360f*scale)/2f;
            canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scale);
            set(skin==0?Color.rgb(5,16,24):skin==1?Color.rgb(8,10,28):Color.rgb(8,18,19));
            canvas.drawColor(p.getColor());
            float t=running&&!powerSave?(android.os.SystemClock.uptimeMillis()-origin)/1000f:0;
            switch(skin){
                case 0: cassette(t); break;
                case 1: orbit(t); break;
                default: lyrics(t); break;
            }
            footer();
            canvas.restore();target=null;
            if(active&&running&&!powerSave)postInvalidateDelayed(120); // max ~8fps, no idle loop when paused
        }
        private void header(String version,int color){
            text("PTIV  /  UI LAB",180,53,13,color,true,true);
            text(version,180,75,11,Color.rgb(180,192,187),false,true);
        }
        private void cassette(float t){
            header("A • CASSETTE / TACTILE",mint);
            rect(36,94,288,172,25,Color.rgb(24,41,42));
            rect(49,108,262,143,15,Color.rgb(9,23,32));
            line(66,126,294,126,4,orange);line(66,135,294,135,3,mint);
            text("P.T. ARCHITECH",180,155,17,Color.rgb(241,247,220),true,true);
            circle(123,196,32,Color.rgb(46,73,70));circle(236,196,32,Color.rgb(46,73,70));
            ring(123,196,26,4,mint);ring(236,196,26,4,mint);
            float angle=t*2f;
            for(int i=0;i<6;i++){
                float a=(float)(angle+i*Math.PI/3);float dx=(float)Math.cos(a)*20f,dy=(float)Math.sin(a)*20f;
                line(123+dx*.45f,196+dy*.45f,123+dx,196+dy,3,Color.rgb(183,220,197));
                line(236+dx*.45f,196+dy*.45f,236+dx,196+dy,3,Color.rgb(183,220,197));
            }
            for(int i=0;i<20;i++){
                float v=running?(float)(.5+.5*Math.sin((i*0.93)+t*2.2)):0.18f;
                rect(65+i*11.5f,238-(v*14),5,3+v*14,2,mint);
            }
            text("LEARN  •  PRACTICE  •  PERFORM",180,284,10,Color.rgb(177,205,193),false,true);
        }
        private void orbit(float t){
            header("B • PEARL / ORBIT",pearl);
            for(int i=0;i<4;i++)ring(180,182,51+i*22,1.5f,i%2==0?Color.rgb(43,91,99):Color.rgb(46,52,112));
            circle(180,182,59,Color.rgb(17,38,62));
            for(int i=0;i<3;i++){
                float r=78+i*18;
                float a=t*(.5f+i*.12f)+(float)(i*2*Math.PI/3);
                float x=180+(float)Math.cos(a)*r;
                float y=182+(float)Math.sin(a)*r;
                circle(x,y,i==0?13:8,i==0?pearl:i==1?Color.rgb(179,142,251):orange);
            }
            ring(180,182,45,5,pearl);
            text("PTIV",180,188,30,Color.rgb(225,252,255),true,true);
            text("PEARL TRIANGLE",180,269,13,Color.rgb(176,203,224),true,true);
            text("ONE PLAYER  /  MANY SKINS",180,286,10,Color.rgb(146,177,199),false,true);
        }
        private void lyrics(float t){
            header("C • LYRICS / FOCUS",mint);
            line(60,113,300,113,2,Color.rgb(61,109,99));
            text("LYRIC STAGE",180,139,14,mint,true,true);
            text("No reviewed lyrics",180,178,20,Color.WHITE,true,true);
            text("loaded in this preview",180,203,13,Color.rgb(183,216,203),false,true);
            text("Open the main Walkman",180,239,13,Color.rgb(174,197,190),false,true);
            text("for verified text + audio",180,258,13,Color.rgb(174,197,190),false,true);
            float wave=(float)(.5+.5*Math.sin(t*1.4f));
            ring(180,203,94,2,Color.rgb(53,99,90));
            rect(89,294,182,3,2,Color.rgb(68,105,94));rect(89,294,60*wave,3,2,mint);
        }
        private void footer(){
            rect(34,305,142,40,17,skin==1?Color.rgb(35,52,87):Color.rgb(31,57,50));
            rect(184,305,142,40,17,skin==1?Color.rgb(35,52,87):Color.rgb(31,57,50));
            text(running?"Pause motion":"Play motion",105,331,12,Color.WHITE,true,true);
            text("Open PTIV ↗",255,331,12,Color.WHITE,true,true);
            text("UX PREVIEW ONLY • AUDIO UNTOUCHED",180,299,9,Color.rgb(153,171,165),false,true);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                downX=e.getX();downY=e.getY();return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                performClick();
                float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
                if(y>=303&&y<=350){
                    if(x<181){running=!running;if(running)origin=android.os.SystemClock.uptimeMillis();invalidate();}
                    else openOriginalPlayer();
                }
                return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
