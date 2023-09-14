package io.appservice.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BroadcastManager {

    public interface Handler{
        void handle (Context ctx, Intent intent );
    }

     private final Map< String , List< Handler >> mHandlers = new HashMap<>();

    private final Map < String , BroadcastReceiver > mExternalReceivers = new HashMap<>();
    private final Map < String , BroadcastReceiver > mLocalReceivers = new HashMap<>();

    public static BroadcastManager getInstance(Context ctx) throws Exception{
        return CoreApp.getSingleton(ctx, BroadcastManager.class);
    }

    public void handle (Context ctx, Intent intent){
        if ( intent.getAction() == null){
            return;
        }
        List <Handler> handlers;
        synchronized (mHandlers){
            handlers = mHandlers.get(intent.getAction());
        }
        for ( Handler handler: handlers){
            handler.handle(ctx, intent);
        }
    }

    public void registerExternal (Context ctx, String external, Handler handler ){
        Set < String > set = new HashSet<>();
        set.add(external);
        register(ctx, set, null, handler);
    }

    public void registerLocal (Context ctx, String local, Handler handler ){
        Set < String > set = new HashSet<>();
        set.add(local);
        register(ctx, null, set, handler);
    }

    public void register (Context ctx, Set< String > external, Set <String > local, final Handler handler ){
        Set < String > total = new HashSet<>();
        if ( external != null )total.addAll(external);
        if ( local != null ) total.addAll(local);
        synchronized (mHandlers) {
            for (String action : total) {
                if ( !mHandlers.containsKey(action)){
                    mHandlers.put(action, new LinkedList<Handler>());
                }
                if ( !mHandlers.get(action).contains(handler)){
                    mHandlers.get(action).add(handler);
                }
            }
        }
        synchronized (mExternalReceivers){
            for (String action : total) {
                if ( !mExternalReceivers.containsKey(action)){
                    BroadcastReceiver receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            handle(context, intent);
                        }
                    };
                    if (external != null && external.contains(action)) {
                        mExternalReceivers.put(action, receiver);
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(action);
                        ctx.registerReceiver(receiver, filter);
                    }
                    if (local != null && local.contains(action)){
                        mLocalReceivers.put(action, receiver);
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(action);
                        LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, filter);
                    }
                }
            }
        }
    }

    public void unregister (Context ctx, Handler handler){
        Set < String > remove = new HashSet<>();
        synchronized (mHandlers){
            for ( Map.Entry < String , List < Handler > > entry: mHandlers.entrySet()){
                if ( entry.getValue().remove(handler)){
                    if ( entry.getValue().size() == 0 ) {
                        remove.add(entry.getKey());
                    }
                }
            }
            for ( String action : remove ){
                mHandlers.remove(action);
            }
        }
        synchronized (mExternalReceivers){
            for ( String action : remove ){
                BroadcastReceiver receiver = mExternalReceivers.get(action);
                if ( receiver != null ){
                    ctx.unregisterReceiver(receiver);
                }
            }
        }
        synchronized (mLocalReceivers){
            for ( String action : remove ){
                BroadcastReceiver receiver = mLocalReceivers.get(action);
                if ( receiver != null ){
                    LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver);
                }
            }
        }
    }

    public void unregisterAll(Context ctx){
        mHandlers.clear();
        for (Map.Entry < String , BroadcastReceiver > receiver : mExternalReceivers.entrySet() ){
            ctx.unregisterReceiver(receiver.getValue());
        }
        for (Map.Entry < String , BroadcastReceiver > receiver : mLocalReceivers.entrySet() ){
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver.getValue());
        }
        mExternalReceivers.clear();
        mLocalReceivers.clear();
    }

}
