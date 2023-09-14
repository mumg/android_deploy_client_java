package io.appservice.core.process;

import android.content.Context;

import java.io.OutputStream;

import io.appservice.core.statemachine.StateContext;

public abstract class ProcessAsync implements StateContext.StateContextThread {
    protected abstract String getExecutable(Context ctx);
    protected abstract String [] getArguments(Context ctx);
    protected abstract void postprocess(int result) throws Exception;

    protected String [] getEnvironmentVariables(Context ctx){
        return null;
    }

    protected OutputStream getStdOut(Context ctx){
        return null;
    }

    protected OutputStream getStdErr(Context ctx){
        return null;
    }
}
