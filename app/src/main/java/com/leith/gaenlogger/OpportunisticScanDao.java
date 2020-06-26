package com.leith.gaenlogger;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OpportunisticScanDao {
    @Query("SELECT * FROM opportunisticScan_table")
    List<OpportunisticScanEntity> getAll();

    @Query("SELECT * FROM opportunisticScan_table WHERE timestamp > :timestamp")
    List<OpportunisticScanEntity> getSince(long timestamp);

    @Query("SELECT * FROM opportunisticScan_table WHERE rpi = :rpi")
    List<OpportunisticScanEntity> getRPI(String rpi);

    //@Insert(onConflict = OnConflictStrategy.REPLACE)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(OpportunisticScanEntity entity);

    @Query("DELETE FROM OpportunisticScan_table")
    void clearDb();
}
