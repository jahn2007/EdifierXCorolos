package io.github.jahn2007.edifierx.device;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import io.github.jahn2007.edifierx.R;
import io.github.jahn2007.edifierx.ui.MainActivity;
public final class ControlService extends Service {
    @Override public void onCreate(){super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("control","耳机控制连接",NotificationManager.IMPORTANCE_LOW));
        var open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        var notification=new Notification.Builder(this,"control").setContentTitle("EdifierXCorolos 控制服务").setContentText("蓝牙控制已启用，可在模块中随时停止").setSmallIcon(R.drawable.ic_headphones).setContentIntent(open).build();
        startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);DeviceRuntime.get(this).start();
    }
    @Override public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
    @Override public void onDestroy(){DeviceRuntime.get(this).stop();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
