Source code of BallsEye football recorder. BallsEye automatically records football matches using smartphone technology locally. 
This project was developed for a final year university project. To run the code, a DJI SDK API Key is required. Much of the code is bloat that has simply not been deleted.
The files that constitue the core section of the AI recorder are located within the [rid](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/tree/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid) folder. 
All files within this folder, other than UASView.java and OverlayView.kt, make up the core recorder. A brief summary of the purpose of each file is outlined as follows:

- [AiVidMain.kt](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/AiVidMain.kt) : Manages the video recorder and image analyser using CameraX. Each image preview frame is passed to the detector to perform YOLO detection.
- [BoundingBox.kt](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/BoundingBox.kt) : Class that stores the YOLOv8 generated bounding boxes.
- [Constants.kt](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/Constants.kt) : Stores filepaths for trained detector and LSTM models.
- [Detector.kt](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/Detector.kt) : Generates bounding boxes for a given input image. Image is resized, TFLite intepreter is used to perform inference for input image, bounding boxes are generated and NMS is applied to filter boxes.
- [FinalFrameState.java](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/FinalFrameState.java) : Class used to store an individual frame state which is a tuple of variables including camera angle, ball detection and ball motion.
- [RecorderWrapperJavaAI.java](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/RecorderWrapperJavaAI.java) : Controls GUI used for match recording. Also contains algorithm used to rotate gimbal based on provided detections.
- [RotAI.kt](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/RotAI.kt) : Performs inference with LSTM classifier and returns selected camera rotation state based on the classifier.
- [StateSequence.java](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/rid/StateSequence.java) : Helper class to store and update LSTM input tensor for each new frame.

This repository also contains the code used to manually record grassroots matches and record filming parameters (such as camera rotation over time). 
This code is found in the [MoveGimbalWithSpeedView.java](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/demo/gimbal/MoveGimbalWithSpeedView.java) file.
This file inherits the GUI from [RecorderUi.java](https://github.com/philipmortimerbristoldiss/BallsEye-AI-Football-Recorder/blob/main/Sample%20Code/app/src/main/java/com/dji/sdk/sample/internal/view/RecorderUi.java).
It displays the current system time and state information. Pressing the buttons leads to the rotation of the gimbal. The sequence of button presses and corresponding gimbal angles
are recorded and written to a text file.


Credit:
DJI's Android SDK Sample application was used a starting point (https://github.com/dji-sdk/Mobile-SDK-Android). Most of the files within this repository are simply these files. 
They could be safely deleted and are not a part of the project. However, they have been kept in as publishing code for the dissertation is only done to demonstrate proof of work.
Surendra Maran's YOLOv8 Android detector was used as a reference point when developing this software (https://github.com/surendramaran/YOLOv8-TfLite-Object-Detector).
