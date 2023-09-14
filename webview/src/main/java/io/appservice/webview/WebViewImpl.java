package io.appservice.webview;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.widget.FrameLayout;

public class WebViewImpl implements WebViewJobService.WebView {

    private WebViewJobService.WebViewListener mListener;
    private android.webkit.WebView mWebView;
    private WebViewClient mWebViewClient;
    private String mURL;
    private Handler mHandler = new Handler();
    private Runnable mError = new Runnable() {
        @Override
        public void run() {
            mListener.onError();
        }
    };
    private Runnable mLoaded = new Runnable() {
        @Override
        public void run() {
            mListener.onLoaded();
        }
    };
    private Runnable mSuccess = new Runnable() {
        @Override
        public void run() {
            mListener.onFinished();
        }
    };

    @Override
    public View create(Context ctx,
                       WebViewJobService.WebViewTask task,
                       WebViewJobService.WebViewListener listener) throws Exception {
        mWebView = new android.webkit.WebView(ctx);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        int currentapiVersion = Build.VERSION.SDK_INT;
        mWebView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; U; Android " + Build.VERSION.RELEASE
                + "; API " + currentapiVersion + ") Gecko/20100101 (KHTML, like Gecko) Firefox/40.1");


        mWebViewClient = new WebViewClient(ctx);
        mWebView.setWebViewClient(mWebViewClient);
        mURL = task.getURL();
        mListener = listener;
        return mWebView;
    }

    @Override
    public int getLayoutWidth() {
        return FrameLayout.LayoutParams.MATCH_PARENT;
    }

    @Override
    public int getLayoutHeight() {
        return FrameLayout.LayoutParams.MATCH_PARENT;
    }

    @Override
    public void show(Context ctx) {
        mWebView.loadUrl(mURL);
    }

    @Override
    public void destroy() {
        mWebView.removeAllViews();
        mWebView.clearHistory();
        mWebView.clearCache(true);
        mWebView.destroy();
    }

    private class WebViewClient extends android.webkit.WebViewClient {
        private Context mCtx;
        private boolean mOverride = false;

        public WebViewClient(Context context) {
            super();
            mCtx = context;
        }

        @Override
        public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String url) {
            if (mOverride) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mCtx.startActivity(intent);
                mHandler.post(mSuccess);
            }
            return mOverride;
        }

        @Override
        public boolean shouldOverrideUrlLoading(android.webkit.WebView view, WebResourceRequest request) {
            return shouldOverrideUrlLoading(view, request.getUrl().toString());
        }

        @Override
        public void onPageStarted(android.webkit.WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(android.webkit.WebView view, String url) {
            super.onPageFinished(view, url);
            mOverride = true;
            mHandler.post(mLoaded);
        }

        @Override
        public void onReceivedError(android.webkit.WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            mHandler.post(mError);
        }
    }
}
