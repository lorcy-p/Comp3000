# ShotTrack 🏀

> On-device basketball shot analysis for Android — powered by YOLO11 and computer vision.

ShotTrack records or imports shooting footage, detects shot attempts using an on-device ML model, and returns entry angle analysis with improvement suggestions — all without sending any data off your device.

---

## Contents

- [Minimum Requirements](#minimum-requirements)
- [Permissions](#permissions)
- [Installation](#installation)
- [Usage](#usage)
- [Model Training & Export](#model-training--export)

---

## Minimum Requirements

ShotTrack requires an Android device meeting the following specification. Devices below this threshold may experience longer processing times or instability.

| Requirement | Specification |
|---|---|
| Operating System | Android 8.0 (API level 26) or higher |
| RAM | 4 GB (recommended minimum) |
| Storage | Sufficient space for APK and saved session data |
| Camera | Rear-facing camera capable of video recording |
| Recommended Device | Mid-range or above (e.g. Samsung A52s or equivalent) |

> **Performance note:** Processing time scales with video length. On a Samsung Galaxy S25, expect approximately 2× the clip duration — a 20-second clip takes ~40 seconds. Times will be longer on minimum-spec devices.

---

## Permissions

ShotTrack requests the following permissions. All are used solely for core functionality — **no data is ever transmitted off-device**.

| Permission | Purpose |
|---|---|
| Camera | Record shooting footage within the app |
| Read Media Video | Select existing video files for analysis |
| Read External Storage | Access video files on Android 9 and below |

---

## Installation

ShotTrack is sideloaded as an APK via Android Studio. These steps assume Android Studio is already installed and configured.

1. **Clone the repository** to your local machine.
2. **Open Android Studio** and select **Open**, then navigate to the Android Studio project directory.
3. **Connect your Android device** via USB.
4. **Wait for recognition** — the device should appear in the toolbar's device selector.
5. Click **Run**, or go to **Run → Run 'app'**. Android Studio will build and deploy the APK directly.
6. **Accept any permission prompts** on the device to allow installation.

The app launches automatically once installation completes. No internet connection is required after this point.

---

## Usage

### 1. Record or select a video

From the home screen, tap **Record** to open the native camera, or **Upload** to select an existing video.

For best results:
- Mount the camera on a stable surface or tripod at a **side-on angle** to the shooter
- Ensure the **full arc of each shot is visible**
- Keep the **hoop in frame** throughout — it must not move

### 2. Processing

Processing begins automatically after a video is selected. A progress screen is shown during this time. Tap **Cancel** at any point to abort and return to the home screen.

### 3. Select the hoop

Once processing completes, a frame from the middle of the video is displayed. **Tap the centre of the hoop** on screen. ShotTrack uses this position to segment shot attempts and calculate entry angles.

### 4. View results

The playback screen shows the video with detection overlays and parabolic arcs drawn over each detected shot. Tap any **shot card** below the video to jump to that attempt.

The analysis screen displays:
- Mean entry angle and standard deviation
- Shot count
- Angle distribution histogram with the optimal range highlighted
- Colour-coded improvement suggestions by priority

### 5. Switch shot type

On the analysis screen, toggle between **Free Throw** and **3 Point** to adjust the optimal angle thresholds and relevant suggestions.

### 6. Sessions

Sessions are saved automatically when leaving the analysis screen. Access previous sessions from **Saved Sessions** on the home screen. Each entry shows shot type, date, mean angle, and standard deviation. Tap the delete option to remove a session.

---

## Model Training & Export

Follow these steps to reproduce or update the ShotTrack detection model. Training runs locally for GPU performance; export runs in Google Colab to avoid library conflicts.

**Resources:**
- 🔗 [Model Conversion Notebook (Google Colab)](https://colab.research.google.com/drive/1KLmLk4IS4BvBmp4bLdRxp-fkse1mToCm?usp=sharing)
- 🔗 [Roboflow Dataset](https://app.roboflow.com/lorcans-projects/comp-3000)

---

### Step 1 — Local Training (VS Code)

Requires an NVIDIA GPU (e.g. RTX 3080) recognised by CUDA for practical training times.

1. Open the `YOLO11Trainer` directory in VS Code.
2. Verify **Python 3.8+** is installed along with the `ultralytics` and `roboflow` libraries.
3. Confirm your GPU is detected by CUDA.
4. Download the dataset from Roboflow in **YOLOv11 format** and update `yaml_path` in the training script to point to `data.yaml`.
5. Run the training script with the following parameters:

   | Parameter | Value |
   |---|---|
   | Epochs | 175 |
   | Image size | 736 |
   | Batch size | 16 |

   Early stopping is applied automatically via the `patience` parameter.

6. Locate the output weights at `runs/detect/train/weights/best.pt`.

---

### Step 2 — Export & Quantization (Google Colab)

1. Open the [Model Conversion Notebook](https://colab.research.google.com/drive/1KLmLk4IS4BvBmp4bLdRxp-fkse1mToCm?usp=sharing).
2. Upload `best.pt` from your local `YOLO11Trainer` folder to Colab session storage.
3. Run the export command:

   ```python
   model.export(format='tflite', imgsz=736, int8=True, data=yaml_path)
   ```

   > **INT8 Quantization** converts weights to 8-bit integers, optimising the model for the Android NPU. Ensure the export uses a representative calibration sample of ~100 images to preserve accuracy.

4. Download the resulting `.tflite` file from Colab.
5. Place the file in `src/main/assets/` within the Android Studio project.
6. Ensure the filename in the Kotlin source matches the new model file.
