package mm.hazardguard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Notifications {
    private Notifications() {}
    private static final String PREFIX="guard_";
    public static void setup(Context c) {
        if(Build.VERSION.SDK_INT<26) return;
        NotificationManager mgr=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null) return;
        for(int i=0;i<8;i++) {
            boolean sound=(i&1)!=0, vibrate=(i&2)!=0, loud=(i&4)!=0;
            String id=PREFIX+i;
            NotificationChannel channel=new NotificationChannel(id,"Hazard alerts • "+(loud?"alarm / ":"")+(sound?"sound":"quiet")+" / "+(vibrate?"vibrate":"no vibration"),
                NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Newly reported hazards; configure alert mode in the app.");
            channel.enableVibration(vibrate);
            if(vibrate) channel.setVibrationPattern(new long[]{0,450,200,450,200,650});
            if(sound) {
                Uri tone=RingtoneManager.getDefaultUri(loud?RingtoneManager.TYPE_ALARM:RingtoneManager.TYPE_NOTIFICATION);
                channel.setSound(tone,new AudioAttributes.Builder().setUsage(loud?AudioAttributes.USAGE_ALARM:AudioAttributes.USAGE_NOTIFICATION_EVENT).build());
            } else channel.setSound(null,null);
            mgr.createNotificationChannel(channel);
        }
        NotificationChannel info=new NotificationChannel(PREFIX+"info","Climate and advisory updates",NotificationManager.IMPORTANCE_DEFAULT);
        info.enableVibration(false); info.setSound(null,null); mgr.createNotificationChannel(info);
    }
    public static void post(Context c, String key, String title, String body, boolean urgent) {
        postInternal(c,key,title,body,urgent?2:0);
    }
    public static void advisory(Context c,String key,String title,String body) {
        postInternal(c,key,title,body,1);
    }
    private static void postInternal(Context c,String key,String title,String body,int tier) {
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        NotificationManager mgr=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null) return;
        setup(c);
        int mode=(Prefs.sound(c)?1:0)+(Prefs.vibration(c)?2:0)+(tier==2 && Prefs.get(c).getBoolean("alarm",false)?4:0);
        String channel=tier>0?PREFIX+mode:PREFIX+"info";
        Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_shield).setContentTitle(title).setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(open).setShowWhen(true)
            .setWhen(System.currentTimeMillis()).setColor(Color.rgb(15,126,125))
            .setCategory(Notification.CATEGORY_EVENT)
            .setPriority(tier>0?Notification.PRIORITY_HIGH:Notification.PRIORITY_DEFAULT);
        if(Build.VERSION.SDK_INT<26) {
            if(tier>0 && Prefs.sound(c)) {
                b.setSound(RingtoneManager.getDefaultUri(tier==2 && Prefs.get(c).getBoolean("alarm",false)?RingtoneManager.TYPE_ALARM:RingtoneManager.TYPE_NOTIFICATION));
            }
            if(tier>0 && Prefs.vibration(c)) b.setVibrate(new long[]{0,450,200,450});
        }
        mgr.notify(key,Math.abs(key.hashCode())%100000+200,b.build());
        // Keep recent advisories on-device only.
        synchronized(Notifications.class) {
            try {
                JSONArray history=new JSONArray(Prefs.get(c).getString("history","[]"));
                JSONArray next=new JSONArray();
                next.put(new JSONObject().put("title",title).put("body",body).put("time",System.currentTimeMillis()));
                for(int i=0;i<Math.min(19,history.length());i++) next.put(history.optJSONObject(i));
                Prefs.get(c).edit().putString("history",next.toString()).apply();
            } catch(Exception ignored) {}
        }
        if(tier==2) Prefs.get(c).edit().putString("voice_pending",body).putLong("voice_pending_at",System.currentTimeMillis()).apply();
    }
    public static void test(Context c) { post(c,"test_"+System.currentTimeMillis(),Locales.text(c,R.string.test_title),Locales.text(c,R.string.test_body),true); }
}
