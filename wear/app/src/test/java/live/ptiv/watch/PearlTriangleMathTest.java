package live.ptiv.watch;

import org.junit.Test;
import static org.junit.Assert.*;

public class PearlTriangleMathTest {
    @Test public void insideTriangleIsGeometricallyCorrect(){
        assertTrue(PearlTriangleMath.inside(216,79));
        assertTrue(PearlTriangleMath.inside(216,105));
        assertTrue(PearlTriangleMath.inside(250,98));
        assertFalse(PearlTriangleMath.inside(216,152));
        assertFalse(PearlTriangleMath.inside(52,318));
        assertFalse(PearlTriangleMath.inside(310,70));
    }

    @Test public void threeSidesRecognizedIndependently(){
        assertEquals(PearlTriangleMath.LEFT, PearlTriangleMath.closestSide(189,68,10));
        assertEquals(PearlTriangleMath.RIGHT, PearlTriangleMath.closestSide(243,68,10));
        assertEquals(PearlTriangleMath.BASE, PearlTriangleMath.closestSide(216,107,10));
        assertEquals(PearlTriangleMath.NONE, PearlTriangleMath.closestSide(216,79,12));
    }

    @Test public void orbitNeedsAllThreeSidesTravelAndTime(){
        assertTrue(PearlTriangleMath.completeOrbit(7,180,1200));
        assertFalse(PearlTriangleMath.completeOrbit(3,180,1200));
        assertFalse(PearlTriangleMath.completeOrbit(7,50,1200));
        assertFalse(PearlTriangleMath.completeOrbit(7,180,200));
        assertFalse(PearlTriangleMath.completeOrbit(7,180,14000));
    }

    @Test public void existingControlsStillHaveDedicatedHitZones(){
        assertEquals(1,PearlTriangleMath.actionAt(52,318));
        assertEquals(2,PearlTriangleMath.actionAt(133,318));
        assertEquals(3,PearlTriangleMath.actionAt(216,318));
        assertEquals(4,PearlTriangleMath.actionAt(299,318));
        assertEquals(5,PearlTriangleMath.actionAt(380,318));
        assertEquals(6,PearlTriangleMath.actionAt(93,381));
        assertEquals(7,PearlTriangleMath.actionAt(216,381));
        assertEquals(8,PearlTriangleMath.actionAt(338,381));
        assertEquals(9,PearlTriangleMath.actionAt(216,261));
        assertEquals(10,PearlTriangleMath.actionAt(216,218));
        assertEquals(0,PearlTriangleMath.actionAt(55,175));
    }
}
