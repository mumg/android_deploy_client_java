package io.appservice.module.logic;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import io.appservice.core.CoreApp;
import io.appservice.core.http.HttpRequestDownloader;
import io.appservice.core.statemachine.StateContext;
import io.appservice.core.statemachine.annotations.StateContextSettings;
import io.appservice.core.statemachine.annotations.StateEntry;
import io.appservice.core.statemachine.annotations.StateEvent;
import io.appservice.core.statemachine.annotations.StateField;
import io.appservice.core.statemachine.annotations.StateThread;
import io.appservice.core.statemachine.annotations.StateTimer;
import io.appservice.core.util.Hash;
import io.appservice.core.util.Logger;
import io.appservice.core.util.MD5;
import io.appservice.core.util.Network;
import io.appservice.module.ModuleApp;


@SuppressWarnings("unused")
@StateContextSettings(
        initial = Updater.ACTIVE,
        recover = Updater.ACTIVE,
        crash = Updater.ERROR_INSTALL
)
public class Updater extends StateContext {

    private static final String LOG_TAG = "IOAPP_Updater";

    private static final String ACTION_UPDATE = "io.appservice.module.UPDATE";


    private static final String INTENT_KEY_URL = "url";
    private static final String INTENT_KEY_RESULT = "result";
    private static final String INTENT_KEY_DATA = "data";
    private static final String INTENT_KEY_EXECUTABLE = "executable";
    private static final String INTENT_KEY_ARGUMENTS = "arguments";
    private static final String INTENT_KEY_ID = "id";
    private static final String INTENT_KEY_FILES = "files";


    private String getFilesDir(Context ctx) {
        return ctx.getExternalCacheDir().getAbsolutePath();
    }

    private void appendLog(String log) {
        if (mLog == null) {
            mLog = new StringBuilder();
        }
        if (log != null) {
            mLog.append(log);
            mLog.append("\n");
        }
    }


    private static class DownloadFile {
        private String url;
        private String md5;
        private String file;

        private DownloadFile(String url, String md5, String file) {
            this.url = url;
            this.md5 = md5;
            this.file = file;
        }

        boolean match(String url, String md5, String file) {
            return this.url.equals(url) && this.md5.equals(md5) && this.file.equals(file);
        }

        @Override
        public int hashCode() {
            return Hash.calc(url, md5, file);
        }
    }

    private static class DownloadList {
        private List<DownloadFile> files = new ArrayList<>();

        private void add(String url, String md5, String file) {
            for (DownloadFile df : files) {
                if (df.match(url, md5, file)) {
                    return;
                }
            }
            files.add(new DownloadFile(url, md5, file));
        }

        private void delete(String path) {
            for (DownloadFile file : files) {
                new File(path + "/" + file.file).delete();
            }
            files.clear();
        }

        private void clear() {
            files.clear();
        }

        DownloadFile get(int index) {
            return files.get(index);
        }

        int size() {
            return files.size();
        }

        @Override
        public int hashCode() {
            return Hash.calc(files);
        }
    }


    private static class UpdateScenario {
        private String url;
        private String md5;
        private String file;
        private String shell;
        private String arguments;

        @Override
        public int hashCode() {
            return Hash.calc(url, md5, file, shell, arguments);
        }
    }

    private static class UpdateFile {
        String url;
        String md5;
        String file;
        UpdateScenario install;

