package com.asforce.asforcetkf2.util;

/**
 * Uygulama genelinde paylaşılan verileri tutan yardımcı sınıf
 */
public class DataHolder {
    public static String url = "";
    public static String measuredLocation0 = "";
    public static String topraklama = ""; // Topraklama ekranı için URL
    
    // Form değerleri için alanlar
    public static String continuity = "0.09";
    public static String extremeIncomeProtection = "---";
    public static String voltage = "230.1";
    public static String findings = "---";
    public static String cycleImpedance = "EK-TP";
    
    // Topraklama sorunu var/yok durumu
    public static boolean hasTopraklamaSorunu = false;
    
    // Singleton örneği önlemek için özel constructor
    private DataHolder() {}
}