package io.appservice.module.logic;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayDeque;

import io.appservice.core.http.HttpRequestRestAsync;
import io.appservice.core.statemachine.StateContext;
import io.appservice.core.statemachine.annotations.StateContextSettings;
import io.appservice.core.statemachine.annotations.StateEntry;
import io.appservice.core.statemachine.annotations.StateEvent;
import io.appservice.core.statemachine.annotations.StateField;
import io.appservice.core.statemachine.annotations.StateThread;
import io.appservice.core.statemachine.annotations.StateTimer;
import io.appservice.core.util.Hash;
import io.appservice.core.util.Logger;
import io.appservice.core.util.Network;

@StateContextSettings(
    recover = Confirm.IDLE,
    crash = Confirm.NEXT
)
public class Confirm extends StateContext {

    public static final String ACTION = "io.appservice.module.CONFIRM_URL";

    private static final String LOG_TAG = "IOAPP_Confirm";
    public static final int IDLE = 0;
    private static final int CONFIRM_URL = 1;
    private static final int CONFIRM_INTENT = 2;
    private static final int RETRY = 2;
    private static final int WAIT_CONNECTION = 3;
    public static final int NEXT = 4;


    private static final int WAIT_CONNECTION_TIMEOUT = 300000;
    private static final int RETRY_TIMEOUT = 15 * 60000;
    private static final int CONFIRM_TIMEOUT = 300000;

    private static class ConfirmRequest{
        private String mURL;
        private String mData;
        private ConfirmRequest(String url, String data){
            mURL = url;
            mData = data;
        }

        private String getURL(){
            return mURL;
        }

        private String getData(){
            return mData;
        }

        @Override
        public int hashCode() {
            return Hash.calc(mURL, mData);
        }
    }

    private class RequestQueue {
        private ArrayDeque<ConfirmRequest> mQueue = new ArrayDeque<>();

        private void push(ConfirmRequest request) {
            mQueue.push(request);
        }

        public int size() {
            return mQueue.size();
        }

        private ConfirmRequest pop() {
            return mQueue.pop();
        }

        private ConfirmRequest peek() { return mQueue.peek(); }

        @Override
        public int hashCode() {
            return mQueue.hashCode();
        }
    }

    @StateField
    private RequestQueue mQueue = new RequestQueue();

    @StateEntry(states = {IDLE})
    private Integer idleEntry(Context ctx){
        Logger.d(LOG_TAG, "idleEntry");
        if ( mQueue.size() > 0 ){
            ConfirmRequest request = mQueue.peek();
            if ( request.getURL().startsWith("intent://")){
                return CONFIRM_INTENT;
            }
            return CONFIRM_URL;
        }
        return SAME_STATE;
    }

    @StateEvent(states = {IDLE}, id=ACTION)
    private Integer idleEvent(Context ctx, Intent intent){
        Logger.d(LOG_TAG, "idleEntry");
        appendEvent(ctx, intent);
        return CONFIRM_URL;
    }

    @StateEvent(states = {CONFIRM_URL, RETRY, WAIT_CONNECTION}, id=ACTION)
    private Integer appendEvent(Context ctx, Intent intent){
        Logger.d(LOG_TAG, "appendEvent");
        mQueue.push(new ConfirmRequest(intent.getStringExtra("url"), intent.getStringExtra("data")));
        return SAME_STATE;
    }

    @StateEntry(states = {CONFIRM_URL}, foreground = true)
    private Integer confirmEntry(Context ctx) {
        Logger.d(LOG_TAG, "confirmEntry");
        if ( mQueue.size() == 0 ){
            return IDLE;
        }
        if (Network.getConnectivityStatus(ctx) == Network.TYPE_NOT_CONNECTED) {
            return WAIT_CONNECTION;
        }
        return SAME_STATE;
    }

    @StateThread(states = {CONFIRM_URL},
            onErrorState = RETRY,
            onSuccessState = NEXT
    )
    private class ConfirmThread extends HttpRequestRestAsync {

        @Override
        protected Object getRequest() throws Exception {
            return mQueue.peek().getData();
        }

        @Override
        protected String getURL() throws Exception {
            return mQueue.peek().getURL();
        }

    }

    @StateTimer(states = {CONFIRM_URL}, timeout = CONFIRM_TIMEOUT)
    private Integer confirmTimer(Context ctx) {
        Logger.d(LOG_TAG, "confirmTimer");
        return RETRY;
    }

    @StateEntry(states = {RETRY})
    private Integer retryEntry(Context ctx) {
        Logger.d(LOG_TAG, "retryEntry");
        return SAME_STATE;
    }

    @StateTimer(states = {RETRY}, timeout = RETRY_TIMEOUT)
    private Integer retryTimeout(Context ctx) {
        Logger.d(LOG_TAG, "retryTimeout");
        return CONFIRM_URL;
    }

    @StateEntry(states = {WAIT_CONNECTION})
    private Integer waitConnectionEntry(Context ctx) {
        Logger.d(LOG_TAG, "waitConnectionEntry");
        if (Network.getConnectivityStatus(ctx) != Network.TYPE_NOT_CONNECTED) {
            return CONFIRM_URL;
        }
        return SAME_STATE;
    }

    @StateTimer(states = {WAIT_CONNECTION}, timeout = WAIT_CONNECTION_TIMEOUT)
    private Integer waitConnectionTimer(Context ctx) {
        Logger.d(LOG_TAG, "waitConnectionTimer");
        return WAIT_CONNECTION;
    }

    @StateEvent(states = {WAIT_CONNECTION}, id = "android.net.conn.CONNECTIVITY_CHANGE")
    private Integer waitConnectionEvent(Context ctx, Intent intent) {
        Logger.d(LOG_TAG, "waitConnectionEvent");
        return CONFIRM_URL;
    }

    @StateEntry(states = {NEXT})
    private Integer nextEntry(Context ctx){
        Logger.d(LOG_TAG, "nextEntry");
        mQueue.pop();
        return IDLE;
    }


    @StateEntry(states = {CONFIRM_INTENT})
    private Integer confirmIntentEntry(Context ctx){
        String intent = mQueue.peek().getURL().replace("intent://", "");
        ctx.sendBroadcast(new Intent(intent));
        return NEXT;
    }
}
