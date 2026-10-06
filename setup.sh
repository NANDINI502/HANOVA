#!/usr/bin/env bash
# Downloads the sherpa-onnx Android library + the offline Mandarin ASR model.
# Run once from the project root:  bash setup.sh
set -e
VER=1.13.7
MODEL=sherpa-onnx-streaming-paraformer-bilingual-zh-en

echo "1/4  sherpa-onnx AAR v$VER (~47 MB)"
curl -L -o app/libs/sherpa-onnx-$VER.aar \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VER/sherpa-onnx-$VER.aar

echo "2/4  ASR model ($MODEL)"
curl -L -o /tmp/$MODEL.tar.bz2 \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$MODEL.tar.bz2
tar xjf /tmp/$MODEL.tar.bz2 -C /tmp
mkdir -p app/src/main/assets/$MODEL
# only the int8 files are needed (keeps the APK small)
cp /tmp/$MODEL/encoder.int8.onnx /tmp/$MODEL/decoder.int8.onnx /tmp/$MODEL/tokens.txt \
   app/src/main/assets/$MODEL/
echo "3/4  Accurate second-pass model (SenseVoice, ~166 MB)"
SV=sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09
curl -L -o /tmp/$SV.tar.bz2 \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$SV.tar.bz2
tar xjf /tmp/$SV.tar.bz2 -C /tmp
mkdir -p app/src/main/assets/$SV
cp /tmp/$SV/model.int8.onnx /tmp/$SV/tokens.txt app/src/main/assets/$SV/

echo "4/4  Nunito font (OFL, for the UI)"
mkdir -p app/src/main/res/font
curl -L -o app/src/main/res/font/nunito.ttf \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/nunito/Nunito%5Bwght%5D.ttf"

echo "Done. Open the folder in Android Studio and hit Run."
