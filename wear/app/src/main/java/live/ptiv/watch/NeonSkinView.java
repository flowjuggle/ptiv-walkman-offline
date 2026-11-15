package live.ptiv.watch;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

/**
 * Native, interactive P.T. Architech skin inspired by the supplied watch reference.
 * The skyline, Pearl Triangle and controls are drawn locally: no network, audio,
 * recording edits, or unverified lyric text. 432x432 logical layout scales to Wear.
 */
public final class NeonSkinView extends View {
    public interface State {
        String title();
        int positionMs();
        int durationMs();
        int trackCount();
        boolean playing();
        boolean shuffle();
        boolean lyricsAvailable();
    }
    public interface Actions {
        void volume(int step);
        void previous();
        void playPause();
        void next();
        void seek(float position01);
        void library();
        void lyrics();
        void shuffle();
        void secretTap();
        void help();
    }
    private static final int CYAN=Color.rgb(26,231,243);
    private static final int ORANGE=Color.rgb(255,165,30);
    private static final int VIOLET=Color.rgb(193,72,252);
    private static final int WHITE=Color.rgb(243,250,254);
    private static final int BG=Color.rgb(5,11,24);
    private final State state;
    private final Actions actions;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float touchX=216, touchY=216;
    private final RectF rr=new RectF();

