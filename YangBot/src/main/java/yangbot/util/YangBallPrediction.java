package yangbot.util;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.BallPrediction;
import rlbot.flat.Physics;
import rlbot.flat.PredictionSlice;
import yangbot.MainClass;
import yangbot.input.BallData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class YangBallPrediction {

    public final int tickRate; // close to 60 or 120
    public final float tickFrequency; // close to 1/60 or 1/120
    public final List<YangPredictionFrame> frames;

    private YangBallPrediction(List<YangPredictionFrame> frames, float tickFrequency) {
        if (frames == null)
            frames = new ArrayList<>();
        frames = Collections.unmodifiableList(frames);

        this.frames = frames;
        this.tickFrequency = tickFrequency;
        this.tickRate = Math.round(1 / tickFrequency);
    }

    public static YangBallPrediction merge(YangBallPrediction one, YangBallPrediction two) {
        if (one.frames.size() == 0)
            return two;
        if (two.frames.size() == 0)
            return one;
        List<YangPredictionFrame> newFrames = new ArrayList<>(Math.max(one.frames.size(), two.frames.size()));

        float targetDt = Math.min(one.tickFrequency, two.tickFrequency);
        int targetRate = Math.round(1 / targetDt);
        assert targetRate > 50 : targetDt + " " + targetRate;

        Function<YangPredictionFrame, Integer> frameToTick = (YangPredictionFrame f) -> Math.round(f.relativeTime / targetDt);

        int i1 = 0, i2 = 0;
        int f1 = frameToTick.apply(one.frames.get(0)), f2 = frameToTick.apply(two.frames.get(0));

        boolean update1 = false, update2 = false;
        while (i1 < one.frames.size() || i2 < two.frames.size()) {
            if (f1 == f2) { // Same frame twice, advance both
                newFrames.add(one.frames.get(i1));
                update1 = update2 = true;
            } else if (f1 < f2) { // Work on f1 first
                newFrames.add(one.frames.get(i1));
                update1 = true;
            } else {// Work on f2 first
                newFrames.add(two.frames.get(i2));
                update2 = true;
            }

            if (update1) {
                update1 = false;
                i1++;
                if (i1 >= one.frames.size())
                    f1 = i1 = Integer.MAX_VALUE;
                else
                    f1 = frameToTick.apply(one.frames.get(i1));
            }
            if (update2) {
                update2 = false;
                i2++;
                if (i2 >= two.frames.size())
                    f2 = i2 = Integer.MAX_VALUE;
                else
                    f2 = frameToTick.apply(two.frames.get(i2));
            }
        }
        return YangBallPrediction.from(newFrames, 1f / targetRate);
    }

    public static YangBallPrediction from(List<YangPredictionFrame> frames, float tickFrequency) {
        return new YangBallPrediction(frames, tickFrequency);
    }

    public static YangBallPrediction empty() {
        return new YangBallPrediction(null, RLConstants.tickFrequency);
    }

    private static YangBallPrediction from(BallPrediction ballPrediction) {
        assert ballPrediction.slicesLength() > 0 : "RLBot Ball Prediction has no frames";

        List<YangPredictionFrame> frames = new ArrayList<>(ballPrediction.slicesLength());

        float startTime = ballPrediction.slices(0).gameSeconds();
        float lastTime = startTime;
        float averageDt = 0;

        for (int i = 0; i < ballPrediction.slicesLength(); i++) {
            PredictionSlice slice = ballPrediction.slices(i);
            frames.add(new YangPredictionFrame(slice.gameSeconds() - startTime, slice));

            averageDt += slice.gameSeconds() - lastTime;
            lastTime = slice.gameSeconds();
        }

        averageDt /= ballPrediction.slicesLength();

        return new YangBallPrediction(frames, averageDt);
    }

    public static YangBallPrediction get() {
        return get(BallPredictionType.RLBOT);
    }

    public static YangBallPrediction get(BallPredictionType ballPredictionType) {
        switch (ballPredictionType) {
            case RLBOT:
                try {
                    return YangBallPrediction.from(RLBotDll.getBallPrediction());
                } catch (RLBotInterfaceException e) {
                    if (MainClass.BOT_TYPE != MainClass.BotType.UNKNOWN)
                        System.err.println("Could not get RLBot ball Prediction!");
                    return YangBallPrediction.empty();
                }
        }

        throw new IllegalStateException("Ball Prediction Type '" + ballPredictionType.name() + "' not recognized");
    }

    public boolean isEmpty() {
        return this.frames.isEmpty();
    }

    public void draw(AdvancedRenderer renderer, Color color, float length) {
        if (this.frames.size() == 0)
            return;
        if (length <= 0)
            length = this.relativeTimeOfLastFrame();

        float time = 0;
        float lastAbsTime = this.frames.get(0).absoluteTime;
        Vector3 lastPos = this.frames.get(0).ballData.position;
        while (time < length) {
            Optional<YangPredictionFrame> frame = this.getFrameAfterRelativeTime(time);
            if (frame.isEmpty())
                break;

            if (Math.floor(lastAbsTime) < Math.floor(frame.get().absoluteTime)) {
                renderer.drawLine3d(color.brighter(), frame.get().ballData.position, frame.get().ballData.position.add(0, 0, 50));
            }
            lastAbsTime = frame.get().absoluteTime;
            time = frame.get().relativeTime;
            ImmutableBallData ball = frame.get().ballData;

            if (ball.makeMutable().isInAnyGoal()) {
                renderer.drawCentered3dCube(color.brighter().brighter(), ball.position, 50);
                renderer.drawString3d(String.format("Goal! (%.1f)", time), Color.WHITE, ball.position.add(0, 0, 150), 1, 1);
                renderer.drawLine3d(color, lastPos, ball.position);
                break;
            }

            if (lastPos.distance(ball.position) < 50)
                continue;
            renderer.drawLine3d(color, lastPos, ball.position);
            lastPos = ball.position;
        }
    }

    public YangPredictionFrame lastFrame() {
        assert this.frames.size() > 0;
        return this.frames.get(this.frames.size() - 1);
    }

    public YangPredictionFrame firstFrame() {
        assert this.frames.size() > 0;
        return this.frames.get(0);
    }

    public float relativeTimeOfLastFrame() {
        if (this.frames.size() == 0)
            return -1;
        return this.lastFrame().relativeTime;
    }

    public YangBallPrediction trim(float relativeStartTime, float relativeEndTime) {
        if (relativeEndTime < relativeStartTime)
            throw new IllegalArgumentException("Relative end time smaller than relative start time");
        if (relativeEndTime - relativeStartTime == 0)
            return YangBallPrediction.empty();

        return new YangBallPrediction(this.getFramesBetweenRelative(relativeStartTime, relativeEndTime), this.tickFrequency);
    }

    // Returns first index at or after relativeTime
    private int binarySearchRelativeTime(float relative){
        if(this.frames.size() == 0)
            return 0;
        assert relative < this.relativeTimeOfLastFrame();
        int left = 0, right = this.frames.size();
        int mid;
        int timeout = 0;
        int candidate = -1;
        while(left < right){
            assert timeout < 200;
            timeout++;
            mid = (left + right) / 2;
            var sample = this.frames.get(mid);
            if(sample.relativeTime >= relative){
                right = mid;
                candidate = mid;
            }
            else
                left = mid + 1;
        }
        assert candidate != -1;
        return candidate;
    }

    public Optional<YangPredictionFrame> getFrameAtRelativeTime(float relativeTime) {
        if (this.isEmpty() || this.lastFrame().relativeTime < relativeTime)
            return Optional.empty();
        if (relativeTime == 0)
            return Optional.of(this.firstFrame());
        assert relativeTime > 0;

        /*assert false: "TODO";
        // TODO: binary search
        int first = 0;
        int last = this.frames.size() - 1;
        int mid = (first + last)/2;
        while( first <= last ){
            if ( frames.get(mid).relativeTime < relativeTime ){
                first = mid + 1;
            }else if ( arr[mid] == key ){
                System.out.println("Element is found at index: " + mid);
                break;
            }else{
                last = mid - 1;
            }
            mid = (first + last)/2;
        }*/

        return this.frames
                .stream()
                .filter((f) -> f.relativeTime >= relativeTime)
                .findFirst();
    }

    public Optional<YangPredictionFrame> getFrameAfterRelativeTime(float relativeTime) {
        for(int i = this.binarySearchRelativeTime(relativeTime); i < this.frames.size(); i++){
            if(this.frames.get(i).relativeTime > relativeTime)
                return Optional.of(this.frames.get(i));
        }
        return Optional.empty();
    }

    public Optional<YangPredictionFrame> getFrameAtAbsoluteTime(float absolute) {
        return this.frames
                .stream()
                .filter((f) -> f.absoluteTime >= absolute)
                .findFirst();
    }

    public List<YangPredictionFrame> getFramesBeforeRelative(float relativeTime) {
        return this.frames
                .stream()
                .filter((f) -> f.relativeTime < relativeTime)
                .collect(Collectors.toList());
    }

    public List<YangPredictionFrame> getFramesBeforeAbsolute(float absoluteTime) {
        return this.frames
                .stream()
                .filter((f) -> f.absoluteTime < absoluteTime)
                .collect(Collectors.toList());
    }

    public YangBallPrediction getBeforeRelative(float relativeTime) {
        return new YangBallPrediction(getFramesBeforeRelative(relativeTime), this.tickFrequency);
    }

    public List<YangPredictionFrame> getFramesAfterRelative(float relativeTime) {
        return this.frames
                .stream()
                .filter((f) -> f.relativeTime > relativeTime)
                .collect(Collectors.toList());
    }

    public List<YangPredictionFrame> getFramesBetweenRelative(float start, float end) {
        return this.frames
                .stream()
                .filter((f) -> f.relativeTime > start && f.relativeTime < end)
                .collect(Collectors.toList());
    }

    public boolean hasFrames() {
        return this.frames.size() > 0;
    }

    enum BallPredictionType {
        RLBOT
    }

    public static class YangPredictionFrame {
        public final float absoluteTime;
        public final ImmutableBallData ballData;
        public float relativeTime;

        public YangPredictionFrame(float absoluteTime, float relativeTime, BallData ballData) {
            this.absoluteTime = absoluteTime;
            this.relativeTime = relativeTime;
            this.ballData = new ImmutableBallData(ballData);
        }

        public YangPredictionFrame(float absoluteTime, float relativeTime, ImmutableBallData ballData) {
            this.absoluteTime = absoluteTime;
            this.relativeTime = relativeTime;
            this.ballData = ballData;
        }

        public YangPredictionFrame(float relativeTime, PredictionSlice predictionSlice) {
            this.relativeTime = relativeTime;
            this.absoluteTime = predictionSlice.gameSeconds();
            Physics physics = predictionSlice.physics();
            this.ballData = new ImmutableBallData(physics, this.absoluteTime);
        }

        public void adjustForLatencyCompensation(float offset) {
            relativeTime -= offset;
        }
    }
}
