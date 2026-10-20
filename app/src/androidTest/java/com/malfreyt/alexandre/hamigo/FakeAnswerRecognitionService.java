package com.malfreyt.alexandre.hamigo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Binder fixture hosted in the test APK's own process. It uses only Android/Java APIs:
 * that process cannot use the Kotlin runtime supplied by the target application's APK.
 * It synthesizes callbacks and never opens AudioRecord or the microphone.
 */
public final class FakeAnswerRecognitionService extends RecognitionService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Callback active;

    @Override
    protected void onStartListening(Intent recognizerIntent, final Callback listener) {
        Log.i("HamigoFakeSpeech", "startListening");
        main.removeCallbacksAndMessages(null);
        active = listener;
        // This fixture synthesizes text and never consumes microphone data. An engine's
        // caller-attributed context prevents RecognitionService from starting a redundant
        // audio data-delivery session for this background test APK. Preflight permission
        // checks on the client still run before onStartListening is called.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            createContext(new android.content.ContextParams.Builder()
                    .setNextAttributionSource(listener.getCallingAttributionSource()).build());
        }
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (active != listener) return;
                try {
                    Log.i("HamigoFakeSpeech", "readyForSpeech");
                    listener.readyForSpeech(new Bundle());
                    listener.beginningOfSpeech();
                    listener.rmsChanged(8f);
                    listener.partialResults(transcript("alpha"));
                } catch (RemoteException error) {
                    Log.w("HamigoFakeSpeech", "Client disconnected before ready callback", error);
                }
            }
        }, 160);
    }

    @Override
    protected void onStopListening(final Callback listener) {
        Log.i("HamigoFakeSpeech", "stopListening");
        main.removeCallbacksAndMessages(null);
        if (active != listener) return;
        try {
            listener.endOfSpeech();
        } catch (RemoteException error) {
            Log.w("HamigoFakeSpeech", "Client disconnected before end callback", error);
        }
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (active != listener) return;
                active = null;
                try {
                    listener.results(transcript("alpha bravo charlie"));
                } catch (RemoteException error) {
                    Log.w("HamigoFakeSpeech", "Client disconnected before result callback", error);
                }
            }
        }, 1600);
    }

    @Override
    protected void onCancel(Callback listener) {
        Log.i("HamigoFakeSpeech", "cancel");
        main.removeCallbacksAndMessages(null);
        active = null;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        active = null;
        super.onDestroy();
    }

    private static Bundle transcript(String text) {
        Bundle result = new Bundle();
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,
                new ArrayList<String>(Collections.singletonList(text)));
        return result;
    }
}
