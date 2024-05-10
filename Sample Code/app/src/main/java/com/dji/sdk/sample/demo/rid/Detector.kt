package com.dji.sdk.sample.demo.rid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

const val debugModeYolo = true

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0
    private var calls = 0



    private lateinit var outputTensor: TensorBuffer
    lateinit var inputBitmap: Bitmap


    private lateinit var imageProcessor: ImageProcessor
    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)

        val options = Interpreter.Options()
        val compatList = CompatibilityList()
        if(compatList.isDelegateSupportedOnThisDevice){
            // if the device has a supported GPU, add the GPU delegate
            val delegateOptions = compatList.bestOptionsForThisDevice
            options.addDelegate(GpuDelegate(delegateOptions))
        }

        var nnApiDelegate: NnApiDelegate? = null
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
        } else {options.numThreads = 4}

        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        inputBitmap = Bitmap.createBitmap(
            tensorWidth,
            tensorHeight,
            Bitmap.Config.ARGB_8888
        )
        // Sets bitmap to black
        for(y in 0 until tensorHeight) for(x in 0 until tensorWidth) inputBitmap.setPixel(x, y, Color.rgb(0, 0, 0))

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(tensorHeight, tensorWidth))
            .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(CastOp(INPUT_IMAGE_TYPE))
            .build()


        outputTensor = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)

        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null && line != "") {
                labels.add(line)
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        interpreter ?: return
        if (tensorWidth == 0) return
        if (tensorHeight == 0) return
        if (numChannel == 0) return
        if (numElements == 0) return

        if(calls == 0) detectorListener.giveContext(context)
        calls++
        if(debugModeYolo) println("Frame height " + frame.height + " Frame width " + frame.width)
        if (frame.height > frame.width || tensorHeight != tensorWidth) throw RuntimeException("Invalid frame / YOLO Model")

        var inferenceTime = SystemClock.uptimeMillis()
        // Rescales image whilst preserving aspect ratio
        val scaleFact = if (frame.width >= frame.height) tensorWidth.toFloat() / frame.width.toFloat() else tensorHeight.toFloat() / frame.height.toFloat()
        val newWidth = (frame.width * scaleFact).toInt()
        if (newWidth != tensorWidth) throw RuntimeException("Invalid width resize")
        val resizedBitmap = Bitmap.createScaledBitmap(frame, newWidth, (frame.height * scaleFact).toInt(), true)

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val processImgBuff = processedImage.buffer
        if(debugModeYolo) println("scaletime " + (SystemClock.uptimeMillis() - inferenceTime))

        // Note if using a pre init outTensor, then only once call to detect at a time can be allowed
        var intTime = SystemClock.uptimeMillis()
        interpreter?.run(processImgBuff, outputTensor.buffer)
        if(debugModeYolo) println("interp time " + (SystemClock.uptimeMillis() - intTime))

        var nmsTime = SystemClock.uptimeMillis()
        val bestBoxes = bestBox(outputTensor.floatArray, resizedBitmap.height)
        if(debugModeYolo) println("nms and bbox time " + (SystemClock.uptimeMillis() - nmsTime))

        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        detectorListener.onDetect(bestBoxes!!, inferenceTime)
    }

    fun saveBitmapToFile(bitmap: Bitmap, name: String) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), name)
            val fOut = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
            fOut.flush()
            fOut.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun bestBox(array: FloatArray, resizeHeight: Int) : List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        val padPerc = (tensorHeight - resizeHeight).toFloat() / (2F * tensorHeight.toFloat())
        val scale = ((padPerc * tensorHeight.toFloat()) + resizeHeight.toFloat()) / resizeHeight.toFloat()
        for (c in 0 until numElements) {
            // Finds max confidence for each prediction
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j
                }
                j++
                arrayIdx += numElements
            }
            maxIdx -= 4

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val cls = maxIdx
                val clsName = labels[cls]
                val cx = array[c] // 0
                val cy = array[c + numElements] // 1
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]
                val x1 = cx - (w/2F)
                val y1 = cy - (h/2F)
                val x2 = cx + (w/2F)
                val y2 = cy + (h/2F)

                // Adjusts for effect of padding to input image (zero padding applied to top and bottom)
                val y1Adj = (y1 - padPerc) * scale
                val y2Adj = (y2 - padPerc) * scale

                if (x1 < 0F || x1 > 1F) continue
                if (y1Adj < 0F || y1Adj > 1F) continue
                if (x2 < 0F || x2 > 1F) continue
                if (y2Adj < 0F || y2Adj > 1F) continue

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1Adj, x2 = x2, y2 = y2Adj,
                        imgWidth = tensorWidth, imgHeight = resizeHeight,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = maxConf, cls = cls, clsName = clsName
                    )
                )
            }
        }
        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>) : List<BoundingBox> {
        if (boxes.isEmpty()) return boxes
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }
    interface DetectorListener {
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
        fun giveContext(context: Context)
    }

    fun drawRectangle(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED // Set color to red (you can change to any color you like)
            style = Paint.Style.STROKE // Set style to stroke (outline)
            strokeWidth = 5f // Set stroke width (you can adjust as needed)
        }
        canvas.drawRect(left, top, right, bottom, paint)
        return bitmap
    }


    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.30F
        private const val IOU_THRESHOLD = 0.5F
    }
}