package com.dji.sdk.sample.demo.rid;

import java.util.Optional;

public class StateSequence {
    // State sequence class because I can't be bothered to deal with float arrays in kotlin
    final float[] input;
    private final int SEQ_LENGTH;
    private final int NO_FEATURES;
    private int buffElement = 0;
    StateSequence(int seqLen, int noFeatures) {
        this.SEQ_LENGTH = seqLen;
        this.NO_FEATURES = noFeatures;
        input = new float[SEQ_LENGTH * NO_FEATURES];
    }

    void newFrame(FinalFrameState f) {
        if (buffElement == SEQ_LENGTH) {
            // Pushes elements down (i.e. pushes oldest sequence item out of array)
            for(int i = 1; i < SEQ_LENGTH; i++) {
                int ind = i * NO_FEATURES;
                int prev = (i - 1) * NO_FEATURES;
                input[prev] = input[ind];
                input[prev + 1] = input[ind + 1];
                input[prev + 2] = input[ind + 2];
                input[prev + 3] = input[ind + 3];
            }

            buffElement--;
        }

        // Adds newest element to array pointer
        int index = buffElement * NO_FEATURES;
        /*input[index] = f.rotState == FinalFrameState.GimbalRotState.LEFT? -1 : (f.rotState == FinalFrameState.GimbalRotState.RIGHT? 1 : 0);
        input[index + 1] = f.scaledRotAngle;*/
        Optional<FinalFrameState.BallPoint> bp = f.ballPoint;
        if (bp.isPresent()) {
            FinalFrameState.BallPoint ball = bp.get();
            input[index] = ball.ballXCoord;
            input[index + 1] = ball.ballYCoord;
            input[index + 2] = ball.box.getX2() - ball.box.getX1();
            input[index + 3] = ball.box.getY2() - ball.box.getY1();
        } else {
            input[index] = -1F;
            input[index + 1] = -1F;
            input[index + 2] = -1F;
            input[index + 3] = -1F;
        }
    }

}
