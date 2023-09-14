package io.appservice.core.support;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.appservice.core.CoreApp;
import io.appservice.core.statemachine.StateProcessQueue;
import io.appservice.core.timer.TimerManager;
import io.appservice.core.util.Logger;

public class WorkerQueue implements StateProcessQueue {

    private static final String LOG_TAG = "IOAPP_WorkerQueue";

    private static final String TIMER_HANDLER_ID = "WorkerQueue";
    private static final String TIMER_ID = "Expired";

    private Context mCtx;

    private enum State{
        Idle,
        Active,
        Start,
        Process
    }

    private AtomicReference<State> mState = new AtomicReference<>(State.Idle);

    private final Vector<Queue<Runnable>> mQueue = new Vector<>();

    private WorkerQueue(Context ctx){
        mCtx = ctx;
        mQueue.add(new ArrayDeque<Runnable>());
        mQueue.add(new ArrayDeque<Runnable>());
    }

    public static WorkerQueue getInstance(Context ctx){
        return CoreApp.getSingleton(ctx, WorkerQueue.class);
    }

    @Override
    public void push(int priority, Runnable task) {
        synchronized (mQueue){
            Queue<Runnable> queue = mQueue.get(priority);
            queue.add(task);
            if ( mState.get() == State.Idle ){
                return;
            }
            if ( mState.compareAndSet(State.Active, State.Start)){
                WorkerService.enqueueWork(mCtx, WorkerService.class, 6396, new Intent());
            }else{
                mQueue.notify();
            }
        }
    }

    private void enqueueStop(Stop runnable){
        synchronized (mQueue) {
            Queue<Runnable> queue = mQueue.get(0);
            if (queue == null) {
                queue = new ArrayDeque<>();
                mQueue.add(0, queue);
            }
            queue.add(runnable);
            mQueue.notify();
        }
    }

    private boolean isEmpty(){
        synchronized (mQueue){
            for ( Queue<Runnable> deque: mQueue){
                if ( deque.size() > 0 ){
                    return false;
                }
            }
        }
        return true;
    }

    private Runnable getTask(CoreApp app){
        for (;;) {
            synchronized (mQueue) {
                if (isEmpty() &&
                        !app.getSM().isForeground()) {
                    return null;
                }
                if (isEmpty()) {
                    try {
                        mQueue.wait();
                    } catch (Exception e) {
                        Logger.e(LOG_TAG, "Wait interrupted " + e.getMessage());
                        return null;
                    }
                }
                for (int index = 0 ; index < mQueue.size(); index ++) {
                    if (mQueue.get(index).size() > 0) {
                        return mQueue.get(index).poll();
                    }
                }
            }
        }
    }

    private abstract class Stop implements Runnable{

    }

    public void start(){
        synchronized (mQueue){
            if ( !isEmpty() ){
                mState.set(State.Start);
                WorkerService.enqueueWork(mCtx, WorkerService.class, 6396, new Intent());
            }else{
                mState.set(State.Active);
            }
        }
        TimerManager.getInstance(mCtx).registerHandler(TIMER_HANDLER_ID, new TimerManager.Handler() {
            @Override
            public void handle(Context ctx, String id) {
                synchronized (mQueue){
                    enqueueStop(null);
                }
            }
        });
    }

    protected void foregroundRun(){
        Logger.w(LOG_TAG, "Enter foreground");
        mState.set(State.Process);
        CoreApp app = CoreApp.getIntance(mCtx);
        TimerManager mgr = TimerManager.getInstance(mCtx);
        //mgr.startTimer(TIMER_HANDLER_ID, TIMER_ID, 9*60*1000L );
        Runnable stopRunnable = null;
        for (;;)
        {
            Runnable runnable = getTask(app);
            if ( runnable instanceof Stop ){
                stopRunnable = runnable;
                break;
            } else if ( runnable == null ){
                break;
            }
            else{
                runnable.run();
            }
        }
        boolean reschedule = CoreApp.getIntance(mCtx).getSM().isForeground();
        synchronized (mQueue){
            if ( !isEmpty() ){
                reschedule = true;
            }
        }
        if (reschedule){
            mState.set(State.Start);
            WorkerService.enqueueWork(mCtx, WorkerService.class, 6396, new Intent());
        }else{
            mState.set(State.Active);
        }
        //mgr.stopTimer(TIMER_HANDLER_ID, TIMER_ID);
        if ( stopRunnable != null ){
            stopRunnable.run();
        }
        Logger.w(LOG_TAG, "Exit foreground");
    }

    protected void foregroundStop(){
        final CountDownLatch latch = new CountDownLatch(1);
        enqueueStop(new Stop() {
            @Override
            public void run() {
                latch.countDown();
            }
        });
        synchronized (latch){
            if ( latch.getCount() >0 ){
                try {
                    latch.await();
                }catch (Exception e){
                    Logger.e(LOG_TAG, "latch interrupted " + e.getMessage());
                }
            }
        }
    }
}
