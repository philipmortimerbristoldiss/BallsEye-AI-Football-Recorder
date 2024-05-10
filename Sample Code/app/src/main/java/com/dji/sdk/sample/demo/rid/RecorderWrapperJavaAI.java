package com.dji.sdk.sample.demo.rid;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.gimbal.MoveGimbalWithSpeedView;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.view.AiRecorderUi;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.gimbal.Axis;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.util.CommonCallbacks;


public class RecorderWrapperJavaAI extends AiRecorderUi {
    private static final String START_REC_STR = "Start Recording";
    private static final String STOP_REC_STR = "Stop Recording";
    private Timer timer;
    private GimbalRotateTimerTask gimbalRotationTimerTask;
    private ImageState state;
    private boolean useLstm = false;

    private static final int YAW_VAL = 15;
    private static final int SPEEDUP = 10;
    int currSpeed = 0;
    private ImageState decideAlgo;

    public RecorderWrapperJavaAI(Context context) {
        super(context);
        leftBtn.setText(START_REC_STR);
        decideAlgo = new ImageState();
        DJISampleApplication.vidRecorder.initialiseCams(decideAlgo);
    }

    private void rotate(int yaw, float currAngle) {
        if((currAngle > ImageState.MAX_YAW && yaw > 0) || ((currAngle < ImageState.MIN_YAW && yaw < 0))){
            stopRotation();
            return;
        }
        if (timer !=null && gimbalRotationTimerTask != null && gimbalRotationTimerTask.yawValue == yaw && currSpeed==yaw) return;
        if (timer !=null) stopRotation();
        if (timer == null) {
            timer = new Timer();
            gimbalRotationTimerTask = new GimbalRotateTimerTask(yaw);
            timer.schedule(gimbalRotationTimerTask, 0, 100);
        }
        currSpeed = yaw;
    }

    private void stopRotation() {
        currSpeed = 0;
        if (timer != null) {
            if(gimbalRotationTimerTask != null) {
                gimbalRotationTimerTask.cancel();
            }
            timer.cancel();
            timer.purge();
            gimbalRotationTimerTask = null;
            timer = null;
        }

        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            DJISampleApplication.getProductInstance().getGimbal().
                    rotate(null, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {

                        }
                    });
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            DJISampleApplication.getProductInstance().getGimbal().setSmoothTrackEnabled(Axis.YAW, true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            DJISampleApplication.getProductInstance().getGimbal().setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(@NonNull GimbalState gimbalState) {
                    decideAlgo.setRotationAngle(gimbalState.getAttitudeInDegrees().getYaw());
                }
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (timer != null) {
            if(gimbalRotationTimerTask != null) {
                gimbalRotationTimerTask.cancel();
            }
            timer.cancel();
            timer.purge();
            gimbalRotationTimerTask = null;
            timer = null;
        }
    }

    @Override
    protected void handleLeftBtnClick() {
        if (leftBtn.getText().equals(START_REC_STR)) {
            DJISampleApplication.vidRecorder.startRecording();
            leftBtn.setText(STOP_REC_STR);
        } else {
            DJISampleApplication.vidRecorder.stopRecording();
            leftBtn.setText(START_REC_STR);
        }
    }

    private static class GimbalRotateTimerTask extends TimerTask {
        float yawValue;

        GimbalRotateTimerTask(float yawValue) {
            super();
            this.yawValue = yawValue;
        }

