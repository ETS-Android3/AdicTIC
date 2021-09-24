package com.adictic.client.service;

import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.adictic.client.R;
import com.adictic.client.entity.BlockedApp;
import com.adictic.client.rest.AdicticApi;
import com.adictic.client.ui.chat.ChatFragment;
import com.adictic.client.util.AdicticApp;
import com.adictic.client.util.Funcions;
import com.adictic.common.entity.TimeBlock;
import com.adictic.common.entity.TimeFreeUse;
import com.adictic.common.ui.BlockAppsActivity;
import com.adictic.common.util.Callback;
import com.adictic.common.util.Constants;
import com.adictic.common.util.Crypt;
import com.adictic.common.util.MyNotificationManager;
import com.adictic.jitsi.activities.IncomingInvitationActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.joda.time.DateTime;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

public class ClientFirebaseMessagingService extends FirebaseMessagingService {

    private final String TAG = "Firebase: ";
    private AdicticApi mTodoService;

    private long updateGeoloc = -1;

    private int retryCount = 0;
    private static final int TOTAL_RETRIES = 10;

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        SharedPreferences sharedPreferences = Funcions.getEncryptedSharedPreferences(getApplicationContext());
        assert sharedPreferences != null;

        if (Objects.equals(Crypt.getAES(token), sharedPreferences.getString(Constants.SHARED_PREFS_TOKEN, "")))
            return;

