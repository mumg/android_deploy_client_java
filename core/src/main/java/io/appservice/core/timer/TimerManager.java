package io.appservice.core.timer;

import android.content.Context;
import android.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.appservice.core.CoreApp;
import io.appservice.core.util.Logger;

import static io.appservice.core.timer.TimerLong.MIN_BACKGROUND_TIMEOUT;

public class TimerManager {

    public interface Handler {
        void handle(Context ctx, String id);
    }

    public interface Scope {
        boolean isValid();
    }

    public interface Setter{
        void set(String id, Long timestamp);
    }

    private Context mCtx;

    private static final String LOG_TAG = "IOAPP_TimerManager";

    private final Map<String, TimerHandler> mHandlers = new HashMap<>();

    private Timer mShortTimer;
    private Timer mLongTimer;

    private class TimerHandler {
        private Handler mHandler;
        private final Map<String, Pair< Long , Scope > > mTimers = new HashMap<>();

        private TimerHandler(Handler handler) {
            mHandler = handler;
        }

        private void check(Context ctx, Long cts) {
            Set<String> fired = new HashSet<>();
            synchronized (mTimers) {
                for (Map.Entry<String, Pair< Long , Scope >> timer : mTimers.entrySet()) {
                    if (timer.getValue().first < cts) {
                        fired.add(timer.getKey());
                    }
                }
                for ( String id : fired ){

                    Pair< Long , Scope > tm = mTimers.remove(id);
                    cancel(tm.first);
                }
            }
            for ( String id : fired ){
                mHandler.handle(ctx, id);
            }
        }

        private void start(String id, Long timestamp, Scope scope, Setter setter ) {
            synchronized (mTimers) {
                if ( mTimers.containsKey(id)){
                    return;
                }
                mTimers.put(id, new Pair <> (timestamp,scope));
                if ( setter != null ){
                    setter.set(id, timestamp);
                }
            }
            invalidate();
        }

        private Pair < Long , Long > calcNearest(){
            Long nearestLong = Long.MAX_VALUE;
            Long nearestShort = Long.MAX_VALUE;
            Long cts = System.currentTimeMillis();
            for (Map.Entry<String, Pair< Long , Scope >> timer : mTimers.entrySet()) {
                Long dt = timer.getValue().first - cts;
                if ( dt <= MIN_BACKGROUND_TIMEOUT ){
                    if (timer.getValue().first < nearestShort) {
                        nearestShort = timer.getValue().first;
                    }
                }
                if ( dt > MIN_BACKGROUND_TIMEOUT ){
                    if ( timer.getValue().first < nearestLong){
                        nearestLong = timer.getValue().first;
                    }
                }
            }
            return new Pair<>(nearestShort, nearestLong);
        }

        private void stop(String id) {
            synchronized (mTimers) {
                Pair< Long , Scope > removed = mTimers.remove(id);
                if (removed != null) {
                    invalidate();
                }
            }
        }

        private boolean validate(){
            boolean invalidated = false;
            synchronized (mTimers) {
                Set < Pair < String , Long > > inv = null;
                for (Map.Entry<String, Pair< Long , Scope >> timer : mTimers.entrySet()) {
                    if ( timer.getValue().second != null ){
                        if ( !timer.getValue().second.isValid()){
                            if ( inv == null ){
                                inv = new HashSet<>();
                            }
                            inv.add(new Pair <>(timer.getKey(), timer.getValue().first));
                        }
                    }
                }
                if ( inv != null ){
                    invalidated = true;
                    for ( Pair < String , Long > timer : inv ){
                        cancel(timer.second);
                        mTimers.remove(timer.first);
                    }
                }
            }
            return invalidated;
        }

    }

    private void cancel(Long timestamp){
        mShortTimer.stop(timestamp);
        mLongTimer.stop(timestamp);
    }

    private TimerManager(Context ctx) throws Exception {
        mCtx = ctx;
        mLongTimer = CoreApp.getSingleton(ctx, TimerLong.class);
        mShortTimer = CoreApp.getSingleton(ctx, TimerShort.class);
    }

    private void timer (){
        synchronized (mHandlers){
            Long cts = System.currentTimeMillis();
            for ( Map.Entry<String, TimerHandler> handler : mHandlers.entrySet()){
                handler.getValue().check(mCtx, cts);
            }
        }
        invalidate();
    }

    public static TimerManager getInstance(Context ctx) {
        return CoreApp.getSingleton(ctx, TimerManager.class);
    }

    public void registerHandler(String handlerId, Handler handler) {
        synchronized (mHandlers) {
            mHandlers.put(handlerId, new TimerHandler(handler));
        }
    }

    public void unregisterHandler(String handlerId) {
        synchronized (mHandlers){
            mHandlers.remove(handlerId);
        }
    }

    public void validate (String handlerId){
        synchronized (mHandlers){
            TimerHandler handler = mHandlers.get(handlerId);
            if ( handler != null ){
                if ( handler.validate() ){
                    invalidate();
                }
            }
        }
    }

    public void startTimer(String handlerId, String id, Long timestamp){
        startTimer(handlerId, id, timestamp, null, null);
    }

    public void startTimer(String handlerId, String id, Long timestamp, Scope scope, Setter setter) {
        TimerHandler handler;
        synchronized (mHandlers){
            handler = mHandlers.get(handlerId);
        }
        if ( handler != null ){
            Logger.i(LOG_TAG, "Starting timer for " + handlerId + " id " + id + " timestamp "+ timestamp + " msec " + (timestamp - System.currentTimeMillis()));
            handler.start(id, timestamp, scope, setter);
        }
    }

    public void stopTimer(String handlerId, String id) {
        TimerHandler handler;
        synchronized (mHandlers){
            handler = mHandlers.get(handlerId);
        }
        if ( handler != null ){
            handler.stop(id);
        }
    }

    private void invalidate(){
        Long nearestShort = Long.MAX_VALUE;
        Long nearestLong = Long.MAX_VALUE;
        synchronized (mHandlers){
            for ( Map.Entry<String, TimerHandler> handler : mHandlers.entrySet()){
                Pair < Long, Long > nearest = handler.getValue().calcNearest();
                if ( nearest.first < nearestShort ){
                    nearestShort = nearest.first;
                }
                if ( nearest.second < nearestLong ){
                    nearestLong = nearest.second;
                }
            }
        }
        if ( nearestShort != Long.MAX_VALUE ){
            TimerShort ts = CoreApp.getSingleton(mCtx, TimerShort.class);
            if ( ts != null ){
                ts.start(nearestShort, new Runnable() {
                    @Override
                    public void run() {
                        timer();
                    }
                });
            }
        }
        if ( nearestLong != Long.MAX_VALUE ){
            TimerLong tl = CoreApp.getSingleton(mCtx, TimerLong.class);
            if ( tl != null ){
                tl.start(nearestLong, new Runnable() {
                    @Override
                    public void run() {
                        timer();
                    }
                });
            }
        }
        Logger.i(LOG_TAG, "=========================================");
        mLongTimer.dump();
        mShortTimer.dump();
    }

}
