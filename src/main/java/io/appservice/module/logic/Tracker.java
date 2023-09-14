package io.appservice.module.logic;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.appservice.core.http.HttpRequestRestAsync;
import io.appservice.core.statemachine.StateContext;
import io.appservice.core.statemachine.annotations.StateContextSettings;
import io.appservice.core.statemachine.annotations.StateEntry;
import io.appservice.core.statemachine.annotations.StateField;
import io.appservice.core.statemachine.annotations.StateThread;
import io.appservice.core.statemachine.annotations.StateTimer;
import io.appservice.core.util.Logger;
import io.appservice.core.util.Network;
import io.appservice.module.Device;
import io.appservice.module.ModuleApp;
import io.appservice.module.liberty.R;

import static io.appservice.module.Const.INTENT_KEY_REQUEST;

@StateContextSettings(
        recover = 0
)
public class Tracker extends StateContext {

    private static final String LOG_TAG = "IOAPP_Tracker";


    private static final int TRACK1_TIMER = 5000;
    private static final int TRACK2_TIMER = 10000;
    private static final int TRACK3_TIMER = 20000;

    private static final int WAIT = 0;
    private static final int RESOLVE = 5;
    private static final int TRACK = 6;
    private static final int TRACK_REST = 7;

    private static final int DEFAULT_TRACKER_PORT = 9500;


    private static final long WAIT_TIMER = 3 * 60 * 1000;


    @StateField
    private Long mLastTS = 0L;

    @StateField
    private String mServerAddress = null;

    private int mServerPort = DEFAULT_TRACKER_PORT;

    private Gson mGSON = new GsonBuilder().create();

    private static class AndroidInfo {
        private String sn;
        private String model;
        private String cpu;
        private String device;
        private String manufacturer;
        private String release;
        private String sdk;
        private String hw;
        private String android_id;
    }

    private static class DeviceInfo {
        private String customer;
        private String version;
        private String id;
        private String carrier;
        private List<String> capabilities = new ArrayList<>();
        private AndroidInfo android = new AndroidInfo();
    }


    private static class TrackRequest {
        private UUID id;
        private Long timestamp;
        private DeviceInfo info = new DeviceInfo();
    }

    private static class TrackResponse {
        private UUID id;
        private Long timestamp;
        private String request;
    }


    @StateTimer(states = {WAIT}, timeout = WAIT_TIMER)
    private Integer waitTimer(Context ctx) {
        return RESOLVE;
    }

    @StateEntry(states = {RESOLVE}, foreground = true, store = false)
    private Integer resolveEntry(Context ctx) {
        if (mServerAddress != null) {
            return TRACK;
        }
        return SAME_STATE;
    }

    @StateThread(states = {RESOLVE},
            onSuccessState = TRACK,
            onErrorState = WAIT
    )
    private class resolveThread implements StateContextThread {
        @Override
        public void run(Context ctx) throws Exception {
            mServerPort = DEFAULT_TRACKER_PORT;
            String tracker = ctx.getString(R.string.tracker);
            if (tracker.contains(":")) {
                String[] split = tracker.split(":");
                if (split.length == 2) {
                    tracker = split[0];
                    mServerPort = Integer.valueOf(split[1]);
                }
            }
            InetAddress[] addr = InetAddress.getAllByName(tracker);
            if (addr.length > 0) {
                mServerAddress = addr[0].getHostAddress();
            }
            Logger.i(LOG_TAG, "Using server " + mServerAddress);
        }

        @Override
        public void stop() {

        }
    }

    private TrackRequest getRequest(Context ctx, UUID id) throws Exception {
        ModuleApp moduleApp = ModuleApp.getIntance(ctx);
        Device.Info info = moduleApp.getDevice().getDeviceInfo();
        TrackRequest request = new TrackRequest();
        request.id = id;
        request.timestamp = mLastTS;
        request.info.customer = moduleApp.getDevice().getCustomerId();
        request.info.version = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        request.info.carrier = moduleApp.getDevice().getCarrier();
        request.info.capabilities = new LinkedList<>();
        request.info.capabilities.add("install");
        request.info.id = moduleApp.getDevice().getDeviceId();
        request.info.android.cpu = info.cpu;
        request.info.android.device = info.device;
        request.info.android.hw = info.hw;
        request.info.android.manufacturer = info.manufacturer;
        request.info.android.sn = info.sn;
        request.info.android.model = info.model;
        request.info.android.release = info.release;
        request.info.android.sdk = info.sdk;
        request.info.android.android_id = info.android_id;
        return request;
    }

