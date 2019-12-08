package yangbot.prediction;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.BallPrediction;
import rlbot.flat.Physics;
import rlbot.flat.PredictionSlice;
import yangbot.input.BallData;
import yangbot.input.RLConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class YangBallPrediction {

    private static final BallPredictionType ballPredictionType = BallPredictionType.RLBOT;
    public final int tickRate; // close to 60 or 120
    public final float tickFrequency; // close to 1/60 or 1/120
    public final List<YangPredictionFrame> frames;

    private YangBallPrediction(List<YangPredictionFrame> frames, float tickFrequency) {
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

    public Optional<YangPredictionFrame> getFrameAtRelativeTime(float relativeTime) {
        for (YangPredictionFrame frame : frames) {
            if (frame.relativeTime >= relativeTime)
                return Optional.of(frame);
        }
        return Optional.empty();
    }

    public List<YangPredictionFrame> getFramesBeforeRelative(float relativeTime) {
        return this.frames
                .stream()
                .filter((f) -> f.relativeTime < relativeTime)
                .collect(Collectors.toList());
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
        public final BallData ballData;
        public float relativeTime;

        public YangPredictionFrame(float absoluteTime, float relativeTime, BallData ballData) {
            this.absoluteTime = absoluteTime;
            this.relativeTime = relativeTime;
            this.ballData = ballData;
        }

        public YangPredictionFrame(float relativeTime, PredictionSlice predictionSlice) {
            this.relativeTime = relativeTime;
            this.absoluteTime = predictionSlice.gameSeconds();
            Physics physics = predictionSlice.physics();
            this.ballData = new BallData(physics);
        }

        public void adjustForLatencyCompensation(float offset) {
            relativeTime -= offset;
        }
    }
}
