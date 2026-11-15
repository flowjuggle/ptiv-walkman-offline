package live.ptiv.watch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Transparent touch overlay only for the Pearl triangle and its helper Pearl.
 *
 * All normal Neon transport/volume/lyrics/shuffle controls remain in the view
 * underneath. On DOWN outside Pearl hit regions, returns false so touches pass
 * directly through to the original working NeonSkinView.
 *
 * Nothing records audio. A deliberate two-Pearl gesture asks WatchActivity to
 * show voice confirmation; voice recording begins only after the user agrees.
 */
final class PearlGestureOverlay extends View {
    interface Actions {
        void pearlTap();
        void menuSide(int menuPage,int side);
        void orbitComplete();
        void inspectAt(float x,float y);
        void requestVoice();
        void showHelp();
        void openLibrary();
    }

    private static final int GOLD=Color.rgb(255,167,34);
    private static final int TEAL=Color.rgb(27,234,236);
    private static final int LIGHT=Color.rgb(247,251,249);
    private static final int PURPLE=Color.rgb(186,93,255);
    private static final long HOLD_MS=10000L;

    private final Actions actions;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean primary=false, helperDrag=false, moving=false, helperMoving=false;
    private boolean longHeld=false, helperCreated=false;
    private float helperX=250f,helperY=94f;
    private float downX,downY,lastX,lastY,travel;
    private long began;
    private int menu=0,sideMask=0,activeSide=PearlTriangleMath.NONE;

    private final Runnable longPress=new Runnable(){
        @Override public void run(){
            if(primary&&!moving){
                menu=(menu+1)%2;
                longHeld=true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }
        }
    };

    PearlGestureOverlay(Context context,Actions actions){
        super(context);
        this.actions=actions;
        setWillNotDraw(false);
        setContentDescription("Interactive Pearl navigation. Drag to the triangle edges for menus. Hold Pearl ten seconds to switch menu colors. Trace all three triangle sides to add a movable second Pearl. Drag the second Pearl onto a control for contextual help, or move it inside the triangle for opt-in voice.");
    }

