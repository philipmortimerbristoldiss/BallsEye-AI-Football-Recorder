package com.dji.sdk.sample.demo.rid;


import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Class used to store the final state data of a single frame
public class FinalFrameState {
    private static final int BALL_CLS_IDX = 0;
    public final int frameId;
    public static enum GimbalRotState {LEFT, STILL, RIGHT};
    public final GimbalRotState rotState;
    public final float rotationAngle;
    private static final float MIN_ROT = -110.1F;
    private static final float MAX_ROT = 90.3F;
    public final float scaledRotAngle;
    public final Optional<BallPoint> ballPoint;
    public final long timeInit;

    FinalFrameState(GimbalRotState rotState, float rotationAngle, List<BoundingBox> boundingBoxes, int id) {
        this.rotState = rotState;
        this.rotationAngle = rotationAngle;
        this.scaledRotAngle = (rotationAngle - MIN_ROT) / (MAX_ROT - MIN_ROT);
        ballPoint = getBallPoint(boundingBoxes);
        frameId = id;
        timeInit = System.currentTimeMillis();
    }

    private static Optional<BallPoint> getBallPoint(List<BoundingBox> boundingBoxes) {
        if(boundingBoxes.isEmpty()) return Optional.empty();
        else{
            List<BoundingBox> ballList = boundingBoxes.stream()
                    .filter(b -> b.getCls() == BALL_CLS_IDX).sorted((a, b) -> {
                        float a_area = (a.getX2() - a.getX1()) * (a.getY2() - a.getY1());
                        float b_area = (b.getX2() - b.getX1()) * (b.getY2() - b.getY1());
                        return Float.compare(a_area, b_area);
                    }).collect(Collectors.toList());
            if (!ballList.isEmpty()) {
                return Optional.of(new BallPoint(ballList.get(ballList.size() / 2)));
            }
        }
        return Optional.empty();
    }

    public static class BallPoint {
        public final float ballXCoord;
        public final float ballYCoord;
        public final float ballArea;
        public final BoundingBox box;
        public BallPoint(BoundingBox b) {
            box = b;
            ballXCoord = (b.getX1() + b.getX2()) / 2F;
            ballYCoord = (b.getY1() + b.getY2()) / 2F;
            ballArea = Math.abs(b.getX2() - b.getX1()) * Math.abs(b.getY2() - b.getY1());
        }
    }

    public static class BallMotion {
        private FinalFrameState olderSpotting;
        private FinalFrameState newerSpotting;

        public float xMotion = 0;
        BallMotion() {
            // Creates dummy data init
            List<BoundingBox> dummyList = List.of(
                    new BoundingBox(0.5F, 0.5F, 0.5F, 0.5F,
                            640, 640, 0, 0, 0, 0, 1.0F, BALL_CLS_IDX, "ball")
            );
            olderSpotting = new FinalFrameState(GimbalRotState.STILL, 0F, dummyList, 0);
            newerSpotting = new FinalFrameState(GimbalRotState.STILL, 0F, dummyList, 1);
            if (olderSpotting.ballPoint.isEmpty()) throw new RuntimeException("Incorrect pass no ball sighted in frame init1");
            if (newerSpotting.ballPoint.isEmpty()) throw new RuntimeException("Incorrect pass no ball sighted in frame init2");

        }

        void addNewSpotting(FinalFrameState state) {
            if (state.ballPoint.isEmpty()) throw new RuntimeException("Incorrect pass no ball sighted in frame");
            olderSpotting = newerSpotting;
            newerSpotting = state;
            xMotion = newerSpotting.ballPoint.get().ballXCoord - olderSpotting.ballPoint.get().ballXCoord;
        }

    }
}
