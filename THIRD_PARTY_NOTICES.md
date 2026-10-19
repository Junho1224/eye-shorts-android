# Third-party components

- **MediaPipe Tasks Vision 0.10.35**, Google, Apache License 2.0. Source and license: https://github.com/google-ai-edge/mediapipe
- **MediaPipe Face Landmarker model**, Google, Apache License 2.0. Downloaded without modification from the official model location: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task . Model documentation: https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker#models . The included detector, face mesh and blendshape model cards identify Apache License 2.0: [BlazeFace short range](https://storage.googleapis.com/mediapipe-assets/MediaPipe%20BlazeFace%20Model%20Card%20%28Short%20Range%29.pdf), [Face Mesh V2](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Face%20Mesh%20V2.pdf), [Blendshape V2](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Blendshape%20V2.pdf). SHA-256: `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`.
- **AndroidX Activity, Core, Lifecycle, CameraX**, Android Open Source Project, Apache License 2.0. https://android.googlesource.com/platform/frameworks/support/
- **Gradle Wrapper 9.1.0**, Gradle, Apache License 2.0. https://github.com/gradle/gradle . Wrapper JAR is distributed as an unmodified binary from the official Gradle repository.
- **JUnit 4.13.2**, Eclipse Public License 1.0, test dependency only. https://github.com/junit-team/junit4

The downloaded model is bundled in `app/src/main/assets/face_landmarker.task`. No sample application source, screenshots, videos or YouTube content is bundled. Third-party components retain their respective licenses. See the corresponding upstream repositories and the license text in `licenses/`.
