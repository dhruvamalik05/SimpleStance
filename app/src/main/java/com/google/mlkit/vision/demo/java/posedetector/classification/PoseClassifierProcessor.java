/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.java.posedetector.classification;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.WorkerThread;
import com.google.common.base.Preconditions;
import com.google.mlkit.vision.common.PointF3D;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
//import org.tensorflow.lite.interpreter;

import org.tensorflow.lite.Interpreter;

import static com.google.mlkit.vision.demo.java.posedetector.classification.PoseEmbedding.getPoseEmbedding;
import static com.google.mlkit.vision.demo.java.posedetector.classification.PoseEmbedding.normalize;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accepts a stream of {@link Pose} for classification and Rep counting.
 */
public class PoseClassifierProcessor {
  private static final String TAG = "PoseClassifierProcessor";
  private static final String POSE_SAMPLES_FILE = "pose/fitness_pose_samples.csv";

  // Specify classes for which we want rep counting.
  // These are the labels in the given {@code POSE_SAMPLES_FILE}. You can set your own class labels
  // for your pose samples.
  private static final String PUSHUPS_CLASS = "bad_pos";
  private static final String SQUATS_CLASS = "good_pos";
  private static final String[] POSE_CLASSES = {
    PUSHUPS_CLASS, SQUATS_CLASS
  };
  Interpreter tflite;

  private final boolean isStreamMode;

  private EMASmoothing emaSmoothing;
  private List<RepetitionCounter> repCounters;
  private PoseClassifier poseClassifier;
  private String lastRepResult;
  private Context context;

  @WorkerThread
  public PoseClassifierProcessor(Context context, boolean isStreamMode) {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
    this.isStreamMode = isStreamMode;
    this.context = context;
    if (isStreamMode) {
      emaSmoothing = new EMASmoothing();
      repCounters = new ArrayList<>();
      lastRepResult = "";
    }
    loadPoseSamples(context);
    try {
      tflite = new Interpreter(loadModelFile());
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  private MappedByteBuffer loadModelFile() throws IOException {
    AssetFileDescriptor fileDescriptor = context.getAssets().openFd("final_nn_model.tflite");
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  public String doInference(ArrayList<Float> landmarks) {
    // Log.i("Inference: ", landmarks.toString());
    float[][] inputVal = new float[1][99];
    for(int i = 0 ; i < landmarks.size() ; i++) {
      inputVal[0][i] = landmarks.get(i);
    }

    float[][] outputVal = new float[1][2];
    tflite.run(inputVal, outputVal);
    //Log.i("Inference: ", ""+outputVal[0][0] + " " + outputVal[0][1]);
    if(outputVal[0][0] > outputVal[0][1]) {
      return "bad";
    }
    return "good";
  }

  private void loadPoseSamples(Context context) {
    List<PoseSample> poseSamples = new ArrayList<>();
    try {
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(context.getAssets().open(POSE_SAMPLES_FILE)));
      String csvLine = reader.readLine();
      while (csvLine != null) {
        // If line is not a valid {@link PoseSample}, we'll get null and skip adding to the list.
        PoseSample poseSample = PoseSample.getPoseSample(csvLine, ",");
        if (poseSample != null) {
          poseSamples.add(poseSample);
        }
        csvLine = reader.readLine();
      }
    } catch (IOException e) {
      Log.e(TAG, "Error when loading pose samples.\n" + e);
    }
    poseClassifier = new PoseClassifier(poseSamples);
    if (isStreamMode) {
      for (String className : POSE_CLASSES) {
        repCounters.add(new RepetitionCounter(className));
      }
    }
  }
  private static List<PointF3D> extractPoseLandmarks(Pose pose) {
    List<PointF3D> landmarks = new ArrayList<>();
    for (PoseLandmark poseLandmark : pose.getAllPoseLandmarks()) {
      landmarks.add(poseLandmark.getPosition3D());
    }
    return landmarks;
  }
  /**
   * Given a new {@link Pose} input, returns a list of formatted {@link String}s with Pose
   * classification results.
   *
   * <p>Currently it returns up to 2 strings as following:
   * 0: PoseClass : X reps
   * 1: PoseClass : [0.0-1.0] confidence
   */
  @WorkerThread
  public List<String> getPoseResult(Pose pose) {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
    List<String> result = new ArrayList<>();
    ClassificationResult classification = poseClassifier.classify(pose);

    // Update {@link RepetitionCounter}s if {@code isStreamMode}.
    if (isStreamMode) {
      // Feed pose to smoothing even if no pose found.
      classification = emaSmoothing.getSmoothedResult(classification);

      // Return early without updating repCounter if no pose found.
      if (pose.getAllPoseLandmarks().isEmpty()) {
//        result.add(lastRepResult);
        result.add("");
        return result;
      }

      for (RepetitionCounter repCounter : repCounters) {
        int repsBefore = repCounter.getNumRepeats();
        int repsAfter = repCounter.addClassificationResult(classification);

        if (repsAfter > repsBefore) {
          // Play a fun beep when rep counter updates.
          ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
          tg.startTone(ToneGenerator.TONE_PROP_BEEP);
          lastRepResult = String.format(
              Locale.US, "%s : %d reps", repCounter.getClassName(), repsAfter);
          break;
        }
      }
      //result.add(lastRepResult);
      result.add("");
    }

    // Add maxConfidence class of current frame to result if pose is found.
    if (!pose.getAllPoseLandmarks().isEmpty()) {
      ArrayList<Float> landmarks = new ArrayList<Float>();
      List<PointF3D> embeddings = new ArrayList<PointF3D>();
      embeddings = normalize(extractPoseLandmarks(pose));
      for (PointF3D s : embeddings)
      {
        // listString += String.valueOf(Float.class.isInstance(s.getPosition3D().getX())) + s.getPosition3D().getY() + s.getPosition3D().getZ() + "\t";
        landmarks.add(s.getX());
        landmarks.add(s.getY());
        landmarks.add(s.getZ());
      }
      String res = doInference(landmarks);
      Log.i("Pose: ", res);
      String maxConfidenceClass = classification.getMaxConfidenceClass();
      if (classification.getClassConfidence("bad_pos") > 0.8) {
        maxConfidenceClass = "bad_pos";
      }
//      String maxConfidenceClassResult = String.format(
//          Locale.US,
//          "%s : %.2f confidence",
//          maxConfidenceClass,
//          classification.getClassConfidence(maxConfidenceClass)
//              / poseClassifier.confidenceRange());
      String maxConfidenceClassResult = res;
      result.add(maxConfidenceClassResult);
    }

    return result;
  }

}