        @Override
        public int hashCode() {
            return Hash.calc(url, md5, file, install);
        }
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private static class Request {
        private String url;
        private List<UpdateScenario> pre;
        private List<UpdateFile> update;
        private List<UpdateScenario> post;
        private List<UpdateScenario> scenario;

        @Override
        public int hashCode() {
            return Hash.calc(url, pre, update, scenario != null ? scenario : post);
        }

        private int size() {
            List<UpdateScenario> _post = scenario != null ? scenario : post;
            return (update != null ? update.size() : 0) +
                    (pre != null ? pre.size() : 0) +
                    (_post != null ? _post.size() : 0);
        }

        private void fillArgsScenario(Context ctx, String path, UpdateScenario scenario, List<String> args) throws Exception {
            args.add(scenario.shell);
            args.add(path + "/" + scenario.file);
            if (scenario.arguments != null) {
                args.addAll(translateCommandline(scenario.arguments));
            }
        }

        private void fillArgsUpdate(Context ctx, String path, UpdateFile file, List<String> args) {
            args.add(file.install.shell);
            args.add(path + "/" + file.install.file);
            args.add(path + "/" + file.file);
            args.addAll(translateCommandline(file.install.arguments));
            if (file.install.arguments != null) {
                if (file.install.arguments != null) {
                    args.addAll(translateCommandline(file.install.arguments));
                }
            }

        }

        private interface ScenarioHandler {
            void onScenario(UpdateScenario scenario) throws Exception;
        }

        private interface FileHandler {
            void onFile(UpdateFile file) throws Exception;
        }

        private void queryItem(int index,
                               ScenarioHandler preHandler,
                               FileHandler updateHandler,
                               ScenarioHandler postHandler) throws Exception {
            int begin = 0;
            int end = 0;
            if (pre != null) {
                end = pre.size();
                if (index >= begin && index < end) {
                    preHandler.onScenario(pre.get(index - begin));
                    return;
                }
            }

            if (pre != null) {
                begin = begin + pre.size();
            }
            if (update != null) {
                end = begin + update.size();
                if (index >= begin && index < end) {
                    updateHandler.onFile(update.get(index - begin));
                    return;
                }
                begin = begin + update.size();
            }
            List<UpdateScenario> _post = scenario != null ? scenario : post;
            if (_post != null) {
                end = begin + _post.size();
                if (index >= begin && index < end) {
                    postHandler.onScenario(_post.get(index - begin));
                }
            }
        }

        private void getArgs(final Context ctx, final String path, int index, final List<String> args) throws Exception {
            queryItem(index,
                    new ScenarioHandler() {
                        @Override
                        public void onScenario(UpdateScenario scenario) throws Exception {
                            fillArgsScenario(ctx, path, scenario, args);
                        }
                    },
                    new FileHandler() {
                        @Override
                        public void onFile(UpdateFile file) throws Exception {
                            fillArgsUpdate(ctx, path, file, args);
                        }
                    },
                    new ScenarioHandler() {
                        @Override
                        public void onScenario(UpdateScenario scenario) throws Exception {
                            fillArgsScenario(ctx, path, scenario, args);
                        }
                    });

        }

        private void fillResponse(final Response response, int index) throws Exception {
            queryItem(index,
                    new ScenarioHandler() {
                        @Override
                        public void onScenario(UpdateScenario scenario) throws Exception {
                            response.file = null;
                            response.scenario = scenario.file;
                        }
                    },
                    new FileHandler() {
                        @Override
                        public void onFile(UpdateFile file) throws Exception {
                            response.file = file.file;
                            if (file.install != null) {
                                response.scenario = file.install.file;
                            }
                        }
                    },
                    new ScenarioHandler() {
                        @Override
                        public void onScenario(UpdateScenario scenario) throws Exception {
                            response.file = null;
                            response.scenario = scenario.file;
                        }
                    });

        }
    }

    private static class Response {
        private String customer;
        private String deviceId;
        private int result;
        private String file;
        private String scenario;
        private String log;

        private Response(int result, CoreApp app) {
            ModuleApp moduleApp = (ModuleApp) app;
            this.result = result;
            this.customer = moduleApp.getDevice().getCustomerId();
            this.deviceId = moduleApp.getDevice().getDeviceId();
        }
    }

    private class RequestQueue {
        private ArrayDeque<Request> mQueue = new ArrayDeque<>();

        private void push(Request request) {
            mQueue.push(request);
        }

        public int size() {
            return mQueue.size();
        }

        private Request pop() {
            return mQueue.pop();
        }

        @Override
        public int hashCode() {
            return mQueue.hashCode();
        }
    }

    private static final int WAIT_CONNECTION_TIMEOUT = 300000;
    private static final int DOWNLOAD_REPEAT_TIMEOUT = 120000;

    public static final int ACTIVE = 1;
    private static final int PREPARE_DOWNLOAD = 2;
    private static final int DOWNLOAD = 3;
    private static final int DOWNLOAD_NEXT = 4;
    private static final int WAIT_DOWNLOAD_CONNECTION = 5;
    private static final int INSTALL = 6;
    private static final int INSTALL_NEXT = 7;
    private static final int ERROR_DOWNLOAD = 8;
    public static final int ERROR_INSTALL = 9;
    private static final int SUCCESS = 10;
    private static final int CONFIRM = 11;
    private static final int CLEANUP = 14;
    private static final int DOWNLOAD_REPEAT = 15;


