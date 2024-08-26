# Libusb Android Helper

libusb_android has the restriction on (unrooted) Android that no USB devices can be listed and found. 
See [libusb android readme](https://github.com/libusb/libusb/blob/master/android/README)

This helper plugin closes this gap by using the native Java API to find and open the devices and retrieve the native file descriptor to continue working with it in libusb_android.

Flutter plugins cannot contain native C (ffi) and Java code at the same time, hence the split into two plugins.

## Getting Started

Add a dependency to your pubspec.yaml

```dart
dependencies:
	libusb_android_helper: ^1.0.0
```

include the libusb_android_helper package at the top of your dart file.

```dart
import 'package:libusb_android_helper/libusb_android_helper.dart';
```

### Optional

Add
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.usb.host" />
    ...
    <application ... >
        <activity ... >
        ...
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"/>
                <action android:name="android.hardware.usb.action.USB_DEVICE_DETACHED"/>
            </intent-filter>

            <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
                    android:resource="@xml/device_filter" />
            <meta-data android:name="android.hardware.usb.action.USB_DEVICE_DETACHED"
                android:resource="@xml/device_filter" />
        </activity>
    </application>
<manifest>
```
to your AndroidManifest.xml

and place device_filter.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-device vendor-id="[Your vid]" product-id="[Your pid]" />
</resources>
```
in the res/xml directory. This will notify your app when one of the specified devices
is connected or disconnected.

### Usage

List all devices already connected:

```dart
Future<List<UsbDevice>> getUsbDevices() async {
    List<UsbDevice>? devices;
    try {
        devices = await LibusbAndroidHelper.listDevices();
    } on PlatformException catch (e) {
        return List<UsbDevice>.empty();
    }
    if (devices != null) {
        return devices;
    } else {
        return List<UsbDevice>.empty();
    }
}
```

Get notified when a USB device is connected or disconnected:
```dart
LibusbAndroidHelper.usbEventStream?.listen((event) {
    try {
        if (event.action == UsbAction.usbDeviceAttached) {
            _device = event.device;
        } else if (event.action == UsbAction.usbDeviceDetached) {
            _device = null;
        }
    } on PlatformException catch (e) {
        // error
    }
});
```

Request authorization to access the USB device from the user:
```dart
if (!(await device.hasPermission())){
    await device.requestPermission();
}
```

Open the device and pass the native handle to libusb_android
```dart
const String _libName = 'libusb_android';
final DynamicLibrary _dynamicLibrary = () {
  if (Platform.isAndroid) {
    return DynamicLibrary.open('lib$_libName.so');
  }
  throw UnsupportedError('Unsupported platform: ${Platform.operatingSystem}');
}();
final LibusbAndroidBindings _bindings = LibusbAndroidBindings(_dynamicLibrary);
// ...
if (await device.open()) {
    int retValue = 0;
    Pointer<Pointer<libusb_context>> context = calloc<Pointer<libusb_context>>();
    Pointer<Pointer<libusb_device_handle>> dev_handle = calloc<Pointer<libusb_device_handle>>();
    retValue = _bindings.libusb_set_option(ctx.value, libusb_option.LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    if (retValue < 0) {
        return;
    }
    retValue =_bindings.libusb_init(context);
    if (retValue < 0) {
        return;
    }
    retValue = _bindings.libusb_wrap_sys_device(ctx.value, device.handle, dev_handle);
    if (retValue < 0) {
        return;
    }
    // Work with dev_handle
}
// ...
// Don't forget
calloc.free(context);
calloc.free(dev_handle);
```
