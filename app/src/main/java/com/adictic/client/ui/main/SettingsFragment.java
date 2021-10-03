package com.adictic.client.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.adictic.client.BuildConfig;
import com.adictic.client.R;
import com.adictic.client.rest.AdicticApi;
import com.adictic.client.ui.inici.Login;
import com.adictic.client.ui.inici.Permisos;
import com.adictic.client.ui.inici.SplashScreen;
import com.adictic.client.util.AdicticApp;
import com.adictic.client.util.Funcions;
import com.adictic.common.entity.GeneralUsage;
import com.adictic.common.util.Callback;
import com.adictic.common.util.Constants;
import com.adictic.common.util.Crypt;
import com.google.firebase.messaging.FirebaseMessaging;

import org.joda.time.DateTime;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

public class SettingsFragment extends PreferenceFragmentCompat {

    private AdicticApi mTodoService;

    private SharedPreferences sharedPreferences;

    private ApplicationInfo appInfo;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mTodoService = ((AdicticApp) requireActivity().getApplication()).getAPI();

        appInfo = requireContext().getApplicationContext().getApplicationInfo();
        sharedPreferences = Funcions.getEncryptedSharedPreferences(getActivity());
        assert sharedPreferences != null;
        if (!sharedPreferences.getBoolean(Constants.SHARED_PREFS_ISTUTOR, false)) {
            // Settings de fill
            setPreferencesFromResource(R.xml.settings_child, rootKey);
            settings_permission();
            if(BuildConfig.DEBUG) {
                settings_change_theme();
                settings_tancar_sessio();
                settings_pujar_informe();
            } else {
                PreferenceScreen preferenceScreen = findPreference("preferenceChild");
                PreferenceCategory myPrefCat = findPreference("pcdebug");
                if (preferenceScreen != null) preferenceScreen.removePreference(myPrefCat);
            }

        } else {
            // Settings de pare
            setPreferencesFromResource(R.xml.settings_parent, rootKey);
            settings_tancar_sessio();
            settings_change_password();
            settings_android();
            if(BuildConfig.DEBUG){
                settings_change_theme();
            } else {
                PreferenceScreen preferenceScreen = findPreference("preferenceParent");
                PreferenceCategory myPrefCat = findPreference("ppdebug");
                if(preferenceScreen != null) preferenceScreen.removePreference(myPrefCat);
            }
        }
        settings_change_notifications();
        settings_change_language();
    }

    private void settings_change_notifications() {
        Preference change_notif = findPreference("setting_notifications");

        assert change_notif != null;
        change_notif.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent();
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");

            //for Android 5-7
            intent.putExtra("app_package", appInfo.packageName);
            intent.putExtra("app_uid", appInfo.uid);

            // for Android 8 and above
            intent.putExtra("android.provider.extra.APP_PACKAGE", appInfo.packageName);

            startActivity(intent);
            return true;
        });

    }

    private void settings_android() {
        Preference setting_android = findPreference("setting_android");

        assert setting_android != null;
        setting_android.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", appInfo.packageName, null);
            intent.setData(uri);
            startActivity(intent);
            return true;
        });
    }

    private void settings_permission(){
        Preference change_perm = findPreference("setting_permission");

        assert change_perm != null;
        change_perm.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(requireActivity(), Permisos.class);
            startActivity(intent);
            return true;
        });
    }

    private void settings_pujar_informe(){
        Preference pujar_informe = findPreference("setting_pujar_informe");

        assert pujar_informe != null;
        pujar_informe.setOnPreferenceClickListener(preference -> {
            Funcions.sendAppUsage(requireContext());
            return true;
        });

    }

    private void settings_change_language() {
        ListPreference language_preference = findPreference("setting_change_language");
        String selectedLanguage = sharedPreferences.getString("language", Locale.getDefault().getLanguage());

        assert language_preference != null;
        language_preference.setValue(selectedLanguage);
        if(language_preference.getEntry() == null || language_preference.getEntry().length()==0) language_preference.setSummary(getString(R.string.language_not_supported));
        else language_preference.setSummary(language_preference.getEntry());

        language_preference.setOnPreferenceChangeListener((preference, newValue) -> {
            sharedPreferences.edit().putString("language", (String) newValue).apply();

            Intent refresh = new Intent(getActivity(), SplashScreen.class);
            requireActivity().finish();
            startActivity(refresh);

            return true;
        });
    }

    private void settings_tancar_sessio() {
        Preference tancarSessio = findPreference("setting_tancar_sessio");

        assert tancarSessio != null;
        tancarSessio.setOnPreferenceClickListener(preference -> {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {

                        Call<String> call;
                        if (!task.isSuccessful()) {
                            call = mTodoService.logout("");
                        } else {
                            // Get new Instance ID token
                            String token = task.getResult();
                            call = mTodoService.logout(Crypt.getAES(token));
                        }
                        call.enqueue(new Callback<String>() {
                            @Override
                            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                    super.onResponse(call, response);
                                if (response.isSuccessful()) {
                                    sharedPreferences.edit().putString(Constants.SHARED_PREFS_USERNAME,null).apply();
                                    sharedPreferences.edit().putString(Constants.SHARED_PREFS_PASSWORD,null).apply();
                                    requireActivity().startActivity(new Intent(getActivity(), Login.class));
                                    requireActivity().finish();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    super.onFailure(call, t);
                            }
                        });

                    });
            return true;
        });
    }

    private void settings_change_theme() {
        ListPreference theme_preference = findPreference("setting_change_theme");
        String selectedTheme = sharedPreferences.getString("theme", "follow_system");

        assert theme_preference != null;
        theme_preference.setValue(selectedTheme);
        theme_preference.setSummary(theme_preference.getEntry());

        theme_preference.setOnPreferenceChangeListener((preference, newValue) -> {
            sharedPreferences.edit().putString("theme", (String) newValue).apply();
            switch((String) newValue){
                case "no":
                    theme_preference.setSummary(getString(R.string.theme_light));
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case "yes":
                    theme_preference.setSummary(getString(R.string.theme_dark));
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                default:
                    theme_preference.setSummary(getString(R.string.theme_default));
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    break;
            }
            return true;
        });
    }

    private void settings_change_password(){
        Preference change_password = findPreference("setting_change_password");

        assert change_password != null;
        change_password.setOnPreferenceClickListener(preference -> {
            requireActivity().startActivity(new Intent(getActivity(), ChangePasswordActivity.class));
            return true;
        });
    }
}