    public NeonSkinView(Context context, State state, Actions actions) {
        super(context);
        this.state=state;
        this.actions=actions;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setClickable(true);
        setFocusable(true);
        setContentDescription("P.T. Architech Neon Walkman. Left minus and right plus control media volume. Center playback, previous, next, library, lyrics and shuffle.");
    }
    private void fill(int color) {p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);p.setColor(color);}
    private void stroke(int color,float width) {
        p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
        p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width) {
        stroke(color,width);c.drawLine(x1,y1,x2,y2,p);
    }
    private void text(Canvas c,String t,float x,float y,float size,int color,boolean bold) {
        fill(color);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(t,x,y,p);
    }
    private String fit(String s,float max,float size) {
        if(s==null||s.trim().isEmpty())return "SELECT P.T. ARCHITECH SONG";
        String result=s.trim();
        fill(WHITE);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        if(p.measureText(result)<=max)return result;
        while(result.length()>4 && p.measureText(result+"…")>max)result=result.substring(0,result.length()-1);
        return result+"…";
    }
    private static String clock(int ms) {
        int seconds=Math.max(0,ms)/1000;
        return String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);
    }
    private void circle(Canvas c,float x,float y,float r,int color,int edge) {
        fill(color);c.drawCircle(x,y,r,p);
        stroke(edge,2.3f);c.drawCircle(x,y,r-2,p);
    }
    private void buttonText(Canvas c,float x,float y,String label,int color,float size) {
        text(c,label,x,y+size*.34f,size,color,true);
    }
    private void skyline(Canvas c) {
        // Ink-blue Brooklyn/DUMBO silhouette and sunset. Deliberately schematic.
        fill(Color.rgb(8,23,34));rr.set(24,45,408,133);c.drawRoundRect(rr,32,32,p);
        p.setShader(new LinearGradient(30,47,390,131,new int[]{
            Color.rgb(0,143,164),Color.rgb(42,19,112),Color.rgb(236,68,117),Color.rgb(13,165,171)
        },null,Shader.TileMode.CLAMP));
        c.drawRoundRect(rr,32,32,p);p.setShader(null);
        fill(Color.rgb(5,11,27));
        int[] x={28,55,82,100,123,143,164,274,297,319,342,360,380,408};
        int[] h={23,35,17,55,29,20,34,42,66,27,24,41,31,18};
        for(int i=0;i<x.length;i++)c.drawRect(x[i],128-h[i],x[i]+14,132,p);
        // Brooklyn bridge towers, suspended cables.
        stroke(Color.rgb(1,7,26),3.3f);
        c.drawLine(38,117,395,117,p);
        c.drawLine(89,77,89,128,p);c.drawLine(103,77,103,128,p);
        c.drawLine(320,77,320,128,p);c.drawLine(334,77,334,128,p);
        c.drawLine(42,114,95,76,p);c.drawLine(95,76,207,114,p);
        c.drawLine(207,114,327,76,p);c.drawLine(327,76,398,114,p);
        stroke(Color.rgb(22,20,46),1.2f);
        for(int n=0;n<11;n++){
            float a=43+15*n;
            c.drawLine(a,115,a,97+(float)Math.abs(a-95)*.2f,p);
            float b=220+15*n;
            c.drawLine(b,115,b,96+(float)Math.abs(b-327)*.2f,p);
        }
    }
    private void pearlTriangle(Canvas c) {
        // Backlit orange-gold frame and iridescent pearl.
        Path tri=new Path();tri.moveTo(216,23);tri.lineTo(166,107);tri.lineTo(266,107);tri.close();
        stroke(Color.argb(100,255,158,10),12);c.drawPath(tri,p);
        stroke(ORANGE,5);c.drawPath(tri,p);
        stroke(Color.rgb(238,249,247),2);c.drawPath(tri,p);
        fill(Color.rgb(13,21,46));rr.set(189,54,243,106);
        p.setShader(new RadialGradient(216,73,30,new int[]{WHITE,Color.rgb(122,229,225),Color.rgb(98,65,132)},null,Shader.TileMode.CLAMP));
        c.drawCircle(216,79,24,p);p.setShader(null);
        stroke(Color.rgb(253,214,149),1.6f);c.drawCircle(216,79,24,p);
    }
    private void bars(Canvas c) {
        for(int i=0;i<7;i++){
            int y=234+i*11;
            line(c,30-i*.6f,y,40-i*.8f,y,CYAN,4);
            line(c,393+i*.6f,y,403+i*.8f,y,CYAN,4);
        }
    }
    private void prev(Canvas c,float x,int color) {
        fill(color);Path a=new Path();a.moveTo(x-1,307);a.lineTo(x-1,329);a.lineTo(x-17,318);a.close();c.drawPath(a,p);
        c.drawRoundRect(x-20,307,x-17,329,1,1,p);
    }
    private void next(Canvas c,float x,int color) {
        fill(color);Path a=new Path();a.moveTo(x+1,307);a.lineTo(x+1,329);a.lineTo(x+17,318);a.close();c.drawPath(a,p);
        c.drawRoundRect(x+17,307,x+20,329,1,1,p);
    }
    private void iconLibrary(Canvas c) {
        circle(c,93,381,23,Color.rgb(5,25,39),CYAN);
        line(c,81,372,101,372,WHITE,2.4f);line(c,81,380,101,380,WHITE,2.4f);
        line(c,81,388,95,388,WHITE,2.4f);
        text(c,"♪",104,390,18,WHITE,true);
    }
    private void iconShuffle(Canvas c) {
        circle(c,338,381,23,Color.rgb(5,25,39),CYAN);
        text(c,"⤨",338,389,31,WHITE,true);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(getWidth()<=0||getHeight()<=0)return;
        canvas.save();
        canvas.scale(getWidth()/432f,getHeight()/432f);
        canvas.drawColor(BG);
        fill(Color.rgb(3,19,29));rr.set(14,13,418,419);c(canvas,rr,196);
        skyline(canvas);
        pearlTriangle(canvas);
        // Brand is readable even when album art is not connected.
        text(canvas,"P.T. ARCHITECH",216,152,25,WHITE,true);
        text(canvas,"W A L K M A N",216,175,17,ORANGE,true);
        line(canvas,40,165,103,165,ORANGE,2);line(canvas,329,165,392,165,ORANGE,2);
        text(canvas,fit(state.title(),355,24),216,218,24,WHITE,true);
        text(canvas,clock(state.positionMs())+" / "+clock(state.durationMs()),216,244,18,WHITE,false);
        // Scrub bar spans 75..357; touch area expanded for small screens.
        fill(Color.rgb(20,68,86));rr.set(75,257,357,264);canvas.drawRoundRect(rr,4,4,p);
        float frac=state.durationMs()<=0?0:Math.max(0,Math.min(1,state.positionMs()/(float)state.durationMs()));
        fill(ORANGE);rr.set(75,257,75+282*frac,264);canvas.drawRoundRect(rr,4,4,p);
        fill(WHITE);canvas.drawCircle(75+282*frac,260,8,p);
        bars(canvas);
        // Five primary playback and volume controls.
        circle(canvas,52,318,30,Color.rgb(4,18,32),ORANGE);
        buttonText(canvas,52,318,"−",WHITE,43);
        circle(canvas,133,318,25,Color.rgb(4,18,32),CYAN);prev(canvas,133,WHITE);
        circle(canvas,216,318,33,Color.rgb(17,14,52),VIOLET);
        if(state.playing()){
            fill(WHITE);canvas.drawRoundRect(205,300,212,337,2,2,p);canvas.drawRoundRect(220,300,227,337,2,2,p);
        }else{
            fill(WHITE);Path play=new Path();play.moveTo(208,297);play.lineTo(208,337);play.lineTo(233,317);play.close();canvas.drawPath(play,p);
        }
        circle(canvas,299,318,25,Color.rgb(4,18,32),CYAN);next(canvas,299,WHITE);
        circle(canvas,380,318,30,Color.rgb(4,18,32),ORANGE);
        buttonText(canvas,380,318,"+",WHITE,39);
        iconLibrary(canvas);
        fill(Color.rgb(22,12,50));rr.set(134,358,297,407);canvas.drawRoundRect(rr,24,24,p);
        stroke(state.lyricsAvailable()?CYAN:VIOLET,2.5f);canvas.drawRoundRect(rr,24,24,p);
        text(canvas,"LYRICS",215,388,19,WHITE,true);
        iconShuffle(canvas);
        text(canvas,"✓ "+state.trackCount()+" OFFLINE",216,420,16,state.trackCount()>0?Color.rgb(134,249,65):WHITE,true);
        canvas.restore();
    }
    private void c(Canvas c,RectF box,float rad){c.drawRoundRect(box,rad,rad,p);}
    private static float distance(float x,float y,float bx,float by) {
        return (float)Math.hypot(x-bx,y-by);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
            touchX=e.getX()*432f/Math.max(1,getWidth());
            touchY=e.getY()*432f/Math.max(1,getHeight());
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP) {
            float x=e.getX()*432f/Math.max(1,getWidth());
            float y=e.getY()*432f/Math.max(1,getHeight());
            float distance=distance(x,y,touchX,touchY);
            if(distance>35){
                // The Neon face itself does not scroll; a swipe on blank space
                // opens the real, scrollable, native song library. Transport
                // controls and the Pearl keep their own hit regions.
                if(PearlTriangleMath.actionAt(touchX,touchY)==0 &&
                    Math.abs(y-touchY)>65 &&
                    Math.abs(y-touchY)>1.3f*Math.abs(x-touchX)){
                    if(y<touchY)actions.library();else actions.help();
                }
                return true;
            }
            touchX=x;touchY=y;performClick();return true;
        }
        return e.getActionMasked()!=MotionEvent.ACTION_CANCEL;
    }
    @Override public boolean performClick(){
        super.performClick();
        float x=touchX,y=touchY;
        // Seven taps = Performer; eleven = Services. The glowing Pearl logo
        // is deliberately separate from the transport buttons.
        if(distance(x,y,216,79)<34){actions.secretTap();return true;}
        if(y>=244&&y<283&&x>=67&&x<=364)actions.seek(Math.max(0,Math.min(1,(x-75)/282f)));
        else if(distance(x,y,52,318)<36)actions.volume(-1);
        else if(distance(x,y,380,318)<36)actions.volume(1);
        else if(distance(x,y,133,318)<32)actions.previous();
        else if(distance(x,y,216,318)<39)actions.playPause();
        else if(distance(x,y,299,318)<32)actions.next();
        else if(distance(x,y,93,381)<30)actions.library();
        else if(y>=355&&y<=410&&x>=131&&x<=304)actions.lyrics();
        else if(distance(x,y,338,381)<30)actions.shuffle();
        invalidate();
        return true;
    }
}
