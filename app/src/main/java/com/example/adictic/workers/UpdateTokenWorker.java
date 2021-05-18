package com.example.adictic.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.adictic.rest.TodoApi;
import com.example.adictic.util.Funcions;
import com.example.adictic.util.TodoApp;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdateTokenWorker extends Worker {
    private final static String TAG = "UpdateTokenWorker";
    private boolean success;
    public UpdateTokenWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Worker start");

        long idUser = getInputData().getLong("idUser", -1);
        String token = getInputData().getString("token");
        TodoApi mTodoService = ((TodoApp) getApplicationContext()).getAPI();

        Call<String> call = mTodoService.updateToken(idUser,token);
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                success = response.isSuccessful();
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                success = false;
            }
        });

        if(!success) {
            Log.d(TAG, "No s'ha pogut penjar Token al servidor.");
            long delay = 1000 * 60 * 5; // Tornar a provar en 5min
            Funcions.runUpdateTokenWorker(getApplicationContext(), idUser, token, delay);
        }

        Log.d(TAG, "Acabar 'doWork'");

        return Result.success();
    }
}