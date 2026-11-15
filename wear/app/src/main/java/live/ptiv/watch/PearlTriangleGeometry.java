package live.ptiv.watch;

/**
 * Pure geometry behind the two-Pearl triangle navigation. No Android API,
 * audio, permissions or background service is involved.
 */
final class PearlTriangleGeometry {
    static final float CX=216f,CY=79f;
    static final float AX=216f,AY=23f;
    static final float BX=166f,BY=107f;
    static final float DX=266f,DY=107f;
    static final int LEFT=0,RIGHT=1,BASE=2;

    private PearlTriangleGeometry(){}

    static float distance(float x,float y,float a,float b){
        return (float)Math.hypot(x-a,y-b);
    }
    static float segmentDistance(float x,float y,float ax,float ay,float bx,float by){
        float vx=bx-ax,vy=by-ay,wx=x-ax,wy=y-ay;
        float t=Math.max(0f,Math.min(1f,(wx*vx+wy*vy)/(vx*vx+vy*vy)));
        return distance(x,y,ax+t*vx,ay+t*vy);
    }
    static int nearestSide(float x,float y,float tolerance){
        float left=segmentDistance(x,y,AX,AY,BX,BY);
        float right=segmentDistance(x,y,AX,AY,DX,DY);
        float base=segmentDistance(x,y,BX,BY,DX,DY);
        float min=Math.min(left,Math.min(right,base));
        if(min>tolerance)return -1;
        if(min==left)return LEFT;
        if(min==right)return RIGHT;
        return BASE;
    }
    private static float sign(float x,float y,float ax,float ay,float bx,float by){
        return (x-bx)*(ay-by)-(ax-bx)*(y-by);
    }
    static boolean inside(float x,float y){
        float a=sign(x,y,AX,AY,BX,BY);
        float b=sign(x,y,BX,BY,DX,DY);
        float c=sign(x,y,DX,DY,AX,AY);
        return !(a<0||b<0||c<0) || !(a>0||b>0||c>0);
    }

    /**
     * Requires three distinct triangle sides, a substantial travel distance,
     * and a return close to the first visited side. A short drag cannot
     * accidentally unlock the companion pearl.
     */
    static final class Circuit {
        private int mask=0,distinctTransitions=0,lastSide=-1;
        private float firstX,firstY,lastX,lastY,total=0;
        private boolean hasLast=false,hasStart=false,finished=false;
        void begin(){
            mask=0;distinctTransitions=0;lastSide=-1;total=0;
            hasLast=false;hasStart=false;finished=false;
        }
        boolean add(float x,float y){
            if(finished)return true;
            if(hasLast)total+=Math.min(110f,distance(x,y,lastX,lastY));
            hasLast=true;lastX=x;lastY=y;
            int edge=nearestSide(x,y,18f);
            if(edge>=0){
                if(!hasStart){firstX=x;firstY=y;hasStart=true;}
                mask|=1<<edge;
                if(lastSide>=0&&lastSide!=edge)distinctTransitions++;
                lastSide=edge;
            }
            finished=mask==7 && distinctTransitions>=3 && total>=190f
                && hasStart && distance(x,y,firstX,firstY)<=31f;
            return finished;
        }
        int visitedMask(){return mask;}
        float tracedDistance(){return total;}
        boolean complete(){return finished;}
    }
}
