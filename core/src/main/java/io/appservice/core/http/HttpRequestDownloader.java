package io.appservice.core.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import javax.net.ssl.SSLException;

import io.appservice.core.statemachine.StateContext;
import io.appservice.core.util.Logger;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;

public abstract class HttpRequestDownloader extends HttpRequestAsync {

    private static final String LOG_TAG = "IOAPP_HttpRequestDownloader";

    public HttpRequestDownloader() {
    }

    public HttpRequestDownloader(boolean insecure){
        super(insecure);
    }

    @Override
    public void preprocess(HttpURLConnection con) throws Exception {
        File file = new File(getPath());
        if (file.exists()) {
            long size = file.length();
            con.setRequestProperty("Range", "bytes=" + size + "-");
        }
    }

    @Override
    public void process(HttpURLConnection con) throws Exception {
        int response;
        try {
            response = con.getResponseCode();
        }catch (IOException e){
            throw new StateContext.StateContextThreadAborted();
        }
        if (response == HTTP_OK ||
            response == HTTP_PARTIAL) {
            OutputStream os = new FileOutputStream(getPath(), response == HTTP_PARTIAL);
            copy(con.getInputStream(), os);
            os.close();
        } else {
            Logger.i(LOG_TAG, "Server responded with " + response);
            throw new RuntimeException("Server response code=" + response);
        }
    }

    public abstract String getPath() throws Exception;
}