    @StateField
    private RequestQueue mRequestQueue = new RequestQueue();
    @StateField
    private Request mRequest;
    @StateField
    private DownloadList mDownloadList = new DownloadList();
    @StateField
    private int mIndex = 0;

    private int mResult;
    private StringBuilder mLog;


    private Gson mGSON = new GsonBuilder().create();

    private static List<String> translateCommandline(String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            return new ArrayList<>();
        }
        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> result = new ArrayList<String>();
        final StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if ("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || current.length() != 0) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }
        if (lastTokenHasBeenQuoted || current.length() != 0) {
            result.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new RuntimeException("unbalanced quotes in " + toProcess);
        }
        return result;
    }

    @StateEntry(states = {ACTIVE})
    private Integer activeEntry(Context ctx) {
        if (mRequestQueue.size() > 0) {
            mRequest = mRequestQueue.pop();
            return PREPARE_DOWNLOAD;
        }
        return SAME_STATE;
    }

    @StateEvent(states = {ACTIVE}, id = ACTION_UPDATE, external = true)
    private Integer activeUpdate(Context ctx, Intent intent) {
        Logger.i(LOG_TAG, "activeUpdate: Received update request");

        try {
            mRequest = mGSON.fromJson(intent.getStringExtra(INTENT_KEY_DATA), Request.class);
            mRequest.url = intent.getStringExtra(INTENT_KEY_URL);
            return PREPARE_DOWNLOAD;
        } catch (Exception e) {
            Logger.i(LOG_TAG, "activeUpdate exception " + e.getMessage());
            return ERROR_INSTALL;
        }
    }

    @StateEvent(states = {
            PREPARE_DOWNLOAD,
            DOWNLOAD,
            WAIT_DOWNLOAD_CONNECTION,
            INSTALL,
            INSTALL_NEXT,
            ERROR_DOWNLOAD,
            ERROR_INSTALL,
            SUCCESS,
            CONFIRM,
            CLEANUP,
            DOWNLOAD_REPEAT}
            , id = ACTION_UPDATE, external = true)
    private void xUpdate(Context ctx, Intent intent) {
        try {
            Logger.i(LOG_TAG, "xUpdate: Received update request, enqueue");
            Request request = mGSON.fromJson(intent.getStringExtra(INTENT_KEY_DATA), Request.class);
            request.url = intent.getStringExtra(INTENT_KEY_URL);
            mRequestQueue.push(request);
        } catch (Exception e) {
            Logger.i(LOG_TAG, "xUpdate exception " + e.getMessage());
        }
    }

    @StateEntry(states = {PREPARE_DOWNLOAD})
    private Integer prepareEntry(Context ctx) {
        Logger.i(LOG_TAG, "prepareEntry");
        mDownloadList.clear();
        mIndex = 0;
        if (mRequest.update != null) {
            for (UpdateFile file : mRequest.update) {
                Logger.i(LOG_TAG, "file url - " + file.url + " file - " + file.file + " md5 - " + file.md5);
                mDownloadList.add(file.url, file.md5, file.file);
                if (file.install != null) {
                    Logger.i(LOG_TAG, "   install url - " + file.install.url + " file - " + file.install.file + " md5 - " + file.install.md5);
                    mDownloadList.add(file.install.url, file.install.md5, file.install.file);
                }
            }
        }
        if (mRequest.scenario != null) {
            for (UpdateScenario scenario : mRequest.scenario) {
                Logger.i(LOG_TAG, "scenario url - " + scenario.url + " file - " + scenario.file + " md5 - " + scenario.md5);
                mDownloadList.add(scenario.url, scenario.md5, scenario.file);
            }
        }
        return DOWNLOAD;
    }

    @StateEntry(states = {DOWNLOAD}, foreground = true)
    private Integer downloadEntry(Context ctx) {
        if (Network.getConnectivityStatus(ctx) == Network.TYPE_NOT_CONNECTED) {
            return WAIT_DOWNLOAD_CONNECTION;
        } else {
            Logger.i(LOG_TAG, "Downloading file " + mDownloadList.get(mIndex).file);
            DownloadFile file = mDownloadList.get(mIndex);
            File path = new File(getFilesDir(ctx) + "/" + file.file);
            if (path.exists()) {
                String md5 = MD5.calculate(path);
                if (md5 != null && md5.toLowerCase().equals(mDownloadList.get(mIndex).md5.toLowerCase())) {
                    return DOWNLOAD_NEXT;
                }
            }
            return SAME_STATE;
        }
    }