        @Override
        public void run() {
            if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
                DJISampleApplication.getProductInstance().getGimbal().
                        rotate(new Rotation.Builder().pitch(Rotation.NO_ROTATION)
                                .mode(RotationMode.SPEED)
                                .yaw(yawValue)
                                .roll(Rotation.NO_ROTATION)
                                .time(0)
                                .build(), new CommonCallbacks.CompletionCallback() {

                            @Override
                            public void onResult(DJIError error) {

                            }
                        });
            }
        }
    }

    @Override public int getDescription() {return R.string.ai_rec_desc_desc;}
    @Override protected int getMiddleBtnTextResourceId() {return R.string.ai_rec_mid;}
    @Override protected int getLeftBtnTextResourceId() {return R.string.ai_rec_left;}
    @Override protected int getRightBtnTextResourceId() {return R.string.ai_rec_right;}
    @Override protected int getDescriptionResourceId() {return R.string.ai_rec_desc;}
    @Override protected void handleRightBtnDown() {}
    @Override protected void handleRightBtnUp() {
        useLstm = !useLstm;
        if (useLstm) {rightBtn.setText("Using LSTM");}
        else rightBtn.setText("Object Tracking");
    }
    @Override protected void handleMiddleBtnDown() {}
    @Override protected void handleMiddleBtnUp() {}

    // Class to store the whole state and process instructions as required
    public class ImageState implements Detector.DetectorListener {
        private RotAI rotDecAi = null;
        private FinalFrameState.GimbalRotState rotation = FinalFrameState.GimbalRotState.STILL;
        private float rotationAngle = 0.f;
        int calls = 0;
        long startTime;
        FinalFrameState prevState;
        int noFramesWithoutBall = 0;
        int NO_FRAMES_TOLERANCE = 6; // Frames without seeing ball before lost
        static final float MAX_YAW = 60F;
        static final float MIN_YAW = -MAX_YAW;
        private FinalFrameState.BallMotion ballMotion = new FinalFrameState.BallMotion();
        private FinalFrameState.GimbalRotState lostDirection = FinalFrameState.GimbalRotState.STILL;


        void setRotationAngle(float newRotAngle) {rotationAngle = newRotAngle;}

        @Override
        public synchronized void onDetect(@NonNull List<BoundingBox> boundingBoxes, long inferenceTime) {
            if (calls == 0) {
                prevState = new FinalFrameState(rotation, rotationAngle, boundingBoxes, calls);
                startTime = System.currentTimeMillis() - 1;
            }
            while(rotDecAi == null || (rotDecAi != null && !rotDecAi.isSetup())); // Waits for first setup
            calls++;
            Long tDiff = (System.currentTimeMillis() - startTime);
            float fps = (float) (calls) / (tDiff.floatValue() / 1000F);
            System.out.println("fps " + fps + "No boxes: " + boundingBoxes.size() + " Inference time: " + inferenceTime +
                    " Angle " + rotationAngle + " rot state " + rotation);

            FinalFrameState currState = new FinalFrameState(rotation, rotationAngle, boundingBoxes, calls);

            if(useLstm) {
                FinalFrameState.GimbalRotState nextRot = rotDecAi.newFrame(currState);
                if(nextRot == FinalFrameState.GimbalRotState.RIGHT){
                    rotate(YAW_VAL, currState.rotationAngle);
                    rotation = FinalFrameState.GimbalRotState.RIGHT;
                } else if (nextRot == FinalFrameState.GimbalRotState.LEFT) {
                    rotate(-YAW_VAL, currState.rotationAngle);
                    rotation = FinalFrameState.GimbalRotState.LEFT;
                } else if(nextRot == FinalFrameState.GimbalRotState.STILL) {
                    stopRotation();
                    rotation = FinalFrameState.GimbalRotState.STILL;
                }
            } else{
                // Tracks ball
                if(currState.ballPoint.isPresent()) {
                    lostDirection = FinalFrameState.GimbalRotState.STILL;
                    ballMotion.addNewSpotting(currState);
                    // Calculates central boundaries based on ball size (closer ball, is smaller boundaries
                    // should be as camera needs to move more quickly). Also set tolerance based on size.
                    NO_FRAMES_TOLERANCE = 6;
                    float dist = 0.25F;
                    if(currState.ballPoint.get().ballArea > 0.0003F) {dist = 0.19F; NO_FRAMES_TOLERANCE = 5;};
                    if(currState.ballPoint.get().ballArea > 0.001F) {dist = 0.12F; NO_FRAMES_TOLERANCE = 3;}
                    // Centers ball simplistically
                    FinalFrameState.BallPoint bp = currState.ballPoint.get();
                    if (bp.ballXCoord <= 0.5F + dist && bp.ballXCoord >= 0.5F - dist){
                        System.out.println("Ball found at " + bp.ballXCoord + " . Staying still.");
                        stopRotation();
                        rotation = FinalFrameState.GimbalRotState.STILL;
                    } else if (bp.ballXCoord > 0.5F + dist) {
                        int speedup = bp.ballXCoord >= 0.90 ? SPEEDUP : 0;
                        System.out.println("Ball found at " + bp.ballXCoord + " . Moving right.");
                        rotate(YAW_VAL + speedup, currState.rotationAngle);
                        rotation = FinalFrameState.GimbalRotState.RIGHT;
                    } else {
                        int speedup = bp.ballXCoord <= 0.10 ? SPEEDUP : 0;
                        System.out.println("Ball found at " + bp.ballXCoord + " . Moving left.");
                        rotate(-YAW_VAL - speedup, currState.rotationAngle);
                        rotation = FinalFrameState.GimbalRotState.LEFT;
                    }
                    noFramesWithoutBall = 0;
                } else {
                    if (noFramesWithoutBall < NO_FRAMES_TOLERANCE) {
                        lostDirection = FinalFrameState.GimbalRotState.STILL;
                        System.out.println("Ball not found but staying still to be patient. Frames without ball " + noFramesWithoutBall);
                        // Allows for patience. Doesn't start looking for lost ball straight away
                        stopRotation();
                        rotation = FinalFrameState.GimbalRotState.STILL;
                    } else {
                        // If beyond max or min rotation limits, look in other direction
                        if(currState.rotationAngle >= MAX_YAW) {
                            System.out.println("Reached max rotation going left");
                            rotate(-YAW_VAL, currState.rotationAngle);
                            lostDirection = FinalFrameState.GimbalRotState.LEFT;
                            rotation = FinalFrameState.GimbalRotState.LEFT;
                        } else if(currState.rotationAngle <= MIN_YAW) {
                            System.out.println("Reached max rotation going right");
                            rotate(YAW_VAL, currState.rotationAngle);
                            lostDirection = FinalFrameState.GimbalRotState.RIGHT;
                            rotation = FinalFrameState.GimbalRotState.RIGHT;
                        } else {
                            // Pick which direction to start looking in based off of previous ball data
                            if(lostDirection == FinalFrameState.GimbalRotState.STILL ) {
                                if (ballMotion.xMotion > 0 ) {
                                    System.out.println("using motion to go right. motion: " + ballMotion.xMotion);
                                    rotate(YAW_VAL, currState.rotationAngle);
                                    rotation = FinalFrameState.GimbalRotState.RIGHT;
                                    lostDirection = FinalFrameState.GimbalRotState.RIGHT;
                                } else if(ballMotion.xMotion <= 0) {
                                    System.out.println("using motion to go left. motion: " + ballMotion.xMotion);
                                    rotate(-YAW_VAL, currState.rotationAngle);
                                    rotation = FinalFrameState.GimbalRotState.LEFT;
                                    lostDirection = FinalFrameState.GimbalRotState.LEFT;
                                }
                            } else { // Continues lost search - probably redunandant as should just stay rotating
                                if(lostDirection == FinalFrameState.GimbalRotState.RIGHT) {
                                    System.out.println("Contining to be lost and go right");
                                    rotate(YAW_VAL, currState.rotationAngle);
                                    rotation = FinalFrameState.GimbalRotState.RIGHT;
                                } else if (lostDirection == FinalFrameState.GimbalRotState.LEFT) {
                                    System.out.println("Contining to be lost and go left");
                                    rotate(-YAW_VAL, currState.rotationAngle);
                                    rotation = FinalFrameState.GimbalRotState.LEFT;
                                }
                            }
                        }
                    }
                    noFramesWithoutBall++;
                }
            }


        }

        @Override
        public void giveContext(@NonNull Context context) {
            if(rotDecAi == null) {
                rotDecAi = new RotAI(context, Constants.DECISION_MODEL_PATH);
                rotDecAi.setup();
            }
        }
    }
}
