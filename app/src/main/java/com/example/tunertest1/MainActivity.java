package com.example.tunertest1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.ImageLinearGauge;
import com.github.anastr.speedviewlib.SpeedView;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.exception.util.ExceptionContextProvider;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.IOException;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {

    // Instance Variables
    private SpeedView differenceDisplay;
    private TextView displayNote;
    private ImageLinearGauge tuneProgressBar;
    private int progressBarTimer = 1;
    private float lastPitch;
    private int pitchTimer = 3;
    private ImageView cr_scale;
    private TextView octive;

    private final double[] noteFrequencies = new double[]{  7902,    7459,    7040,    6645,    6272,
               5920, 5587.65, 5274.04, 4978.03, 4698.64, 4434.92, 4186.01, 3951.07, 3729.31, 3520.00,
            3322.44, 3135.96, 2959.96, 2793.83, 2637.02, 2489.02, 2349.32, 2217.46, 2093.00, 1975.53,
            1864.66, 1760.00, 1661.22, 1567.98, 1479.98, 1396.91, 1318.51, 1244.51, 1174.66, 1108.73,
            1046.50, 987.767, 932.328, 880.000, 830.609, 783.991, 739.989, 698.456, 659.255, 622.254,
            587.330, 554.365, 523.251, 493.883, 466.164, 440.000, 415.305, 391.995, 369.994, 349.228,
            329.628, 311.127, 293.665, 277.183, 261.626, 246.942, 233.082, 220.000, 207.652, 195.998,
            184.997, 174.614, 164.814, 155.563, 146.832, 138.591, 130.813, 123.471, 116.541, 110.000,
            103.826, 97.9989, 92.4986, 87.3071, 82.4069, 77.7817, 73.4162, 69.2957, 65.4064, 61.7354,
            58.2705, 55.0000, 51.9131, 48.9994, 46.2493, 43.6535, 41.2034, 38.8909, 36.7081, 34.6478,
            32.7032, 30.8677, 29.1352, 27.5000, 25.9565, 24.4997, 23.1247, 21.8268, 20.6017, 19.4454,
            18.3540, 17.3239, 16.3516};


    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RECORD_PERMISSION:
                permissionToRecordAccepted = grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();
    }

    // Constants
    final static int RECORD_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wireWidgets();

        ActivityCompat.requestPermissions(this, permissions,
                RECORD_PERMISSION);

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,0);

        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult result, AudioEvent e) {
                final float pitchInHz = result.getPitch();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //display.setText("" + findNote(pitchInHz));
                        if (pitchTimer == 3) {
                            lastPitch = pitchInHz;
                            pitchTimer = 0;
                        }
                        if (pitchInHz == -1) {
                            pitchTimer++;
                        }
                        double diff = findScaledDiff(roundPitch(lastPitch, pitchInHz));
                        setDisplay(diff);
                        //displayNote.setText(findNote(roundPitch(lastPitch, pitchInHz)));
                        octive.setText("" + getOctive(pitchInHz));
                        cr_scale.setRotation(getRotationAngle(findNote(roundPitch(lastPitch, pitchInHz))));
                        if (Math.abs(findScaledDiff(roundPitch(lastPitch, pitchInHz))) <= 1
                                 && progressBarTimer < 50) {
                            tuneProgressBar.speedPercentTo(progressBarTimer*6);
                            progressBarTimer++;
                            if (tuneProgressBar.getCurrentSpeed() >= 90) {
                                Toast.makeText(MainActivity.this, "In tune! "+ findNote(roundPitch(lastPitch, pitchInHz)), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            tuneProgressBar.speedPercentTo(0, 1000);
                            progressBarTimer = 1;
                        }
                        Log.d("tag", "" + findScaledDiff(roundPitch(lastPitch, pitchInHz)));
                    }
                });
            }
        };
        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
        dispatcher.addAudioProcessor(p);
        new Thread(dispatcher,"Audio Dispatcher").start();

    }

    private void wireWidgets() {
        differenceDisplay = findViewById(R.id.speedView);
        displayNote = findViewById(R.id.textiew_displayNote);
        tuneProgressBar = findViewById(R.id.tuneProgressBar);
        cr_scale = findViewById(R.id.cr_image);
        octive = findViewById(R.id.octiveDisplay);
    }

    private void setDisplay(double diff) {
        if (Math.abs(diff) <= 1) {
            differenceDisplay.speedPercentTo(50, 300);
        } else if (Math.abs(diff) <= 2) {
            if (diff > 0) {
                differenceDisplay.speedPercentTo(60, 300);
            } else {
                differenceDisplay.speedPercentTo(40, 300);
            }
        } else if (Math.abs(diff) <= 3) {
            if (diff > 0) {
                differenceDisplay.speedPercentTo(70, 300);
            } else {
                differenceDisplay.speedPercentTo(30, 300);
            }
        } else if (Math.abs(diff) <= 4) {
            if (diff > 0) {
                differenceDisplay.speedPercentTo(80, 300);
            } else {
                differenceDisplay.speedPercentTo(20, 300);
            }
        } else {
            if (diff > 0) {
                differenceDisplay.speedPercentTo(90, 300);
            } else {
                differenceDisplay.speedPercentTo(10, 300);
            }
        }
    }

    private String getOctive(double f) {
        double minDifference = 10;
        int index = 0;
        if (f!= -1) {
            for (int i = 0; i < noteFrequencies.length; i++) {
                if (Math.abs(f - noteFrequencies[i]) <= minDifference) {
                    minDifference = Math.abs(f - noteFrequencies[i]);
                    index = i;
                }
            }
            if (index <= 11) {
                return "8";
            } else if (index <= 11+12) {
                return "7";
            } else if (index <= 11+12*2) {
                return "6";
            } else if (index <= 11+12*3) {
                return "5";
            } else if (index <= 11+12*4) {
                return "4";
            } else if (index <= 11+12*5) {
                return "3";
            } else if (index <= 11+12*6) {
                return "2";
            } else if (index <= 11+12*7) {
                return "1";
            }
        }
        return " ";
    }

    private float getRotationAngle(String note) {
        if (note.equals("C")) {
            return 0;
        } else if (note.equals("C♯")) {
            return -30;
        } else if (note.equals("D")) {
            return -60;
        } else if (note.equals("E♭")) {
            return -90;
        } else if (note.equals("E")) {
            return -120;
        } else if (note.equals("F")) {
            return -150;
        } else if (note.equals("F♯")) {
            return 180;
        } else if (note.equals("G")) {
            return 150;
        } else if (note.equals("G♯")) {
            return 120;
        } else if (note.equals("A")) {
            return 90;
        } else if (note.equals("B♭")) {
            return 60;
        } else {
            return 30;
        }
    }

    private double roundPitch(double lastf, double thisf) {
        if (thisf == -1 && lastPitch != -1) {
            return lastf;
        }
        return thisf;
    }

    private String findNote(double frequency) {
        double minDifference = 10;
        int index = 0;
        if (frequency != -1) {
            for (int i = 0; i < noteFrequencies.length; i++) {
                if (Math.abs(frequency - noteFrequencies[i]) <= minDifference) {
                    minDifference = Math.abs(frequency - noteFrequencies[i]);
                    index = i;
                }
            }
            return getNoteName(index);
        } else {
            return " ";
        }
    }

    private int findScaledDiff(double frequency) {  // percent off from a scale of 1 to 10
        double minDifference = 10;
        int index = 0;

        if (frequency != -1) {
            for (int i = 0; i < noteFrequencies.length; i++) {
                if (Math.abs(frequency - noteFrequencies[i]) < minDifference) {
                    minDifference = Math.abs(frequency - noteFrequencies[i]);
                    index = i;
                }
            }
            if (index > 0) {
                double nextFrequency = noteFrequencies[index-1];
                double thisFrequency = noteFrequencies[index];
                double lastFrequency = noteFrequencies[index+1];
                if (frequency >= lastFrequency && frequency <= thisFrequency) {
                    double diff = thisFrequency - lastFrequency;
                    double scaleSection = diff / 5;
                    int n = 1;
                    while (thisFrequency - n*scaleSection > frequency) {
                        thisFrequency -= n*scaleSection;
                        n++;
                    }
                    return +n;
                } else if (frequency >= thisFrequency && frequency <= nextFrequency) {
                    double diff = nextFrequency - thisFrequency;
                    double scaleSection = diff / 5;
                    int n = 1;
                    while (thisFrequency + n*scaleSection < frequency) {
                        thisFrequency += n*scaleSection;
                        n++;
                    }
                    return -n;
                }
            }
        }
        return -10;

    }

    private String getNoteName(int index) {
        if (index%12 == 11) {
            return "C";
        } else if (index%12 == 10) {
            return "C♯";
        } else if (index%12 == 9) {
            return "D";
        } else if (index%12 == 8) {
            return "E♭";
        } else if (index%12 == 7) {
            return "E";
        } else if (index%12 == 6) {
            return "F";
        } else if (index%12 == 5) {
            return "F♯";
        } else if (index%12 == 4) {
            return "G";
        } else if (index%12 == 3) {
            return "G♯";
        } else if (index%12 == 2) {
            return "A";
        } else if (index%12 == 1) {
            return "B♭";
        } else {
            return "B";
        }
    }

}

//  ♯   ♭