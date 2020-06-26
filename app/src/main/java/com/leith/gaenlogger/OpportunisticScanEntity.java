package com.leith.gaenlogger;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = "opportunisticScan_table", primaryKeys = "rpi")
public class OpportunisticScanEntity {

    @ColumnInfo(name = "rpi")
    @NonNull
    String rpi;

    @ColumnInfo(name = "fname")
    String fname;

    @ColumnInfo(name = "timestamp")
    long timestamp;
}
