package com.demo.mybluetoothdemo.utils;

import android.content.Context;

import com.kaopiz.kprogresshud.KProgressHUD;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by admin on 2017/8/29.
 */

public class CheckUtils {
    private static String ADDRESS;

    public static void setAddrss(String addrss) {
        ADDRESS = "68" + addrss.toUpperCase() + 68;
    }

    public static KProgressHUD showDialog(Context context) {
        KProgressHUD dialog = KProgressHUD.create(context)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setCancellable(true)
                .setAnimationSpeed(1)
                .setDimAmount(0.5f);
        return dialog;
    }

    /**
     * 十六进制串转化为byte数组
     */
    public static byte[] hex2byte(String hex) {
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        char[] arr = hex.toCharArray();
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0, j = 0, l = hex.length(); i < l; i++, j++) {
            String swap = "" + arr[i++] + arr[i];
            int byteint = Integer.parseInt(swap, 16) & 0xFF;
            b[j] = new Integer(byteint).byteValue();
        }
        return b;
    }

    /**
     * 将byte数组化为十六进制串
     */
    public static StringBuilder byte2hex(byte[] data) {
        StringBuilder stringBuilder = new StringBuilder(data.length);
        for (byte byteChar : data) {
            stringBuilder.append(String.format("%02X ", byteChar).trim());
        }
        return stringBuilder;
    }

    /**
     * 判断输入的的字符是否是十六进制数
     */
    public static boolean isHexNum(String data) {
        Pattern pattern = Pattern.compile("^[A-Fa-f0-9]+$");
        Matcher mc = pattern.matcher(data);
        return mc.matches();
    }

    /**
     * 输入正常12位的地址数据，然后返回两两倒叙的字符串地址
     */
    public static String getMeterAddress(String data) {
        String meterAddress = "";
        for (int i = 10; i >= 0; i -= 2) {
            meterAddress += data.substring(i, i + 2);
        }
        return meterAddress;
    }

    /**
     * 将表地址转换为MAC地址,然后添加冒号分隔符
     */
    public static String getMacAddress(String data) {
        StringBuffer macAddress = new StringBuffer(data);
        int index;
        while ((data.split(":").length - 1) != 5) {
            index = data.lastIndexOf(":") + 3;
            macAddress.insert(index, ":");
            data = macAddress.toString();
        }
        return macAddress.toString();
    }

    /**
     * MAC地址转化表地址
     */
    public static String Mac2MeterAddress(String data) {
        data = data.replace(":", "").replace("C0", "");
        long num = Long.parseLong(data, 16);
        return getMeterAddress(String.format("%012d", num));
    }


    /**
     * 将前面的字符串算出和校验
     */
    public static String checkSum(String data) {
        byte[] nums = hex2byte(data);
        int res = 0;
        for (byte num : nums) {
            if (num >= 0) {
                res += num;
            } else {
                res += num + 256;
            }
            if (res >= 256) {
                res -= 256;
            }
        }
        return String.format("%02x", res).toUpperCase();
    }

    public static String getFullData(String data) {
        data = ADDRESS + data;
        return data + checkSum(data) + "16";//地址+数据+cs+16
    }
}
