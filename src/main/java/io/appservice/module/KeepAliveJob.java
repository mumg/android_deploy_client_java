package io.appservice.module;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import io.appservice.core.util.Logger;


public class KeepAliveJob extends JobService {

    private static final String LOG_TAG = "IOAPP_KeepAliveJob";

    private static final int JOB = 3984;

    @Override
    public boolean onStartJob(JobParameters params) {
        Logger.w(LOG_TAG, "onStartJob");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }


    public static void start(Context ctx){
        JobScheduler jobScheduler = (JobScheduler) ctx.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.schedule(new JobInfo.Builder(JOB + 1,
                    new ComponentName(ctx.getApplicationContext(), KeepAliveJob.class))
                    .setPeriodic(15*60*1000)
                    .build());
        }
    }
}

