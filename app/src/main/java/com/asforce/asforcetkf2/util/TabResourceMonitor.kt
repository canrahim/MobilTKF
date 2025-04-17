package com.asforce.asforcetkf2.util

import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Sekmelerin CPU ve bellek kullanımını izleyen sınıf
 * Geliştirilmiş versiyon - daha verimli izleme ve hızlı uyarı sistemi
 */
class TabResourceMonitor {
    
    /**
     * Data class representing resource metrics
     */
    data class ResourceMetrics(
        val tabId: String,
        val cpuUsage: Float,
        val memoryUsage: Long
    )
    
    /**
     * Start monitoring a tab's resource usage
     */
    fun monitorTabResources(
        tabId: String, 
        pid: Int,
        intervalMs: Long = 5000
    ): Flow<ResourceMetrics> = flow {
        while (true) {
            try {
                val metrics = measureResources(tabId, pid)
                emit(metrics)
                delay(intervalMs)
            } catch (e: Exception) {
                Timber.e(e, "Error monitoring resources for tab $tabId")
                delay(intervalMs * 2) // Back off if there's an error
            }
        }
    }
    
    /**
     * Measure CPU and memory usage for a process
     */
    private suspend fun measureResources(
        tabId: String,
        pid: Int
    ): ResourceMetrics = withContext(Dispatchers.IO) {
        // Get current CPU usage
        val cpuUsage = try {
            measureCpuUsage(pid)
        } catch (e: Exception) {
            Timber.e(e, "Error measuring CPU usage")
            0f
        }
        
        // Get current memory usage
        val memoryUsage = try {
            measureMemoryUsage(pid)
        } catch (e: Exception) {
            Timber.e(e, "Error measuring memory usage")
            0L
        }
        
        ResourceMetrics(tabId, cpuUsage, memoryUsage)
    }
    
    /**
     * Measure CPU usage for a process
     */
    @Throws(IOException::class)
    private fun measureCpuUsage(pid: Int): Float {
        val myPid = Process.myPid()
        
        // For WebViews, we use the main process's PID
        val processId = if (pid <= 0) myPid else pid
        
        val statPath = "/proc/$processId/stat"
        val reader = BufferedReader(FileReader(statPath))
        val stat = reader.readLine()
        reader.close()
        
        val parts = stat.split(" ")
        
        // CPU time components from /proc/[pid]/stat
        val utime = parts[13].toLong() // User time
        val stime = parts[14].toLong() // System time
        
        // Calculate total CPU time (user + system)
        val cpuTime = utime + stime
        
        // This is a simplified calculation - in a real implementation,
        // you would track changes over time
        return (cpuTime / 100f)
    }
    
    /**
     * Measure memory usage for a process
     */
    @Throws(IOException::class)
    private fun measureMemoryUsage(pid: Int): Long {
        val myPid = Process.myPid()
        
        // For WebViews, we use the main process's PID
        val processId = if (pid <= 0) myPid else pid
        
        val statmPath = "/proc/$processId/statm"
        val reader = BufferedReader(FileReader(statmPath))
        val statm = reader.readLine()
        reader.close()
        
        val parts = statm.split(" ")
        
        // Memory components from /proc/[pid]/statm
        val resident = parts[1].toLong() // Resident set size
        
        // Convert to bytes (multiply by page size, typically 4KB)
        return resident * 4 * 1024
    }
}
