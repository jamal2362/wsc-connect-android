package wscconnect.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.text.Html;
import android.text.Spanned;
import android.util.Base64;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.auth0.android.jwt.JWT;

import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import wscconnect.android.activities.AppActivity;
import wscconnect.android.callbacks.RetroCallback;
import wscconnect.android.callbacks.SimpleCallback;
import wscconnect.android.fragments.myApps.appOptions.AppWebviewFragment;
import wscconnect.android.models.AccessTokenModel;

import static android.content.Context.VIBRATOR_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static wscconnect.android.activities.MainActivity.EXTRA_OPTION_TYPE;

/**
 * @author Christopher Walz
 * @copyright 2017-2018 Christopher Walz
 * @license GNU General Public License v3.0 <https://opensource.org/licenses/LGPL-3.0>
 */

public class Utils {
    public final static String SHARED_PREF_KEY = "wsc-connect";
    private static boolean accessTokenRefreshing;
    private static SimpleCallback onRefreshAccessTokenFinishCallback;

    public static boolean hasInternetConnection(Context context) {
        if (context == null) return false;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }

    public static void hideKeyboard(Activity activity) {
        try {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            if (((activity.getCurrentFocus() != null) && (activity.getCurrentFocus().getWindowToken() != null))) {
                ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void hideProgressView(View view, ProgressBar bar) {
        hideProgressView(view, bar, true);
    }

    public static void hideProgressView(View view, ProgressBar bar, boolean makeViewVisible) {
        if (bar != null) {
            ViewGroup vg = (ViewGroup) bar.getParent();
            if (vg != null) {
                vg.removeView(bar);
            }
        }

        if (makeViewVisible) {
            view.setVisibility(View.VISIBLE);
        }
    }

    public static ProgressBar showProgressView(Context context, View view, int style) {
        ProgressBar progressBar = new ProgressBar(context, null, style);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setLayoutParams(view.getLayoutParams());

        view.setVisibility(View.GONE);
        ViewGroup vg = (ViewGroup) view.getParent();
        int index = vg.indexOfChild(view);
        vg.addView(progressBar, index);

        return progressBar;
    }


    public static void logout(Context context, String appID) {
        Utils.saveUnreadNotifications(context, appID, 0);
        Utils.saveUnreadConversations(context, appID, 0);
        Utils.removeAccessTokenString(context, appID);
    }

    public static void saveAccessToken(Context context, String appID, String accessToken) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString("accessToken-" + appID, accessToken).apply();
    }

    public static void saveRefreshToken(Context context, String appID, String accessToken) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString("refreshToken-" + appID, accessToken).apply();
    }

    public static void saveUnreadNotifications(Context context, String appID, int count) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putInt("unreadNotifications-" + appID, count).apply();
    }