    @StateThread(states = {DOWNLOAD},
            onSuccessState = DOWNLOAD_NEXT,
            onAbortState = DOWNLOAD_REPEAT,
            onErrorState = ERROR_DOWNLOAD
    )
    private class DownloadThread extends HttpRequestDownloader {

        private DownloadThread() {
            super(true);
        }

        @Override
        public String getPath() throws Exception {
            DownloadFile file = mDownloadList.get(mIndex);

            return getFilesDir(getContext()) + "/" + file.file;
        }

        @Override
        protected String getURL() throws Exception {
            DownloadFile file = mDownloadList.get(mIndex);
            return file.url;
        }

        @Override
        public void process(HttpURLConnection con) throws Exception {
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                appendLog("Error download file " + mDownloadList.get(mIndex).url + " responseCode " + con.getResponseCode());
            }
            super.process(con);
        }

        @Override
        protected void postprocess() throws Exception {
            String md5 = MD5.calculate(new File(getPath()));
            if (md5 == null || !md5.toLowerCase().equals(mDownloadList.get(mIndex).md5.toLowerCase())) {
                appendLog("MD5 doesn't match for " + mDownloadList.get(mIndex).url);
                throw new RuntimeException("MD5 doesn't match");
            }
        }
    }

    @StateEntry(states = {DOWNLOAD_NEXT})
    private Integer downloadNextEntry(Context ctx) {
        mIndex++;
        if (mIndex < mDownloadList.size()) {
            return DOWNLOAD;
        } else {
            mIndex = 0;
            return INSTALL;
        }
    }

    @StateEntry(states = {DOWNLOAD_REPEAT})
    private Integer downloadRepeatEnter(Context ctx) {
        Logger.i(LOG_TAG, "downloadRepeatEnter");
        return SAME_STATE;
    }

    @StateTimer(states = {DOWNLOAD_REPEAT}, timeout = DOWNLOAD_REPEAT_TIMEOUT)
    private Integer downloadRepeatTimer(Context ctx) {
        Logger.i(LOG_TAG, "downloadRepeatTimer");
        return DOWNLOAD;
    }

    @StateEntry(states = {WAIT_DOWNLOAD_CONNECTION})
    private Integer waitDownloadConnectionEntry(Context ctx) {
        Logger.i(LOG_TAG, "waitDownloadConnectionEntry");
        if (Network.getConnectivityStatus(ctx) != Network.TYPE_NOT_CONNECTED) {
            return DOWNLOAD;
        }
        return SAME_STATE;
    }

    @StateTimer(states = {WAIT_DOWNLOAD_CONNECTION}, timeout = WAIT_CONNECTION_TIMEOUT)
    private Integer waitDownloadConnectionTimer(Context ctx) {
        Logger.i(LOG_TAG, "waitDownloadConnectionTimer");
        return WAIT_DOWNLOAD_CONNECTION;
    }

    @StateEvent(states = {WAIT_DOWNLOAD_CONNECTION}, id = "android.net.conn.CONNECTIVITY_CHANGE")
    private Integer waitDownloadConnectionEvent(Context ctx, Intent intent) {
        Logger.i(LOG_TAG, "waitDownloadConnectionEvent");
        return DOWNLOAD;
    }


    @StateThread(states = {INSTALL},
            onSuccessState = INSTALL_NEXT,
            onErrorState = ERROR_INSTALL
    )
    private class InstallThread implements StateContextThread {

        private String join(String[] array) {
            if (array == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String item : array) {
                sb.append(item);
                sb.append(" ");
            }
            return sb.toString();
        }

        public class StreamReader implements Runnable {
            private InputStream mInput;
            private OutputStream mOutput;
            private String mPrefix;

