package com.adictic.admin.util.hilt;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@EntryPoint
@InstallIn(SingletonComponent.class)
public interface AdminEntryPoint {
    AdminRepository getRepository();
}
