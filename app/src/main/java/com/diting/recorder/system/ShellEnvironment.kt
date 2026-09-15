package com.diting.recorder.system

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Looper
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

@SuppressLint("PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
object ShellEnvironment {

    private const val SHELL_PACKAGE = "com.android.shell"
    private const val SHELL_UID = 2000

    private val ACTIVITY_THREAD_CLASS: Class<*>
    private val ACTIVITY_THREAD: Any

    @SuppressLint("StaticFieldLeak")
    private var systemContext: Context? = null

    @SuppressLint("StaticFieldLeak")
    private var shellContext: Context? = null

    init {
        try {
            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread")
            val activityThreadConstructor: Constructor<*> = ACTIVITY_THREAD_CLASS.getDeclaredConstructor()
            activityThreadConstructor.isAccessible = true
            ACTIVITY_THREAD = activityThreadConstructor.newInstance()

            val currentActivityThreadField: Field = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread")
            currentActivityThreadField.isAccessible = true
            currentActivityThreadField.set(null, ACTIVITY_THREAD)

            val systemThreadField: Field = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread")
            systemThreadField.isAccessible = true
            systemThreadField.setBoolean(ACTIVITY_THREAD, true)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    @JvmStatic
    fun bypassHiddenApi() {
        try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val runtime = vmRuntimeClass.getDeclaredMethod("getRuntime").invoke(null)
            vmRuntimeClass.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                .invoke(runtime, arrayOf("L"))
        } catch (e: Exception) {
            System.err.println("[!] Hidden API bypass failed: " + e.message)
        }
    }

    @JvmStatic
    fun applyWorkarounds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fillConfigurationController()
        }
        fillBoundApplication()
        fillInitialApplication()
    }

    @JvmStatic
    fun getSystemContext(): Context {
        try {
            if (systemContext == null) {
                if (Looper.getMainLooper() == null) {
                    Looper.prepareMainLooper()
                }
                val getSystemContextMethod: Method = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext").also { it.isAccessible = true }
                systemContext = getSystemContextMethod.invoke(ACTIVITY_THREAD) as Context
            }
            return systemContext!!
        } catch (e: Exception) {
            throw RuntimeException("Failed to acquire system context", e)
        }
    }

    @JvmStatic
    fun getShellContext(): Context {
        try {
            if (shellContext == null) {
                val baseContext = getSystemContext()
                val shellPackageContext = try {
                    baseContext.createPackageContext(SHELL_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
                } catch (e: Exception) {
                    System.err.println("[!] Falling back to system context for shell package: " + e.message)
                    baseContext
                }
                shellContext = ShellContext(shellPackageContext)
            }
            return shellContext!!
        } catch (e: Exception) {
            throw RuntimeException("Failed to acquire shell context", e)
        }
    }

    @JvmStatic
    fun getDisplayManagerGlobal(): Any {
        try {
            val dmClass = Class.forName("android.hardware.display.DisplayManagerGlobal")
            return dmClass.getMethod("getInstance").invoke(null)!!
        } catch (e: Exception) {
            throw RuntimeException("Failed to acquire DisplayManagerGlobal", e)
        }
    }

    private fun fillBoundApplication() {
        try {
            val appBindDataClass = Class.forName("android.app.ActivityThread\$AppBindData")
            val appBindDataConstructor: Constructor<*> = appBindDataClass.getDeclaredConstructor()
            appBindDataConstructor.isAccessible = true
            val appBindData = appBindDataConstructor.newInstance()

            val applicationInfo = ApplicationInfo()
            applicationInfo.packageName = SHELL_PACKAGE
            applicationInfo.uid = SHELL_UID
            setDeclaredField(appBindDataClass, appBindData, "appInfo", applicationInfo)
            setDeclaredField(ACTIVITY_THREAD_CLASS, ACTIVITY_THREAD, "mBoundApplication", appBindData)
        } catch (t: Throwable) {
            System.err.println("[!] Could not fill app info: " + t.message)
        }
    }

    private fun fillInitialApplication() {
        try {
            val application = Instrumentation.newApplication(Application::class.java, getShellContext())
            setDeclaredField(ACTIVITY_THREAD_CLASS, ACTIVITY_THREAD, "mInitialApplication", application)
        } catch (t: Throwable) {
            System.err.println("[!] Could not fill app context: " + t.message)
        }
    }

    private fun fillConfigurationController() {
        try {
            val configurationControllerClass = Class.forName("android.app.ConfigurationController")
            val activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal")
            val constructor = configurationControllerClass.getDeclaredConstructor(activityThreadInternalClass)
            constructor.isAccessible = true
            val configurationController = constructor.newInstance(ACTIVITY_THREAD)
            setDeclaredField(ACTIVITY_THREAD_CLASS, ACTIVITY_THREAD, "mConfigurationController", configurationController)
        } catch (t: Throwable) {
            System.err.println("[!] Could not fill configuration controller: " + t.message)
        }
    }

    private fun setDeclaredField(owner: Class<*>, target: Any?, fieldName: String, value: Any?) {
        val field: Field = owner.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private class ShellContext(base: Context) : ContextWrapper(base) {

        private val shellApplicationInfo: ApplicationInfo?

        init {
            var appInfo: ApplicationInfo? = null
            try {
                appInfo = base.applicationInfo
                if (appInfo != null) {
                    appInfo.packageName = SHELL_PACKAGE
                    appInfo.uid = SHELL_UID
                }
            } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            shellApplicationInfo = appInfo
        }

        override fun getPackageName(): String = SHELL_PACKAGE

        override fun getOpPackageName(): String = SHELL_PACKAGE

        override fun getApplicationInfo(): ApplicationInfo =
            shellApplicationInfo ?: super.getApplicationInfo()

        override fun getApplicationContext(): Context = this

        @SuppressLint("NewApi")
        override fun getAttributionSource(): AttributionSource {
            return AttributionSource.Builder(SHELL_UID).setPackageName(SHELL_PACKAGE).build()
        }

        override fun getSystemService(name: String): Any? {
            val service = super.getSystemService(name)
            if (service != null) {
                patchServiceContext(service)
            }
            return service
        }

        private fun patchServiceContext(service: Any) {
            var current: Class<*>? = service.javaClass
            while (current != null) {
                try {
                    val contextField: Field = current.getDeclaredField("mContext")
                    contextField.isAccessible = true
                    contextField.set(service, this)
                    return
                } catch (e: NoSuchFieldException) {
                    current = current.superclass
                } catch (e: Exception) {
                    return
                }
            }
        }
    }
}
