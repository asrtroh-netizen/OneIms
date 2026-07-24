package com.onetools.app.recorder;

interface IShellRecorder {
    String ping();
    /** @return 0 ok, otherwise error code; detail in lastError() */
    int startRecording(String absolutePath, int preferredSource);
    void stopRecording();
    boolean isRecording();
    String lastError();
    String activeSourceName();
    /** Probe which MediaRecorder.AudioSource values initialize under shell UID. */
    String probeSources();
}
