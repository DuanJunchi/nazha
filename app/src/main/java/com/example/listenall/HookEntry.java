package com.example.listenall;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "fm.qingting.qtradio";
    private static final Set<String> hooked = new HashSet<>();

    private static final String[] TARGETS = {
        "/apkOrder/apkActivate",
        "/user/getApkKeyV2"
    };

    private boolean isTarget(String s) {
        if (s == null) return false;
        for (String t : TARGETS) {
            if (s.contains(t)) return true;
        }
        return false;
    }

    private void dumpRetObj(String tag, Object obj) {
        if (obj == null) {
            XposedBridge.log(tag + " = null");
            return;
        }
        XposedBridge.log(tag + " = " + obj);
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
                XposedBridge.log("  " + f.getName() + " = " + f.get(obj));
            }
        } catch (Throwable e) {
            XposedBridge.log("  <dump failed: " + e + ">");
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        // 延迟到 Application.attachBaseContext 之后执行，模拟 Frida 的 Java.perform
        // 避免在加固库初始化前过早加载类导致崩溃
        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "attachBaseContext",
            Context.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    synchronized (hooked) {
                        if (hooked.contains(lpparam.packageName)) return;
                        hooked.add(lpparam.packageName);
                    }

                    XposedBridge.log("[+] ListenAllHook loaded for " + lpparam.packageName);
                    ClassLoader cl = lpparam.classLoader;

                    // 1. 初始化 projectArgs
                    try {
                        Class<?> projectClass = XposedHelpers.findClass("com.autoapp.autoapp.StaticClass.project", cl);
                        Object assetArgs = XposedHelpers.callStaticMethod(projectClass, "getAssetProjectArgs");
                        if (assetArgs != null) {
                            XposedHelpers.setStaticObjectField(projectClass, "projectArgs", assetArgs);
                            XposedBridge.log("[+] Project.projectArgs = getAssetProjectArgs()");
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[-] init projectArgs failed: " + t);
                    }

                    // 2. Hook Http.getHttp
                    try {
                        Class<?> httpClass = XposedHelpers.findClass("com.autoapp.autoapp.Classes.Http", cl);
                        XposedBridge.hookAllMethods(httpClass, "getHttp", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String url = "";
                                try {
                                    if (param.args.length > 0 && param.args[0] != null) {
                                        url = param.args[0].toString();
                                    }
                                } catch (Throwable ignored) {}

                                // Mock apkActivate
                                if (url.contains("/apkOrder/apkActivate")) {
                                    XposedBridge.log("\n========== [MOCK] 拦截 apkActivate 请求 ==========");
                                    XposedBridge.log("url = " + url);
                                    String mockResponse = "{\"code\":200,\"msg\":\"更新完成\",\"data\":null}";
                                    XposedBridge.log("========== [MOCK] 返回伪造成功数据 ==========");
                                    XposedBridge.log(mockResponse);
                                    param.setResult(mockResponse);
                                    return;
                                }

                                // Mock getApkKeyV2
                                if (url.contains("/user/getApkKeyV2")) {
                                    XposedBridge.log("\n========== [MOCK] 拦截 getApkKeyV2 请求 ==========");
                                    XposedBridge.log("url = " + url);
                                    String mockJsCode = "jFsIQLOVDPQzZMPv7MO/9CxGzX2cIk2Dl5viTpqBOGs7D7k+EMPhisqJkw2ogQs4t+jXdScB8qhmtIW5WJqK3SocJoktiwdAgcVzGhEwq0LZE5JtbxGE6FWRoFcEDBBmz/5BQ3GV+9FINP8IEXmw4uRB8G+yasP68rcqVlgLPDNgxyPLbkSrNc1od7pkAzDIScc8sfcuM2kv1rjbb/G7lUzN9dHmFeoaj6IM3PbMJb2l48GxLFu+oUvN5h2nhm8V38/ARh7pHsvJjBXiQTksiWWlV1C4FUZIZzTFdG955rev+oDPS7BR41AKGEE3DwccvzqLy09FuRJrK4rf6P8h/GzKWBiK/dUTL9OontNdCcy3+lQdGDnIYY4zM/uDhVxcin/hHLohFz3m3zu7elCUXGWcIsebcL1VrA/ubQHIVyBPXVgrhTaTdppt3RNpzYybf3PBS4xfmOJ7Gkbg/RfRlbidCCLaqpQwNEXPUa8/gxtsaJgqCS2tCEs//Y1W4zOqigF3a5nKzEL2pxChz7rN/BP8NdNf5OGtNr3IBj7Arx4YIZrDjlYwna53ai7CANlb21mDWmSlnNYtpXBuv49UAC3I5YH8oTH7gyGdYtfMFo4DPye/dPdnbtiVDc8w9iyQFuOXidT6vDvaTAfZmnc5M+xuWBJ/ClMLzy4n1azqu84Q6QNdQIUEQHvFUIZc64Z2OW08SmYiyC1eTk6csGNSa2/6A3RqmmEKYzXBu6NUtuJ82Yoyy86G3GRqvkeb/V191WMTJB0re7q7E9IKYr85mQm1g0mUwTSYWQtBAl+/57ghcmx7jhQsu16eI5lC/Lu5xcwgqTXCrQeRNVOoTi+vWUKg+5YmQ/fXLjFZRf31Hk9mQigEKaH6Bov/hHd3PyP4J1LRq/QJNIJNeZzNAGWEjXl8ZHmPuBsxamqE0tk4D+Px3S0B0NOVTBG3b2+uQeOXdxba+ye+me6Iq5pyhiGnQtl8DEDt6FwQDBv5nqnfZJGa4CYhQMX3frUrioRyhwSoWOveH3eN8WccrK1z00a5XvUJTFwRHowo9GumIlRzzKrIfQYT0p5gEmvEblbsGx75wbh6M8L4fxUJn4TQVMzm5m/fRJcfXM7Uw62i1m0QVBL7xRXXq6wYsv19xk8cW7FTVrLdoTo4tgWqJLwM5rKh/SEyWWVo7ruAn7qcIJTmmEMVTrYiSpGRSv7w4pqpZJPfeWyf9l8WN25JZ7KAVHwDDJfH1nZ4EGQyuMOx/UDs8AQ/QoR87bAefTTL51SuOkZ5L+47i4E5nReBd4kW1gyM8//SApgIdh0OWEtGyXibk2Gi/21VcwxclKt0imTkGy4ALSIm2Bo5tGAr3BcBmlY/Mh6UiMcBSt8tveq2TGHydUWPZsVKvK0m8mp5s2OcoBoQ+BZ/MtapwkJTMsVOPTWUfn+womY1MhH2c6TcNk0CKgpJU57cx9AvBdSCry0KN3v8umbsASSdU+2KhTiAbQuDfFq+UtJytkvNB52VlDm8b/HYd5TWSncqeM72hm8drK06HJwkLAdiCx/prMn1nfyad5vZJqwtQNbr0LNq2LO5sOLmxFPTHoMI5ykEV6LjlCLtBKnAsifK/gCkIX/3ul5eT/OgBx+fAzA9HhsDlwh/LmmtO/PJEBLi2BPk/ATWMbGHSumKPp7fsn1fILA1cKdR4bPEhyANsTCAEzCXW2vhov5doesI5yi42vfigiJPPydPUT632w1nzvlBgdDFFK/CXnIXRv2ifvkTKG0PUIJZcf4X0g6pBuBAFanjhaZIOaXrnn4LDiNLfCOunnZK5qA8x1Rt0LqOqAMvtzibI+of7CdtOcGPxaJG9x5U19gtZrsp4vfoXqHcjHUs59LQe0b/tg==";
                                    String mockResponse = "{\"code\":200,\"msg\":\"SUCCESS\",\"data\":{\"jsCode\":\"" + mockJsCode + "\",\"KeyID\":\"940b7b1bf7dedefa52ea95ff2929d757\",\"activateCard\":\"微信ddgreverse\",\"endDay\":\"999\",\"remarks\":null,\"keyID\":\"940b7b1bf7dedefa52ea95ff2929d757\"}}";
                                    XposedBridge.log("========== [MOCK] 返回伪造成功数据 ==========");
                                    XposedBridge.log(mockResponse);
                                    param.setResult(mockResponse);
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                String url = "";
                                try {
                                    if (param.args.length > 0 && param.args[0] != null) {
                                        url = param.args[0].toString();
                                    }
                                } catch (Throwable ignored) {}

                                boolean matched = isTarget(url);
                                if (matched) {
                                    XposedBridge.log("\n========== Http.getHttp REQUEST ==========");
                                    XposedBridge.log("url = " + url);
                                }

                                Object ret = param.getResult();
                                if (matched || isTarget(ret != null ? ret.toString() : null)) {
                                    XposedBridge.log("========== Http.getHttp RESPONSE ==========");
                                    XposedBridge.log(String.valueOf(ret));
                                }
                            }
                        });
                        XposedBridge.log("[+] hooked Http.getHttp for Mocking");
                    } catch (Throwable t) {
                        XposedBridge.log("[-] hook Http.getHttp failed: " + t);
                    }

                    // 3. Hook autoClient.userActivate
                    try {
                        XposedHelpers.findAndHookMethod(
                            "com.autoapp.autoapp.client.autoClient",
                            cl,
                            "userActivate",
                            "java.lang.String", "java.lang.String", "java.lang.String",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object ret = param.getResult();
                                    XposedBridge.log("\n========== userActivate RETURN ==========");
                                    dumpRetObj("ret", ret);
                                }
                            }
                        );
                    } catch (Throwable ignored) {}

                    // 4. Hook autoClient.getApkKey
                    try {
                        XposedHelpers.findAndHookMethod(
                            "com.autoapp.autoapp.client.autoClient",
                            cl,
                            "getApkKey",
                            "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object ret = param.getResult();
                                    XposedBridge.log("\n========== getApkKey RETURN ==========");
                                    dumpRetObj("ret", ret);
                                }
                            }
                        );
                    } catch (Throwable ignored) {}

                    XposedBridge.log("[*] Mock ready. Now click login/activate with any card code.");
                }
            }
        );
    }
}
