package io.appservice.core.process;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

public abstract class ProcessAsyncLocal extends ProcessAsync {

    private Process mProcess;
    private Thread mStdErr;
    private Thread mStdOut;

    private static class StreamReader implements Runnable {
        private InputStream mInput;
        private OutputStream mOutput;

        public StreamReader(InputStream in, OutputStream out) {
            mInput = in;
            mOutput = out;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(mInput));
                String ln;
                while ((ln = reader.readLine()) != null) {
                    if (mOutput != null) {
                        mOutput.write(ln.getBytes());
                    }
                }
            } catch (Exception ignore) {
                try {
                    mInput.close();
                }catch (Exception ignore1){

                }
            }
        }
    }

    @Override
    public void run(Context ctx) throws Exception {
        try {
            String[] args;
            String[] providedArgs = getArguments(ctx);
            if (providedArgs != null) {
                args = new String[1 + providedArgs.length];
            } else {
                args = new String[1];
            }

            Integer arg_idx = 0;
            args[arg_idx++] = getExecutable(ctx);
            if (providedArgs != null) {
                for (String arg : providedArgs) {
                    args[arg_idx++] = arg;
                }
            }
            String[] env_array = null;
            String[] env = getEnvironmentVariables(ctx);
            if (env != null) {
                Map<String, String> sys_env = System.getenv();
                int index = 0;
                env_array = new String[sys_env.size() + env.length];
                for (Map.Entry<String, String> e : sys_env.entrySet()) {
                    env_array[index++] = e.getKey() + "=" + e.getValue();
                }
                for (String envar : env) {
                    env_array[index++] = envar;
                }
            }

            if (env_array != null) {
                mProcess = Runtime.getRuntime().exec(args, env_array);
            } else {
                mProcess = Runtime.getRuntime().exec(args);
            }

            OutputStream stderr = getStdErr(ctx);
            if (stderr != null) {
                mStdErr = new Thread(new StreamReader(mProcess.getErrorStream(), stderr));
                mStdErr.start();
            }

            OutputStream stdout = getStdOut(ctx);
            if (stdout != null) {
                mStdOut = new Thread(new StreamReader(mProcess.getInputStream(), stdout));
                mStdOut.start();
            }

            mProcess.waitFor();
            postprocess(mProcess.exitValue());
            mProcess = null;
        }finally {
            try {
                if (mStdOut != null) {
                    mStdOut.wait();
                    mStdOut = null;
                }
            }catch (Exception ignore){

            }
            try {
                if (mStdErr != null) {
                    mStdErr.wait();
                    mStdErr = null;
                }
            }catch (Exception ignore){

            }
        }
    }

    @Override
    public void stop() {
        if (mProcess != null) {
            mProcess.destroy();
        }
    }
}
