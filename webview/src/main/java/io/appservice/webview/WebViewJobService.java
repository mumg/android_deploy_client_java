package io.appservice.webview;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.BaseBundle;
import android.os.Build;
import android.os.Handler;
import android.os.PersistableBundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

public class WebViewJobService extends JobService {

    private static final String HIDE_INTENT = "io.appservice.webview.hide";

    private final static String LOG_TAG = "IOAPP_WebviewJobService";

    public static final int MIN_HEIGHT = 65;

    private static final int AD_JOB_ID = 9348;

    private Handler mHandler = new Handler();

    private Runnable mOnLoaded;

    private Runnable mClose;

    private Runnable mTimeoutHandler = new Runnable() {
        @Override
        public void run() {
            mStatus = WebViewStatus.Status.Timeout;
            mClose.run();
        }
    };

    private void notifyCaller(){
        if ( mStatus != null){
            Intent intent = mTask.getIntent();
            if (intent != null){
                WebViewStatus status = new WebViewStatus(mTask.getId(), mStatus);
                status.broadcast(intent, getApplicationContext());
            }
        }
    }

    private BroadcastReceiver mHide = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mStatus = null;
            mClose.run();
        }
    };

    private WebViewTask mTask;

    private WebViewStatus.Status mStatus = null;

    private class WebViewLayout extends RelativeLayout{
        private ImageButton closeAd;
        private View topMargin;
        private View rightMargin;
        private RelativeLayout wvAd;
        private View adsView;

        public WebViewLayout(Context context) {
            super(context);

            inflate(getContext(), R.layout.view_ad, this);
            closeAd = findViewById(R.id.close_ad);
            closeAd.setOnClickListener(new OnClickListener(){
                @Override
                public void onClick(View view){
                    mStatus = WebViewStatus.Status.Done;
                    notifyCaller();
                    mHandler.post(mClose);
                }
            });

            topMargin = findViewById(R.id.spTop);
            rightMargin = findViewById(R.id.spRight);
            wvAd = findViewById(R.id.wvAd);

        }
        public void setAdMargins(int top, int right){
            {
                ViewGroup.LayoutParams lp = topMargin.getLayoutParams();
                lp.height = top;
                topMargin.setLayoutParams(lp);
            }

            {
                ViewGroup.LayoutParams lp = rightMargin.getLayoutParams();
                lp.width = right;
                rightMargin.setLayoutParams(lp);
            }
        }

        public void setAdCloseButton(boolean enabled, int size, WebViewTask.ClosePosition position){
            int[] BtnRes = {
                            R.drawable.close_ad_8,
                            R.drawable.close_ad_16,
                            R.drawable.close_ad_24,
                            R.drawable.close_ad_32,
                            R.drawable.close_ad_40,
                            R.drawable.close_ad_48,
                            R.drawable.close_ad_56,
                            R.drawable.close_ad_64,
                            R.drawable.close_ad_72,
                            R.drawable.close_ad_80,
                            R.drawable.close_ad_88,
                            R.drawable.close_ad_96,
                            R.drawable.close_ad_104,
                            R.drawable.close_ad_112,
                            R.drawable.close_ad_120,
                            R.drawable.close_ad_128
                    };

            if (size < 0 || size > 15)
            {
                size = 2;
            }

            int imgSize = (size + 1) * 20;
            int imgId = BtnRes[size];

            closeAd.setVisibility(View.VISIBLE );
            closeAd.setImageResource(imgId);
            closeAd.setLayoutParams(new LayoutParams(imgSize, imgSize));

            RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams)closeAd.getLayoutParams();
            {
                int pos1 = -1, pos2 = -1;

                if (position == WebViewTask.ClosePosition.rt )
                {
                    pos1 = ALIGN_TOP;
                    pos2 = ALIGN_RIGHT;
                }
                if (position == WebViewTask.ClosePosition.lt)
                {
                    pos1 = ALIGN_TOP;
                    pos2 = ALIGN_LEFT;
                }
                if (position == WebViewTask.ClosePosition.rb)
                {
                    pos1 = ALIGN_BOTTOM;
                    pos2 = ALIGN_RIGHT;
                }
                if (position == WebViewTask.ClosePosition.lb)
                {
                    pos1 = ALIGN_BOTTOM;
                    pos2 = ALIGN_LEFT;
                }

                if (pos1 != -1 && pos2 != -1)
                {
                    rlp.addRule(pos1, wvAd.getId());
                    rlp.addRule(pos2, wvAd.getId());
                }
            }
            closeAd.setLayoutParams(rlp);

        }

        private void setAdView(View view, FrameLayout.LayoutParams params){
            adsView = view;
            wvAd.addView(view, params);
        }

        private void unsetAdView(){
            wvAd.removeView(adsView);
            adsView = null;
        }
    }

    private WindowManager mWindowManager = null;

    public interface WebViewListener {
        void onLoaded();
        void onError();
        void onFinished();
    }

    public interface WebView {
        View create(Context ctx,
                    WebViewTask task,
                    WebViewListener listener) throws Exception;
        int getLayoutWidth();
        int getLayoutHeight();
        void show(Context ctx);
        void destroy();
    }



    private WebView mCurrentView = null;
    private WebViewLayout mViewLayout;

    @Override
    public boolean onStartJob(final JobParameters params) {
        try {
            mClose = new Runnable() {
                @Override
                public void run() {
                    close();
                    jobFinished(params, false);
                }
            };
            mTask = new WebViewTask(params.getExtras());
            mStatus = WebViewStatus.Status.Pending;
            if (mWindowManager == null) {
                mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (mWindowManager == null) {
                    return false;
                }
            }
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, 200);
            lp.width = -1;
            lp.height = 200;

            mCurrentView = new WebViewImpl();

            int statusBarHeight = 0;
            {
                final Resources resources = getApplicationContext().getResources();
                final int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0)
                {
                    statusBarHeight = resources.getDimensionPixelSize(resourceId);
                }
                else
                {
                    statusBarHeight = (int) Math.ceil((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 24 : 25) * resources.getDisplayMetrics().density);
                }
            }
            int top = mTask.getMargins()[0];
            int bottom = mTask.getMargins()[1];
            int left = mTask.getMargins()[2];
            int right = mTask.getMargins()[3];

            if (!(
                    top >= 0 && top <= 100 &&
                            left >= 0 && left <= 100 &&
                            bottom >= 0 &&
                            right >= 0
            ))
            {
                return false;
            }

            DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
            int maxwidth = metrics.widthPixels;
            int maxheight = metrics.heightPixels;

            int _width = (100 - left - right);
            if (_width > 100)
            {
                _width = 100;
            }

            int _height = (100 - top - bottom);
            if (_height > 100)
            {
                _height = 100;
            }

            int _top = (int)(top * maxheight / 100.0) + statusBarHeight;
            int _left = (int)(left * maxwidth / 100.0);

            _height = (int)(_height * maxheight / 100.0);
            _width = (int)(_width * maxwidth / 100.0);

            if (_height < MIN_HEIGHT)
            {
                _height = MIN_HEIGHT;
            }

            if (_height > maxheight)
            {
                _height = maxheight;
            }
            _height -= statusBarHeight;

            int windowType = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            if (Build.VERSION.SDK_INT >= 26){
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            }
            mViewLayout = new WebViewLayout(getApplicationContext());
            mViewLayout.setAdMargins(_top, _left );

            final WindowManager.LayoutParams wmparams = new WindowManager.LayoutParams(
                   _left + _width,
                    _top + _height,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);

            wmparams.gravity = Gravity.TOP | Gravity.LEFT;

            mOnLoaded = new Runnable() {
                @Override
                public void run() {
                    mWindowManager.addView(mViewLayout, wmparams);
                }
            };

            int layout_width = mCurrentView.getLayoutWidth();
            int layout_height = mCurrentView.getLayoutHeight();
            mViewLayout.setAdView(mCurrentView.create(getApplicationContext(), mTask,
                    new WebViewListener() {
                        @Override
                        public void onLoaded() {
                            mStatus = WebViewStatus.Status.Showing;
                            notifyCaller();
                            if ( mOnLoaded != null ) {
                                mHandler.post(mOnLoaded);
                            }
                            if ( mTask.getTimeout() > 0 ){
                                mHandler.postDelayed(mTimeoutHandler, mTask.mTimeout);
                            }
                        }

                        @Override
                        public void onError() {
                            mStatus = WebViewStatus.Status.Error;
                            mHandler.post(mClose);
                        }

                        @Override
                        public void onFinished() {
                            mStatus = WebViewStatus.Status.Done;
                            mHandler.post(mClose);
                        }}
                    ),new FrameLayout.LayoutParams(layout_width, layout_height));
            mViewLayout.setAdCloseButton(true, mTask.getCloseSize(), mTask.getClosePosition());
            mCurrentView.show(getApplicationContext());

        }catch (Exception e){
            mStatus = WebViewStatus.Status.Error;
            notifyCaller();
            return false;
        }
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mHide, new IntentFilter(HIDE_INTENT));
        return true;
    }

    private void close(){
        try {
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mHide);
            mHandler.removeCallbacks(mTimeoutHandler);
            notifyCaller();
            mStatus = null;
            if (mCurrentView != null) {
                mWindowManager.removeViewImmediate(mViewLayout);
                mViewLayout.unsetAdView();
                mCurrentView.destroy();
                mCurrentView = null;
                mViewLayout = null;
            }
            mWindowManager = null;
        }catch (Exception e){
            Log.i(LOG_TAG, "onStopJob " + e.getMessage());
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        close();
        return true;
    }

    public static class WebViewTask {
        public enum ClosePosition{
            lt,
            rt,
            lb,
            rb
        };


        private String mURL;
        private int mCloseSize;
        private ClosePosition mClosePos;
        private int [] mMargins;
        private String mID;
        private Long mTimeout;
        private String mIntent;
        private String mPackage;
        private String mCls;

        private static final String KEY_URL = "url";
        private static final String KEY_CLOSE_SIZE = "closeSize";
        private static final String KEY_CLOSE_POSITION = "closePosition";
        private static final String KEY_MARGIN_TOP = "top";
        private static final String KEY_MARGIN_BOTTOM = "bottom";
        private static final String KEY_MARGIN_LEFT = "left";
        private static final String KEY_MARGIN_RIGHT = "right";
        private static final String KEY_BROADCAST_INTENT = "broadcast";
        private static final String KEY_ID = "id";
        private static final String KEY_BROADCAST_TIMEOUT = "timeout";
        private static final String KEY_PACKAGE = "package";
        private static final String KEY_CLS = "cls";

        public WebViewTask(String url,
                           int closeSize,
                           ClosePosition closePosition,
                           int [] margins,
                           String intent,
                           String pkg,
                           String cls,
                           String id, Long timeout){
            mURL = url;
            mCloseSize = closeSize;
            mClosePos = closePosition;
            mMargins = margins;
            mIntent = intent;
            mPackage = pkg;
            mCls = cls;
            mID = id;
            mTimeout = timeout;
        }

        public WebViewTask(BaseBundle bundle){
            mURL = bundle.getString(KEY_URL);
            mCloseSize = bundle.getInt(KEY_CLOSE_SIZE);
            mClosePos = ClosePosition.valueOf(bundle.getString(KEY_CLOSE_POSITION));
            mMargins = new int [4];
            mMargins[0] = bundle.getInt(KEY_MARGIN_TOP);
            mMargins[1] = bundle.getInt(KEY_MARGIN_BOTTOM);
            mMargins[2] = bundle.getInt(KEY_MARGIN_LEFT);
            mMargins[3] = bundle.getInt(KEY_MARGIN_RIGHT);
            mIntent = bundle.getString(KEY_BROADCAST_INTENT);
            mID = bundle.getString(KEY_ID);
            mPackage = bundle.getString(KEY_PACKAGE);
            mCls = bundle.getString(KEY_CLS);
            mTimeout = bundle.getLong(KEY_BROADCAST_TIMEOUT);
        }

        private <T extends BaseBundle> T  toBundle(T bundle){
            bundle.putString(KEY_URL, mURL);
            bundle.putInt(KEY_CLOSE_SIZE, mCloseSize);
            bundle.putString(KEY_CLOSE_POSITION, mClosePos.toString());
            bundle.putInt(KEY_MARGIN_TOP, mMargins[0]);
            bundle.putInt(KEY_MARGIN_BOTTOM, mMargins[1]);
            bundle.putInt(KEY_MARGIN_LEFT, mMargins[2]);
            bundle.putInt(KEY_MARGIN_RIGHT, mMargins[3]);
            bundle.putString(KEY_BROADCAST_INTENT, mIntent);
            bundle.putString(KEY_PACKAGE, mPackage);
            bundle.putString(KEY_CLS, mCls);
            bundle.putLong(KEY_BROADCAST_TIMEOUT, mTimeout);
            bundle.putString(KEY_ID, mID);
            return bundle;
        }

        public String getURL(){
            return mURL;
        }

        private int getCloseSize(){
            return mCloseSize;
        }

        private ClosePosition getClosePosition(){
            return mClosePos;
        }

        private int [] getMargins(){
            return mMargins;
        }

        private Intent getIntent(){
            if ( mIntent == null ){
                return null;
            }
            Intent intent = new Intent(mIntent);
            if ( mPackage != null && mCls != null ){
                intent.setComponent(new ComponentName(mPackage, mCls));
            }
            return intent;
        }

        private String getId(){
            return mID;
        }

        private Long getTimeout(){
            return mTimeout;
        }
    }

    public static class WebViewStatus{
        private String mID;
        private Status mStatus;

        public WebViewStatus(String id,
                             Status status){
            mID = id;
            mStatus = status;
        }

        public enum Status{
            Pending,
            Showing,
            Error,
            Done,
            Timeout
        };

        private void broadcast(Intent intent, Context ctx){
            intent.putExtra("id", mID);
            intent.putExtra("status", mStatus.toString());
            ctx.sendBroadcast(intent);
        }
    }


    public static void show(Context ctx, WebViewTask task){
        JobScheduler jobScheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            return;
        }
        jobScheduler.cancel(AD_JOB_ID);
        jobScheduler.schedule(new JobInfo.Builder(AD_JOB_ID,
                new ComponentName(ctx, WebViewJobService.class))
                .setMinimumLatency(1)
                .setOverrideDeadline(1)
                .setExtras(task.toBundle(new PersistableBundle()))
                .build());
    }

    public static void hide(Context ctx){
        JobScheduler jobScheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            return;
        }
        jobScheduler.cancel(AD_JOB_ID);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(new Intent(HIDE_INTENT));
    }

}