    public static void saveWscConnectToken(Context context, String appID, String wscConnectToken) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString("wscConnectToken-" + appID, wscConnectToken).apply();
    }

    public static void saveUsername(Context context, String appID, String username) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString("username-" + appID, username).apply();
    }

    public static int getUnreadNotifications(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getInt("unreadNotifications-" + appID, 0);
    }

    public static void saveUnreadConversations(Context context, String appID, int count) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putInt("unreadConversations-" + appID, count).apply();
    }

    public static int getUnreadConversations(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getInt("unreadConversations-" + appID, 0);
    }

    public static String getWscConnectToken(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getString("wscConnectToken-" + appID, "");
    }

    public static String getUsername(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getString("username-" + appID, "");
    }

    public static String getAccessTokenString(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getString("accessToken-" + appID, null);
    }

    public static void removeAccessTokenString(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().remove("accessToken-" + appID).apply();
    }

    public static String getRefreshTokenString(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getString("refreshToken-" + appID, null);
    }

    public static void saveInstallPluginVersion(String appID, String pluginVersion, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString("installPluginVersion-" + appID, pluginVersion).apply();
    }

    public static String getInstallPluginVersion(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getString("installPluginVersion-" + appID, "1.0.0");
    }

    public static void setNotificationChannel(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        prefs.edit().putString("notificationChannel", name).apply();
    }

    public static String getNotificationChannel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return prefs.getString("notificationChannel", null);
    }

    public static AccessTokenModel getAccessToken(Context context, String appID) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        String token = prefs.getString("accessToken-" + appID, null);

        if (token == null) {
            return null;
        }

        JWT jwt = new JWT(token);
        return AccessTokenModel.fromJWT(jwt);
    }

    public static ArrayList<AccessTokenModel> getAllAccessTokens(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);

        ArrayList<AccessTokenModel> tokens = new ArrayList<>();

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("accessToken-")) {
                JWT jwt = new JWT(entry.getValue().toString());
                tokens.add(AccessTokenModel.fromJWT(jwt));
            }
        }

        Collections.sort(tokens, (t1, t2) -> {
            String app1Name = t1.getAppName().replaceAll("[^a-zA-Z]", "").toLowerCase();
            String app2Name = t2.getAppName().replaceAll("[^a-zA-Z]", "").toLowerCase();

            return app1Name.compareToIgnoreCase(app2Name);
        });

        return tokens;
    }

    public static void removeAllAccessTokens(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("accessToken-")) {
                prefs.edit().remove(entry.getKey()).apply();
            }
        }
    }

    private static void callOnRefreshAccessTokenFinishCallback(boolean success) {
        if (onRefreshAccessTokenFinishCallback != null) {
            onRefreshAccessTokenFinishCallback.onReady(success);
        }
    }

    private static void setOnRefreshAccessTokenFinishCallback(SimpleCallback callback) {
        onRefreshAccessTokenFinishCallback = callback;
    }

    public static void refreshAccessToken(final Activity activity, final String appID, final SimpleCallback callback) {
        String refreshToken = Utils.getRefreshTokenString(activity, appID);

        if (accessTokenRefreshing) {
            setOnRefreshAccessTokenFinishCallback(success -> {
                setOnRefreshAccessTokenFinishCallback(null);
                callback.onReady(success);
            });
            return;
        }

        accessTokenRefreshing = true;

        Utils.getAPI(activity, refreshToken).getAccessToken().enqueue(new RetroCallback<ResponseBody>(activity) {
            @Override
            public void onResponse(@NotNull Call<ResponseBody> call, @NotNull Response<ResponseBody> response) {
                super.onResponse(call, response);

                accessTokenRefreshing = false;

                if (response.isSuccessful()) {
                    JSONObject obj;
                    try {
                        assert response.body() != null;
                        obj = new JSONObject(response.body().string());
                        String accessToken = obj.getString("accessToken");

                        saveAccessToken(activity, appID, accessToken);
                        callback.onReady(true);
                        callOnRefreshAccessTokenFinishCallback(true);
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                        callback.onReady(false);
                        callOnRefreshAccessTokenFinishCallback(false);
                    }
                } else {
                    callback.onReady(false);
                    callOnRefreshAccessTokenFinishCallback(false);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, @NotNull Throwable t) {
                super.onFailure(call, t);

                accessTokenRefreshing = false;
                t.printStackTrace();

                callback.onReady(false);
                callOnRefreshAccessTokenFinishCallback(false);
            }
        });
    }

    public static void showDataNotification(Context context, String tag, int id, String appID, String optionType, String title, String message, String eventName, int eventID, Bitmap largeIcon) {
        Utils.showDataNotification(context, tag, id, appID, optionType, title, message, eventName, eventID, largeIcon, false);
    }

    public static void showDataNotification(Context context, String tag, int id, String appID, String optionType, String title, String message, String eventName, int eventID, Bitmap largeIcon, boolean ignoreSound) {
        // get preferences
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String ringtone = prefs.getString("pref_notifications_ringtone", null);
        boolean vibrate = prefs.getBoolean("pref_notifications_vibration", false);
        Vibrator v = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        long[] pattern = new long[]{0, 300, 100, 300};

        // reset ringtone if ignoreSound is true
        if (ignoreSound) {
            ringtone = null;
        }

        String notificationChannelName = getNotificationChannel(context);
        if (notificationChannelName == null) {
            Random r = new Random();
            notificationChannelName = "default" + r.nextInt();
            setNotificationChannel(context, notificationChannelName);
        }

        NotificationManager mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, notificationChannelName);

        // specific stuff for higher android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelName,
                    context.getString(R.string.default_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH);

            // sound
            if (ringtone != null && !ringtone.isEmpty() && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build();
                    notificationChannel.setSound(Uri.parse(ringtone), audioAttributes);
                }
            }

            if (vibrate && audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            }

            // create channel
            mNotificationManager.createNotificationChannel(notificationChannel);
        } else {
            if (vibrate && audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                v.vibrate(pattern, -1);
            }

        }

        notificationBuilder.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        notificationBuilder.setSmallIcon(R.drawable.ic_notification);

        if (ringtone != null && !ringtone.isEmpty() && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                notificationBuilder.setSound(Uri.parse(ringtone), AudioManager.STREAM_NOTIFICATION);
            }
        }

        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon);
        }

        Intent intent = new Intent(context, AppActivity.class);
        intent.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(Long.toString(System.currentTimeMillis()));
        intent.putExtra(AccessTokenModel.EXTRA, Utils.getAccessToken(context, appID));
        intent.putExtra(AppActivity.EXTRA_EVENT_NAME, eventName);
        intent.putExtra(AppActivity.EXTRA_EVENT_ID, eventID);
        if (optionType != null) {
            intent.putExtra(EXTRA_OPTION_TYPE, optionType);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(contentIntent);

        notificationBuilder.setAutoCancel(true);
        notificationBuilder.setContentTitle(title);
        message = Utils.fromHtml(message).toString();
        notificationBuilder.setContentText(message);
        notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        try {
            mNotificationManager.notify(tag, id, notificationBuilder.build());
        } catch (SecurityException e) {

            if (!ignoreSound) {
                Utils.showDataNotification(context, tag, id, appID, optionType, title, message, eventName, eventID, largeIcon, true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(source);
        }
    }

    public static API getAPI(Context context) {
        return Utils.getAPI(context, API.ENDPOINT, null);
    }

    public static API getAPI(Context context, final String token) {
        return Utils.getAPI(context, API.ENDPOINT, token);
    }

    public static API getAPI(final Context context, final String url, final String token) {
        int timeout = 10;

        final OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.writeTimeout(timeout, TimeUnit.SECONDS);
        clientBuilder.connectTimeout(timeout, TimeUnit.SECONDS);
        clientBuilder.readTimeout(timeout, TimeUnit.SECONDS);

        Interceptor offlineResponseCacheInterceptor = chain -> {
            Request request = chain.request();
            if (!Utils.hasInternetConnection(context)) {
                request = request.newBuilder()
                        .header("Cache-Control",
                                "public, only-if-cached, max-stale=" + 2419200)
                        .build();
            }
            return chain.proceed(request);
        };

        Interceptor fixUrlInterceptor = chain -> {
            Request request = chain.request();
            String url1 = request.url().toString();
            url1 = url1.replace("%3F", "?");
            url1 = url1.replace("%2F", "/");

            request = request.newBuilder()
                    .url(url1)
                    .build();

            return chain.proceed(request);
        };

        clientBuilder.addInterceptor(offlineResponseCacheInterceptor);
        clientBuilder.addInterceptor(fixUrlInterceptor);
        clientBuilder.cache(new Cache(new File(context.getCacheDir(),
                "APICache"), 50 * 1024 * 1024));

        if (token != null) {
            clientBuilder.addInterceptor(chain -> {
                Request newRequest = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + token)
                        // necessary, because some Apache webservers discard the auth headers
                        .addHeader("X-Authorization", "Bearer " + token)
                        .build();
                return chain.proceed(newRequest);
            });
        }

        // set user agent
        clientBuilder.addInterceptor(chain -> {
            Request newRequest = chain.request().newBuilder()
                    .addHeader("User-Agent", AppWebviewFragment.USER_AGENT)
                    .build();
            return chain.proceed(newRequest);
        });

        // add current app version
        int versionCode;
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // an error is likely not good. Set versionCode to 1
            versionCode = 1;
        }
        final int finalVersionCode = versionCode;
        clientBuilder.addInterceptor(chain -> {
            Request newRequest = chain.request().newBuilder()
                    .addHeader("X-App-Version-Code", String.valueOf(finalVersionCode))
                    .build();
            return chain.proceed(newRequest);
        });

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        // add your other interceptors …
        // add logging as last interceptor
        clientBuilder.addInterceptor(logging);  // <-- this is the important line!

        Retrofit retrofit = new Retrofit.Builder()
                .client(clientBuilder.build())
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();
        return retrofit.create(API.class);
    }

    public static String getApiUrlExtension(String appApiUrl) {
        if (appApiUrl.contains("index.php/WSCConnectAPI/")) {
            return "index.php/WSCConnectAPI/";
        } else if (appApiUrl.contains("index.php?wsc-connect-api/")) {
            return "index.php?wsc-connect-api/";
        } else if (appApiUrl.contains("wsc-connect-api/")) {
            return "wsc-connect-api/";
        } else if (appApiUrl.contains("index.php?wsc-connect-api")) {
            return "index.php?wsc-connect-api";
        } else if (appApiUrl.contains("wsc-connect-api")) {
            return "wsc-connect-api";
        }

        return "";
    }

    public static String prepareApiUrl(String appApiUrl) {
        appApiUrl = appApiUrl.replace("index.php/WSCConnectAPI/", "");
        appApiUrl = appApiUrl.replace("index.php?wsc-connect-api/", "");
        appApiUrl = appApiUrl.replace("wsc-connect-api/", "");
        appApiUrl = appApiUrl.replace("index.php?wsc-connect-api", "");
        appApiUrl = appApiUrl.replace("wsc-connect-api", "");

        if (!appApiUrl.endsWith("/")) {
            appApiUrl = appApiUrl + "/";
        }

        return appApiUrl;
    }

    public static void setError(Context context, TextView view) {
        setError(view, context.getString(R.string.required));
    }

    public static void setError(TextView view, String error) {
        view.requestFocus();
        view.setError(error);
        ViewParent parent = view.getParent();
        if (parent != null) {
            parent.requestChildFocus(view, view);
        }
    }

    @SuppressLint("InflateParams")
    public static void showLoadingOverlay(Activity activity, boolean show) {
        if (activity == null) {
            return;
        }

        Window window = activity.getWindow();

        if (window == null) {
            return;
        }

        ViewGroup rootView = window.getDecorView().findViewById(android.R.id.content);
        LayoutInflater li = LayoutInflater.from(activity);
        View loadingView = li.inflate(R.layout.loading_overlay_view, null);

        View v = rootView.findViewById(R.id.loading_overlay_view);
        if (show) {
            if (v == null) {
                rootView.addView(loadingView);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        } else {
            if (v != null) {
                rootView.findViewById(R.id.loading_overlay_view).setVisibility(View.GONE);
            }
        }
    }

    public static String decryptString(String encryptedText, String secret, String initVector) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");

            byte[] ivv = initVector.getBytes(StandardCharsets.UTF_8);
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOf(ivv, ivv.length), 0, cipher.getBlockSize());
            SecretKeySpec skeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "AES");

            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(Base64.decode(encryptedText, Base64.DEFAULT));

            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            //Crashlytics.logException(e);
        }

        return encryptedText;
    }
}