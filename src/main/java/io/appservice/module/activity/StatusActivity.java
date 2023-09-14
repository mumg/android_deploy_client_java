package io.appservice.module.activity;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.appservice.core.util.Logger;
import io.appservice.module.liberty.R;

public class StatusActivity extends Activity
{
  private DateFormat mDF = new SimpleDateFormat( "HH:mm:ss", Locale.US);
  private TextView mLogs;
  private Logger.Listener mListener = new Logger.Listener() {
    @Override
    public void onLog(final Logger.LogMessage log) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          StringBuilder txt = new StringBuilder();
          txt.append("<font color='#FFFFFF'>");
          txt.append(mDF.format(new Date(log.timestamp)));
          txt.append(" [");
          txt.append(log.tag.replace("IOAPP_", ""));
          txt.append("] ");
          txt.append("</font><font color='");
          if ( log.level.equals("I")){
            txt.append("#3ACC30"); //green
          }else if ( log.level.equals("E")){
            txt.append("#FF0000"); //red
          }else if ( log.level.equals("W")){
            txt.append("#F9F911");
          }else if ( log.level.equals("D")){
            txt.append("#BFBFBF");
          }else{
            txt.append("#FFFFFF");
          }
          txt.append("'>");
          txt.append(log.message);
          txt.append("</font><br>");
          mLogs.append(Html.fromHtml(txt.toString()));
        }
      });
    }
  };


  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    //ModuleApp app = CoreApp.getIntance(getApplicationContext());
    //if ( !app.getDevice().getDeviceInfo().sn.equals(getIntent().getStringExtra("sn"))){
    //  finish();
    //}else {
      setContentView(R.layout.debug);
      mLogs = findViewById(R.id.logs);
      mLogs.setMovementMethod(new ScrollingMovementMethod());
      mLogs.setHorizontallyScrolling(true);
    //}
  }

  @Override
  public void onResume(){
    Logger.attach(mListener);
    super.onResume();
  }

  @Override
  public void onPause(){
    Logger.detach(mListener);
    mLogs.setText("");
    super.onPause();
  }
}
