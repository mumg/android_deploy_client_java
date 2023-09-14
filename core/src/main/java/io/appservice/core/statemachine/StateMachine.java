package io.appservice.core.statemachine;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.appservice.core.BroadcastManager;
import io.appservice.core.timer.TimerManager;
import io.appservice.core.statemachine.annotations.StateContextSettings;
import io.appservice.core.statemachine.annotations.StateEntry;
import io.appservice.core.statemachine.annotations.StateEvent;
import io.appservice.core.statemachine.annotations.StateExit;
import io.appservice.core.statemachine.annotations.StateThread;
import io.appservice.core.statemachine.annotations.StateTimer;
import io.appservice.core.util.Logger;

@SuppressWarnings("unused")
public class StateMachine {
    private static final String LOG_TAG = "IOAPP_StateMachine";
    private Map<Integer, State> mStateMap = new HashMap<>();

    private StateContext mContext;
    private StateContextStorage mStorage;
    private StateProcessQueue mQueue;

    protected boolean isForeground() {
        return mContext.isForeground();
    }

    private enum ThreadState {
        Idle,
        Running,
        Finished,
        Terminate
    }

    public StateContext getContext() {
        return mContext;
    }

    private class ThreadFinishedTask implements Runnable {
        private static final int SUCCESS = 0;
        private static final int FAILED = 2;
        private static final int ABORTED = 3;
        private ThreadDesc mDesc;
        private int mResult;
        private Context mCtx;

        private ThreadFinishedTask(Context ctx, ThreadDesc desc, int result) {
            mCtx = ctx;
            mDesc = desc;
            mResult = result;
        }

        @Override
        public void run() {
            try {
                mDesc.mThread.join();
            } catch (Exception e) {
                Logger.i(LOG_TAG, "Exception during thread shutdown wait " + e.getMessage());
            }
            if (mDesc.isActive(mContext.mCurrentState)) {
                if (mResult == SUCCESS) {
                    if (mDesc.mOnSuccessState != -1) {
                        changeState(mCtx, mDesc.mOnSuccessState);
                    }
                } else if (mResult == FAILED) {
                    if (mDesc.mOnErrorState != -1) {
                        changeState(mCtx, mDesc.mOnErrorState);
                    }
                } else {
                    if (mDesc.mOnAbortState != -1) {
                        changeState(mCtx, mDesc.mOnAbortState);
                    }
                }
            }
            mDesc.mState = ThreadState.Finished;
            mDesc.mThreadBody = null;
        }
    }

    protected class ThreadDesc {
        private int mStates[];
        private Class<? extends StateContext.StateContextThread> mThreadType;
        private int mOnSuccessState;
        private int mOnErrorState;
        private int mOnAbortState;
        private Thread mThread;
        private StateContext.StateContextThread mThreadBody;

        private ThreadState mState = ThreadState.Idle;

        private ThreadDesc(int states[],
                           Class<? extends StateContext.StateContextThread> thread,
                           int onSuccessState,
                           int onErrorState,
                           int onAbortState) {
            mStates = states;
            mThreadType = thread;
            mOnSuccessState = onSuccessState;
            mOnErrorState = onErrorState;
            mOnAbortState = onAbortState;
        }

        boolean isActive(int state) {
            for (int own : mStates) {
                if (state == own) {
                    return true;
                }
            }
            return false;
        }

