package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GazeMathTest {

    @Test
    void measuresTheAngleToTheTarget() {
        assertEquals(0.0, GazeMath.angleTo(0, 0, 1, 0, 0, 0, 0, 0, 10), 1.0E-9);
        assertEquals(90.0, GazeMath.angleTo(0, 0, 1, 0, 0, 0, 10, 0, 0), 1.0E-9);
        assertEquals(180.0, GazeMath.angleTo(0, 0, 1, 0, 0, 0, 0, 0, -10), 1.0E-9);
    }

    @Test
    void phantomsSpawnBehindOrInThePeripheryNeverInFront() {
        for (float yaw = -180; yaw <= 180; yaw += 15) {
            double lookX = -Math.sin(Math.toRadians(yaw));
            double lookZ = Math.cos(Math.toRadians(yaw));
            for (double side = 0; side <= 1.0; side += 0.25) {
                for (boolean left : new boolean[] {true, false}) {
                    double[] offset = GazeMath.offset(yaw, side, left, 10);
                    double angle = GazeMath.angleTo(lookX, 0, lookZ, 0, 0, 0, offset[0], 0, offset[1]);
                    assertTrue(angle >= 69.0, () -> "phantom inside view cone: " + angle);
                    assertEquals(10.0, Math.hypot(offset[0], offset[1]), 1.0E-9);
                }
            }
        }
        double[] straightBehind = GazeMath.offset(0f, 0, true, 5);
        assertEquals(0.0, straightBehind[0], 1.0E-9);
        assertEquals(-5.0, straightBehind[1], 1.0E-9);
    }

    @Test
    void yawFacesTheTarget() {
        assertEquals(0.0f, GazeMath.yawTowards(0, 0, 0, 5), 1.0E-4f);
        assertEquals(90.0f, GazeMath.yawTowards(0, 0, -5, 0), 1.0E-4f);
        assertEquals(-90.0f, GazeMath.yawTowards(0, 0, 5, 0), 1.0E-4f);
    }
}
