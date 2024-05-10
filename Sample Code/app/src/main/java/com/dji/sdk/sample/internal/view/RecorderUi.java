package com.dji.sdk.sample.internal.view;

import static com.dji.sdk.sample.demo.gimbal.MoveGimbalWithSpeedView.getStringState;

import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.gimbal.MoveGimbalWithSpeedView;

import java.util.Random;

/**
 * Created by dji on 15/12/20.
 */
public abstract class RecorderUi extends LinearLayout implements View.OnClickListener, PresentableView, View.OnTouchListener {

    protected final static int DISABLE = 0;
    private TextView infoText;
    private TextView timer;
    protected Button middleBtn;
    protected Button leftBtn;
    protected Button rightBtn;
    protected StringBuffer stringBuffer;
    protected String randName;

    private Thread updateTimeThread;

    public MoveGimbalWithSpeedView.RotState rotation = MoveGimbalWithSpeedView.RotState.STILL;


    public RecorderUi(Context context) {
        super(context);
        initUI(context);
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    private void initUI(Context context) {
        setOrientation(VERTICAL);
        setBackgroundColor(context.getResources().getColor(R.color.white));
        setClickable(true);
        setWeightSum(1);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_three_btn, this, true);
        randName = String.valueOf(new Random().nextInt());

        infoText = (TextView) findViewById(R.id.text_info);
        timer = (TextView) findViewById(R.id.time);

        middleBtn = (Button) findViewById(R.id.btn_middle);

        leftBtn = (Button) findViewById(R.id.btn_left);
        rightBtn = (Button) findViewById(R.id.btn_right);

        if (getMiddleBtnTextResourceId() == DISABLE) {
            middleBtn.setVisibility(INVISIBLE);
        } else {
            middleBtn.setText(getString(getMiddleBtnTextResourceId()));
            middleBtn.setOnTouchListener(this);
        }

        if (getLeftBtnTextResourceId() == DISABLE) {
            leftBtn.setVisibility(INVISIBLE);
        } else {
            leftBtn.setText(getString(getLeftBtnTextResourceId()));
            leftBtn.setOnClickListener(this);
        }

        if (getRightBtnTextResourceId() == DISABLE) {
            rightBtn.setVisibility(INVISIBLE);
        } else {
            rightBtn.setText(getString(getRightBtnTextResourceId()));
            rightBtn.setOnTouchListener(this);
        }

        infoText.setText(getString(getDescriptionResourceId()));

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        stringBuffer = new StringBuffer();

        updateTimeThread = new Thread() {
            @Override
            public void run() {
                while(true){
                    infoText.setText(getTimerString());
                    try{
                        Thread.sleep(5);
                    }catch(InterruptedException e) {}
                }
            }
        };
        updateTimeThread.start();
    }

    protected void showStringBufferResult() {
        post(new Runnable() {
            @Override
            public void run() {
                infoText.setText(getTimerString());
            }
        });
    }

    private String getString(int id) {
        return getResources().getString(id);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(v.getId() == R.id.btn_middle && event.getAction() == MotionEvent.ACTION_DOWN) handleMiddleBtnDown();
        else if(v.getId() == R.id.btn_right && event.getAction() == MotionEvent.ACTION_DOWN) handleRightBtnDown();
        else if(v.getId() == R.id.btn_middle && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) handleMiddleBtnUp();
        else if(v.getId() == R.id.btn_right && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) handleRightBtnUp();
        else return false;

        return true;
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btn_left) handleLeftBtnClick();
    }

    protected void changeDescription(@StringRes int newDescriptionResID) {
        changeDescription(getContext().getString(newDescriptionResID));
    }

    String getTimerString() {
        return  stringBuffer.toString() + System.currentTimeMillis() +
                "\n" + "Rot: " + getStringState(rotation) + "\n" +
                "file name " + randName;
    }

    protected void changeDescription(final String newDescription) {
        post(new Runnable() {
            @Override
            public void run() {
                infoText.setText(newDescription);
            }
        });
    }

    /**
     * @return DISABLE to hide button
     */
    protected abstract int getMiddleBtnTextResourceId();

    /**
     * @return DISABLE to hide button
     */
    protected abstract int getLeftBtnTextResourceId();

    /**
     * @return DISABLE to hide button
     */
    protected abstract int getRightBtnTextResourceId();

    protected abstract int getDescriptionResourceId();


    protected abstract void handleLeftBtnClick();

    protected abstract void handleRightBtnDown();
    protected abstract void handleRightBtnUp();
    protected abstract void handleMiddleBtnDown();
    protected abstract void handleMiddleBtnUp();

}
