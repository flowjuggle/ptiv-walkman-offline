package live.ptiv.watch;

/** Pure geometry for the tappable Pearl/triangle; no Android dependencies. */
final class PearlTriangleMath {
    static final float AX=216f, AY=23f, BX=166f, BY=107f, CX=266f, CY=107f;
    static final int LEFT=0, RIGHT=1, BASE=2, NONE=-1;
    private PearlTriangleMath(){}

    static float distance(float x,float y,float x2,float y2){
        return (float)Math.hypot(x-x2,y-y2);
    }

    static float segmentDistance(float x,float y,
                                 float ax,float ay,float bx,float by) {
        float dx=bx-ax,dy=by-ay;
        float len2=dx*dx+dy*dy;
        float t=len2==0?0:((x-ax)*dx+(y-ay)*dy)/len2;
        t=Math.max(0,Math.min(1,t));
        return distance(x,y,ax+t*dx,ay+t*dy);
    }

    static int closestSide(float x,float y,float tolerance){
        float l=segmentDistance(x,y,AX,AY,BX,BY);
        float r=segmentDistance(x,y,AX,AY,CX,CY);
        float b=segmentDistance(x,y,BX,BY,CX,CY);
        float best=Math.min(l,Math.min(r,b));
        if(best>tolerance)return NONE;
        if(l<=r&&l<=b)return LEFT;
        if(r<=b)return RIGHT;
        return BASE;
    }

    static boolean inside(float x,float y){
        // Barycentric signs; <=2px tolerance for an accurate finger landing.
        float d1=(x-BX)*(AY-BY)-(AX-BX)*(y-BY);
        float d2=(x-CX)*(BY-CY)-(BX-CX)*(y-CY);
        float d3=(x-AX)*(CY-AY)-(CX-AX)*(y-AY);
        boolean neg=d1<0||d2<0||d3<0;
        boolean pos=d1>0||d2>0||d3>0;
        return !(neg&&pos);
    }

    /**
     * Trace requires three distinct physical sides, enough traveled distance,
     * and a deliberate slow-enough gesture. Never trigger by a tap or a swipe.
     */
    static boolean completeOrbit(int sideMask,float travel,long gestureMs){
        return (sideMask&7)==7 && travel>=135f && gestureMs>=350L && gestureMs<=12000L;
    }

    static int actionAt(float x,float y) {
        if(distance(x,y,52,318)<38)return 1; // volume down
        if(distance(x,y,133,318)<36)return 2; // previous
        if(distance(x,y,216,318)<43)return 3; // play/pause
        if(distance(x,y,299,318)<36)return 4; // next
        if(distance(x,y,380,318)<38)return 5; // volume up
        if(distance(x,y,93,381)<32)return 6; // library
        if(y>=351&&y<=412&&x>=129&&x<=307)return 7; // lyrics
        if(distance(x,y,338,381)<33)return 8; // shuffle
        if(y>=246&&y<=282&&x>=67&&x<=365)return 9; // scrub
        if(y>=194&&y<=244)return 10; // title and clock
        return 0; // general help
    }
}