    @StateEntry(states = {TRACK}, foreground = true, store = false)
    private Integer trackEntry(Context ctx) {
        Logger.d(LOG_TAG, "trackEntry");
        if (Network.getConnectivityStatus(ctx) == Network.TYPE_NOT_CONNECTED) {
            return WAIT;
        } else {
            return SAME_STATE;
        }
    }

    @StateThread(states = {TRACK},
            onSuccessState = WAIT,
            onErrorState = TRACK_REST
    )
    private class TrackThread implements StateContextThread {

        private DatagramSocket mSocket;

        private boolean receive(Context ctx, UUID id, int timeout) throws Exception {
            mSocket.setSoTimeout(timeout);
            byte[] buf = new byte[1472];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                mSocket.receive(dp);
                TrackResponse response = mGSON.fromJson(new String(dp.getData(), 0, dp.getLength()), TrackResponse.class);
                if (id.equals(response.id)) {
                    Logger.d(LOG_TAG, "Response received");
                    mLastTS = response.timestamp;
                    Logger.w(LOG_TAG, "New TS " + mLastTS);
                    if (response.request != null) {
                        Intent task = new Intent(TaskLoader.ACTION_TASK);
                        task.putExtra(INTENT_KEY_REQUEST, response.request);
                        LocalBroadcastManager.getInstance(ctx).sendBroadcast(task);
                    }
                    return true;
                }
            } catch (SocketTimeoutException ignore) {
            }
            return false;
        }

        @Override
        public void run(final Context ctx) throws Exception {
            try {
                UUID id = UUID.randomUUID();
                mSocket = new DatagramSocket();
                byte data[] = mGSON.toJson(getRequest(ctx, id)).getBytes();
                InetAddress addr = InetAddress.getByName(mServerAddress);
                DatagramPacket packet = new DatagramPacket(data, data.length, addr, mServerPort);

                Logger.d(LOG_TAG, "Send 1 track request to " + mServerAddress);
                mSocket.send(packet);
                if (receive(ctx, id, TRACK1_TIMER)) {
                    return;
                }
                Logger.d(LOG_TAG, "Send 2 track request to " + mServerAddress);
                mSocket.send(packet);
                if (receive(ctx, id, TRACK2_TIMER)) {
                    return;
                }
                Logger.d(LOG_TAG, "Send 3 track request to " + mServerAddress);
                mSocket.send(packet);
                if ( receive(ctx, id, TRACK3_TIMER)){
                    return;
                }
                throw new RuntimeException("UDP tracking unreachable");
            } catch (IOException e) {
                StringWriter out = new StringWriter();
                PrintWriter writer = new PrintWriter(out);
                e.printStackTrace(writer);
                Logger.e(LOG_TAG, "Socket exception " + out.toString());
            } finally {
                if (mSocket != null) {
                    mSocket.close();
                }

            }
        }

        @Override
        public void stop() {
            if (mSocket != null) {
                DatagramSocket socket = mSocket;
                mSocket = null;
                socket.close();
            }
        }
    }

    @StateEntry(states = {TRACK_REST})
    private Integer trackRestEnter(Context ctx) {
        Logger.e(LOG_TAG, "UDP tracking not responding, try REST");
        return SAME_STATE;
    }

    @StateThread(states = {TRACK_REST},
            onSuccessState = WAIT,
            onErrorState = WAIT
    )
    private class TrackRestThread extends HttpRequestRestAsync {
        @Override
        protected Object getRequest() throws Exception {
            return Tracker.this.getRequest(getContext(), UUID.randomUUID());
        }

        @Override
        protected String getURL() throws Exception {
            return "http://" + mServerAddress + ":" + (mServerPort + 1) + "/track";
        }

        @Override
        protected void postprocess() throws Exception {
            TrackResponse response = getResponse(TrackResponse.class);
            mLastTS = response.timestamp;
            if (response.request != null) {
                Intent task = new Intent(TaskLoader.ACTION_TASK);
                task.putExtra(INTENT_KEY_REQUEST, response.request);
                getContext().sendBroadcast(task);
            }
        }
    }
}

