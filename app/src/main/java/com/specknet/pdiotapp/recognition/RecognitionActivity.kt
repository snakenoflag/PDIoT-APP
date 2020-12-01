package com.specknet.pdiotapp.recognition

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.RingProgressBar
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.CountUpTimer
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class RecognitionActivity : AppCompatActivity() {



    lateinit var startRecognitionButton: Button
    lateinit var stopRecognitionButton: Button
    lateinit var progressBar: RingProgressBar
    lateinit var progressBar2: RingProgressBar
    lateinit var progressBar3: RingProgressBar
    lateinit var progressBar4: RingProgressBar
    lateinit var progressBar5: RingProgressBar


    lateinit var resultText: TextView
    lateinit var resultText2: TextView
    lateinit var resultText3: TextView
    lateinit var resultText4: TextView
    lateinit var resultText5: TextView

    lateinit var timer: TextView
    lateinit var countUpTimer: CountUpTimer


    lateinit var respeckReceiver: BroadcastReceiver
    lateinit var looper: Looper


    val filterTest = IntentFilter(Constants.ACTION_INNER_RESPECK_BROADCAST)

    val activityType: Array<String> = arrayOf("Climbing stairs",
        "Descending stairs",
        "Desk work",
        "Lying down left",
        "Lying down right",
        "Lying down on back",
        "Lying down on stomach",
        "Running",
        "Sitting bent backward",
        "Sitting bent forward",
        "Sitting",
        "Standing",
        "Walking at normal speed",
        "Movement")



    var last_x = -100f
    var last_y = -100f
    var last_z = -100f



    var inputChanels = 3
    var windowSize = 30
    lateinit var inputData: ByteBuffer
    var timeFlag = 0
    var startFlag = 0
    var modelFlag = 1
    var step = 0
    var indexBuffer = 0
    var models:HashMap<String,Interpreter> = HashMap()
    lateinit var output: Array<FloatArray>
    val POSITION = "Wrist_Right"
    var mProgress = 0f
    var mProgress2 = 0f
    var mProgress3 = 0f
    var mProgress4 = 0f
    var mProgress5 = 0f
    var xProgress = 0f
    var xProgress2 = 0f
    var xProgress3 = 0f
    var xProgress4 = 0f
    var xProgress5 = 0f


    lateinit var frequencies: ArrayList<Long>
    lateinit var frequenciesAppend: ArrayList<Long>



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognition)


        setupBars()

        setupButtons()

        resultText = findViewById(R.id.result_text)
        resultText2 = findViewById(R.id.result_text2)
        resultText3 = findViewById(R.id.result_text3)
        resultText4 = findViewById(R.id.result_text4)
        resultText5 = findViewById(R.id.result_text5)



        output = Array(1) { FloatArray(2) }

        for (ACTIVITY in activityType){
            var MODEL_FILE = POSITION + "_" + ACTIVITY + ".tflite"

            try {
                models.put(ACTIVITY,Interpreter(loadModelFile(this, MODEL_FILE)))
            } catch (e: IOException) {
                e.printStackTrace()
                modelFlag = 0
                Log.e("Debug1", "IOExeception:"+MODEL_FILE)
            }
        }
        frequencies = ArrayList<Long>()
        frequenciesAppend = ArrayList()

        var timeCounter = 0


        inputData = ByteBuffer.allocateDirect(windowSize * inputChanels * 4)
        inputData.order(ByteOrder.nativeOrder())
        inputData.rewind()


        // register receiver
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_INNER_RESPECK_BROADCAST) {
                    // get all relevant intent contents
                    val x = intent.getFloatExtra(Constants.EXTRA_RESPECK_LIVE_X, 0f)
                    val y = intent.getFloatExtra(Constants.EXTRA_RESPECK_LIVE_Y, 0f)
                    val z = intent.getFloatExtra(Constants.EXTRA_RESPECK_LIVE_Z, 0f)

                    if (x == last_x && y == last_y && z == last_z) {
                        Log.e("Debug", "DUPLICATE VALUES")
                    }
                    last_x = x
                    last_y = y
                    last_z = z
                    step = step + 1
                    if (step == windowSize){
                        step = 0
                        timeFlag = 1
                        indexBuffer = 0

                    }else{
                        timeFlag = 0
                        inputData.putFloat(indexBuffer,x)
                        inputData.putFloat(indexBuffer+4*windowSize,y)
                        inputData.putFloat(indexBuffer+4*windowSize,z)
                        indexBuffer = indexBuffer + 4
                    }


                    if (timeFlag==1 && startFlag == 1){
                        timeCounter = timeCounter + 1
                    }
                    if((timeCounter == 1 && startFlag == 1) && modelFlag == 1 ) {
                        timeCounter = 0
                        predict()
                        inputData.clear()

                    }
                    if (startFlag==1){
                        xProgress = mProgress - progressBar.progress.toFloat()
                        xProgress2 = mProgress2 - progressBar2.progress.toFloat()
                        xProgress3 = mProgress3 - progressBar3.progress.toFloat()
                        xProgress4 = mProgress4 - progressBar4.progress.toFloat()
                        xProgress5 = mProgress5 - progressBar5.progress.toFloat()

                        progressBar.setProgress (mProgress - xProgress/windowSize*(windowSize-1-step))
                        progressBar2.setProgress (mProgress2 - xProgress2/windowSize*(windowSize-1-step))
                        progressBar3.setProgress (mProgress3 - xProgress3/windowSize*(windowSize-1-step))
                        progressBar4.setProgress (mProgress4 - xProgress4/windowSize*(windowSize-1-step))
                        progressBar5.setProgress (mProgress5 - xProgress5/windowSize*(windowSize-1-step))
                    }



                }

            }
        }

        // important to set this on a background thread otherwise it will block the UI
        val handlerThread = HandlerThread("bgProcThread")
        handlerThread.start()
        looper = handlerThread.looper
        val handler = Handler(looper)
        this.registerReceiver(respeckReceiver, filterTest, null, handler)

        timer = findViewById(R.id.count_up_timer_text)
        timer.visibility = View.INVISIBLE

        countUpTimer = object: CountUpTimer(1000) {
            override fun onTick(elapsedTime: Long) {
                val date = Date(elapsedTime)
                val formatter = SimpleDateFormat("mm:ss")
                val dateFormatted = formatter.format(date)
                timer.text = "Time elapsed: " + dateFormatted
            }
        }

    }


    private fun setupButtons() {
        startRecognitionButton = findViewById(R.id.start_recognition_button)
        stopRecognitionButton = findViewById(R.id.stop_recognition_button)

        stopRecognitionButton.isClickable = false
        stopRecognitionButton.isEnabled = false

        startRecognitionButton.setOnClickListener {
            Toast.makeText(this, "Starting recognition", Toast.LENGTH_LONG).show()

            startRecognitionButton.isClickable = false
            startRecognitionButton.isEnabled = false

            stopRecognitionButton.isClickable = true
            stopRecognitionButton.isEnabled = true

            startRecognition()
        }

        stopRecognitionButton.setOnClickListener {
            Toast.makeText(this, "Pause", Toast.LENGTH_LONG).show()
            startRecognitionButton.isClickable = true
            startRecognitionButton.isEnabled = true

            stopRecognitionButton.isClickable = false
            stopRecognitionButton.isEnabled = false

            stopRecognition()
        }

    }

    private fun setupBars(){
        progressBar = findViewById(R.id.result_bar);
        progressBar2 = findViewById(R.id.result_bar2);
        progressBar3 = findViewById(R.id.result_bar3);
        progressBar4 = findViewById(R.id.result_bar4);
        progressBar5 = findViewById(R.id.result_bar5);

    }

    private fun startRecognition() {

        startFlag = 1;
        timer.visibility = View.VISIBLE
        countUpTimer.start()
    }

    private fun stopRecognition() {
        startFlag = 0;
        countUpTimer.stop()
        countUpTimer.reset()
        timer.text = "Time elapsed: 00:00"
    }


    private fun predict(){
        var sum:Float = 0F
        var outputMap:HashMap<Float,String> = HashMap()
        for (ACTIVITY in activityType) {
            models[ACTIVITY]?.run(inputData, output)
            outputMap.put(output[0][0],ACTIVITY)
            sum = sum + output[0][0]
        }
        val keys:FloatArray = outputMap.keys.toFloatArray()
        Arrays.sort(keys)

        Log.e("Result", "output:" +outputMap)


        mProgress = getConfidence(keys[13],sum)
        resultText.text = outputMap.get(keys[13])

        Log.e("Result", "mProgress:" +keys[13])
        mProgress2 = getConfidence(keys[12],sum)
        resultText2.text = outputMap.get(keys[12])

        mProgress3 = getConfidence(keys[11],sum)
        resultText3.text = outputMap.get(keys[11])

        mProgress4 = getConfidence(keys[10],sum)
        resultText4.text = outputMap.get(keys[10])

        mProgress5 = getConfidence(keys[9],sum)
        resultText5.text = outputMap.get(keys[9])


    }


    private fun getConfidence(score:Float,sum:Float):Float{
        var confidence = score/sum
        return (10000*(confidence))
    }





    @Throws(IOException::class)
    private fun loadModelFile(
        activity: Activity,
        MODEL_FILE: String
    ): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_FILE)
        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }




    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckReceiver)
        looper.quit()
        for(ACTIVITY in activityType){
            models.get(ACTIVITY)?.close()
        }
    }




}