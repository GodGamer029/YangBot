package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompositeBakeSegment extends BakeablePathSegment {

    private List<BakeablePathSegment> combinedSegments;

    public CompositeBakeSegment(List<BakeablePathSegment> segs, float arrivalSpeed, float arrivalTime) {
        super(segs.get(0).getStartSpeed(), segs.get(0).getStartBoost(), arrivalSpeed, arrivalTime);
        this.combinedSegments = segs;
        this.allowBoost = segs.get(segs.size() - 1).isAllowBoost();
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        List<Curve> curves = this.combinedSegments.stream().map(s -> s.bake(maxSamples)).collect(Collectors.toList());

        var comp = new Curve();
        comp.length = 0;
        int numPoints = 0;

        for(var c : curves){
            numPoints += c.points.size();
            comp.length += c.length;
        }
        if(this.combinedSegments.size() > 1)
            numPoints -= this.combinedSegments.size() - 1;

        comp.points = new ArrayList<>(numPoints);
        comp.tangents = new ArrayList<>(numPoints);
        comp.curvatures = new float[numPoints];
        comp.distances = new float[numPoints];
        comp.maxSpeeds = new float[numPoints];

        float[] dists = new float[curves.size()];
        for(int i = curves.size() - 2; i >= 0; i--){
            dists[i] = curves.get(i + 1).length + dists[i + 1];
        }

        int off = 0;
        for(int i = 0; i < curves.size(); i++) {
            var c = curves.get(i);
            if (i > 0) {
                var cOld = curves.get(i - 1);
                assert cOld.points.get(cOld.points.size() - 1).distance(c.points.get(0)) < 10;
                comp.points.add(cOld.points.get(cOld.points.size() - 1).add(c.points.get(0)).mul(0.5f));
                comp.tangents.add(cOld.tangents.get(cOld.tangents.size() - 1).add(c.tangents.get(0)).mul(0.5f));
                comp.distances[off] = c.distances[0] + dists[i];
                //assert c.distances[0] == dists[i - 1];
                comp.curvatures[off] = (cOld.curvatures[cOld.curvatures.length - 1] + c.curvatures[0]) / 2;
                comp.maxSpeeds[off] = (cOld.maxSpeeds[cOld.maxSpeeds.length - 1] + c.maxSpeeds[0]) / 2;
                off++;
                assert comp.points.size() == off;
            }

            int startOff = i == 0 ? 0 : 1;
            int endOff = i == curves.size() - 1 ? 0 : 1;
            for (int e = startOff; e < c.points.size() - endOff; e++) {
                comp.points.add(c.points.get(e));
                comp.tangents.add(c.tangents.get(e));
                comp.distances[e + off - startOff] = c.distances[e] + dists[i];
            }

            System.arraycopy(c.curvatures, startOff, comp.curvatures, off, c.curvatures.length - (startOff + endOff));
            System.arraycopy(c.maxSpeeds, startOff, comp.maxSpeeds, off, c.maxSpeeds.length - (startOff + endOff));
            off += c.maxSpeeds.length - (startOff + endOff);
        }
        assert comp.points.size() == comp.curvatures.length : comp.points.size() + " " + comp.curvatures.length;
        assert off == comp.curvatures.length;

        return comp;
    }
}
