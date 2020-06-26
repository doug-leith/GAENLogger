package com.leith.gaenlogger;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {OpportunisticScanEntity.class}, version = 2)
public abstract class OpportunisticScanRepository extends RoomDatabase {
    private static volatile OpportunisticScanRepository INSTANCE; // singleton
    static synchronized OpportunisticScanRepository getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context, OpportunisticScanRepository.class, "opportunisticScandb").build();
        }
        return INSTANCE;
    }
    public abstract OpportunisticScanDao opportunisticScanDao();
}