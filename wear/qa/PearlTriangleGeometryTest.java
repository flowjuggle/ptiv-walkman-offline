package live.ptiv.watch;

final class PearlTriangleGeometryTest {
    private static void check(boolean result,String message){
        if(!result)throw new AssertionError(message);
    }
    public static void main(String[] args){
        check(PearlTriangleGeometry.inside(216,79),"anchored pearl must be inside");
        check(PearlTriangleGeometry.inside(216,104),"base interior must be inside");
        check(!PearlTriangleGeometry.inside(216,165),"outside cannot trigger voice");
        check(!PearlTriangleGeometry.inside(52,318),"volume tap cannot trigger voice");
        check(PearlTriangleGeometry.nearestSide(190,65,18)==0,"left side maps library");
        check(PearlTriangleGeometry.nearestSide(240,65,18)==1,"right side maps lyrics");
        check(PearlTriangleGeometry.nearestSide(216,106,18)==2,"bottom side maps controls");
        check(PearlTriangleGeometry.nearestSide(216,79,18)==-1,"center is not a side");
        PearlTriangleGeometry.Circuit c=new PearlTriangleGeometry.Circuit();
        c.begin();
        check(!c.add(190,65),"one touch cannot unlock");
        check(!c.add(216,23),"half triangle cannot unlock");
        check(!c.add(240,65),"two sides cannot unlock");
        check(!c.add(266,107),"two vertices cannot unlock");
        check(!c.add(216,107),"third side alone cannot unlock yet");
        check(!c.add(166,107),"large trace must return to start");
        check(c.add(190,65),"closed orbit around all three sides must unlock");
        check(c.visitedMask()==7,"must see all sides");
        check(c.complete(),"circuit must remain unlocked until reset");
        c.begin();
        check(!c.complete(),"reset clears unlocked state");
        for(int i=0;i<20;i++)check(!c.add(200,65+i*.1f),"stationary jitter never unlocks");
        System.out.println("PASS PearlTriangleGeometry 14 grouped checks: side menus, voice region, 3-edge orbit, reset and jitter guards");
    }
}
