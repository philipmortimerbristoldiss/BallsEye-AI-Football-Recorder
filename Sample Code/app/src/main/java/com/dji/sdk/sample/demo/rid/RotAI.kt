package com.dji.sdk.sample.demo.rid

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.util.Random
import kotlin.math.abs

class RotAI (
        private val context: Context,
        private val modelPath: String,
) {
    private lateinit var interpreterLstm: Interpreter
    var isSetup = false
    private var sequenceLength = -1

    private var buffElements = 0
    private lateinit var statesObj: StateSequence

    private lateinit var inputTensor: TensorBuffer
    private lateinit var outputTensor: TensorBuffer
    private lateinit var inputShape: IntArray



    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options()
        val compatList = CompatibilityList()
        if(compatList.isDelegateSupportedOnThisDevice && false){
            // if the device has a supported GPU, add the GPU delegate
            val delegateOptions = compatList.bestOptionsForThisDevice
            options.addDelegate(GpuDelegate(delegateOptions))
        }

        var nnApiDelegate: NnApiDelegate? = null
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && false) {
            nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
        } else {options.numThreads = 4}

        interpreterLstm = Interpreter(model, options)

        isSetup = true

        println("Shapea")
        val inp_shape = interpreterLstm.getInputTensor(0)?.shape() ?: return
        for (shape in inp_shape)
            println(shape)
        println(inp_shape)
        val out_shape = interpreterLstm.getOutputTensor(0)?.shape() ?: return
        println(interpreterLstm.getOutputTensor(0)?.dataType().toString())
        println(interpreterLstm.getInputTensor(0)?.dataType().toString())
        for (shape in out_shape)
            println(shape)

        sequenceLength =interpreterLstm.getInputTensor(0).shape()[1]
        println("SEQ LENGTH " + sequenceLength)
        statesObj = StateSequence(sequenceLength, interpreterLstm.getInputTensor(0).shape()[2])

        inputTensor = TensorBuffer.createFixedSize(interpreterLstm.getInputTensor(0).shape(), DataType.FLOAT32)
        outputTensor = TensorBuffer.createFixedSize(interpreterLstm.getOutputTensor(0).shape(), DataType.FLOAT32)
        inputShape = interpreterLstm.getInputTensor(0).shape()

        var t = SystemClock.uptimeMillis()
        interpreterLstm.run(inputTensor.buffer, outputTensor.buffer)
        println("aiDec " + (SystemClock.uptimeMillis() - t))

    }

    // Uses LSTM to rotate device. If still in early init, might be nonsense
    fun newFrame(frame: FinalFrameState): FinalFrameState.GimbalRotState{
        statesObj.newFrame(frame)
        inputTensor = TensorBuffer.createFixedSize(interpreterLstm.getInputTensor(0).shape(), DataType.FLOAT32)
        outputTensor = TensorBuffer.createFixedSize(interpreterLstm.getOutputTensor(0).shape(), DataType.FLOAT32)
        inputTensor.loadArray(statesObj.input, inputShape)
        interpreterLstm.run(inputTensor.buffer, outputTensor.buffer)
        var results = outputTensor.floatArray
        var leftCnf = results.get(0) * 80
        var stillCnf = results.get(1)
        var rightCnf = results.get(2) * 90
        println("RESULTS. Left: " + leftCnf + " Middle: " + stillCnf + " Right: " + rightCnf)

        var bestMove = FinalFrameState.GimbalRotState.LEFT
        if (rightCnf > leftCnf) bestMove = FinalFrameState.GimbalRotState.RIGHT
        if (stillCnf > leftCnf && stillCnf > rightCnf) bestMove = FinalFrameState.GimbalRotState.STILL

        // Moves probablistically (i.e. moves with each prob. Consider they sum up to 1)
        val rand = Random().nextFloat()
        val firstCheck = Random().nextFloat()

        val netMot = rightCnf - leftCnf
        var retMove = FinalFrameState.GimbalRotState.STILL
        if(abs(netMot) >= rand) {
            if (netMot < 0.0F) retMove = FinalFrameState.GimbalRotState.LEFT
            if(netMot > 0.0F) retMove = FinalFrameState.GimbalRotState.RIGHT
        }

        if(retMove!=bestMove) println("DIFFERENT")

        return bestMove
    }
}