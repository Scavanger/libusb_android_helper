import 'package:flutter/services.dart';

/// Represents the action of an [UsbEvent]
enum UsbAction {
  /// Device has been connected
  usbDeviceAttached,

  /// Device has been disconnected
  usbDeviceDetached
}

/// Displays the return value when a USB device is connected or disconnected
class UsbEvent {
  final UsbAction _action;
  final UsbDevice _device;

  /// The relevant [UsbAction]
  UsbAction get action => _action;

  /// The relevant [UsbDevice]
  UsbDevice get device => _device;

  UsbEvent(this._action, this._device);
}

/// Provides a USB device
class UsbDevice {
  static const MethodChannel _channel =
      MethodChannel('libusb_android_helper/methods');

  final String _identifier;
  int _handle = 0;
  bool _isOpen = false;

  /// Unique identifier of the device
  String get identifier => _identifier;

  /// The file descriptor of the device.
  /// This can be passed to libusb_android to continue working with the device there.
  /// Is only valid when the device is open
  int get handle => _handle;

  /// True when the device is open
  /// [handle] is only valid when the device is open
  bool get isOpen => _isOpen;

  UsbDevice._create(this._identifier);

  /// Creates a new UsbDevice from the unique identifier
  static Future<UsbDevice> fromIdentifier(String identifier) async {
    String deviceName = await _channel
        .invokeMethod("getDeviceFromIdentifier", {"identifier": identifier});
    return UsbDevice.create(deviceName);
  }

  /// creates a new UsbDevice from the device name,
  /// in Android typically the file name of the device
  static UsbDevice create(String deviceName) => UsbDevice._create(deviceName);

  /// True if the device has the appropriate access permission
  Future<bool> hasPermission() async {
    return await _channel
        .invokeMethod("hasPermission", {"identifier": _identifier});
  }

  /// Requests the access rights from the user via the corresponding Android system dialogue
  /// True if the user has granted access rights
  Future<bool> requestPermission() async {
    return await _channel
        .invokeMethod("requestPermission", {"identifier": _identifier});
  }

  /// Open the device, handle is only valid after opening
  /// True when the device has been successfully opened
  Future<bool> open() async {
    if (_isOpen) {
      return false;
    }

    int? handle =
        await _channel.invokeMethod("open", {"identifier": _identifier});
    if (handle != null && handle != 0) {
      _isOpen = true;
      _handle = handle;
      return true;
    }
    return false;
  }

  /// Close the device, handle will then become invalid
  /// True when the device has been successfully closed
  Future<bool> close() async {
    if (!_isOpen) {
      return true;
    }
    bool? isClosed =
        await _channel.invokeMethod("close", {"identifier": _identifier});
    if (isClosed != null && isClosed) {
      _isOpen = false;
      _handle = 0;
      return true;
    }
    return false;
  }

  @override
  String toString() {
    return "UsbDevice: $_identifier";
  }

  @override
  bool operator ==(other) {
    if (other is! UsbDevice) {
      return false;
    }
    return _identifier == other._identifier;
  }

  @override
  int get hashCode {
    return _identifier.hashCode;
  }
}

/// Helper class for libusb_android to list USB devices
/// and detect currently connected or disconnected devices
class LibusbAndroidHelper {
  static const MethodChannel _channel =
      MethodChannel('libusb_android_helper/methods');
  static const EventChannel _eventChannel =
      EventChannel('libusb_android_helper/events');

  static Stream<UsbEvent>? _eventStream;

  /// Lists currently connected devices and returns a fixed length [List] of [UsbDevice] objects
  static Future<List<UsbDevice>?> listDevices() async {
    List<String>? devices = await _channel.invokeListMethod("listDevices");
    return devices!
        .map((deviceName) => UsbDevice.create(deviceName))
        .toList(growable: false);
  }

  /// Listen for connected or disconnected USB devices.
  /// Returns a [Stream] of [UsbEvent]
  static Stream<UsbEvent>? get usbEventStream {
    return _eventStream ??= _eventChannel.receiveBroadcastStream().map((value) {
      UsbAction action = UsbAction.usbDeviceAttached;
      if (value['event'] == "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
        action = UsbAction.usbDeviceAttached;
      } else if (value['event'] ==
          "android.hardware.usb.action.USB_DEVICE_DETACHED") {
        action = UsbAction.usbDeviceDetached;
      }

      return UsbEvent(action, UsbDevice.create(value["identifier"]));
    });
  }
}
