package io.appservice.core.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static android.content.Context.MODE_PRIVATE;

public class Logger
{
    private static final int MAX_LOG_QUEUE = 100;

    public interface Listener{
        void onLog(LogMessage msg);
    }

    public static class LogMessage{
        public Long timestamp;
        public String level;
        public String tag;
        public String message;
        private LogMessage(String level, String tag, String message){
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.tag = tag;
            this.message = message;
        }
    }

  private static boolean mEnabled = true;
  private static String mPackage;
  private static final List< LogMessage > mLogQueue = new LinkedList<>();
  private static final List<Listener> mListeners = new LinkedList<>();

  public static void init(Context context)
  {
      mPackage = context.getPackageName();
    SharedPreferences updaterPreferences = context.getSharedPreferences("settings", MODE_PRIVATE);
    mEnabled = true; //updaterPreferences.getBoolean("debug", false);
  }

  public static void setup(Context ctx, Intent intent){
      SharedPreferences.Editor updaterPreferences = ctx.getSharedPreferences("settings", MODE_PRIVATE).edit();
      if (intent.hasExtra("debug"))
      {
          mEnabled = intent.getBooleanExtra("debug", false);
           Log.i("IOAPP_Debug", "Set debug state to " + mEnabled);
          updaterPreferences.putBoolean("debug", mEnabled);
      }
      updaterPreferences.apply();
  }

  private static void enqueue(String level, String tag, String message){
      LogMessage msg = new LogMessage(level, tag, message);
    synchronized (mLogQueue){
        mLogQueue.add(msg);
        while(mLogQueue.size() > MAX_LOG_QUEUE){
            mLogQueue.remove(0);
        }
    }
    synchronized (mListeners){
        for ( Listener listener : mListeners){
            listener.onLog(msg);
        }
    }
  }

  public static void attach(Listener listener){
      synchronized (mListeners){
          mListeners.add(listener);
      }
      synchronized (mLogQueue){
          for ( LogMessage log : mLogQueue){
              listener.onLog(log);
          }
      }
  }

    public static void detach(Listener listener){
      synchronized (mListeners){
          mListeners.remove(listener);
      }
    }


  public static void d(String tag, String message)
  {
    if (mEnabled)
    {
      Log.d("[" + mPackage + "] " + tag, message);
    }
      enqueue("D", tag, message);
  }

  public static void i(String tag, String message)
  {
    if (mEnabled)
    {
      Log.i("[" + mPackage + "] " + tag, message);
    }
      enqueue("I", tag, message);
  }


  public static void e(String tag, String message){
      if (mEnabled)
      {
          Log.e("[" + mPackage + "] " + tag, message);
      }
      enqueue("E", tag, message);
  }

    public static void w(String tag, String message){
        if (mEnabled)
        {
            Log.w("[" + mPackage + "] " + tag, message);
        }
        enqueue("W", tag, message);
    }

}
