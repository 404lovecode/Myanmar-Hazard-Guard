package mm.hazardguard;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public class MonitorJob extends JobService {
    private static final int JOB_ID=71042;
    private volatile boolean stopped;
    public static void schedule(Context c) {
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null) return;
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(c,MonitorJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15*60_000L)
            .setPersisted(true)
            .build();
        scheduler.schedule(info);
    }
    @Override public boolean onStartJob(final JobParameters params) {
        stopped=false;
        new Thread(new Runnable() { @Override public void run() {
            try { if(!stopped) HazardRepository.update(getApplicationContext(),true); }
            catch(Exception ignored) {}
            finally { jobFinished(params,false); }
        }},"HazardGuard-monitor").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { stopped=true; return true; }
}
