package com.dji.sdk.sample.demo.gimbal;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.view.BaseThreeBtnView;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.view.RecorderUi;

import dji.common.error.DJIError;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.util.CommonCallbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class for moving gimbal with speed.
 */
public class MoveGimbalWithSpeedView extends RecorderUi {
    private Timer timer;
    private GimbalRotateTimerTask gimbalRotationTimerTask;

    private static final int YAW_VAL = 20;

    private List<Pair<Long, Float>> yaws = new ArrayList<>();

    public enum RotState {LEFT, STILL, RIGHT};

    private List<Pair<Long, RotState>> rotStates = new ArrayList<>();


    public static String getStringState(RotState state) {
       if(state == RotState.LEFT) return "L";
       else if (state == RotState.RIGHT) return "R";
       else return "N";
    }

    public MoveGimbalWithSpeedView(Context context) {
        super(context);
    }

    @Override
    protected int getLeftBtnTextResourceId() {return R.string.move_gimbal_in_speed_event;}

    @Override
    protected int getMiddleBtnTextResourceId() {return R.string.move_gimbal_in_speed_left; }

    @Override
    protected int getRightBtnTextResourceId() {return R.string.move_gimbal_in_speed_right; }

    @Override
    protected int getDescriptionResourceId() {
        return R.string.move_gimbal_in_speed_description;
    }

    private void stopRotation() {
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

    private void rotate(int yaw) {
        if (timer == null) {
            timer = new Timer();
            gimbalRotationTimerTask = new GimbalRotateTimerTask(yaw);
            timer.schedule(gimbalRotationTimerTask, 0, 100);
        }
    }

    @Override
    protected void handleLeftBtnClick() {
        // Converts recorded data to string
        int len = yaws.size();
        int rotStatesLen = rotStates.size();
        StringBuilder fileBuilder = new StringBuilder("Time (millis),yaw angle (degrees)");
        for(int i = 0; i < len; i++) {
            String rec = "\n" + yaws.get(i).first.toString() + "," + String.format("%.1f", yaws.get(i).second);
            fileBuilder.append(rec);
        }
        // Writes data so far to text file to be saved
        FileWriterResults.writeToFile("helloFootball" + randName + ".txt", fileBuilder.toString());
        // Writes rotation states
        StringBuilder rotStatesFile = new StringBuilder("Time (millis),Rot State(L,R,N)");
        for(int i = 0; i < rotStatesLen; i++) {
            String c = getStringState(rotStates.get(i).second);
            String rec = "\n" + rotStates.get(i).first.toString() + "," + c;
            rotStatesFile.append(rec);
        }
        FileWriterResults.writeToFile("rotStates" + randName + ".txt", rotStatesFile.toString());
    }

    private void setRotState(RotState newState) {
        long time = System.currentTimeMillis();
        rotation = newState;
        rotStates.add(new Pair<>(time, newState));
        //showStringBufferResult();
    }

    @Override
    protected void handleRightBtnDown() {
        setRotState(RotState.RIGHT);
        stopRotation();
        rotate(YAW_VAL);
    }

    @Override
    protected void handleRightBtnUp() {
        setRotState(RotState.STILL);
        stopRotation();
    }

    @Override
    protected void handleMiddleBtnDown() {
        setRotState(RotState.LEFT);
        stopRotation();
        rotate(-YAW_VAL);
    }

    @Override
    protected void handleMiddleBtnUp() {
        setRotState(RotState.STILL);
        stopRotation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            DJISampleApplication.getProductInstance().getGimbal().setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(@NonNull GimbalState gimbalState) {
                    long time = System.currentTimeMillis();
                    float yaw = gimbalState.getAttitudeInDegrees().getYaw();
                    yaws.add(new Pair<>(time, yaw));
                    stringBuffer.delete(0, stringBuffer.length());
                    stringBuffer.append("PitchInDegrees: ").
                            append(gimbalState.getAttitudeInDegrees().getPitch()).append("\n");
                    stringBuffer.append("RollInDegrees: ").
                            append(gimbalState.getAttitudeInDegrees().getRoll()).append("\n");
                    stringBuffer.append("YawInDegrees: ").
                            append(yaw).append("\n");
                    //showStringBufferResult();
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
    public int getDescription() {
        return R.string.gimbal_listview_rotate_gimbal;
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
}
