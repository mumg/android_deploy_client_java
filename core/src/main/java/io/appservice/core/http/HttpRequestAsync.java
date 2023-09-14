package io.appservice.core.http;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.appservice.core.statemachine.StateContext;
import io.appservice.core.util.Logger;
import io.appservice.core.util.Network;

public abstract class HttpRequestAsync implements StateContext.StateContextThread {

    private static final String LOG_TAG = "IOAPP_HttpRequestAsync";

    private static final int BUF_SIZE = 0x1000;

    private boolean mInsecure = false;

    private Context mCtx;

    protected Context getContext() {
        return mCtx;
    }

    private HttpURLConnection mCon;

    protected static void copy(InputStream from, OutputStream to)
            throws IOException {
        byte[] buf = new byte[BUF_SIZE];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
    }

    public HttpRequestAsync() {

    }

    public HttpRequestAsync(boolean insecure) {
        mInsecure = insecure;
    }

    @Override
    public void run(Context ctx) throws Exception {
        mCtx = ctx;
        if (Network.getConnectivityStatus(ctx) == Network.TYPE_NOT_CONNECTED) {
            throw new StateContext.StateContextThreadAborted();
        }
        URL obj = new URL(getURL());
        if (mInsecure) {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        }
        try {
            mCon = (HttpURLConnection) obj.openConnection();
            preprocess(mCon);
            process(mCon);
            postprocess();
        }catch (IOException e){
            Logger.i(LOG_TAG, "Connection closed");
            throw new StateContext.StateContextThreadAborted();
        }

    }

    @Override
    public void stop() {
        if ( mCon != null ){
            mCon.disconnect();
        }
    }

    protected abstract String getURL() throws Exception;

    protected abstract void preprocess(HttpURLConnection con) throws Exception;

    protected abstract void process(HttpURLConnection con) throws Exception;

    protected void postprocess() throws Exception{

    }

}
