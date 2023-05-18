package xfk233.GenshinProxy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.XModuleResources
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findConstructor
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.regex.Pattern


class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val regex = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|mob)\\.com")
    private lateinit var server: String
    private var forceUrl = false
    private lateinit var modulePath: String
    private lateinit var moduleRes: XModuleResources

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.miHoYo.Yuanshen") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        findMethod(Application::class.java, true) { name == "attachBaseContext" }.hookBefore {
            val context = it.args[0] as Context
            val sp = context.getSharedPreferences("serverConfig", 0)
            forceUrl = sp.getBoolean("forceUrl", false)
            server = sp.getString("serverip", "") ?: ""
        }
        sslHook(lpparam)
        hook()
        findMethod("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }.hookBefore { param ->
            val context = param.thisObject as Activity
            val sp = context.getSharedPreferences("serverConfig", 0)
            AlertDialog.Builder(context).apply {
                setCancelable(false)
                setTitle("欢迎来到天理尝蛆")
                setMessage("本服务器完全免费,如果您是购买的到的服务器那么就代表您被骗了!请立即申请退款并举报投诉商家,服务器官网http://casks.me 祝您游戏愉快")
                setNegativeButton("我已认真阅读并知晓") { _, _ ->
                    server = "https://rel.tianliserver.com"
                    hook()
                    Toast.makeText(context, "欢迎来到天理尝蛆,祝您游戏愉快！", Toast.LENGTH_LONG).show()
                }
            }.show()
        }
    }

    private fun sslHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        findMethodOrNull("com.combosdk.lib.third.okhttp3.internal.tls.OkHostnameVerifier") { name == "verify" }?.hookBefore {
            it.result = true
        }
        findMethodOrNull("com.combosdk.lib.third.okhttp3.CertificatePinner") { name == "check" && parameterTypes[0] == String::class.java && parameterTypes[1] == List::class.java }?.hookBefore {
            it.result = null
        }
        JustTrustMe().hook(lpparam)
    }

    private fun hook() {
        findMethod("com.miHoYo.sdk.webview.MiHoYoWebview") { name == "load" && parameterTypes[0] == String::class.java && parameterTypes[1] == String::class.java }.hookBefore {
            replaceUrl(it, 1)
        }

        findMethod("okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }

        findMethod("com.google.gson.Gson") { name == "fromJson" && parameterTypes[0] == String::class.java && parameterTypes[1] == java.lang.reflect.Type::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findConstructor("java.net.URL") { parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
    }

    private fun replaceUrl(method: XC_MethodHook.MethodHookParam, args: Int) {
        if (!forceUrl) return
        if (!this::server.isInitialized) return
        if (server == "") return

        if (BuildConfig.DEBUG) XposedBridge.log("old: " + method.args[args].toString())
        val m = regex.matcher(method.args[args].toString())
        if (m.find()) {
            method.args[args] = m.replaceAll(server)
        }
        if (BuildConfig.DEBUG) XposedBridge.log("new: " + method.args[args].toString())
    }
}