        long idUser = sharedPreferences.getLong(Constants.SHARED_PREFS_IDUSER,-1);
        if(idUser!=-1) {
            if (sharedPreferences.getBoolean("isTutor", false))
                idUser = -1;
            else
                idUser = sharedPreferences.getLong("idUser", -1);

            Funcions.runUpdateTokenWorker(getApplicationContext(), idUser, token, 0);
        }
    }

    public void updateBlockedAppsList(Map<String, String> map) {
        if(!Funcions.accessibilityServiceOn())
            return;

        List<BlockedApp> limitedAppsList = new ArrayList<>();
        List<String> permanentBlockedApps = new ArrayList<>();
        map.remove("blockApp");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            try {
                String pkgName = entry.getKey();
                int totalTime = Integer.parseInt(entry.getValue());
                if (totalTime == 0)
                    permanentBlockedApps.add(pkgName);
                else {
                    BlockedApp blockedApp = new BlockedApp();
                    blockedApp.pkgName = pkgName;
                    blockedApp.timeLimit = totalTime;
                    limitedAppsList.add(blockedApp);
                }
            }
            catch (NumberFormatException e){
                e.printStackTrace();
            }
        }

        AccessibilityScreenService.instance.setBlockedApps(permanentBlockedApps);
        AccessibilityScreenService.instance.setAppsLimitades(limitedAppsList);

        // Actualitzem mapa Accessibility amb dades noves
        HashMap<String, Integer> timeMap = new HashMap<>();
        if(Funcions.accessibilityServiceOn()){
            for(BlockedApp limitedApp : limitedAppsList) {
                int dayAppUsage = Funcions.getDayAppUsage(getApplicationContext(), limitedApp.pkgName);
                if (dayAppUsage > limitedApp.timeLimit)
                    AccessibilityScreenService.instance.addBlockedApp(limitedApp.pkgName);
                else
                    timeMap.put(limitedApp.pkgName, dayAppUsage);
            }
            AccessibilityScreenService.instance.setTempsAppsLimitades(timeMap);

            AccessibilityScreenService.instance.setChangedBlockedApps(true);

            // Ensenyar pantalla bloqueig si és una app bloquejada
            if(AccessibilityScreenService.instance.isCurrentAppBlocked())
                Funcions.showBlockAppScreen(ClientFirebaseMessagingService.this, AccessibilityScreenService.instance.getCurrentPackage(), AccessibilityScreenService.instance.getCurrentAppName());
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        ClientNotificationManager clientNotificationManager = ((AdicticApp) getApplicationContext()).getNotificationManager();
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        mTodoService = ((AdicticApp) getApplicationContext()).getAPI();
        SharedPreferences sharedPreferences = Funcions.getEncryptedSharedPreferences(getApplicationContext());
        assert sharedPreferences != null;

        Map<String, String> messageMap = remoteMessage.getData();

        String title = "";
        String body = "";
        Class<?> activitatClass = null;
        Intent activitatIntent = null;
        MyNotificationManager.Channels channel = MyNotificationManager.Channels.GENERAL;

        // Check if message contains a data payload.
        if (messageMap.size() > 0) {
            Log.d(TAG, "Message data payload: " + messageMap);

            if(!messageMap.containsKey("action")){
                Log.e(TAG,"La consulta de firebase no té la clau 'action'");
                return;
            }
            String action = messageMap.get("action");
            if(action==null){
                Log.e(TAG,"La clau 'action' de firebase és null");
                return;
            }
            switch(action){
                // ************* Accions del dispositiu fill *************
                case "geolocActive":
                    Funcions.runGeoLocWorker(ClientFirebaseMessagingService.this);
                case "blockDevice":
                    if (Objects.equals(messageMap.get("blockDevice"), "1")) {
                        sharedPreferences.edit().putBoolean(Constants.SHARED_PREFS_BLOCKEDDEVICE,true).apply();
                        sharedPreferences.edit().putLong(Constants.SHARED_PREFS_BLOCKEDDEVICE_START, DateTime.now().getMillis()).apply();

                        if(Funcions.accessibilityServiceOn()) {
                            AccessibilityScreenService.instance.setBlockDevice(true);
                            AccessibilityScreenService.instance.updateDeviceBlock();

                            boolean freeUse = AccessibilityScreenService.instance.getFreeUse();

                            if(!freeUse) {
                                title = getString(R.string.phone_locked);
                                body = getString(R.string.notif_phone_locked);
                                channel = MyNotificationManager.Channels.BLOCK;
                            }
                        }
                    }
                    else {
                        if(Funcions.accessibilityServiceOn()) {
                            AccessibilityScreenService.instance.setBlockDevice(false);
                            AccessibilityScreenService.instance.updateDeviceBlock();
                        }

                        sharedPreferences.edit().putBoolean(Constants.SHARED_PREFS_BLOCKEDDEVICE,false).apply();
                        sendBlockDeviceTime(sharedPreferences);
                    }
                    break;
                case "freeUse":
                    if (Objects.equals(messageMap.get("freeUse"), "1")) {
                        sharedPreferences.edit().putBoolean(Constants.SHARED_PREFS_FREEUSE, true).apply();
                        sharedPreferences.edit().putLong(Constants.SHARED_PREFS_FREEUSE_START, DateTime.now().getMillis()).apply();

                        if(Funcions.accessibilityServiceOn()) {
                            AccessibilityScreenService.instance.setFreeUse(true);
                            AccessibilityScreenService.instance.updateDeviceBlock();
                        }

                        title = getString(R.string.free_use_activation);
                    } else {
                        sharedPreferences.edit().putBoolean(Constants.SHARED_PREFS_FREEUSE, false).apply();

                        if(Funcions.accessibilityServiceOn()) {
                            AccessibilityScreenService.instance.setFreeUse(false);
                            AccessibilityScreenService.instance.updateDeviceBlock();
                        }

                        sendFreeUseTime(sharedPreferences);

                        title = getString(R.string.free_use_deactivation);
                    }
                    break;
                case "blockApp":
                    updateBlockedAppsList(messageMap);

                    title = getString(R.string.update_blocked_apps);
                    activitatClass = BlockAppsActivity.class;
                    channel = MyNotificationManager.Channels.BLOCK;
                    break;
                case "liveApp":
                    String s = messageMap.get("liveApp");
                    boolean active = Boolean.parseBoolean(messageMap.get("bool"));

                    if(Funcions.accessibilityServiceOn())
                        AccessibilityScreenService.instance.setLiveApp(active);

                    sharedPreferences.edit().putBoolean(Constants.SHARED_PREFS_LIVEAPP,active).apply();

                    if(active) {
                        //Si el dispositiu no està bloquejat enviem el nou liveapp
                        KeyguardManager myKM = (KeyguardManager) getApplicationContext().getSystemService(KEYGUARD_SERVICE);
                        if (!myKM.isDeviceLocked() && Funcions.accessibilityServiceOn())
                            AccessibilityScreenService.instance.enviarLiveApp();

                        Funcions.startAppUsageWorker24h(getApplicationContext());
                        Funcions.sendAppUsage(getApplicationContext());

                        long now = System.currentTimeMillis();
                        long minute = 1000*60;
                        if(updateGeoloc == -1 || now - updateGeoloc > minute) {
                            Funcions.runGeoLocWorkerOnce(getApplicationContext());
                            updateGeoloc = now;
                        }
                    }

                    Log.d(TAG, "Token liveApp: " + s);
                    break;
                case "getIcon":
                    messageMap.remove("getIcon");
                    List<String> list = new ArrayList<>(messageMap.keySet());
                    sendIcon(list);
                    break;
                case "horaris":
                    Funcions.checkHoraris(getApplicationContext());
                    title = getString(R.string.horaris_notification);
                    break;
                case "events":
                    Funcions.checkEvents(getApplicationContext());
                    break;
                // ************* Accions del dispositiu tutor *************
                case "currentAppUpdate":
                    String aux = messageMap.get("currentAppUpdate");

                    Intent intentCurrApp = new Intent("liveApp");
                    intentCurrApp.putExtra("appName", messageMap.get("appName"));
                    intentCurrApp.putExtra("pkgName", aux);
                    intentCurrApp.putExtra("time", messageMap.get("time"));
                    intentCurrApp.putExtra("idChild", messageMap.get("idChild"));
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intentCurrApp);

                    Log.d(TAG, "Current AppUpdate: " + aux + " |Time: " + messageMap.get("time"));
                    break;
                case "installedApp":
                    String appNameInsApp = messageMap.get("installedApp");
                    String childNameInsApp = messageMap.get("childName");
                    title = getString(R.string.title_installed_app, childNameInsApp);
                    body = appNameInsApp;
                    activitatClass = BlockAppsActivity.class;
                    break;
                case "uninstalledApp":
                    String appNameUninsApp = messageMap.get("uninstalledApp");
                    String childNameUninsApp = messageMap.get("childName");
                    title = getString(R.string.title_uninstalled_app, childNameUninsApp);
                    body = appNameUninsApp;
                    activitatClass = BlockAppsActivity.class;
                    break;
                case "geolocFills":
                    Intent intentGeoFill = new Intent("actualitzarLoc");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intentGeoFill);

                    Log.d(TAG,"Actualitzar fills");
                    break;
                case "chat":
                    switch (Objects.requireNonNull(remoteMessage.getData().get("chat"))) {
                        case "0":
                            channel = MyNotificationManager.Channels.CHAT;
                            break;
                        case "1":  //Message with Chat
                            body = remoteMessage.getData().get("body");
                            Long myId = Long.parseLong(Objects.requireNonNull(remoteMessage.getData().get("myID")));
                            Long userID = Long.parseLong(Objects.requireNonNull(remoteMessage.getData().get("userID")));
                            if (ChatFragment.adminUserId.equals(userID)) {
                                Intent intent = new Intent("NewMessage");
                                intent.putExtra("message", body);
                                intent.putExtra("senderId", userID);
                                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                            } else {
                                clientNotificationManager.displayNotificationChat(title, body, userID, myId);
                            }
                            break;
                        case "2":
                            Long userId = Long.parseLong(Objects.requireNonNull(remoteMessage.getData().get("userID")));
                            if (ChatFragment.adminUserId.equals(userId)) {
                                Intent intent = new Intent("CloseChat");
                                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                            }
                            break;
                    }
                    break;
                case "callVideochat":
                    String type = messageMap.get("type");
                    if (type == null)
                        Log.e(TAG, "Error en el missatge de firebase de callVideochat: No hi ha type");
                    else {
                        if (type.equals("invitation")) {
                            String meetingId = messageMap.get("chatId");
                            Intent intent = new Intent(getApplicationContext(), IncomingInvitationActivity.class);
                            intent.putExtra(
                                    "admin_name",
                                    messageMap.get("admin_name")
                            );
                            intent.putExtra(
                                    "admin_id",
                                    messageMap.get("admin_id")
                            );
                            intent.putExtra(
                                    com.adictic.jitsi.utilities.Constants.REMOTE_MSG_MEETING_ROOM,
                                    meetingId
                            );
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            clientNotificationManager.displayGeneralNotification(getString(R.string.callNotifTitle), getString(R.string.callNotifDesc, messageMap.get("admin_name")), intent, MyNotificationManager.Channels.VIDEOCHAT);
                            startActivity(intent);
                        } else if (type.equals("invitationResponse")) {
                            Intent intent = new Intent("invitationResponse");
                            intent.putExtra(
                                    "invitationResponse",
                                    messageMap.get("invitationResponse")
                            );
                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                        }
                    }
                    break;
                default:
                    Log.e(TAG,"Clau 'action' no reconeguda: "+action);
                    break;
            }
        }

        // Check if message contains a notification payload.
        if (!title.equals("")) {
            Log.d(TAG, "Message Notification Body: " + body);

            if(activitatClass == null) clientNotificationManager.displayGeneralNotification(title, body, activitatIntent, channel);
            else clientNotificationManager.displayGeneralNotification(title, body, activitatClass, channel);
        }
    }

    private void sendBlockDeviceTime(SharedPreferences sharedPreferences) {
        long idChild = sharedPreferences.getLong(Constants.SHARED_PREFS_IDUSER, -1);

        TimeBlock timeBlock = new TimeBlock();
        timeBlock.start = sharedPreferences.getLong(Constants.SHARED_PREFS_BLOCKEDDEVICE_START, -1);
        if(timeBlock.start == -1 || idChild == -1)
            return;
        timeBlock.end = DateTime.now().getMillis();
        retryCount = 0;
        Call<String> call = mTodoService.postTempsBloqueig(sharedPreferences.getLong(Constants.SHARED_PREFS_IDUSER, -1), timeBlock);
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    super.onResponse(call, response);
                if(!response.isSuccessful() && retryCount++ < TOTAL_RETRIES)
                    call.clone().enqueue(this);
                else {
                    sharedPreferences.edit().putLong(Constants.SHARED_PREFS_BLOCKEDDEVICE_START, -1).apply();
                    retryCount = 0;
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    super.onFailure(call, t);
                if(retryCount++ < TOTAL_RETRIES)
                    call.clone().enqueue(this);
                else {
                    sharedPreferences.edit().putLong(Constants.SHARED_PREFS_BLOCKEDDEVICE_START, -1).apply();
                    retryCount = 0;
                }
            }
        });
    }

    private void sendFreeUseTime(SharedPreferences sharedPreferences) {
        long idChild = sharedPreferences.getLong(Constants.SHARED_PREFS_IDUSER, -1);

        TimeFreeUse timeFreeUse = new TimeFreeUse();
        timeFreeUse.start = sharedPreferences.getLong(Constants.SHARED_PREFS_FREEUSE_START, -1);
        if(timeFreeUse.start == -1 || idChild == -1)
            return;
        timeFreeUse.end = DateTime.now().getMillis();
        retryCount = 0;
        Call<String> call = mTodoService.postTempsFreeUse(idChild, timeFreeUse);
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    super.onResponse(call, response);
                if(!response.isSuccessful() && retryCount++ < TOTAL_RETRIES)
                    call.clone().enqueue(this);
                else {
                    retryCount = 0;
                    sharedPreferences.edit().putLong(Constants.SHARED_PREFS_FREEUSE_START, -1).apply();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    super.onFailure(call, t);
                if(retryCount++ < TOTAL_RETRIES)
                    call.clone().enqueue(this);
                else {
                    retryCount = 0;
                    sharedPreferences.edit().putLong(Constants.SHARED_PREFS_FREEUSE_START, -1).apply();
                }
            }
        });
    }

    @NonNull
    private Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    private void sendIcon(List<String> list) {
        for (String s : list) {
            try {
                PackageManager mPm = getApplicationContext().getPackageManager();
                Drawable icon = mPm.getApplicationIcon(s);

                Bitmap bitmap = getBitmapFromDrawable(icon);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS,100,stream);
                else
                    bitmap.compress(Bitmap.CompressFormat.WEBP,100,stream);

                byte[] byteArray = stream.toByteArray();

                RequestBody requestFile =
                        RequestBody.create(
                                byteArray,
                                MediaType.parse("image/webp")
                        );

                // MultipartBody.Part is used to send also the actual file name
                MultipartBody.Part body =
                        MultipartBody.Part.createFormData("file", s, requestFile);

                Call<String> call = mTodoService.postIcon(s, body);

                call.enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    super.onResponse(call, response);
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    super.onFailure(call, t);
                    }
                });
            } catch (PackageManager.NameNotFoundException e) {
//                e.printStackTrace();
            }
        }
    }
}