            public StreamReader(String prefix, InputStream in, OutputStream out) {
                mPrefix = prefix;
                mInput = in;
                mOutput = out;
            }

            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(mInput));
                    String ln;
                    while ((ln = reader.readLine()) != null) {
                        Logger.d(LOG_TAG, mPrefix + ln);
                        if (mOutput != null) {
                            mOutput.write((mPrefix + ln + "\n").getBytes());
                        }
                    }
                } catch (Exception e) {
                    Logger.w(LOG_TAG, mPrefix + " closed");
                }
            }
        }


        @Override
        public void run(Context ctx) throws Exception {
            ModuleApp app = CoreApp.getIntance(ctx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<String> args = new LinkedList<>();
            mRequest.getArgs(ctx, getFilesDir(ctx), mIndex, args);
            Logger.d(LOG_TAG, "exec: " + join(args.toArray(new String[0])));
            Map<String, String> env = System.getenv();
            int index = 0;
            List<String> env_array = new LinkedList<>();
            for (Map.Entry<String, String> e : env.entrySet()) {
                env_array.add(e.getKey() + "=" + e.getValue());
            }
            env_array.add("IO_CUSTOMER_ID=" + app.getDevice().getCustomerId());
            env_array.add("IO_INSTALLER=" + app.getDevice().getPackageName());
            Process sh = Runtime.getRuntime().exec(args.toArray(new String[0]), env_array.toArray(new String[0]));
            Thread stderr = new Thread(new StreamReader("stderr: ", sh.getErrorStream(), out));
            stderr.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(sh.getInputStream()));
            String ln;
            while ((ln = reader.readLine()) != null) {
                Logger.w(LOG_TAG, "stdout: " + ln);
                out.write(("stdout:" + ln + "\n").getBytes());
            }
            sh.waitFor();
            stderr.join();
            mResult = (byte) sh.exitValue();
            appendLog(new String(out.toByteArray()));
            Logger.i(LOG_TAG, "Process has been finished with result " + mResult);
            if (mResult != 0) {
                throw new RuntimeException("Could not exec");
            }
        }

        @Override
        public void stop() {

        }
    }


    @StateEntry(states = {INSTALL_NEXT})
    private Integer installNextEntry(Context ctx) {
        Logger.d(LOG_TAG, "installNext");
        mIndex++;
        if (mIndex == mRequest.size()) {
            Logger.i(LOG_TAG, "Install complete");
            return SUCCESS;
        } else {
            Logger.i(LOG_TAG, "Index " + mIndex);
            return INSTALL;
        }
    }


    @StateEntry(states = {ERROR_DOWNLOAD})
    private Integer downloadErrorEntry(Context ctx) {
        Logger.d(LOG_TAG, "downloadErrorEntry");
        Response rsp = new Response(250, CoreApp.getIntance(ctx));
        rsp.log = mLog != null ? mLog.toString() : null;
        if (mDownloadList != null && mDownloadList.get(mIndex) != null) {
            rsp.file = mDownloadList.get(mIndex).file;
        }
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(
                new Intent(Confirm.ACTION)
                        .putExtra("url", mRequest.url)
                        .putExtra("data", mGSON.toJson(rsp))
        );
        return CLEANUP;
    }

    @StateEntry(states = {ERROR_INSTALL})
    private Integer installErrorEntry(Context ctx) {
        Logger.d(LOG_TAG, "installErrorEntry");
        Response rsp = new Response(mResult, CoreApp.getIntance(ctx));
        rsp.log = mLog != null ? mLog.toString() : null;
        if (mIndex >= 0 && mRequest != null) {
            try {
                mRequest.fillResponse(rsp, mIndex);
            }catch (Exception e){
                Logger.i(LOG_TAG, "Could not fill response");
            }
        }
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(
                new Intent(Confirm.ACTION)
                        .putExtra("url", mRequest.url)
                        .putExtra("data", mGSON.toJson(rsp))
        );
        return CLEANUP;
    }

    @StateEntry(states = {SUCCESS})
    private Integer successEntry(Context ctx) {
        Logger.i(LOG_TAG, "successEntry");
        Response rsp = new Response(0, CoreApp.getIntance(ctx));
        rsp.log = mLog != null ? mLog.toString() : null;
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(
                new Intent(Confirm.ACTION)
                        .putExtra("url", mRequest.url)
                        .putExtra("data", mGSON.toJson(rsp))
        );
        return CLEANUP;
    }


    @StateEntry(states = {CLEANUP})
    private Integer cleanupEntry(Context ctx) {
        Logger.e(LOG_TAG, "cleanupEntry");
        mLog = null;
        mRequest = null;
        mDownloadList.delete(getFilesDir(ctx));
        File[] files = new File(getFilesDir(ctx)).listFiles();
        for ( File file: files ){
            file.delete();
        }
        return ACTIVE;
    }

}
