#!/bin/bash
echo "Installing ..."
adb install -r SMSer_debug.apk

echo "Starting ..."
adb shell am start -n com.example.posix.smser/.MainActivity

echo "Results ..."
sleep 5
adb logcat -d | grep SMSer
