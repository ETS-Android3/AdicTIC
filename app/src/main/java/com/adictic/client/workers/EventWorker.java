package com.adictic.client.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.adictic.client.service.AccessibilityScreenService;
import com.adictic.client.util.Funcions;
import com.adictic.client.util.hilt.AdicticRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EventWorker extends Worker {

    @Inject
    AdicticRepository repository;

    public EventWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if(!repository.accessibilityServiceOn())
            return Result.failure();

        boolean start = getInputData().getBoolean("start", false);
        int events = start ? 1 : 0;

        AccessibilityScreenService.instance.setActiveEvents(events);
        AccessibilityScreenService.instance.updateDeviceBlock();

        return Result.success();
    }
}