    private static float clamp(float x,float lo,float hi){
        return Math.max(lo,Math.min(hi,x));
    }
    private float X(MotionEvent e){
        return 432f*e.getX()/Math.max(1,getWidth());
    }
    private float Y(MotionEvent e){
        return 432f*e.getY()/Math.max(1,getHeight());
    }
    private void paint(int color,float stroke){
        p.reset();p.setAntiAlias(true);p.setColor(color);
        p.setStyle(stroke<=0?Paint.Style.FILL:Paint.Style.STROKE);
        if(stroke>0){p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);}
    }
    private void lettering(Canvas c,String s,float x,float y,int color,int size){
        paint(color,0);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);
        c.drawText(s,x,y,p);
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(getWidth()==0||getHeight()==0)return;
        canvas.save();
        canvas.scale(getWidth()/432f,getHeight()/432f);
        Path tri=new Path();
        tri.moveTo(216,23);tri.lineTo(166,107);tri.lineTo(266,107);tri.close();

        // A clear colored rim lets the user know which menu is active.
        int edge=menu==0?GOLD:TEAL;
        paint(Color.argb(165,12,10,30),12);canvas.drawPath(tri,p);
        paint(edge,4);canvas.drawPath(tri,p);
        if(activeSide!=PearlTriangleMath.NONE){
            paint(LIGHT,6);
            if(activeSide==PearlTriangleMath.LEFT)
                canvas.drawLine(216,23,166,107,p);
            else if(activeSide==PearlTriangleMath.RIGHT)
                canvas.drawLine(216,23,266,107,p);
            else canvas.drawLine(166,107,266,107,p);
        }

        // Replace the stock Pearl with a high-contrast iridescent icon.
        paint(Color.rgb(8,17,47),0);canvas.drawCircle(216,79,29,p);
        p.setShader(new RadialGradient(207,70,27,
            new int[]{Color.rgb(255,255,240),Color.rgb(119,235,228),
                      menu==0?PURPLE:TEAL,Color.rgb(23,31,80)},
            null,Shader.TileMode.CLAMP));
        canvas.drawCircle(216,79,25,p);p.setShader(null);
        paint(LIGHT,2);canvas.drawCircle(216,79,25,p);

        lettering(canvas,menu==0?"SONGS":"SEND",136,53,edge,12);
        lettering(canvas,menu==0?"CONTROL":"APPS",299,53,edge,12);
        lettering(canvas,menu==0?"INFO":"VOICE",216,129,edge,12);

        if(primary&&!moving&&!longHeld){
            float fraction=clamp((SystemClock.uptimeMillis()-began)/(float)HOLD_MS,0,1);
            paint(TEAL,3);
            canvas.drawArc(185,48,247,110,-90,360*fraction,false,p);
            postInvalidateDelayed(130);
        }
        if(helperCreated){
            paint(Color.argb(85,24,252,244),0);
            canvas.drawCircle(helperX,helperY,26,p);
            p.setShader(new RadialGradient(helperX-5,helperY-5,20,
                new int[]{LIGHT,TEAL,Color.rgb(35,84,142)},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(helperX,helperY,16,p);
            p.setShader(null);
            paint(LIGHT,2);canvas.drawCircle(helperX,helperY,16,p);
            lettering(canvas,"?",helperX,helperY+6,Color.rgb(8,30,56),17);
        }
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        float x=X(event), y=Y(event);
        if(action==MotionEvent.ACTION_DOWN){
            primary=false;helperDrag=false;moving=false;helperMoving=false;
            longHeld=false;sideMask=0;activeSide=PearlTriangleMath.NONE;
            downX=x;downY=y;lastX=x;lastY=y;travel=0;
            began=SystemClock.uptimeMillis();
            // Helper Pearl takes precedence if it is distinct from the center.
            if(helperCreated && PearlTriangleMath.distance(x,y,helperX,helperY)<23
                  && PearlTriangleMath.distance(x,y,216,79)>=25){
                helperDrag=true;return true;
            }
            if(PearlTriangleMath.distance(x,y,216,79)<29){
                primary=true;
                main.removeCallbacks(longPress);
                main.postDelayed(longPress,HOLD_MS);
                invalidate();return true;
            }
            // Return false: all normal playback buttons keep their own touch handling.
            return false;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(primary){
                travel+=PearlTriangleMath.distance(x,y,lastX,lastY);
                if(PearlTriangleMath.distance(x,y,downX,downY)>12){
                    moving=true;main.removeCallbacks(longPress);
                    int side=PearlTriangleMath.closestSide(x,y,17);
                    if(side!=PearlTriangleMath.NONE){
                        activeSide=side;
                        sideMask|=1<<side;
                    }
                }
                lastX=x;lastY=y;invalidate();return true;
            }
            if(helperDrag){
                helperX=clamp(x,22,410);helperY=clamp(y,22,410);
                helperMoving|=PearlTriangleMath.distance(x,y,downX,downY)>24;
                invalidate();return true;
            }
            return false;
        }
        if(action==MotionEvent.ACTION_UP){
            main.removeCallbacks(longPress);
            if(primary){
                primary=false;activeSide=PearlTriangleMath.NONE;
                long elapsed=SystemClock.uptimeMillis()-began;
                if(longHeld){
                    // A 10-second hold toggles the second menu; no accidental play.
                }else if(moving){
                    if(PearlTriangleMath.completeOrbit(sideMask,travel,elapsed)){
                        helperCreated=true;
                        helperX=250;helperY=94;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        actions.orbitComplete();
                    }else{
                        int side=PearlTriangleMath.closestSide(x,y,22);
                        if(side!=PearlTriangleMath.NONE) actions.menuSide(menu,side);
                    }
                }else if(elapsed<750){
                    actions.pearlTap();
                }
                invalidate();return true;
            }
            if(helperDrag){
                helperDrag=false;
                if(helperMoving && PearlTriangleMath.inside(helperX,helperY))
                    actions.requestVoice();
                else
                    actions.inspectAt(helperX,helperY);
                invalidate();return true;
            }
            return false;
        }
        if(action==MotionEvent.ACTION_CANCEL){
            main.removeCallbacks(longPress);
            primary=false;helperDrag=false;
            activeSide=PearlTriangleMath.NONE;
            invalidate();return true;
        }
        return false;
    }

    @Override protected void onDetachedFromWindow(){
        main.removeCallbacks(longPress);
        super.onDetachedFromWindow();
    }
}
