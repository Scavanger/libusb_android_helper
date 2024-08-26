package com.scavanger.libusb_android_helper;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** LibusbAndroidHelperPlugin */
public class LibusbAndroidHelperPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
  private static final String ACTION_USB_PERMISSION = "libusb_android_helper/permission";
  private static final String METHOD_CHANNEL = "libusb_android_helper/methods";
  private static final String EVENT_CHANNEL = "libusb_android_helper/events";

  private final String TAG = LibusbAndroidHelperPlugin.class.getSimpleName();
  private BinaryMessenger binaryMessenger = null;
  private MethodChannel channel = null;
  private EventChannel.EventSink eventSink = null;
  private EventChannel eventChannel = null;

  private Context context = null;
  private UsbManager usbManager = null;
  private UsbDeviceConnection usbDeviceConnection = null;

  private final BroadcastReceiver usbDeviceReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
      if (action == null || device == null || eventSink == null) {
        return;
      }
      HashMap<String, Object> dev = new HashMap<>();
      dev.put("identifier", device.getDeviceName());
      if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
        dev.put("event", UsbManager.ACTION_USB_DEVICE_ATTACHED);
      } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
        dev.put("event", UsbManager.ACTION_USB_DEVICE_DETACHED);
      }
      eventSink.success(dev);
    }
  };

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    binaryMessenger = flutterPluginBinding.getBinaryMessenger();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), METHOD_CHANNEL);
    channel.setMethodCallHandler(this);

    context = flutterPluginBinding.getApplicationContext();
    usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

    eventChannel = new EventChannel(binaryMessenger, EVENT_CHANNEL);
    eventChannel.setStreamHandler(this);

    registerReceiver(usbDeviceReceiver, new String[]{UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED});
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    context.unregisterReceiver(usbDeviceReceiver);
    eventChannel.setStreamHandler(null);
    binaryMessenger = null;
    usbManager = null;
    context = null;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "getDeviceFromIdentifier":
        try {
          UsbDevice device = getDeviceFromIdentifier(call.argument("identifier"));
          result.success(device.getDeviceName());
        } catch (Exception e) {
          result.error(TAG, e.getMessage(), null);
        }
        break;
      case "listDevices":
        try {
          if (usbManager == null) {
            result.error(TAG, "UsbManager is null", null);
          }
          Map<String, UsbDevice> devices = usbManager.getDeviceList();
          if (devices == null) {
            result.error(TAG, "Could not get USB device list.", null);
            return;
          }
          ArrayList<String> devicesArray = new ArrayList<>();
          for (UsbDevice device : devices.values()) {
            devicesArray.add(device.getDeviceName());
          }
          result.success(devicesArray);
        } catch (Exception e) {
          result.error(TAG, e.getMessage(), null);
        }
        break;
      case "hasPermission":
        try {
          UsbDevice device = getDeviceFromIdentifier(call.argument("identifier"));
          result.success(hasPermission(device));
        } catch (Exception e) {
          result.error(TAG, e.getMessage(), null);
        }
        break;
      case "requestPermission":
        try {
          UsbDevice device = getDeviceFromIdentifier(call.argument("identifier"));
          if (device == null) {
            result.error(TAG, "Device \"" + call.argument("identifier") + "\" doesn't exist." , null);
          }
          if (hasPermission(device)) {
            result.success(true);
          }
          BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              synchronized (this) {
                context.unregisterReceiver(this);
                result.success(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
              }
            }
          };
          registerReceiver(receiver, new String[] { ACTION_USB_PERMISSION });
          usbManager.requestPermission(device, getBroadcastIntent());
        } catch (Exception e) {
          result.error(TAG, e.getMessage(), null);
        }
        break;
      case "open":
        try {
          if (usbManager == null) {
            result.error(TAG, "UsbManager is null", null);
          }
          UsbDevice device = getDeviceFromIdentifier(call.argument("identifier"));
          if (device == null) {
            result.error(TAG, "Device \"" + call.argument("identifier") + "\" doesn't exist.", null);
            break;
          }
          if (!hasPermission(device)) {
            result.error(TAG, "No permission to open this device", null);
            break;
          }
          usbDeviceConnection = usbManager.openDevice(device);
          if (usbDeviceConnection != null) {
            result.success(usbDeviceConnection.getFileDescriptor());
            return;
          }

          result.success( null);
        } catch (Exception e) {
          result.error(TAG, e.getMessage(), null);
        }
        break;
      case "close":
        try {
          if (usbDeviceConnection != null) {
            usbDeviceConnection.close();
            usbDeviceConnection = null;
          }
          result.success(true);
        } catch (Exception e) {
          result.error(TAG, e.getMessage(), null);
        }
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    eventSink = events;
  }

  @Override
  public void onCancel(Object arguments) {
    eventSink = null;
  }

  private PendingIntent getBroadcastIntent() {
    if (Build.VERSION.SDK_INT >=  Build.VERSION_CODES.S) {
      return PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
    } else {
      return PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
    }
  }

  private void registerReceiver(BroadcastReceiver receiver, String[] filterActions) {

    IntentFilter filter = new IntentFilter();
    for (String action : filterActions) {
      filter.addAction(action);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      context.registerReceiver(receiver, filter);
    }
  }

  private UsbDevice getDeviceFromIdentifier(String identifier)  {
    if (usbManager == null) {
      throw new IllegalStateException("Usb manager is null");
    }
    if (identifier == null || identifier.isBlank()) {
      throw new IllegalArgumentException("Argument identifier is null or blank");
    }
    return usbManager.getDeviceList().get(identifier);
  }

  private Boolean hasPermission(UsbDevice device) {
    if (device == null) {
      throw new IllegalArgumentException("device is null");
    }
    return usbManager.hasPermission(device);
  }
}
