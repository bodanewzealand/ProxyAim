package com.proxyaim.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class ProxyVpnService extends VpnService {
    private WindowManager windowManager;
    private View floatingView;
    private ParcelFileDescriptor vpnInterface = null;
    private Thread vpnThread;
    private boolean isBlocking = false;

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "ProxyChannel")
                .setContentTitle("ProxyAim")
                .setContentText("الخدمة شغالة...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
        startForeground(1, notification);

        startVpn();
        createFloatingButton();

        return START_STICKY;
    }

    private void startVpn() {
        try {
            Builder builder = new Builder();
            builder.setSession("ProxyAim");
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0); // تمرير كل الترافيك

            vpnInterface = builder.establish();
            if (vpnInterface == null) return;

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            DatagramChannel tunnel = DatagramChannel.open();
            tunnel.connect(new InetSocketAddress("127.0.0.1", 8080)); // توجيه وهمي
            protect(tunnel.socket());

            vpnThread = new Thread(() -> {
                ByteBuffer packet = ByteBuffer.allocate(32767);
                while (true) {
                    try {
                        if (isBlocking) {
                            Thread.sleep(200); // هنا اللاج سويتش بيشتغل (بيوقف قراءة البيانات)
                            continue;
                        }
                        packet.clear();
                        int length = in.read(packet.array());
                        if (length > 0) {
                            packet.limit(length);
                            // هنا بنسيب الباكيت تعدي
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
            });
            vpnThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ImageView button = new ImageView(this);
        button.setImageResource(android.R.drawable.presence_online);
        button.setBackgroundColor(0x00000000);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        button.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        triggerLagSwitch(); // تفعيل اللاج سويتش
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        floatingView = button;
        windowManager.addView(floatingView, params);
    }

    private void triggerLagSwitch() {
        isBlocking = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            isBlocking = false;
        }, 200); // مدة اللاج 200 مللي ثانية
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "ProxyChannel", "Proxy Service", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        if (vpnThread != null) vpnThread.interrupt();
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
        }
    }

    @Override
    public IBinder onBind(android.content.Intent intent) { return null; }
        }
