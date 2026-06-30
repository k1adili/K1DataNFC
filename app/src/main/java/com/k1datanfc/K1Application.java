package com.k1datanfc;

import androidx.multidex.MultiDexApplication;

public class K1Application extends MultiDexApplication {

    private static K1Application instance;
    private EncryptionManager encryptionManager;
    private DatabaseManager databaseManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        encryptionManager = new EncryptionManager(this);
        databaseManager = new DatabaseManager(this, encryptionManager);
    }

    public static K1Application getInstance() {
        return instance;
    }

    public EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
