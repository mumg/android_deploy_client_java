package io.appservice.module.logic;

import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.util.Stack;

import io.appservice.core.http.HttpRequestRestAsync;
import io.appservice.core.statemachine.StateContext;
import io.appservice.core.statemachine.annotations.StateEntry;
import io.appservice.core.statemachine.annotations.StateEvent;
import io.appservice.core.statemachine.annotations.StateField;
import io.appservice.core.statemachine.annotations.StateThread;
import io.appservice.core.statemachine.annotations.StateTimer;
import io.appservice.core.util.Logger;
import io.appservice.core.util.Network;

import static io.appservice.module.Const.INTENT_KEY_DATA;
import static io.appservice.module.Const.INTENT_KEY_REQUEST;
import static io.appservice.module.Const.INTENT_KEY_URL;

public class TaskLoader extends StateContext{

    private static final String LOG_TAG = "IOAPP_TaskLoader";

    public static final String ACTION_TASK = "io.appservice.module.TASK";


    private static final int TIMEOUT_SERVER = 60000; // 1 min

    private Gson mGSON = new GsonBuilder().create();

    @StateField
    private Stack<String> mTasksQueue = new Stack<>();

    @StateField
    private String mTaskURL;

    private static class Task{
        private String type;
        private String confirmURL;
        private JsonElement data;
    }

    private static final int IDLE = 0;
    private static final int PREPARE = 1;
    private static final int QUERY = 2;
    private static final int WAIT_INTERNET = 3;
    private static final int WAIT_SERVER = 4;

    @StateEntry(states = {IDLE})
    private Integer idleEntry(Context ctx){
        mTaskURL = null;
        Logger.d(LOG_TAG, "idleEntry");
        if ( !mTasksQueue.empty()){
            return QUERY;
        }
        return SAME_STATE;
    }

    @StateEvent(states = {IDLE}, id=ACTION_TASK)
    private Integer idleEvent(Context ctx, Intent intent){
        String req = intent.getStringExtra(INTENT_KEY_REQUEST);
        Logger.w(LOG_TAG, "idleEvent with request" + req);
        mTasksQueue.push(req);
        return PREPARE;
    }

    @StateEvent(states = {PREPARE, QUERY, WAIT_INTERNET, WAIT_SERVER}, id=ACTION_TASK)
    private Integer xEvent(Context ctx, Intent intent){
        String req = intent.getStringExtra(INTENT_KEY_REQUEST);
        Logger.w(LOG_TAG, "idleEvent with request" + req);
        mTasksQueue.push(req);
        return SAME_STATE;
    }



    @StateEntry(states = {PREPARE})
    private Integer prepareEntry(Context ctx){
        Logger.d(LOG_TAG, "prepareEntry");
        mTaskURL = mTasksQueue.pop();
        if ( mTaskURL == null ){
            return IDLE;
        }
        if (Network.getConnectivityStatus(ctx) == Network.TYPE_NOT_CONNECTED){
            return WAIT_INTERNET;
        }else{
            return QUERY;
        }
    }

    @StateEntry(states = {QUERY}, foreground = true, store = false)
    private Integer queryEntry(Context ctx){
        Logger.d(LOG_TAG, "queryEntry");
        return SAME_STATE;
    }

    @StateThread(states = {QUERY},
            onSuccessState = IDLE,
            onErrorState = IDLE,
            onAbortState = WAIT_SERVER
    )
    private class Query extends HttpRequestRestAsync {
        @Override
        protected Object getRequest() throws Exception {
            return null;
        }

        @Override
        protected String getURL() throws Exception {
            return mTaskURL;
        }

        @Override
        protected void postprocess() {
                        Task task = getResponse(Task.class);
            Logger.i(LOG_TAG, "querySuccess");
            Intent handle = new Intent(task.type);
            handle.putExtra(INTENT_KEY_URL, task.confirmURL);
            handle.putExtra(INTENT_KEY_DATA, mGSON.toJson(task.data));
            getContext().sendBroadcast(handle);
        }
    }

    @StateTimer(states = {WAIT_SERVER}, timeout = TIMEOUT_SERVER)
    private Integer waitTimer(Context ctx){
        Logger.d(LOG_TAG, "waitTimer");
        return QUERY;
    }

    @StateEntry(states = {WAIT_INTERNET})
    private Integer waitInternetEntry(Context ctx){
        Logger.d(LOG_TAG, "waitInternetEntry");
        if ( Network.getConnectivityStatus(ctx) != Network.TYPE_NOT_CONNECTED){
            return QUERY;
        }
        return SAME_STATE;
    }

    @StateEvent(states = {WAIT_INTERNET}, id="android.net.conn.CONNECTIVITY_CHANGE")
    private Integer waitInternetEvent(Context ctx, Intent intent){
        Logger.d(LOG_TAG, "waitInternetEvent");
        if ( Network.getConnectivityStatus(ctx) != Network.TYPE_NOT_CONNECTED){
            return QUERY;
        }
        return SAME_STATE;
    }
}
