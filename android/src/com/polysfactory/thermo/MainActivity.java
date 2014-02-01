package com.polysfactory.thermo;

import java.io.IOException;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements GlassConnectionListener, Callback {

    private GlassConnection mGlassConnection;
    private static final String TARGET = "22:13:04:16:58:32";
    private SurfaceHolder mSurfaceHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mGlassConnection = new GlassConnection(bluetoothAdapter);
        mGlassConnection.setGlassConnectionListener(this);

        SurfaceView mSurfaceView = (SurfaceView) findViewById(R.id.fd_activity_surface_view);
        mSurfaceView.getHolder().addCallback(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            mGlassConnection.connect(TARGET);
            mGlassConnection.startReaderThread();
        } catch (IOException e) {
            Log.w(C.TAG, "error while connecting", e);
        }
        t.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGlassConnection.close();
        t.interrupt();
        try {
            t.join();
        } catch (InterruptedException e) {
            Log.w(C.TAG, "thread join error", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private float[] mTempData;

    @Override
    public void onReceivedTemp(float[] temp) {
        synchronized (this) {
            mTempData = temp;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (this) {
            mSurfaceHolder = holder;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (this) {
            mSurfaceHolder = null;
        }
    }

    private static final int GRID_WIDTH = 40;
    private static final int GRID_HEIGHT = 40;
    private static final int Y_OFFSET = 100;

    Thread t = new Thread() {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                synchronized (this) {
                    if (mSurfaceHolder != null) {
                        Canvas canvas = mSurfaceHolder.lockCanvas();
                        if (mTempData != null && mTempData.length == 64) {
                            for (int j = 0; j < 16; j++) {
                                for (int i = 0; i < 4; i++) {
                                    int index = j * 4 + i;
                                    float f = mTempData[index];
                                    int x = j * GRID_WIDTH;
                                    int y = i * GRID_HEIGHT + Y_OFFSET;
                                    Paint p = new Paint();
                                    int red = (int) ((f - 15.0) * 10);
                                    int blue = (int) ((40.0 - f) * 10);
                                    red = Math.min(255, red);
                                    blue = Math.min(255, blue);
                                    p.setColor(Color.rgb(red, 0, blue));
                                    canvas.drawRect(new Rect(x, y, x + GRID_WIDTH, y + GRID_HEIGHT), p);
                                }
                            }
                        } else {
                            Log.d(C.TAG, "temp data is not available. skip frame...");
                        }
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        };
    };
}
