package yangbot.util.lut;

import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;


public class PowerslideUtil {

    public static PowerslideEntry getPowerslide(float startSpeed, Vector2 startPosition, Vector2 startTangent, Vector2 endTangent) {

        final var coolLut = LutManager.get().getDriftLut();
        assert coolLut != null : "Call loadLut() on the MainClass first!";

        final var keyToIndex = coolLut.getKeyToIndexFunction();
        assert keyToIndex != null;
        final var speedHelper = keyToIndex.getValueHelper1();
        final var rotationHelper = keyToIndex.getValueHelper2();

        final float startAngle = (float) (startTangent.angleBetween(endTangent) * (180f / Math.PI));

        final float angleIndex = rotationHelper.getFloatIndexForValue(Math.abs(startAngle));
        final float speedIndex = speedHelper.getFloatIndexForValue(startSpeed);

        float interpolatedX = InterpolationUtil.bilinear((i1, i2) -> coolLut.getWithIndex(keyToIndex.i2ToIndex(i1, i2)).finalPos.x, speedIndex, angleIndex);

        float interpolatedY = InterpolationUtil.bilinear((i1, i2) -> coolLut.getWithIndex(keyToIndex.i2ToIndex(i1, i2)).finalPos.y, speedIndex, angleIndex);
        interpolatedY *= Math.signum(startAngle); // Flip right component if necessary

        float interpolatedSpeed = InterpolationUtil.bilinear((i1, i2) -> coolLut.getWithIndex(keyToIndex.i2ToIndex(i1, i2)).finalSpeed, speedIndex, angleIndex);
        float interpolatedTime = InterpolationUtil.bilinear((i1, i2) -> coolLut.getWithIndex(keyToIndex.i2ToIndex(i1, i2)).time, speedIndex, angleIndex);

        var startOrient = new Matrix2x2(startTangent, startTangent.cross());
        // I messed up the dot product while making the lut, so this code will have to live with that now, because im not gonna regenerate the LUT again
        var endPos = startOrient.dot(new Vector2(interpolatedX, interpolatedY)).add(startPosition);

        return new PowerslideEntry(interpolatedTime, interpolatedSpeed, endPos);
    }

}
