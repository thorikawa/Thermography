package com.polysfactory.thermo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class GlassConnection {

    private static final UUID UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private static final UUID UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private static final UUID UUID_SSP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter mBluetoothAdapter;

    private volatile SocketState mState = SocketState.READY;

    private BluetoothSocket mSocket;

    private OutputStream mOutputStream;

    private InputStream mInputStream;

    private GlassConnectionListener mGlassConnectionListener;

    private static final int BUFFER_LENGTH = 1024;

    private static final byte[] BUFFER = new byte[BUFFER_LENGTH];

    public GlassConnection(BluetoothAdapter bluetoothAdapter) {
        mBluetoothAdapter = bluetoothAdapter;
    }

    public synchronized void stop() {
        close();
    }

    public void startReaderThread() {
        mGlassReaderThread.start();
    }

    protected synchronized void connect(String address) throws IOException {

        if (mState != SocketState.READY) {
            Log.w(C.TAG, "Already connected");
            return;
        }

        Log.d(C.TAG, "connect:" + address);
        mState = SocketState.CONNECTING;

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        try {
            mSocket = createSocket(device);
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();
            mState = SocketState.CONNECTED;
        } catch (IOException exp) {
            close();
            throw exp;
        }
    }

    private BluetoothSocket createSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(UUID_SSP);
    }

    protected void close() {

        if (mState == SocketState.READY) {
            Log.d(C.TAG, "Already closed");
            return;
        }

        mState = SocketState.READY;
        try {
            mSocket.close();
        } catch (IOException e) {
            Log.w(C.TAG, "close error", e);
        }
        mSocket = null;

    }

    public void write(byte[] buf) throws IOException {
        if (mState != SocketState.CONNECTED) {
            throw new IOException("not connected");
        }

        Log.d(C.TAG, "write:" + Arrays.toString(buf));
        mOutputStream.write(buf);
    }

    private static final byte START_BYTE = '$';
    private static final byte END_BYTE = '*';
    private ByteArrayOutputStream baos;
    private DataOutputStream w;

    private Thread mGlassReaderThread = new Thread() {
        public void run() {
            try {
                while (true) {
                    if (mState != SocketState.CONNECTED) {
                        throw new IOException();
                    }
                    // TODO: read from mInputStream
                    int n = mInputStream.read(BUFFER);
                    for (int i = 0; i < n; i++) {
                        byte b = BUFFER[i];
                        if (b == START_BYTE) {
                            baos = new ByteArrayOutputStream();
                            w = new DataOutputStream(baos);
                        } else if (b == END_BYTE) {
                            if (w != null) {
                                w.flush();
                                byte[] byteArray = baos.toByteArray();
                                String s = new String(byteArray);
                                String[] stringData = s.split(",");
                                float[] data = new float[64];
                                Log.d(C.TAG, "data:" + s);
                                Log.d(C.TAG, "length:" + stringData.length);
                                for (int j = 0; j < Math.min(stringData.length, 64); j++) {
                                    data[j] = Float.parseFloat(stringData[j]);
                                }
                                if (mGlassConnectionListener != null) {
                                    mGlassConnectionListener.onReceivedTemp(data);
                                }
                            }
                        } else {
                            if (w != null) {
                                w.write(b);
                            }
                        }
                    }
                    // Log.i(C.TAG, n + " bytes read");
                }
            } catch (IOException e) {
                Log.w(C.TAG, "read error", e);
            }
        };
    };

    public static enum SocketState {
        READY, CONNECTING, CONNECTED
    }

    public void setGlassConnectionListener(GlassConnectionListener glassConnectionListener) {
        this.mGlassConnectionListener = glassConnectionListener;
    }

    public boolean isConnected() {
        return mState == SocketState.CONNECTED;
    }
}
