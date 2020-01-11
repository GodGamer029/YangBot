package yangbot.prediction;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.BallPrediction;
import rlbot.flat.Physics;
import rlbot.flat.PredictionSlice;
import yangbot.input.BallData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.AdvancedRenderer;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class YangBallPrediction {

    public final int tickRate; // close to 60 or 120
    public final float tickFrequency; // close to 1/60 or 1/120
    public final List<YangPredictionFrame> frames;

    private YangBallPrediction(List<YangPredictionFrame> frames, float tickFrequency) {
        if (frames == null)
            frames = new ArrayList<>();
        float latencyCompensation = RLConstants.gameLatencyCompensation;
        if (latencyCompensation > 0) {
            List<YangPredictionFrame> newFrames = new ArrayList<>(frames.size());
            for (YangPredictionFrame frame : frames) {
                if (frame.relativeTime < latencyCompensation)
                    continue;

                frame.adjustForLatencyCompensation(latencyCompensation);
                newFrames.add(frame);
            }
            frames = newFrames;
        }
        frames = Collections.unmodifiableList(frames);

        this.frames = frames;
        this.tickFrequency = tickFrequency;
        this.tickRate = Math.round(1 / tickFrequency);
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
                    System.err.println("Could not get RLBot ball Prediction!");
                    throw new RuntimeException(e);
                }
        }

        throw new IllegalStateException("Ball Prediction Type '" + ballPredictionType.name() + "' not recognized");
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
            if (!frame.isPresent())
                break;
            if (Math.floor(lastAbsTime) < Math.floor(frame.get().absoluteTime)) {
                renderer.drawLine3d(color.brighter(), frame.get().ballData.position, frame.get().ballData.position.add(0, 0, 50));
            }
            lastAbsTime = frame.get().absoluteTime;
            time = frame.get().relativeTime;
            ImmutableBallData ball = frame.get().ballData;
            if (lastPos.distance(ball.position) < 50)
                continue;
            renderer.drawLine3d(color, lastPos, ball.position);
            lastPos = ball.position;

            if (ball.makeMutable().isInAnyGoal()) {
                renderer.drawCentered3dCube(color.brighter().brighter(), ball.position, 50);
                renderer.drawString3d("Goal!", Color.WHITE, ball.position.add(0, 0, 150), 1, 1);
                break;
            }
        }
    }

    public float relativeTimeOfLastFrame() {
        if (this.frames.size() == 0)
            return -1;
        return this.frames.get(this.frames.size() - 1).relativeTime;
    }

    public YangBallPrediction trim(float relativeStartTime, float relativeEndTime) {
        if (relativeEndTime < relativeStartTime)
            throw new IllegalArgumentException("Relative end time smaller than relative start time");
        if (relativeEndTime - relativeStartTime == 0)
            return YangBallPrediction.empty();

        return new YangBallPrediction(this.getFramesBetweenRelative(relativeStartTime, relativeEndTime), this.tickFrequency);
    }

    public Optional<YangPredictionFrame> getFrameAtRelativeTime(float relativeTime) {
        return this.frames
                .stream()
                .filter((f) -> f.relativeTime >= relativeTime)
                .findFirst();
    }

    public Optional<YangPredictionFrame> getFrameAfterRelativeTime(float relativeTime) {
        return this.frames
                .stream()
                .filter((f) -> f.relativeTime > relativeTime)
                .findFirst();
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
