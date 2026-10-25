image_embedder.onnx
    The phone app's MediaPipe image embedder (MobileNet-V3 small, app/src/main/assets/image_embedder.tflite,
    Apache 2.0) converted with tools/convert_embedder.sh. Input: 1x224x224x3 float RGB in 0..1, the whole
    photo stretched to 224x224. Output: 1x1024, L2-normalized by the app. Matches MediaPipe's embeddings
    to a cosine similarity of about 0.997 on test photos.

yunet.onnx
    OpenCV Zoo's YuNet face detector, face_detection_yunet_2023mar.onnx (MIT license):
    https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