        void start(final Context ctx, int state) {
            if (!isActive(state)) {
                return;
            }
            if (mState != ThreadState.Idle) {
                return;
            }
            try {

                Constructor c = mThreadType.getDeclaredConstructor(mContext.getClass());
                c.setAccessible(true);
                mThreadBody = (StateContext.StateContextThread) c.newInstance(mContext);
                final ThreadDesc desc = this;
                mState = ThreadState.Running;
                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Logger.i(LOG_TAG, "Thread " + mThreadBody.getClass().getName() + " running");
                        int result = ThreadFinishedTask.SUCCESS;
                        try {
                            mThreadBody.run(ctx);
                        } catch (StateContext.StateContextThreadAborted aborted) {
                            result = ThreadFinishedTask.ABORTED;
                            Logger.w(LOG_TAG, "Thread " + mThreadBody.getClass().getName() + " aborted");
                        } catch (Exception e) {
                            result = ThreadFinishedTask.FAILED;
                            Logger.w(LOG_TAG, "Thread " + mThreadBody.getClass().getName() + " exception " + e.getMessage());
                        }
                        Logger.d(LOG_TAG, "Thread " + mThreadBody.getClass().getName() + " stopped");
                        if (mState == ThreadState.Running) {
                            mQueue.push(0, new ThreadFinishedTask(ctx, desc, result));
                            Logger.d(LOG_TAG, "Thread finished with result " + result);
                        }
                        mState = ThreadState.Finished;
                    }
                });
                mThread.start();
            } catch (Exception e) {
                Logger.e(LOG_TAG, "Could not start thread " + e.getMessage());
            }
        }

        void stop(int state) {
            if (isActive(state)) {
                return;
            }
            if (mState == ThreadState.Running) {
                mState = ThreadState.Terminate;
                if (mThreadBody != null) mThreadBody.stop();
                try {
                    mThread.join();
                } catch (Exception ignore) {

                }
            }
            mState = ThreadState.Idle;
        }

    }

    public void save(boolean store) {
        if (mStorage == null) {
            return;
        }
        if (!store) {
            Logger.d(LOG_TAG, mContext.getClass().getName() + " state store false");
            return;
        }
        if (!mStore) {
            Logger.d(LOG_TAG, mContext.getClass().getName() + " store settings disabled");
            return;
        }
        mStorage.save(mContext);
    }

    private List<ThreadDesc> mThreads = new ArrayList<>();
    private boolean mStore = true;
    private int mInitialState = 0;
    private int mRestoreState = -1;
    private int mCrashState = -1;

    protected boolean doStore() {
        return mStore;
    }

    protected int getInitialState() {
        return mInitialState;
    }

    protected int getRestoreState() {
        return mRestoreState;
    }

    public void init(Context ctx,
                     StateProcessQueue queue,
                     StateContextStorage storage,
                     Class<? extends StateContext> contextType) throws Exception {
        mQueue = queue;
        mStorage = storage;
        List <StateContext.TimerDesc > timerDescs = new LinkedList<>();
        if (contextType.isAnnotationPresent(StateContextSettings.class)) {
            StateContextSettings settings = contextType.getAnnotation(StateContextSettings.class);
            if (settings != null) {
                mInitialState = settings.initial();
                mRestoreState = settings.recover();
                mStore = settings.store();
                mCrashState = settings.crash();
            }
        }
        Method[] methods = contextType.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(StateEntry.class)) {

                StateEntry entry = method.getAnnotation(StateEntry.class);
                if (entry == null) {
                    continue;
                }
                method.setAccessible(true);
                for (int a_state : entry.states()) {
                    State state = mStateMap.get(a_state);
                    if (state == null) {
                        state = new State();
                        mStateMap.put(a_state, state);
                    }
                    state.setEntry(method);
                    state.setForeground(entry.foreground());
                    state.setStore(entry.store());
                }

            } else if (method.isAnnotationPresent(StateExit.class)) {
                StateExit exit = method.getAnnotation(StateExit.class);
                if (exit == null) {
                    continue;
                }
                method.setAccessible(true);
                for (int a_state : exit.states()) {
                    State state = mStateMap.get(a_state);
                    if (state == null) {
                        state = new State();
                        mStateMap.put(a_state, state);
                    }
                    state.setExit(method);
                }
            } else if (method.isAnnotationPresent(StateEvent.class)) {
                StateEvent event = method.getAnnotation(StateEvent.class);
                if (event == null) {
                    continue;
                }
                method.setAccessible(true);
                for (int a_state : event.states()) {
                    State state = mStateMap.get(a_state);
                    if (state == null) {
                        state = new State();
                        mStateMap.put(a_state, state);
                    }
                    state.addEventHandler(event.id(), event.external(), method);
                }
            } else if (method.isAnnotationPresent(StateTimer.class)) {
                StateTimer timer = method.getAnnotation(StateTimer.class);
                method.setAccessible(true);
                if (timer != null) {
                    String id = timer.id();
                    if (id.length() == 0) {
                        id = method.getName();
                    }
                    for (int a_state : timer.states()) {
                        State state = mStateMap.get(a_state);
                        if (state == null) {
                            state = new State();
                            mStateMap.put(a_state, state);
                        }
                    }
                    timerDescs.add(new StateContext.TimerDesc(timer.states(), id, method, timer.timeout()));
                }
            }
        }
        Class<?> innerClasses[] = contextType.getDeclaredClasses();
        for (Class<?> declC : innerClasses) {
            if (declC.isAnnotationPresent(StateThread.class)) {
                StateThread thread = declC.getAnnotation(StateThread.class);
                for (int a_state : thread.states()) {
                    State state = mStateMap.get(a_state);
                    if (state == null) {
                        state = new State();
                        state.setForeground(true);
                        mStateMap.put(a_state, state);
                    }
                }
                if (StateContext.StateContextThread.class.isAssignableFrom(declC)) {
                    mThreads.add(
                            new ThreadDesc(
                                    thread.states(),
                                    (Class<? extends StateContext.StateContextThread>) declC,
                                    thread.onSuccessState(),
                                    thread.onErrorState(),
                                    thread.onAbortState() == -1 ? thread.onErrorState() : thread.onAbortState()));
                }
            }
        }
        if (mStorage != null) {
            mContext = mStorage.load(contextType);
        } else {
            mContext = contextType.newInstance();
        }
        for (StateContext.TimerDesc timerDesc : timerDescs ){
            mContext.addTimerDesc(timerDesc);
        }
        mContext.init(ctx);
        mContext.setHandler(new StateContext.StateEventHandler() {
            @Override
            public void pushEvent(Context ctx, Intent intent) {
                mQueue.push(1, new HandleEventTask(ctx, intent));
            }
        });
        if (mContext.mCurrentState == -1) {
            mQueue.push(0, new EnterStateTask(mInitialState,
                    ctx));
        } else {
            int recover_state = mRestoreState == -1 ? mContext.mCurrentState : mRestoreState;
            mQueue.push(0, new EnterStateTask(recover_state, ctx));
        }
    }

    private class EnterStateTask implements Runnable {
        private int mNewState;
        private Context mCtx;

        private EnterStateTask(Integer newState,
                               Context ctx) {
            mNewState = newState;
            mCtx = ctx;
        }

        @Override
        public void run() {
            Logger.d(LOG_TAG, "Enter new state " + mNewState + " - " + mContext.getClass().getName());
            State state = mStateMap.get(mNewState);
            if (state != null) {
                try {
                    mContext.mCurrentState = mNewState;
                    mContext.setForeground(state.isForeground());
                    Integer new_state = state.entry(mContext, mCtx);
                    if (new_state != StateContext.SAME_STATE) {
                        changeState(mCtx, new_state);
                    } else {
                        mContext.startTimers(mCtx);
                        for (ThreadDesc thread : mThreads) {
                            thread.start(mCtx, mNewState);
                        }
                        save(state.doStore());
                    }
                } catch (Exception e) {
                    Logger.e(LOG_TAG, "Exception in entry to state " + mNewState + " in context " + mContext.getClass().getName());
                    if (mCrashState != -1) {
                        Logger.e(LOG_TAG, "Change state to crash handling state");
                        changeState(mCtx, mCrashState);
                    }
                }
            } else {
                Logger.e(LOG_TAG, "State not found " + mNewState + " in context " + mContext.getClass().getName());
            }
        }
    }

    private class ExitStateTask implements Runnable {
        private int mNewState;
        private Context mCtx;

        private ExitStateTask(Integer newState,
                              Context ctx) {
            mNewState = newState;
            mCtx = ctx;
        }

        @Override
        public void run() {
            Logger.d(LOG_TAG, "Exit state " + mContext.mCurrentState + " - " + mContext.getClass().getName());
            State state = mStateMap.get(mContext.mCurrentState);
            if (state != null) {
                try {

                    for (ThreadDesc thread : mThreads) {
                        thread.stop(mNewState);
                    }
                    state.exit(mContext, mCtx);
                    mContext.mCurrentState = mNewState;
                    TimerManager.getInstance(mCtx).validate(mContext.getClass().getName());
                } catch (Exception e) {
                    Logger.e(LOG_TAG, "Exception in exit state " + mContext.mCurrentState + " in context " + mContext.getClass().getName());
                    if (mCrashState != -1) {
                        Logger.e(LOG_TAG, "Change state to crash handling state");
                        changeState(mCtx, mCrashState);
                    }
                }
            } else {
                Logger.e(LOG_TAG, "State not found " + mNewState + " in context " + mContext.getClass().getName());
            }
        }
    }

    private void changeState(Context ctx, int newState) {
        if ( mContext.mCurrentState != -1 ){
            mQueue.push(0,new ExitStateTask(newState, ctx));
        }
        mQueue.push(0,new EnterStateTask(newState, ctx));
    }

    private class HandleEventTask implements Runnable {
        private Context mCtx;
        private Intent mIntent;

        private HandleEventTask(Context ctx, Intent intent) {
            mCtx = ctx;
            mIntent = intent;
        }

        @Override
        public void run() {
            State state = mStateMap.get(mContext.mCurrentState);
            if (state != null) {
                try {
                    Integer new_state = state.handle(mContext, mCtx, mIntent);
                    if (new_state != StateContext.SAME_STATE) {
                        changeState(mCtx, new_state);
                    }
                } catch (Exception e) {
                    Logger.e(LOG_TAG, "Exception in event handler " + mContext.mCurrentState + " in context " + mContext.getClass().getName());
                    if (mCrashState != -1) {
                        Logger.e(LOG_TAG, "Change state to crash handling state");
                        changeState(mCtx, mCrashState);
                    }
                }
            } else {
                Logger.e(LOG_TAG, "State not found " + mContext.mCurrentState + " in context " + mContext.getClass().getName());
            }
        }
    }


    private boolean isStateForeground(Integer state) {
        State st = mStateMap.get(state);
        if (st != null) {
            return st.isForeground();
        }
        return false;
    }


    public void handle(Context ctx,
                       Intent intent) {
        mQueue.push(1,new HandleEventTask(ctx, intent));
    }

    public void register(Context ctx) throws Exception {
        Set<String> external = new HashSet<>();
        Set<String> local = new HashSet<>();
        for (Map.Entry<Integer, State> state : mStateMap.entrySet()) {
            state.getValue().getEvents(external, local);
        }
        BroadcastManager.getInstance(ctx).register(ctx, external, local,
                new BroadcastManager.Handler() {
                    @Override
                    public void handle(Context ctx, Intent intent) {
                        mQueue.push(1,new HandleEventTask(ctx, intent));
                    }
                });
        TimerManager.getInstance(ctx).registerHandler(mContext.getClass().getName(),
                new TimerManager.Handler() {
                    @Override
                    public void handle(Context ctx, String id) {
                        mQueue.push(1,new HandleTimerTask(id, ctx, isForeground()));
                    }
                });
    }

    public void startForeground(Context ctx) {
        for (ThreadDesc thread : mThreads) {
            thread.start(ctx, mContext.mCurrentState);
        }
    }

    public void stopForeground() {
        for (ThreadDesc thread : mThreads) {
            thread.stop(-1);
        }
    }

    private class HandleTimerTask implements Runnable {
        private String mTimer;
        private Context mCtx;
        private boolean mForeground;

        private HandleTimerTask(String timer, Context ctx, boolean foreground) {
            mTimer = timer;
            mCtx = ctx;
            mForeground = foreground;
        }

        @Override
        public void run() {
            StateContext.TimerDesc desc = mContext.getTimerDesc(mTimer);
            if ( desc != null ) {
                if (desc.isActive(mContext.mCurrentState)) {
                    Logger.d(LOG_TAG, "Firing timer " + desc.getId());
                    try {
                        Integer next_state = (Integer) desc.getHandler().invoke(mContext, mCtx);
                        if (next_state != StateContext.SAME_STATE) {
                            changeState(mCtx, next_state);
                        }
                    } catch (Exception e) {
                        Logger.e(LOG_TAG, "Exception occurred during timer handle " + e.getMessage());
                        if (mCrashState != -1) {
                            changeState(mCtx, mCrashState);
                        }
                    }
                }
            }
        }
    }

}
